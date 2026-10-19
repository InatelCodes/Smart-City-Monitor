package br.smartcity.monitor.escalonamento.ui;

import br.smartcity.monitor.escalonamento.model.ProcessoResultado;
import br.smartcity.monitor.escalonamento.model.ResultadoEscalonamento;
import br.smartcity.monitor.escalonamento.model.TipoTrechoExecucao;
import br.smartcity.monitor.escalonamento.model.TrechoExecucao;
import br.smartcity.monitor.escalonamento.sincronismo.Bloqueio;
import br.smartcity.monitor.escalonamento.sincronismo.ExecucaoIntegrada;
import br.smartcity.monitor.escalonamento.sincronismo.RegistroSimulado;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Um escalonador: estado da CPU, fila de pronto e Gantt no tempo atual. */
final class CartaoAlgoritmo extends VBox {

    private final ResultadoEscalonamento resultado;
    private final ExecucaoIntegrada integrada;
    private final Label statusArquivo = new Label();
    private final FlowPane bloqueados = new FlowPane(6, 6);
    private final int fimGantt;
    private final Label relogio = Tema.rotulo("t = 0", "relogio");
    private final Label cpuTitulo = new Label();
    private final Label cpuSub = new Label();
    private final VBox cpuCaixa = new VBox(1, cpuTitulo, cpuSub);
    private final FlowPane fila = new FlowPane(6, 6);
    private final GanttChart gantt = new GanttChart();

    CartaoAlgoritmo(
            ResultadoEscalonamento resultado,
            String descricao,
            int tempoMaximo,
            ObservableValue<Number> tempo
    ) {
        this(resultado.algoritmo(), descricao, resultado, null, tempoMaximo, tempo);
    }

    /** Versão com sincronismo: mostra lock, bloqueados e a situação dos registros. */
    CartaoAlgoritmo(
            String titulo,
            String descricao,
            ExecucaoIntegrada execucao,
            int tempoMaximo,
            ObservableValue<Number> tempo
    ) {
        this(titulo, descricao, execucao.resultado(), execucao, tempoMaximo, tempo);
    }

    private CartaoAlgoritmo(
            String titulo,
            String descricao,
            ResultadoEscalonamento resultado,
            ExecucaoIntegrada integrada,
            int tempoMaximo,
            ObservableValue<Number> tempo
    ) {
        super(12);
        this.resultado = resultado;
        this.integrada = integrada;
        this.fimGantt = resultado.gantt().get(resultado.gantt().size() - 1).fim();
        getStyleClass().add("card");
        setPadding(new Insets(16, 18, 14, 18));

        Label nome = Tema.rotulo(titulo, "algo-nome");
        Label desc = Tema.rotulo(descricao, "algo-desc");
        nome.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
        relogio.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
        desc.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
        HBox linhaTitulo = new HBox(10, nome, Tema.espaco(), relogio);
        linhaTitulo.setAlignment(Pos.CENTER_LEFT);
        HBox linhaDesc = new HBox(desc);
        VBox cabecalho = new VBox(4, linhaTitulo, linhaDesc);

        cpuTitulo.getStyleClass().add("cpu-titulo");
        cpuSub.getStyleClass().add("cpu-sub");
        cpuCaixa.setAlignment(Pos.CENTER);
        cpuCaixa.setMinWidth(150);
        cpuCaixa.setPrefWidth(150);
        cpuCaixa.setMinHeight(54);
        cpuCaixa.getStyleClass().add("cpu-caixa");
        Label rotuloCpu = Tema.rotulo("NA CPU", "eyebrow");
        VBox blocoCpu = new VBox(4, rotuloCpu, cpuCaixa);

        Label rotuloFila = Tema.rotulo("FILA DE PRONTO  (próximo à esquerda)", "eyebrow");
        fila.setMinHeight(34);
        fila.setAlignment(Pos.CENTER_LEFT);
        VBox blocoFila = new VBox(4, rotuloFila, fila);
        VBox direita = new VBox(8, blocoFila);
        HBox.setHgrow(direita, javafx.scene.layout.Priority.ALWAYS);

        HBox estado = new HBox(22, blocoCpu, direita);
        estado.setAlignment(Pos.TOP_LEFT);
        getChildren().addAll(cabecalho, estado);

        if (integrada != null) {
            bloqueados.setMinHeight(28);
            bloqueados.setAlignment(Pos.CENTER_LEFT);
            direita.getChildren().add(new VBox(4,
                    Tema.rotulo("BLOQUEADOS  (dormindo, esperando o lock)", "eyebrow"), bloqueados));
            statusArquivo.getStyleClass().add("arquivo-status");
            statusArquivo.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
            Label rotuloArquivo = Tema.rotulo("ARQUIVO", "eyebrow");
            rotuloArquivo.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
            FlowPane linhaArquivo = new FlowPane(10, 6, rotuloArquivo, statusArquivo, badgeRegistros(), chipsRegistros());
            linhaArquivo.setAlignment(Pos.CENTER_LEFT);
            getChildren().add(linhaArquivo);
        }

        gantt.tempoProperty().bind(tempo);
        if (integrada != null) {
            gantt.definir(integrada, tempoMaximo);
        } else {
            gantt.definir(resultado, tempoMaximo);
        }
        getChildren().add(gantt);

        tempo.addListener((obs, a, b) -> atualizar(b.doubleValue()));
        atualizar(tempo.getValue().doubleValue());
    }

    private Label badgeRegistros() {
        boolean ok = integrada.registrosCorrompidos() == 0;
        Label l = Tema.rotulo(ok
                ? "✓  " + integrada.registrosValidos() + " de " + integrada.registrosEsperados() + " registros íntegros"
                : "✕  " + integrada.registrosCorrompidos() + " de " + integrada.registrosEsperados() + " registros corrompidos",
                "badge-registros", ok ? "badge-ok" : "badge-erro");
        l.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
        return l;
    }

    private HBox chipsRegistros() {
        HBox chips = new HBox(5);
        chips.setAlignment(Pos.CENTER_LEFT);
        for (RegistroSimulado r : integrada.registros()) {
            var p = resultado.processos().stream()
                    .filter(x -> x.processo().id().equals(r.processoId()))
                    .findFirst().orElseThrow().processo();
            Label chip = Tema.chip(p.id() + (r.integro() ? " ✓" : " ✕"), p.tipoEvento());
            chip.getStyleClass().add("chip-pequeno");
            chip.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
            if (!r.integro()) {
                chip.getStyleClass().add("chip-corrompido");
            }
            chips.getChildren().add(chip);
        }
        return chips;
    }

    private void atualizar(double t) {
        int agora = (int) Math.floor(t + 1e-9);
        boolean concluido = agora >= fimGantt;
        relogio.setText(concluido ? "t = " + fimGantt + "  ✓" : "t = " + agora);

        TrechoExecucao atual = null;
        if (!concluido) {
            for (TrechoExecucao trecho : resultado.gantt()) {
                if (trecho.inicio() <= agora && agora < trecho.fim()) {
                    atual = trecho;
                    break;
                }
            }
        }
        atualizarCpu(atual, concluido, agora);
        atualizarFila(atual, concluido, agora);
        if (integrada != null) {
            atualizarBloqueados(agora, concluido);
            atualizarArquivo(agora, concluido);
        }
    }

    private boolean bloqueadoEm(String id, int agora) {
        if (integrada == null) {
            return false;
        }
        for (Bloqueio b : integrada.bloqueios()) {
            if (b.processoId().equals(id) && b.inicio() <= agora && agora < b.fim()) {
                return true;
            }
        }
        return false;
    }

    private void atualizarBloqueados(int agora, boolean concluido) {
        bloqueados.getChildren().clear();
        if (!concluido) {
            for (ProcessoResultado pr : resultado.processos()) {
                if (bloqueadoEm(pr.processo().id(), agora)) {
                    Label chip = Tema.chip(pr.processo().id(), pr.processo().tipoEvento());
                    chip.getStyleClass().add("chip-bloqueado");
                    bloqueados.getChildren().add(chip);
                }
            }
        }
        if (bloqueados.getChildren().isEmpty()) {
            bloqueados.getChildren().add(Tema.rotulo(concluido ? "—" : "ninguém", "fila-vazia"));
        }
    }

    /** Escritas "abertas": já começaram a gravar o registro, mas ainda não terminaram. */
    private void atualizarArquivo(int agora, boolean concluido) {
        List<String> abertas = new ArrayList<>();
        if (!concluido) {
            for (ProcessoResultado pr : resultado.processos()) {
                Integer abre = integrada.inicioEscrita(pr.processo().id());
                if (abre != null && abre <= agora && agora < pr.conclusao()) {
                    abertas.add(pr.processo().id());
                }
            }
        }
        statusArquivo.getStyleClass().removeAll("arquivo-livre", "arquivo-lock", "arquivo-colisao");
        if (concluido || abertas.isEmpty()) {
            statusArquivo.setText(integrada.comSincronismo() ? "lock livre" : "nenhuma escrita em andamento");
            statusArquivo.getStyleClass().add("arquivo-livre");
        } else if (integrada.comSincronismo()) {
            statusArquivo.setText("lock com " + String.join(", ", abertas));
            statusArquivo.getStyleClass().add("arquivo-lock");
        } else if (abertas.size() == 1) {
            statusArquivo.setText(abertas.get(0) + " escrevendo");
            statusArquivo.getStyleClass().add("arquivo-lock");
        } else {
            statusArquivo.setText("⚠ colisão: " + String.join(" + ", abertas) + " escrevendo ao mesmo tempo");
            statusArquivo.getStyleClass().add("arquivo-colisao");
        }
    }

    private void atualizarCpu(TrechoExecucao atual, boolean concluido, int agora) {
        String fundo;
        String titulo;
        String sub;
        if (concluido) {
            fundo = "#2f9e6b";
            titulo = "Concluído";
            sub = "todos finalizados";
        } else if (atual == null) {
            fundo = "#dbe3e8";
            titulo = "—";
            sub = "";
        } else if (atual.tipo() == TipoTrechoExecucao.PROCESSO) {
            fundo = Tema.hex(atual.tipoEvento());
            titulo = atual.processoId();
            int feito = executado(atual.processoId(), agora);
            int total = resultado.processos().stream()
                    .filter(p -> p.processo().id().equals(atual.processoId()))
                    .mapToInt(p -> p.processo().burst()).findFirst().orElse(0);
            sub = Tema.nome(atual.tipoEvento()) + " · " + feito + "/" + total;
        } else if (atual.tipo() == TipoTrechoExecucao.TROCA_CONTEXTO) {
            fundo = "#64748b";
            titulo = "Troca de contexto";
            sub = "CPU ocupada sem trabalho útil";
        } else {
            fundo = "#dbe3e8";
            titulo = "CPU ociosa";
            sub = "nenhum processo pronto";
        }
        boolean claro = fundo.equals("#dbe3e8");
        cpuCaixa.setStyle("-fx-background-color:" + fundo + ";");
        cpuTitulo.setStyle("-fx-text-fill:" + (claro ? "#40525c" : "white") + ";");
        cpuSub.setStyle("-fx-text-fill:" + (claro ? "#5b6b75" : "rgba(255,255,255,0.85)") + ";");
        cpuTitulo.setText(titulo);
        cpuSub.setText(sub);
    }

    private int executado(String id, int agora) {
        int soma = 0;
        for (TrechoExecucao trecho : resultado.gantt()) {
            if (trecho.tipo() == TipoTrechoExecucao.PROCESSO && trecho.processoId().equals(id)) {
                soma += Math.max(0, Math.min(agora, trecho.fim()) - trecho.inicio());
            }
        }
        return soma;
    }

    private record Pronto(ProcessoResultado resultado, int desde, boolean chegada) {
    }

    private void atualizarFila(TrechoExecucao atual, boolean concluido, int agora) {
        fila.getChildren().clear();
        List<Pronto> prontos = new ArrayList<>();
        if (!concluido) {
            for (ProcessoResultado pr : resultado.processos()) {
                String id = pr.processo().id();
                if (agora < pr.processo().tempoChegada() || agora >= pr.conclusao()) {
                    continue;
                }
                if (atual != null && atual.tipo() == TipoTrechoExecucao.PROCESSO
                        && atual.processoId().equals(id)) {
                    continue;
                }
                if (bloqueadoEm(id, agora)) {
                    continue;
                }
                int desde = pr.processo().tempoChegada();
                boolean chegada = true;
                for (TrechoExecucao trecho : resultado.gantt()) {
                    if (trecho.tipo() == TipoTrechoExecucao.PROCESSO
                            && trecho.processoId().equals(id)
                            && trecho.fim() <= agora) {
                        desde = trecho.fim();
                        chegada = false;
                    }
                }
                if (integrada != null) {
                    for (Bloqueio b : integrada.bloqueios()) {
                        if (b.processoId().equals(id) && b.fim() <= agora && b.fim() >= desde) {
                            desde = b.fim();
                            chegada = false;
                        }
                    }
                }
                prontos.add(new Pronto(pr, desde, chegada));
            }
            // chegadas entram antes do processo preemptado no mesmo instante
            prontos.sort(Comparator.comparingInt(Pronto::desde)
                    .thenComparing(p -> !p.chegada())
                    .thenComparingInt(p -> p.resultado().processo().ordemCadastro()));
        }
        if (prontos.isEmpty()) {
            fila.getChildren().add(Tema.rotulo(concluido ? "—" : "vazia", "fila-vazia"));
            return;
        }
        for (int i = 0; i < prontos.size(); i++) {
            var p = prontos.get(i).resultado().processo();
            if (i > 0) {
                fila.getChildren().add(Tema.rotulo("›", "fila-seta"));
            }
            fila.getChildren().add(Tema.chip(p.id(), p.tipoEvento()));
        }
    }
}
