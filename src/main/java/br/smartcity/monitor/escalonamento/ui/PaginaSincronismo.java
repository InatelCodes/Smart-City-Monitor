package br.smartcity.monitor.escalonamento.ui;

import br.smartcity.monitor.escalonamento.model.ProcessoUrbano;
import br.smartcity.monitor.escalonamento.sincronismo.ComparacaoSincronismo;
import br.smartcity.monitor.escalonamento.sincronismo.ModoSincronizacao;
import br.smartcity.monitor.escalonamento.sincronismo.ResultadoSincronismo;
import br.smartcity.monitor.escalonamento.sincronismo.SimuladorSincronismo;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Página 2: escrita concorrente no arquivo, sem lock, com lock ou os dois. */
final class PaginaSincronismo extends VBox {

    enum Modo {
        SEM("Sem sincronismo"), COM("Com sincronismo"), AMBOS("Comparar os dois");

        private final String rotulo;

        Modo(String rotulo) {
            this.rotulo = rotulo;
        }

        String rotulo() {
            return rotulo;
        }
    }

    private record Par(ResultadoSincronismo sem, ResultadoSincronismo com) {
    }

    private static final String COR_SEM = "#d9534f";
    private static final String COR_COM = "#2f9e6b";
    private static final Path DIRETORIO = Path.of("target", "experimento-sincronismo");

    private final EscalonamentoController ctrl;
    private final Segmentado<Modo> modo = new Segmentado<>(List.of(Modo.values()), Modo::rotulo);
    private final Label explicacao = Tema.rotulo("", "pagina-sub");
    private final Button executar = new Button("▶  Executar");
    private final VBox area = new VBox(16);
    private final ResultadosPorModo<Modo> guardados = new ResultadosPorModo<>(Modo.class);

    PaginaSincronismo(EscalonamentoController ctrl) {
        super(16);
        this.ctrl = ctrl;
        getStyleClass().add("pagina");

        Label titulo = Tema.rotulo("O que acontece quando os sensores escrevem no mesmo arquivo?", "pagina-titulo");
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
        explicacao.setText(switch (modo.valor()) {
            case SEM -> "Uma thread por processo, todas escrevendo ao mesmo tempo, sem lock: os registros se misturam (condição de corrida).";
            case COM -> "As mesmas threads, mas com ReentrantLock: só uma entra na região crítica por vez e cada registro sai inteiro.";
            case AMBOS -> "Mesma carga executada nos dois modos, alternando as rodadas. A duração é a mediana de "
                    + SimuladorSincronismo.REPETICOES_PADRAO + " execuções (após aquecimento da JVM): em ms de relógio, uma única execução oscila bastante.";
        });
    }

    /** Descarta todos os resultados guardados (a carga mudou). */
    void limpar() {
        guardados.descartar();
        exibir();
    }

    /** Mostra o resultado guardado do modo atual, ou o estado vazio se ele ainda não foi executado. */
    private void exibir() {
        ResultadosPorModo.Entrada guardado = guardados.obter(modo.valor());
        if (guardado == null) {
            mostrarVazio();
        } else {
            area.getChildren().setAll(guardado.blocos());
        }
    }

    private void mostrarVazio() {
        Label vazio = Tema.rotulo(
                "Escolha o modo acima e clique em Executar.\nUma thread de sensor é criada para cada processo da carga "
                        + "(a duração real das threads não depende dos bursts).",
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
        Set<String> esperadas = new HashSet<>();
        for (ProcessoUrbano p : carga) {
            esperadas.add(linhaEsperada(p));
        }
        Modo escolhido = modo.valor();

        ProgressIndicator espera = new ProgressIndicator();
        espera.setMaxSize(36, 36);
        VBox carregando = new VBox(10, espera, Tema.rotulo("Disparando as threads...", "vazio"));
        carregando.setAlignment(Pos.CENTER);
        carregando.getStyleClass().addAll("card", "vazio-caixa");
        area.getChildren().setAll(carregando);
        executar.setDisable(true);

        Task<Par> tarefa = new Task<>() {
            @Override
            protected Par call() throws Exception {
                Files.createDirectories(DIRETORIO);
                if (escolhido == Modo.AMBOS) {
                    ComparacaoSincronismo c = ctrl.compararSincronismo(DIRETORIO);
                    return new Par(c.semSincronismo(), c.comSincronismo());
                }
                ModoSincronizacao m = escolhido == Modo.SEM
                        ? ModoSincronizacao.SEM_SINCRONISMO : ModoSincronizacao.COM_SINCRONISMO;
                ctrl.setModoSincronizacao(m);
                ResultadoSincronismo unico = ctrl.executarExperimentoSincronismo(
                        DIRETORIO.resolve(escolhido == Modo.SEM ? "sem-sincronismo.txt" : "com-sincronismo.txt"));
                return new Par(
                        m == ModoSincronizacao.SEM_SINCRONISMO ? unico : null,
                        m == ModoSincronizacao.COM_SINCRONISMO ? unico : null);
            }
        };
        tarefa.setOnSucceeded(e -> {
            executar.setDisable(false);
            mostrar(escolhido, tarefa.getValue(), esperadas);
        });
        tarefa.setOnFailed(e -> {
            executar.setDisable(false);
            exibir();
            Throwable erro = tarefa.getException();
            new Alert(Alert.AlertType.ERROR, String.valueOf(erro.getMessage()), ButtonType.OK).showAndWait();
        });
        Thread t = new Thread(tarefa, "experimento-sincronismo");
        t.setDaemon(true);
        t.start();
    }

    private void mostrar(Modo m, Par c, Set<String> esperadas) {
        List<javafx.scene.Node> blocos = new java.util.ArrayList<>();
        switch (m) {
            case SEM -> blocos.add(new CartaoSync(c.sem(), esperadas));
            case COM -> blocos.add(new CartaoSync(c.com(), esperadas));
            case AMBOS -> {
                CartaoSync sem = new CartaoSync(c.sem(), esperadas);
                CartaoSync com = new CartaoSync(c.com(), esperadas);
                HBox lado = new HBox(16, sem, com);
                HBox.setHgrow(sem, Priority.ALWAYS);
                HBox.setHgrow(com, Priority.ALWAYS);
                sem.setMaxWidth(Double.MAX_VALUE);
                com.setMaxWidth(Double.MAX_VALUE);
                sem.setPrefWidth(10);
                com.setPrefWidth(10);
                blocos.add(lado);
                blocos.add(placar(c));
            }
        }
        guardados.guardar(m, blocos, 0);
        // o usuário pode ter trocado de modo enquanto as threads rodavam
        if (modo.valor() == m) {
            area.getChildren().setAll(blocos);
        } else {
            exibir();
        }
    }

    // --------------------------------------------------------------- comparação

    private VBox placar(Par c) {
        ResultadoSincronismo s = c.sem();
        ResultadoSincronismo k = c.com();
        GridPane g = new GridPane();
        g.setVgap(12);
        g.setHgap(16);
        ColumnConstraints c0 = new ColumnConstraints(190);
        ColumnConstraints c1 = new ColumnConstraints();
        c1.setHgrow(Priority.ALWAYS);
        g.getColumnConstraints().addAll(c0, c1);

        linha(g, 0, "Registros íntegros", "maior é melhor", s.registrosValidos(), k.registrosValidos(), s.registrosEsperados(), true);
        linha(g, 1, "Duração (ms, mediana)", "menor é melhor", s.duracaoMs(), k.duracaoMs(), 0, false);
        linha(g, 2, "Espera pelo lock (ms)", "só existe com lock", s.tempoMedioEsperaMs(), k.tempoMedioEsperaMs(), 0, false);

        HBox chave = new HBox(16, chave(COR_SEM, "Sem sincronismo"), chave(COR_COM, "Com sincronismo"));
        chave.setAlignment(Pos.CENTER_RIGHT);
        HBox topo = new HBox(Tema.rotulo("PLACAR — MESMA CARGA, DOIS MODOS", "eyebrow"), Tema.espaco(), chave);

        VBox leitura = new VBox(4, Tema.rotulo("LEITURA RÁPIDA", "eyebrow"));
        leitura.getStyleClass().add("leitura");
        for (String frase : frases(s, k)) {
            Label l = Tema.rotulo(frase, "leitura-texto");
            l.setWrapText(true);
            leitura.getChildren().add(l);
        }
        VBox card = new VBox(14, topo, g, leitura);
        card.getStyleClass().add("card");
        card.setPadding(new Insets(16, 18, 16, 18));
        return card;
    }

    private void linha(GridPane g, int i, String nome, String dica, double a, double b, double esperado, boolean inteiro) {
        double max = Math.max(a, b) <= 0 ? 1 : Math.max(Math.max(a, b), esperado);
        VBox rotulo = new VBox(1, Tema.rotulo(nome, "placar-nome"), Tema.rotulo(dica, "placar-dica"));
        rotulo.setAlignment(Pos.CENTER_LEFT);
        String ta = inteiro ? (int) a + " de " + (int) esperado : Tema.num(a);
        String tb = inteiro ? (int) b + " de " + (int) esperado : Tema.num(b);
        VBox barras = new VBox(2, new BarraValor(ta, a / max, COR_SEM, true), new BarraValor(tb, b / max, COR_COM, true));
        g.add(rotulo, 0, i);
        g.add(barras, 1, i);
    }

    private HBox chave(String cor, String texto) {
        javafx.scene.layout.Region q = new javafx.scene.layout.Region();
        q.setMinSize(14, 14);
        q.setMaxSize(14, 14);
        q.setStyle("-fx-background-color:" + cor + ";-fx-background-radius:4;");
        HBox h = new HBox(6, q, Tema.rotulo(texto, "legenda"));
        h.setAlignment(Pos.CENTER_LEFT);
        return h;
    }

    private static List<String> frases(ResultadoSincronismo s, ResultadoSincronismo k) {
        String integridade = s.registrosCorrompidos() == 0
                ? "Nesta execução o modo sem lock não chegou a corromper (a colisão é provável, não garantida) — rode de novo."
                : "Sem lock, " + s.registrosCorrompidos() + " de " + s.registrosEsperados() + " registros saíram corrompidos; com lock, "
                + k.registrosCorrompidos() + " de " + k.registrosEsperados() + ".";
        String custo = "Com lock, cada thread esperou em média " + Tema.num(k.tempoMedioEsperaMs())
                + " ms para entrar na região crítica. Duração (mediana): " + Tema.num(k.duracaoMs()) + " ms com lock e "
                + Tema.num(s.duracaoMs()) + " ms sem lock.";
        String nota = k.duracaoMs() < s.duracaoMs()
                ? "Com lock ficou mais rápido porque, sem lock, as threads aguardam numa barreira (CyclicBarrier) que força a "
                + "colisão e dormem 1 ms entre fragmentos; o experimento mede colisão, não desempenho (ver README)."
                : "Sem lock as threads rodam em paralelo (mas esperam numa barreira para forçar a colisão); com lock elas "
                + "se serializam, então a duração com lock tende a ser maior.";
        return List.of(integridade, custo, nota);
    }

    /** Mesma regra de RegistroCompartilhado.linhaCompleta (que é package-private). */
    private static String linhaEsperada(ProcessoUrbano p) {
        return "INICIO|" + sanitizar(p.id()) + "|" + p.tipoEvento() + "|" + sanitizar(p.descricao()) + "|FIM";
    }

    private static String sanitizar(String valor) {
        return valor.replace("\\", "\\\\").replace("|", "\\|").replace("\r", "\\r").replace("\n", "\\n");
    }

    // --------------------------------------------------------------- cartão

    private static final class CartaoSync extends VBox {

        CartaoSync(ResultadoSincronismo r, Set<String> esperadas) {
            super(12);
            boolean com = r.modo() == ModoSincronizacao.COM_SINCRONISMO;
            boolean integro = r.registrosCorrompidos() == 0;
            getStyleClass().add("card");
            setPadding(new Insets(16, 18, 16, 18));

            Label nome = Tema.rotulo(com ? "Com sincronismo" : "Sem sincronismo", "algo-nome");
            Label desc = Tema.rotulo(com ? "ReentrantLock" : "escrita livre", "algo-desc");
            HBox cab = new HBox(10, nome, desc);
            cab.setAlignment(Pos.CENTER_LEFT);

            Label banner = new Label(integro
                    ? "✓  ARQUIVO ÍNTEGRO — " + r.registrosValidos() + " de " + r.registrosEsperados() + " registros completos"
                    : "⚠  COLISÃO DETECTADA — " + r.registrosCorrompidos() + " de " + r.registrosEsperados() + " registros corrompidos");
            banner.getStyleClass().addAll("banner", integro ? "banner-ok" : "banner-erro");
            banner.setMaxWidth(Double.MAX_VALUE);

            ProgressBar integridade = new ProgressBar((double) r.registrosValidos() / r.registrosEsperados());
            integridade.getStyleClass().add("barra-integridade");
            integridade.setMaxWidth(Double.MAX_VALUE);

            HBox metricas = new HBox(14,
                    mini("DURAÇÃO (MEDIANA)", Tema.num(r.duracaoMs()) + " ms"),
                    mini("ESPERA MÉDIA PELO LOCK", com ? Tema.num(r.tempoMedioEsperaMs()) + " ms" : "— (sem lock)"));

            VBox linhas = new VBox(4);
            String[] partes = r.conteudoArquivo().split("\n");
            for (String parte : partes) {
                if (parte.isEmpty()) {
                    continue;
                }
                boolean valida = esperadas.contains(parte);
                Label marca = Tema.rotulo(valida ? "✓" : "✕", "marca", valida ? "marca-ok" : "marca-erro");
                Label texto = Tema.rotulo(parte, "linha-arquivo");
                texto.setWrapText(true);
                HBox.setHgrow(texto, Priority.ALWAYS);
                HBox linha = new HBox(8, marca, texto);
                linha.setAlignment(Pos.CENTER_LEFT);
                linha.getStyleClass().addAll("linha-caixa", valida ? "linha-ok" : "linha-erro");
                linhas.getChildren().add(linha);
            }
            ScrollPane rolagem = new ScrollPane(linhas);
            rolagem.setFitToWidth(true);
            rolagem.setPrefViewportHeight(Math.min(230, linhas.getChildren().size() * 32 + 8));
            rolagem.setMaxHeight(260);
            rolagem.getStyleClass().add("rolagem-arquivo");
            Label caminho = Tema.rotulo("ARQUIVO  " + r.arquivo().getFileName(), "eyebrow");

            getChildren().addAll(cab, banner, integridade, metricas, caminho, rolagem);
        }

        private static VBox mini(String titulo, String valor) {
            VBox v = new VBox(2, Tema.rotulo(titulo, "eyebrow"), Tema.rotulo(valor, "mini-valor"));
            v.getStyleClass().add("mini");
            HBox.setHgrow(v, Priority.ALWAYS);
            return v;
        }
    }
}
