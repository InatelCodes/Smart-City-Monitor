package br.smartcity.monitor.escalonamento.ui;

import br.smartcity.monitor.escalonamento.model.MetricasEscalonamento;
import br.smartcity.monitor.escalonamento.model.ProcessoUrbano;
import br.smartcity.monitor.escalonamento.sincronismo.ExecucaoIntegrada;
import br.smartcity.monitor.escalonamento.sincronismo.ModoSincronizacao;
import br.smartcity.monitor.escalonamento.sincronismo.PoliticaEscalonamento;
import br.smartcity.monitor.escalonamento.sincronismo.SimuladorEscalonamentoSincronizado;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToDoubleFunction;

/**
 * Página 2: os escalonadores rodando a MESMA carga com e sem sincronismo.
 * Tempo lógico (unidades de CPU): o resultado é idêntico a cada execução.
 */
final class PaginaIntegrada extends VBox {

    private record Coluna(String nome, String curto, String cor, ExecucaoIntegrada execucao) {
    }

    private record Metrica(String nome, String dica, ToDoubleFunction<ExecucaoIntegrada> f,
                           boolean menorMelhor, boolean inteiro) {
    }

    private static final List<Metrica> METRICAS = List.of(
            new Metrica("Registros íntegros", "maior é melhor", e -> e.registrosValidos(), false, true),
            new Metrica("Espera média", "inclui o tempo bloqueado", e -> e.resultado().metricas().tempoMedioEspera(), true, false),
            new Metrica("Resposta média", "menor é melhor", e -> e.resultado().metricas().tempoMedioResposta(), true, false),
            new Metrica("Retorno médio", "menor é melhor", e -> e.resultado().metricas().tempoMedioRetorno(), true, false),
            new Metrica("Duração total", "menor é melhor", e -> e.resultado().metricas().makespan(), true, true),
            new Metrica("Trocas de contexto", "menos é melhor", e -> e.resultado().metricas().trocasContexto(), true, true),
            new Metrica("Tempo bloqueado", "soma no lock, todos os processos", e -> e.tempoBloqueadoTotal(), true, true));

    private static final String COR_FCFS_SEM = "#8aa0ab";
    private static final String COR_FCFS_COM = "#173b4a";
    private static final String COR_RR_SEM = "#8ccfc8";
    private static final String COR_RR_COM = "#2b9b91";

    private final EscalonamentoController ctrl;
    private final Segmentado<PaginaCpu.Modo> modo =
            new Segmentado<>(List.of(PaginaCpu.Modo.values()), PaginaCpu.Modo::rotulo);
    private final Label explicacao = Tema.rotulo("", "pagina-sub");
    private final Button executar = new Button("▶  Executar");
    private final VBox area = new VBox(16);
    private final DoubleProperty tempo = new SimpleDoubleProperty();
    private final BarraReproducao reproducao = new BarraReproducao(tempo);
    private final ResultadosPorModo<PaginaCpu.Modo> guardados = new ResultadosPorModo<>(PaginaCpu.Modo.class);

    PaginaIntegrada(EscalonamentoController ctrl) {
        super(16);
        this.ctrl = ctrl;
        getStyleClass().add("pagina");

        Label titulo = Tema.rotulo("O lock muda o jeito como cada escalonador atende os eventos?", "pagina-titulo");
        VBox cabecalho = new VBox(4, titulo, explicacao);
        executar.getStyleClass().add("btn-primario");
        executar.setOnAction(e -> executar());
        HBox barra = new HBox(14, modo, Tema.espaco(), executar);
        barra.setAlignment(Pos.CENTER_LEFT);
        modo.valorProperty().addListener((obs, a, b) -> {
            atualizarExplicacao();
            exibir();
        });
        atualizarExplicacao();
        exibir();
        getChildren().addAll(cabecalho, barra, area);
    }

    private void atualizarExplicacao() {
        String base = "Mesma carga, mesmos parâmetros, tempo lógico (ut): o resultado é idêntico a cada execução. "
                + "Ao fim do burst, cada processo grava um registro no arquivo (região crítica). ";
        explicacao.setText(base + switch (modo.valor()) {
            case FCFS -> "O FCFS não preempta: veja se sincronizar muda alguma coisa.";
            case RR -> "No Round Robin a preempção pode cortar uma escrita no meio.";
            case AMBOS -> "Os dois escalonadores lado a lado, cada um sem e com lock.";
        });
    }

    /** Descarta todos os resultados guardados (carga ou parâmetros mudaram). */
    void limpar() {
        guardados.descartar();
        exibir();
    }

    /** Mostra o resultado guardado do modo atual, ou o estado vazio se ele ainda não foi executado. */
    private void exibir() {
        reproducao.parar();
        ResultadosPorModo.Entrada guardado = guardados.obter(modo.valor());
        if (guardado == null) {
            mostrarVazio();
            return;
        }
        reproducao.configurar(guardado.tempoMax());
        area.getChildren().setAll(guardado.blocos());
    }

    private void mostrarVazio() {
        Label vazio = Tema.rotulo(
                "Escolha o escalonador acima e clique em Executar.\nCada um roda duas vezes: sem sincronismo e com sincronismo.",
                "vazio");
        vazio.setAlignment(Pos.CENTER);
        vazio.setMaxWidth(Double.MAX_VALUE);
        VBox caixa = new VBox(vazio);
        caixa.getStyleClass().addAll("card", "vazio-caixa");
        caixa.setAlignment(Pos.CENTER);
        area.getChildren().setAll(caixa);
    }

    private void executar() {
        List<ProcessoUrbano> carga = ctrl.obterProcessos();
        if (carga.isEmpty()) {
            new Alert(Alert.AlertType.WARNING, "Cadastre pelo menos um processo na barra lateral.", ButtonType.OK).showAndWait();
            return;
        }
        try {
            int q = ctrl.getQuantum();
            int c = ctrl.getCustoTrocaContexto();
            int w = ctrl.getTamanhoEscrita();
            var sim = new SimuladorEscalonamentoSincronizado();
            PaginaCpu.Modo m = modo.valor();
            if (m != PaginaCpu.Modo.FCFS) {
                EscalonamentoController.validarFatias(carga, q);
            }
            ExecucaoIntegrada fSem = null;
            ExecucaoIntegrada fCom = null;
            ExecucaoIntegrada rSem = null;
            ExecucaoIntegrada rCom = null;
            if (m != PaginaCpu.Modo.RR) {
                fSem = sim.executar(carga, PoliticaEscalonamento.FCFS, q, c, w, ModoSincronizacao.SEM_SINCRONISMO);
                fCom = sim.executar(carga, PoliticaEscalonamento.FCFS, q, c, w, ModoSincronizacao.COM_SINCRONISMO);
            }
            if (m != PaginaCpu.Modo.FCFS) {
                rSem = sim.executar(carga, PoliticaEscalonamento.ROUND_ROBIN, q, c, w, ModoSincronizacao.SEM_SINCRONISMO);
                rCom = sim.executar(carga, PoliticaEscalonamento.ROUND_ROBIN, q, c, w, ModoSincronizacao.COM_SINCRONISMO);
            }
            mostrar(m, fSem, fCom, rSem, rCom, q);
        } catch (RuntimeException ex) {
            new Alert(Alert.AlertType.ERROR, String.valueOf(ex.getMessage()), ButtonType.OK).showAndWait();
        }
    }

    private static int fim(ExecucaoIntegrada e) {
        return e == null ? 0 : e.resultado().metricas().makespan();
    }

    private void mostrar(PaginaCpu.Modo m, ExecucaoIntegrada fSem, ExecucaoIntegrada fCom,
                         ExecucaoIntegrada rSem, ExecucaoIntegrada rCom, int quantum) {
        int tempoMax = Math.max(Math.max(fim(fSem), fim(fCom)), Math.max(fim(rSem), fim(rCom)));
        reproducao.configurar(tempoMax);

        List<Node> blocos = new ArrayList<>();
        blocos.add(reproducao);
        blocos.add(legenda());

        List<Coluna> colunas = new ArrayList<>();
        if (m == PaginaCpu.Modo.AMBOS) {
            GridPane grade = new GridPane();
            grade.setHgap(16);
            grade.setVgap(16);
            ColumnConstraints c0 = new ColumnConstraints();
            c0.setPercentWidth(50);
            ColumnConstraints c1 = new ColumnConstraints();
            c1.setPercentWidth(50);
            grade.getColumnConstraints().addAll(c0, c1);
            grade.add(cartao(fSem, quantum, tempoMax), 0, 0);
            grade.add(cartao(rSem, quantum, tempoMax), 1, 0);
            grade.add(cartao(fCom, quantum, tempoMax), 0, 1);
            grade.add(cartao(rCom, quantum, tempoMax), 1, 1);
            blocos.add(grade);
        } else {
            ExecucaoIntegrada sem = fSem != null ? fSem : rSem;
            ExecucaoIntegrada com = fCom != null ? fCom : rCom;
            blocos.add(cartao(sem, quantum, tempoMax));
            blocos.add(cartao(com, quantum, tempoMax));
        }
        if (fSem != null) {
            colunas.add(new Coluna("FCFS sem lock", "FCFS", COR_FCFS_SEM, fSem));
            colunas.add(new Coluna("FCFS com lock", "FCFS+lock", COR_FCFS_COM, fCom));
        }
        if (rSem != null) {
            colunas.add(new Coluna("Round Robin sem lock", "RR", COR_RR_SEM, rSem));
            colunas.add(new Coluna("Round Robin com lock", "RR+lock", COR_RR_COM, rCom));
        }
        blocos.add(placar(colunas, fSem, fCom, rSem, rCom));
        guardados.guardar(m, blocos, tempoMax);
        area.getChildren().setAll(blocos);
    }

    private CartaoAlgoritmo cartao(ExecucaoIntegrada e, int quantum, int tempoMax) {
        String titulo = e.politica().nome() + " · " + (e.comSincronismo() ? "com sincronismo" : "sem sincronismo");
        String desc = (e.politica() == PoliticaEscalonamento.ROUND_ROBIN ? "quantum " + quantum + " · " : "não preemptivo · ")
                + (e.comSincronismo() ? "lock no arquivo" : "escrita livre");
        CartaoAlgoritmo c = new CartaoAlgoritmo(titulo, desc, e, tempoMax, tempo);
        c.setMaxWidth(Double.MAX_VALUE);
        return c;
    }

    // ---------------------------------------------------------------- legenda

    private FlowPane legenda() {
        FlowPane f = new FlowPane(18, 6);
        f.setPadding(new Insets(0, 4, 0, 4));
        f.getChildren().addAll(
                item("#1fa89a", "executando"),
                item("#1fa89a55", "esperando na fila"),
                item("#64748b", "troca de contexto"),
                item("#102f3daa", "escrevendo no arquivo (região crítica)"),
                item("#d9534f44", "bloqueado esperando o lock"),
                item("#d9534f", "linha fina vermelha = registro corrompido"));
        return f;
    }

    private HBox item(String cor, String texto) {
        Region q = new Region();
        q.setMinSize(14, 14);
        q.setMaxSize(14, 14);
        q.setStyle("-fx-background-color:" + cor + ";-fx-background-radius:4;");
        HBox h = new HBox(6, q, Tema.rotulo(texto, "legenda"));
        h.setAlignment(Pos.CENTER_LEFT);
        return h;
    }

    // ---------------------------------------------------------------- placar

    private VBox placar(List<Coluna> colunas, ExecucaoIntegrada fSem, ExecucaoIntegrada fCom,
                        ExecucaoIntegrada rSem, ExecucaoIntegrada rCom) {
        GridPane g = new GridPane();
        g.setVgap(14);
        g.setHgap(16);
        ColumnConstraints c0 = new ColumnConstraints(170);
        ColumnConstraints c1 = new ColumnConstraints();
        c1.setHgrow(Priority.ALWAYS);
        ColumnConstraints c2 = new ColumnConstraints(170);
        g.getColumnConstraints().addAll(c0, c1, c2);

        int linha = 0;
        for (Metrica m : METRICAS) {
            double melhor = m.menorMelhor() ? Double.MAX_VALUE : -Double.MAX_VALUE;
            double max = 0;
            for (Coluna c : colunas) {
                double v = m.f().applyAsDouble(c.execucao());
                melhor = m.menorMelhor() ? Math.min(melhor, v) : Math.max(melhor, v);
                max = Math.max(max, v);
            }
            if (m.nome().equals("Registros íntegros")) {
                max = colunas.get(0).execucao().registrosEsperados();
            }
            if (max <= 0) {
                max = 1;
            }
            VBox nome = new VBox(1, Tema.rotulo(m.nome(), "placar-nome"), Tema.rotulo(m.dica(), "placar-dica"));
            nome.setAlignment(Pos.CENTER_LEFT);
            VBox barras = new VBox(2);
            List<String> vencedores = new ArrayList<>();
            for (Coluna c : colunas) {
                double v = m.f().applyAsDouble(c.execucao());
                boolean vence = Math.abs(v - melhor) < 0.005;
                if (vence) {
                    vencedores.add(c.curto());
                }
                String texto = m.nome().equals("Registros íntegros")
                        ? (int) v + " de " + c.execucao().registrosEsperados()
                        : m.inteiro() ? Tema.inteiro(Math.round(v)) : Tema.num(v);
                barras.getChildren().add(new BarraValor(texto, v / max, c.cor(), vence));
            }
            Label melhores = Tema.rotulo(vencedores.size() == colunas.size() ? "todos iguais" : "melhor: " + String.join(" · ", vencedores), "placar-dica");
            melhores.setWrapText(true);
            g.add(nome, 0, linha);
            g.add(barras, 1, linha);
            g.add(melhores, 2, linha);
            linha++;
        }

        FlowPane chave = new FlowPane(16, 4);
        chave.setAlignment(Pos.CENTER_RIGHT);
        for (Coluna c : colunas) {
            chave.getChildren().add(item(c.cor(), c.nome()));
        }
        HBox topo = new HBox(12, Tema.rotulo("PLACAR — MESMA CARGA, SEM E COM SINCRONISMO", "eyebrow"), Tema.espaco(), chave);
        topo.setAlignment(Pos.CENTER_LEFT);

        VBox leitura = new VBox(5, Tema.rotulo("LEITURA RÁPIDA", "eyebrow"));
        leitura.getStyleClass().add("leitura");
        for (String frase : leitura(fSem, fCom, rSem, rCom)) {
            Label l = Tema.rotulo(frase, "leitura-texto");
            l.setWrapText(true);
            leitura.getChildren().add(l);
        }
        VBox card = new VBox(14, topo, g, leitura);
        card.getStyleClass().add("card");
        card.setPadding(new Insets(16, 18, 16, 18));
        return card;
    }

    private static List<String> leitura(ExecucaoIntegrada fSem, ExecucaoIntegrada fCom,
                                        ExecucaoIntegrada rSem, ExecucaoIntegrada rCom) {
        List<String> frases = new ArrayList<>();
        if (fSem != null) {
            frases.addAll(frasesDaPolitica(fSem, fCom));
        }
        if (rSem != null) {
            frases.addAll(frasesDaPolitica(rSem, rCom));
        }
        if (fCom != null && rCom != null) {
            MetricasEscalonamento f = fCom.resultado().metricas();
            MetricasEscalonamento r = rCom.resultado().metricas();
            String quem = r.tempoMedioResposta() < f.tempoMedioResposta() ? "Round Robin"
                    : f.tempoMedioResposta() < r.tempoMedioResposta() ? "FCFS" : "nenhum (empate)";
            frases.add("Com lock nos dois, quem começa a atender os eventos mais cedo: " + quem
                    + " (resposta média " + Tema.num(f.tempoMedioResposta()) + " no FCFS vs " + Tema.num(r.tempoMedioResposta()) + " no RR).");
        }
        return frases;
    }

    private static List<String> frasesDaPolitica(ExecucaoIntegrada sem, ExecucaoIntegrada com) {
        String nome = sem.politica().nome();
        int n = sem.registrosEsperados();
        List<String> frases = new ArrayList<>();
        if (sem.registrosCorrompidos() == 0) {
            frases.add(nome + ": mesmo sem lock nenhum registro foi corrompido"
                    + (sem.politica() == PoliticaEscalonamento.FCFS
                    ? " — sem preempção, ninguém interrompe uma escrita em andamento."
                    : " — nesta carga nenhuma escrita foi cortada por outro processo."));
        } else {
            frases.add(nome + ": sem lock, " + sem.registrosCorrompidos() + " de " + n
                    + " registros saíram corrompidos (a preempção cortou escritas no meio); com lock, "
                    + com.registrosCorrompidos() + " de " + n + ".");
        }

        MetricasEscalonamento a = sem.resultado().metricas();
        MetricasEscalonamento b = com.resultado().metricas();
        List<String> melhorou = new ArrayList<>();
        List<String> piorou = new ArrayList<>();
        comparar("espera média", a.tempoMedioEspera(), b.tempoMedioEspera(), false, melhorou, piorou);
        comparar("resposta média", a.tempoMedioResposta(), b.tempoMedioResposta(), false, melhorou, piorou);
        comparar("retorno médio", a.tempoMedioRetorno(), b.tempoMedioRetorno(), false, melhorou, piorou);
        comparar("duração total", a.makespan(), b.makespan(), true, melhorou, piorou);
        comparar("trocas de contexto", a.trocasContexto(), b.trocasContexto(), true, melhorou, piorou);

        if (melhorou.isEmpty() && piorou.isEmpty()) {
            frases.add(nome + ": o lock não mudou nenhum tempo — ninguém precisou esperar por ele.");
            return frases;
        }
        if (!piorou.isEmpty()) {
            frases.add(nome + ": o lock custou — " + String.join("; ", piorou) + ". Os processos somaram "
                    + Tema.inteiro(com.tempoBloqueadoTotal()) + " ut dormindo esperando o arquivo.");
        }
        if (!melhorou.isEmpty()) {
            frases.add(nome + ": o lock até melhorou — " + String.join("; ", melhorou)
                    + ". Quem encontra o arquivo ocupado dorme sem gastar CPU nem troca de contexto e o dono do lock "
                    + "termina sem ser interrompido: o escalonamento fica mais perto de \"rodar até acabar\", "
                    + "que tem menos trocas (explicação no README).");
        }
        return frases;
    }

    private static void comparar(String nome, double sem, double com, boolean inteiro,
                                 List<String> melhorou, List<String> piorou) {
        if (Math.abs(sem - com) < 0.005) {
            return;
        }
        String texto = nome + " " + (inteiro ? Tema.inteiro(Math.round(sem)) : Tema.num(sem))
                + " → " + (inteiro ? Tema.inteiro(Math.round(com)) : Tema.num(com));
        (com < sem ? melhorou : piorou).add(texto);
    }
}
