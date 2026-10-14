package br.smartcity.monitor.escalonamento.ui;

import br.smartcity.monitor.escalonamento.algoritmo.RoundRobin;
import br.smartcity.monitor.escalonamento.model.*;
import br.smartcity.monitor.escalonamento.simulacao.ComparacaoEscalonamento;
import br.smartcity.monitor.escalonamento.sincronismo.*;
import br.smartcity.monitor.model.TipoEvento;
import javafx.application.Platform;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.util.Duration;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Text;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Interface do simulador didático de escalonamento de CPU e sincronismo. */
public final class EscalonamentoView extends BorderPane {
    private final EscalonamentoController controller = new EscalonamentoController();
    private final TableView<ProcessoUrbano> tabela = new TableView<>();
    private final TextField id = new TextField(), descricao = new TextField();
    private final ComboBox<TipoEvento> tipo = new ComboBox<>(FXCollections.observableArrayList(TipoEvento.values()));
    private final Spinner<Integer> chegada = new Spinner<>(0,100000,0), burst = new Spinner<>(1,100000,4), quantum = new Spinner<>(1,1000,2), custo = new Spinner<>(0,1000,1);
    private final VBox resultados = new VBox(8);
    private final TextArea syncOutput = new TextArea();
    private final VBox syncResultados = new VBox(10);
    private final TitledPane syncDetalhes = new TitledPane(
            "Ver conteúdo bruto dos arquivos",
            syncOutput
    );
    private final ComboBox<String> algoritmoReproducao = new ComboBox<>(FXCollections.observableArrayList("FCFS", "Round Robin"));
    private final HBox linhaTempoGantt = new HBox(5);
    private final ScrollPane rolagemGantt = new ScrollPane(linhaTempoGantt);
    private final FlowPane legendaGantt = new FlowPane(8, 6);
    private final List<VBox> blocosGantt = new ArrayList<>();
    private final Label cpuAtual = new Label("CPU aguardando");
    private final Label intervaloAtual = new Label(
            "Execute a comparação para montar a linha do tempo."
    );
    private final Label contadorReproducao = new Label("Passo 0/0");
    private final ProgressBar progressoReproducao = new ProgressBar(0);
    private final Label statusReproducao = new Label("Execute a comparação para habilitar a reprodução.");
    private final Button reproduzir = new Button("▶ Reproduzir"), pausar = new Button("⏸ Pausar"), avancar = new Button("⏭ Próximo passo"), reiniciar = new Button("↺ Reiniciar");
    private final Timeline timeline = new Timeline();
    private ResultadoEscalonamento resultadoFcfs, resultadoRoundRobin;
    private int indiceReproducao = -1;
    private int proximoId=4;

    public EscalonamentoView() {
        setPadding(new Insets(16)); setStyle("-fx-background-color:#f3f6f8;");
        Label titulo=new Label("Escalonamento de CPU"); titulo.setStyle("-fx-font-size:22px;-fx-font-weight:bold;");
        Label subtitulo=new Label("Compare FCFS e Round Robin usando a mesma carga de processos urbanos.");
        VBox header=new VBox(4,titulo,subtitulo); header.setPadding(new Insets(0,0,12,0)); setTop(header);
        configurarTabela();
        GridPane formulario=new GridPane(); formulario.setHgap(8); formulario.setVgap(6);
        id.setPromptText("P4"); descricao.setPromptText("Descrição do evento"); tipo.setValue(TipoEvento.TRANSITO);
        int col=0; formulario.add(new Label("ID"),col,0); formulario.add(id,col++,1); formulario.add(new Label("Tipo"),col,0); formulario.add(tipo,col++,1); formulario.add(new Label("Descrição"),col,0); formulario.add(descricao,col++,1); formulario.add(new Label("Chegada"),col,0); formulario.add(chegada,col++,1); formulario.add(new Label("Burst CPU"),col,0); formulario.add(burst,col,1);
        Button adicionar=new Button("Adicionar processo"), remover=new Button("Remover selecionado"), exemplo=new Button("Restaurar exemplo"), comparar=new Button("Comparar FCFS × Round Robin");
        adicionar.setOnAction(e->adicionar()); remover.setOnAction(e->{ProcessoUrbano p=tabela.getSelectionModel().getSelectedItem(); if(p!=null){controller.removerProcesso(p.id()); atualizarTabela();}}); exemplo.setOnAction(e->{controller.restaurarCargaExemplo(); proximoId=4; atualizarTabela(); resultados.getChildren().clear();}); comparar.setDefaultButton(true); comparar.setOnAction(e->comparar());
        HBox botoes=new HBox(8,adicionar,remover,exemplo, new Label("Quantum"),quantum,new Label("Custo troca"),custo,comparar); botoes.setPadding(new Insets(8,0,8,0));
        VBox carga=new VBox(8,new Label("CARGA DE PROCESSOS"),formulario,botoes,tabela); carga.setPadding(new Insets(12)); carga.setStyle(cardStyle());
        Button sync = new Button("Executar comparação de sincronismo");
        sync.setOnAction(e -> executarSync());
        syncOutput.setEditable(false);
        syncOutput.setPrefRowCount(10);
        syncOutput.setWrapText(false);
        syncDetalhes.setExpanded(false);
        Label explicacaoSync = new Label(
                "A mesma carga é executada duas vezes. Somente o uso do ReentrantLock muda: "
                        + "primeiro não há exclusão mútua e depois as threads acessam o arquivo uma por vez."
        );
        explicacaoSync.setWrapText(true);
        syncResultados.getChildren().setAll(
                new Label("Execute o experimento para visualizar a comparação.")
        );
        VBox sincronismo = new VBox(
                8,
                new Label("SINCRONISMO DE ARQUIVO COMPARTILHADO"),
                explicacaoSync,
                sync,
                syncResultados,
                syncDetalhes
        );
        sincronismo.setPadding(new Insets(12));
        sincronismo.setStyle(cardStyle());
        resultados.setPadding(new Insets(12)); resultados.setStyle(cardStyle());
        VBox reproducao = criarPainelReproducao();
        VBox centro=new VBox(12,carga,resultados,reproducao,sincronismo);
        centro.setFillWidth(true);
        ScrollPane rolagem=new ScrollPane(centro);
        rolagem.setFitToWidth(true);
        rolagem.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        rolagem.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        rolagem.setStyle("-fx-background: #f3f6f8; -fx-background-color: #f3f6f8;");
        setCenter(rolagem); atualizarTabela();
        algoritmoReproducao.setValue("FCFS");
        definirControlesHabilitados(false);
    }
    private String cardStyle(){return "-fx-background-color:white;-fx-background-radius:10;-fx-border-color:#dce4e9;-fx-border-radius:10;";}
    private void configurarTabela(){
        tabela.setPrefHeight(190); tabela.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        tabela.getColumns().add(col("ID",p->p.id())); tabela.getColumns().add(col("Tipo",p->p.tipoEvento().toString())); tabela.getColumns().add(col("Descrição",ProcessoUrbano::descricao)); tabela.getColumns().add(col("Chegada",p->String.valueOf(p.tempoChegada()))); tabela.getColumns().add(col("Burst",p->String.valueOf(p.burst())));
    }
    private TableColumn<ProcessoUrbano,String> col(String nome,java.util.function.Function<ProcessoUrbano,String> f){TableColumn<ProcessoUrbano,String> c=new TableColumn<>(nome);c.setCellValueFactory(v->new javafx.beans.property.ReadOnlyStringWrapper(f.apply(v.getValue())));return c;}
    private void atualizarTabela(){tabela.setItems(FXCollections.observableArrayList(controller.obterProcessos()));}
    private void adicionar(){try{String pid=id.getText().isBlank()?"P"+proximoId:id.getText().trim();controller.adicionarProcesso(pid,tipo.getValue(),descricao.getText(),chegada.getValue(),burst.getValue());proximoId++;id.clear();descricao.clear();atualizarTabela();}catch(RuntimeException ex){erro(ex.getMessage());}}
    private void comparar(){
        try {
            pararTimeline();
            List<ProcessoUrbano> carga=controller.obterProcessos();
            ComparacaoEscalonamento c=ComparacaoEscalonamento.executar(carga,quantum.getValue(),custo.getValue());
            resultadoFcfs=c.fcfs(); resultadoRoundRobin=c.roundRobin();
            resultados.getChildren().setAll(
                    painel(c.fcfs()),
                    painel(c.roundRobin()),
                    tabelaComparativa(c),
                    criarGraficosComparativos(
                            c,
                            carga,
                            custo.getValue(),
                            quantum.getValue()
                    )
            );
            definirControlesHabilitados(true);
            carregarReproducaoSelecionada();
        } catch(RuntimeException ex){erro(ex.getMessage());}
    }

    private VBox criarPainelReproducao(){
        Label titulo = new Label("REPRODUÇÃO DO ESCALONAMENTO");
        Label ajuda = new Label(
                "Cada bloco representa um intervalo da CPU. As cores identificam "
                        + "os processos e o destaque acompanha a execução."
        );
        ajuda.setWrapText(true);

        linhaTempoGantt.setAlignment(Pos.CENTER_LEFT);
        linhaTempoGantt.setPadding(new Insets(10));
        rolagemGantt.setFitToHeight(true);
        rolagemGantt.setPannable(true);
        rolagemGantt.setPrefHeight(125);
        rolagemGantt.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        rolagemGantt.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        rolagemGantt.setStyle(
                "-fx-background:#f8fafc;"
                        + "-fx-background-color:#f8fafc;"
                        + "-fx-border-color:#dce4e9;"
                        + "-fx-border-radius:8;"
        );

        Label rotuloCpu = new Label("ESTADO ATUAL DA CPU");
        rotuloCpu.setStyle(
                "-fx-font-size:11px;-fx-font-weight:bold;-fx-text-fill:#64748b;"
        );
        cpuAtual.setStyle(
                "-fx-font-size:20px;-fx-font-weight:bold;-fx-text-fill:#173b68;"
        );
        intervaloAtual.setWrapText(true);
        VBox estadoCpu = new VBox(3, rotuloCpu, cpuAtual, intervaloAtual);
        estadoCpu.setPadding(new Insets(10, 14, 10, 14));
        estadoCpu.setMinWidth(290);
        estadoCpu.setStyle(
                "-fx-background-color:#eef5ff;"
                        + "-fx-background-radius:8;"
                        + "-fx-border-color:#b8d2f2;"
                        + "-fx-border-radius:8;"
        );

        progressoReproducao.setMaxWidth(Double.MAX_VALUE);
        progressoReproducao.setPrefHeight(14);
        HBox.setHgrow(progressoReproducao, Priority.ALWAYS);
        HBox progresso = new HBox(
                10,
                new Label("Progresso:"),
                progressoReproducao,
                contadorReproducao
        );
        progresso.setAlignment(Pos.CENTER_LEFT);

        algoritmoReproducao.setOnAction(e -> { if (resultadoFcfs != null) carregarReproducaoSelecionada(); });
        reproduzir.setOnAction(e -> iniciarReproducao());
        pausar.setOnAction(e -> { timeline.pause(); statusReproducao.setText("Reprodução pausada. Use Reproduzir para continuar ou Próximo passo para avançar."); });
        avancar.setOnAction(e -> { timeline.pause(); avancarPasso(); });
        reiniciar.setOnAction(e -> { pararTimeline(); carregarReproducaoSelecionada(); });
        HBox controles = new HBox(8, new Label("Algoritmo:"), algoritmoReproducao, reproduzir, pausar, avancar, reiniciar);
        controles.setAlignment(Pos.CENTER_LEFT);
        controles.setPadding(new Insets(8,0,4,0));
        VBox painel = new VBox(
                8,
                titulo,
                ajuda,
                controles,
                estadoCpu,
                progresso,
                legendaGantt,
                rolagemGantt,
                statusReproducao
        );
        painel.setPadding(new Insets(12)); painel.setStyle(cardStyle());
        return painel;
    }

    private void definirControlesHabilitados(boolean habilitado){
        algoritmoReproducao.setDisable(!habilitado); reproduzir.setDisable(!habilitado);
        pausar.setDisable(!habilitado); avancar.setDisable(!habilitado); reiniciar.setDisable(!habilitado);
    }

    private ResultadoEscalonamento resultadoSelecionado(){
        return "Round Robin".equals(algoritmoReproducao.getValue()) ? resultadoRoundRobin : resultadoFcfs;
    }

    private void carregarReproducaoSelecionada(){
        pararTimeline();
        ResultadoEscalonamento r = resultadoSelecionado();
        if (r == null) {
            linhaTempoGantt.getChildren().clear();
            legendaGantt.getChildren().clear();
            blocosGantt.clear();
            progressoReproducao.setProgress(0);
            contadorReproducao.setText("Passo 0/0");
            cpuAtual.setText("CPU aguardando");
            intervaloAtual.setText(
                    "Execute a comparação para montar a linha do tempo."
            );
            statusReproducao.setText(
                    "Execute a comparação para habilitar a reprodução."
            );
            return;
        }

        indiceReproducao = -1;
        montarLinhaTempo(r);
        progressoReproducao.setProgress(0);
        contadorReproducao.setText("Passo 0/" + r.gantt().size());
        cpuAtual.setText("CPU pronta — " + r.algoritmo());
        intervaloAtual.setText(
                "Pressione Reproduzir ou Próximo passo para iniciar."
        );
        rolagemGantt.setHvalue(0);
        statusReproducao.setText(r.algoritmo() + ": pronto para reproduzir — " + r.gantt().size() + " trechos no total.");
    }

    private void montarLinhaTempo(ResultadoEscalonamento resultado) {
        linhaTempoGantt.getChildren().clear();
        legendaGantt.getChildren().clear();
        blocosGantt.clear();

        LinkedHashMap<String, TipoEvento> processos = new LinkedHashMap<>();
        boolean possuiTroca = false;
        boolean possuiOciosidade = false;

        for (TrechoExecucao trecho : resultado.gantt()) {
            if (trecho.tipo() == TipoTrechoExecucao.PROCESSO) {
                processos.putIfAbsent(
                        trecho.processoId(),
                        trecho.tipoEvento()
                );
            } else if (trecho.tipo()
                    == TipoTrechoExecucao.TROCA_CONTEXTO) {
                possuiTroca = true;
            } else {
                possuiOciosidade = true;
            }
        }

        processos.forEach((processoId, tipoEvento) ->
                legendaGantt.getChildren().add(criarItemLegenda(
                        processoId + " • " + tipoEvento,
                        corProcesso(processoId),
                        "white"
                ))
        );
        if (possuiTroca) {
            legendaGantt.getChildren().add(criarItemLegenda(
                    "Troca de contexto",
                    "#64748b",
                    "white"
            ));
        }
        if (possuiOciosidade) {
            legendaGantt.getChildren().add(criarItemLegenda(
                    "CPU ociosa",
                    "#e2e8f0",
                    "#334155"
            ));
        }

        for (int indice = 0; indice < resultado.gantt().size(); indice++) {
            TrechoExecucao trecho = resultado.gantt().get(indice);
            if (indice > 0) {
                Label seta = new Label("→");
                seta.setStyle(
                        "-fx-font-size:18px;-fx-font-weight:bold;"
                                + "-fx-text-fill:#94a3b8;"
                );
                linhaTempoGantt.getChildren().add(seta);
            }

            VBox bloco = criarBlocoGantt(trecho);
            blocosGantt.add(bloco);
            linhaTempoGantt.getChildren().add(bloco);
        }
        atualizarDestaqueGantt(-1);
    }

    private Label criarItemLegenda(
            String texto,
            String fundo,
            String corTexto
    ) {
        Label item = new Label(texto);
        item.setPadding(new Insets(4, 9, 4, 9));
        item.setStyle(
                "-fx-background-color:" + fundo + ";"
                        + "-fx-background-radius:12;"
                        + "-fx-text-fill:" + corTexto + ";"
                        + "-fx-font-size:11px;-fx-font-weight:bold;"
        );
        return item;
    }

    private VBox criarBlocoGantt(TrechoExecucao trecho) {
        Label nome = new Label(nomeTrecho(trecho));
        nome.setStyle("-fx-font-size:14px;-fx-font-weight:bold;");
        Label intervalo = new Label(trecho.inicio() + " → " + trecho.fim());
        intervalo.setStyle("-fx-font-size:12px;");
        Label duracao = new Label(trecho.duracao() + " unid.");
        duracao.setStyle("-fx-font-size:10px;");

        VBox bloco = new VBox(3, nome, intervalo, duracao);
        bloco.setAlignment(Pos.CENTER);
        bloco.setPadding(new Insets(8));
        bloco.setPrefWidth(Math.max(
                78,
                Math.min(220, trecho.duracao() * 32.0)
        ));
        bloco.setMinHeight(78);

        String detalhe = trecho.tipo() == TipoTrechoExecucao.PROCESSO
                ? trecho.processoId() + " • " + trecho.tipoEvento()
                : nomeTrecho(trecho);
        Tooltip.install(bloco, new Tooltip(
                detalhe + "\nIntervalo: " + trecho.inicio() + "–"
                        + trecho.fim() + "\nDuração: " + trecho.duracao()
                        + " unidade(s)"
        ));
        return bloco;
    }

    private void atualizarDestaqueGantt(int indiceAtual) {
        ResultadoEscalonamento resultado = resultadoSelecionado();
        if (resultado == null) {
            return;
        }

        for (int indice = 0; indice < blocosGantt.size(); indice++) {
            VBox bloco = blocosGantt.get(indice);
            TrechoExecucao trecho = resultado.gantt().get(indice);
            boolean atual = indice == indiceAtual;
            String cor = corTrecho(trecho);
            String corTexto = trecho.tipo() == TipoTrechoExecucao.OCIOSO
                    ? "#334155"
                    : "white";

            bloco.setOpacity(atual ? 1 : indice < indiceAtual ? 0.82 : 0.5);
            bloco.setScaleX(atual ? 1.04 : 1);
            bloco.setScaleY(atual ? 1.04 : 1);
            bloco.setStyle(
                    "-fx-background-color:" + cor + ";"
                            + "-fx-background-radius:8;"
                            + "-fx-border-color:"
                            + (atual ? "#0f172a" : "transparent") + ";"
                            + "-fx-border-width:3;"
                            + "-fx-border-radius:8;"
                            + "-fx-text-fill:" + corTexto + ";"
                            + (atual
                            ? "-fx-effect:dropshadow(gaussian,rgba(15,23,42,0.35),10,0.2,0,3);"
                            : "")
            );
            for (javafx.scene.Node filho : bloco.getChildren()) {
                if (filho instanceof Label label) {
                    label.setTextFill(
                            javafx.scene.paint.Color.web(corTexto)
                    );
                }
            }
        }
    }

    private String nomeTrecho(TrechoExecucao trecho) {
        if (trecho.tipo() == TipoTrechoExecucao.OCIOSO) {
            return "CPU ociosa";
        }
        if (trecho.tipo() == TipoTrechoExecucao.TROCA_CONTEXTO) {
            return "TROCA";
        }
        return trecho.processoId();
    }

    private String corTrecho(TrechoExecucao trecho) {
        if (trecho.tipo() == TipoTrechoExecucao.TROCA_CONTEXTO) {
            return "#64748b";
        }
        if (trecho.tipo() == TipoTrechoExecucao.OCIOSO) {
            return "#e2e8f0";
        }
        return corProcesso(trecho.processoId());
    }

    private String corProcesso(String processoId) {
        String[] paleta = {
                "#2563eb",
                "#0f766e",
                "#c2410c",
                "#7e22ce",
                "#15803d",
                "#be123c"
        };
        return paleta[Math.floorMod(processoId.hashCode(), paleta.length)];
    }

    private void iniciarReproducao(){
        ResultadoEscalonamento r = resultadoSelecionado();
        if (r == null || r.gantt().isEmpty()) return;
        if (indiceReproducao >= r.gantt().size()-1) carregarReproducaoSelecionada();
        if (timeline.getStatus() == javafx.animation.Animation.Status.PAUSED) { timeline.play(); statusReproducao.setText("Reproduzindo " + r.algoritmo() + "..."); return; }
        timeline.stop();
        timeline.getKeyFrames().setAll(new KeyFrame(Duration.millis(750), e -> avancarPasso()));
        timeline.setCycleCount(Timeline.INDEFINITE);
        statusReproducao.setText("Reproduzindo " + r.algoritmo() + "...");
        timeline.playFromStart();
    }

    private void avancarPasso(){
        ResultadoEscalonamento r = resultadoSelecionado();
        if (r == null || r.gantt().isEmpty()) return;
        if (indiceReproducao + 1 >= r.gantt().size()) {
            pararTimeline();
            statusReproducao.setText("Reprodução concluída. Duração lógica: " + r.metricas().makespan() + " unidades.");
            return;
        }
        indiceReproducao++;
        TrechoExecucao trecho = r.gantt().get(indiceReproducao);
        String nome = nomeTrecho(trecho);
        atualizarDestaqueGantt(indiceReproducao);
        cpuAtual.setText(
                trecho.tipo() == TipoTrechoExecucao.PROCESSO
                        ? "Executando " + nome
                        : nome
        );
        intervaloAtual.setText(
                "Tempo " + trecho.inicio() + " → " + trecho.fim()
                        + " • duração " + trecho.duracao() + " unidade(s)"
        );
        progressoReproducao.setProgress(
                (indiceReproducao + 1.0) / r.gantt().size()
        );
        contadorReproducao.setText(
                "Passo " + (indiceReproducao + 1) + "/" + r.gantt().size()
        );
        Platform.runLater(() -> rolagemGantt.setHvalue(
                r.gantt().size() <= 1
                        ? 0
                        : indiceReproducao / (double) (r.gantt().size() - 1)
        ));
        statusReproducao.setText(String.format(Locale.ROOT, "%s | passo %d/%d: %s, tempo %d–%d.", r.algoritmo(), indiceReproducao+1, r.gantt().size(), nome, trecho.inicio(), trecho.fim()));
        if (indiceReproducao == r.gantt().size()-1) {
            pararTimeline();
            cpuAtual.setText("Execução concluída");
            statusReproducao.setText(statusReproducao.getText() + " Reprodução concluída.");
        }
    }

    private void pararTimeline(){ timeline.stop(); }
    private VBox painel(ResultadoEscalonamento r){
        MetricasEscalonamento m=r.metricas();
        Label h=new Label(r.algoritmo()+" — Gantt:\n"+r.gantt().stream()
                .map(t->{
                    String nome=t.tipo()==TipoTrechoExecucao.OCIOSO?"CPU ociosa":
                            t.tipo()==TipoTrechoExecucao.TROCA_CONTEXTO?"TROCA CONTEXTO":t.processoId();
                    return nome+" ["+t.inicio()+"–"+t.fim()+"]";
                }).reduce((a,b)->a+"  →  "+b).orElse("—"));
        h.setWrapText(true); h.setMaxWidth(Double.MAX_VALUE);
        Label stats=new Label(String.format(Locale.forLanguageTag("pt-BR"),
                "Espera média: %.2f | Retorno: %.2f | Resposta: %.2f | Duração: %d | Trocas: %d | CPU útil: %.1f%% | Vazão: %.3f proc/unid",
                m.tempoMedioEspera(),m.tempoMedioRetorno(),m.tempoMedioResposta(),m.makespan(),m.trocasContexto(),m.utilizacaoCpuPercentual(),m.vazao()));
        stats.setWrapText(true); stats.setMaxWidth(Double.MAX_VALUE);
        return new VBox(5,h,stats);
    }
    private TableView<String[]> tabelaComparativa(ComparacaoEscalonamento c){TableView<String[]> t=new TableView<>();t.setPrefHeight(165);t.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);String[][] rows={{"Espera média",fmt(c.fcfs().metricas().tempoMedioEspera()),fmt(c.roundRobin().metricas().tempoMedioEspera())},{"Retorno médio",fmt(c.fcfs().metricas().tempoMedioRetorno()),fmt(c.roundRobin().metricas().tempoMedioRetorno())},{"Resposta média",fmt(c.fcfs().metricas().tempoMedioResposta()),fmt(c.roundRobin().metricas().tempoMedioResposta())},{"Duração total",String.valueOf(c.fcfs().metricas().makespan()),String.valueOf(c.roundRobin().metricas().makespan())},{"Trocas de contexto",String.valueOf(c.fcfs().metricas().trocasContexto()),String.valueOf(c.roundRobin().metricas().trocasContexto())}};for(int i=0;i<3;i++){final int n=i;TableColumn<String[],String> col=new TableColumn<>(new String[]{"Métrica","FCFS","Round Robin"}[i]);col.setCellValueFactory(v->new javafx.beans.property.ReadOnlyStringWrapper(v.getValue()[n]));t.getColumns().add(col);}t.setItems(FXCollections.observableArrayList(rows));return t;}

    private VBox criarGraficosComparativos(
            ComparacaoEscalonamento comparacao,
            List<ProcessoUrbano> carga,
            int custoTroca,
            int quantumSelecionado
    ) {
        MetricasEscalonamento fcfs = comparacao.fcfs().metricas();
        MetricasEscalonamento roundRobin = comparacao.roundRobin().metricas();

        BarChart<String, Number> tempos = criarGrafico(
                "Tempos médios — menor é melhor",
                "Unidades de tempo"
        );
        tempos.getData().add(
                criarSerie(
                    "FCFS",
                    new String[]{"Espera", "Retorno", "Resposta"},
                    new double[]{
                            fcfs.tempoMedioEspera(),
                            fcfs.tempoMedioRetorno(),
                            fcfs.tempoMedioResposta()
                    }
                )
        );
        tempos.getData().add(
                criarSerie(
                        "Round Robin",
                        new String[]{"Espera", "Retorno", "Resposta"},
                        new double[]{
                                roundRobin.tempoMedioEspera(),
                                roundRobin.tempoMedioRetorno(),
                                roundRobin.tempoMedioResposta()
                        }
                )
        );

        BarChart<String, Number> sobrecarga = criarGrafico(
                "Duração e trocas — menor é melhor",
                "Quantidade"
        );
        sobrecarga.getData().add(
                criarSerie(
                    "FCFS",
                    new String[]{"Duração total", "Trocas", "Tempo em trocas"},
                    new double[]{
                            fcfs.makespan(),
                            fcfs.trocasContexto(),
                            fcfs.tempoTrocasContexto()
                    }
                )
        );
        sobrecarga.getData().add(
                criarSerie(
                        "Round Robin",
                        new String[]{"Duração total", "Trocas", "Tempo em trocas"},
                        new double[]{
                                roundRobin.makespan(),
                                roundRobin.trocasContexto(),
                                roundRobin.tempoTrocasContexto()
                        }
                )
        );

        HBox graficos = new HBox(12, tempos, sobrecarga);
        HBox.setHgrow(tempos, Priority.ALWAYS);
        HBox.setHgrow(sobrecarga, Priority.ALWAYS);
        tempos.setMaxWidth(Double.MAX_VALUE);
        sobrecarga.setMaxWidth(Double.MAX_VALUE);

        Label titulo = new Label("GRÁFICOS COMPARATIVOS");
        Label ajuda = new Label(
                "As barras usam a mesma carga. Altere o quantum ou o custo de troca e compare novamente."
        );
        ajuda.setWrapText(true);
        return new VBox(
                8,
                titulo,
                ajuda,
                graficos,
                criarGraficoImpactoQuantum(
                        carga,
                        custoTroca,
                        quantumSelecionado
                )
        );
    }

    private VBox criarGraficoImpactoQuantum(
            List<ProcessoUrbano> carga,
            int custoTroca,
            int quantumSelecionado
    ) {
        int maiorBurst = carga.stream()
                .mapToInt(ProcessoUrbano::burst)
                .max()
                .orElse(1);
        SortedSet<Integer> valoresQuantum = new TreeSet<>();

        if (maiorBurst <= 12) {
            for (int valor = 1; valor <= maiorBurst; valor++) {
                valoresQuantum.add(valor);
            }
        } else {
            int quantidadeAmostras = 12;
            for (int indice = 0; indice < quantidadeAmostras; indice++) {
                int valor = 1 + (int) Math.round(
                        (maiorBurst - 1)
                                * indice
                                / (double) (quantidadeAmostras - 1)
                );
                valoresQuantum.add(valor);
            }
        }
        valoresQuantum.add(Math.min(quantumSelecionado, maiorBurst));

        String[] categorias = valoresQuantum.stream()
                .map(String::valueOf)
                .toArray(String[]::new);
        double[] duracoes = new double[valoresQuantum.size()];
        double[] temposTroca = new double[valoresQuantum.size()];
        int indice = 0;

        for (int valorQuantum : valoresQuantum) {
            MetricasEscalonamento metricas = new RoundRobin(
                    valorQuantum,
                    custoTroca
            ).executar(carga).metricas();
            duracoes[indice] = metricas.makespan();
            temposTroca[indice] = metricas.tempoTrocasContexto();
            indice++;
        }

        BarChart<String, Number> grafico = criarGrafico(
                "Impacto do quantum no Round Robin — menor é mais rápido",
                "Unidades de tempo"
        );
        grafico.setPrefHeight(370);
        grafico.getData().add(criarSerie(
                "Duração total",
                categorias,
                duracoes
        ));
        grafico.getData().add(criarSerie(
                "Tempo gasto em trocas",
                categorias,
                temposTroca
        ));

        Label explicacao = new Label(
                "Eixo horizontal: quantum. Cada grupo usa a mesma carga e custo "
                        + "de troca. Quantum selecionado: " + quantumSelecionado
                        + ". A partir do maior burst (" + maiorBurst
                        + "), aumentar o quantum não altera esta carga."
        );
        explicacao.setWrapText(true);

        return new VBox(6, grafico, explicacao);
    }

    private BarChart<String, Number> criarGrafico(
            String titulo,
            String rotuloEixoY
    ) {
        CategoryAxis eixoX = new CategoryAxis();
        NumberAxis eixoY = new NumberAxis();
        eixoY.setLabel(rotuloEixoY);
        eixoY.setForceZeroInRange(true);

        BarChart<String, Number> grafico = new GraficoBarrasComValores(
                eixoX,
                eixoY
        );
        grafico.setTitle(titulo);
        grafico.setAnimated(false);
        grafico.setLegendVisible(true);
        grafico.setCategoryGap(24);
        grafico.setBarGap(4);
        grafico.setPrefHeight(330);
        return grafico;
    }

    private XYChart.Series<String, Number> criarSerie(
            String nome,
            String[] categorias,
            double[] valores
    ) {
        XYChart.Series<String, Number> serie = new XYChart.Series<>();
        serie.setName(nome);
        for (int i = 0; i < categorias.length; i++) {
            serie.getData().add(
                    new XYChart.Data<>(categorias[i], valores[i])
            );
        }
        return serie;
    }

    private String fmt(double n){return String.format(Locale.forLanguageTag("pt-BR"),"%.2f",n);}

    private void executarSync(){
        List<ProcessoUrbano> carga = controller.obterProcessos();
        if (carga.isEmpty()) {
            erro("Cadastre pelo menos um processo.");
            return;
        }

        Label andamento = new Label(
                "Executando a mesma carga sem lock e com lock..."
        );
        andamento.setStyle("-fx-font-weight:bold;-fx-text-fill:#2457a7;");
        syncResultados.getChildren().setAll(andamento);
        syncOutput.clear();
        syncDetalhes.setExpanded(false);

        CompletableFuture
                .supplyAsync(() -> {
                    try {
                        return new SimuladorSincronismo().comparar(
                                carga,
                                Path.of("target", "experimento-sincronismo")
                        );
                    } catch (Exception ex) {
                        throw new RuntimeException(ex);
                    }
                })
                .whenComplete((comparacao, erro) -> Platform.runLater(() -> {
                    if (erro != null) {
                        Throwable causa = erro.getCause() == null
                                ? erro
                                : erro.getCause();
                        Label falha = new Label(
                                "Falha ao executar: " + causa.getMessage()
                        );
                        falha.setStyle(
                                "-fx-font-weight:bold;-fx-text-fill:#c9372c;"
                        );
                        syncResultados.getChildren().setAll(falha);
                        return;
                    }

                    syncResultados.getChildren().setAll(
                            criarComparacaoSincronismo(comparacao)
                    );
                    syncOutput.setText(
                            formatSync(comparacao.semSincronismo())
                                    + "\n\n"
                                    + formatSync(comparacao.comSincronismo())
                    );
                }));
    }

    private VBox criarComparacaoSincronismo(
            ComparacaoSincronismo comparacao
    ) {
        ResultadoSincronismo semLock = comparacao.semSincronismo();
        ResultadoSincronismo comLock = comparacao.comSincronismo();

        VBox cardSemLock = criarCardSincronismo(
                "SEM SINCRONISMO",
                semLock,
                "#c9372c",
                semLock.houveColisao()
                        ? "As threads misturaram fragmentos enquanto escreviam "
                                + "simultaneamente no mesmo arquivo."
                        : "Nenhuma colisão apareceu nesta execução. Repita o "
                                + "experimento para observar a condição de corrida."
        );
        VBox cardComLock = criarCardSincronismo(
                "COM SINCRONISMO",
                comLock,
                "#17823b",
                "O ReentrantLock protege a região crítica: cada thread conclui "
                        + "seu registro antes que a próxima comece."
        );
        HBox cards = new HBox(12, cardSemLock, cardComLock);
        HBox.setHgrow(cardSemLock, Priority.ALWAYS);
        HBox.setHgrow(cardComLock, Priority.ALWAYS);

        BarChart<String, Number> integridade = criarGrafico(
                "Integridade dos registros",
                "Quantidade de registros"
        );
        integridade.getData().add(criarSerie(
                "Válidos",
                new String[]{"Sem lock", "Com lock"},
                new double[]{
                        semLock.registrosValidos(),
                        comLock.registrosValidos()
                }
        ));
        integridade.getData().add(criarSerie(
                "Corrompidos",
                new String[]{"Sem lock", "Com lock"},
                new double[]{
                        semLock.registrosCorrompidos(),
                        comLock.registrosCorrompidos()
                }
        ));

        BarChart<String, Number> tempos = criarGrafico(
                "Tempos observados — variam a cada execução",
                "Milissegundos"
        );
        tempos.getData().add(criarSerie(
                "Duração total",
                new String[]{"Sem lock", "Com lock"},
                new double[]{semLock.duracaoMs(), comLock.duracaoMs()}
        ));
        tempos.getData().add(criarSerie(
                "Espera média pelo lock",
                new String[]{"Sem lock", "Com lock"},
                new double[]{
                        semLock.tempoMedioEsperaMs(),
                        comLock.tempoMedioEsperaMs()
                }
        ));

        HBox graficos = new HBox(12, integridade, tempos);
        HBox.setHgrow(integridade, Priority.ALWAYS);
        HBox.setHgrow(tempos, Priority.ALWAYS);
        integridade.setMaxWidth(Double.MAX_VALUE);
        tempos.setMaxWidth(Double.MAX_VALUE);

        Label leitura = new Label(
                "Leitura principal: o lock preserva a integridade dos registros, "
                        + "mas faz as threads esperarem para entrar na região crítica."
        );
        leitura.setWrapText(true);
        leitura.setStyle("-fx-font-weight:bold;");

        return new VBox(10, cards, graficos, leitura);
    }

    private VBox criarCardSincronismo(
            String titulo,
            ResultadoSincronismo resultado,
            String cor,
            String explicacao
    ) {
        Label cabecalho = new Label(titulo);
        cabecalho.setStyle(
                "-fx-font-size:15px;-fx-font-weight:bold;-fx-text-fill:" + cor + ";"
        );

        Label status = new Label(
                resultado.houveColisao()
                        ? "⚠ COLISÃO DETECTADA"
                        : "✓ ARQUIVO ÍNTEGRO"
        );
        status.setStyle(
                "-fx-font-size:16px;-fx-font-weight:bold;-fx-text-fill:" + cor + ";"
        );

        Label registros = new Label(String.format(
                Locale.forLanguageTag("pt-BR"),
                "Esperados: %d   |   Válidos: %d   |   Corrompidos: %d",
                resultado.registrosEsperados(),
                resultado.registrosValidos(),
                resultado.registrosCorrompidos()
        ));
        Label tempos = new Label(String.format(
                Locale.forLanguageTag("pt-BR"),
                "Duração: %.2f ms   |   Espera média pelo lock: %.2f ms",
                resultado.duracaoMs(),
                resultado.tempoMedioEsperaMs()
        ));
        Label detalhe = new Label(explicacao);
        detalhe.setWrapText(true);

        VBox card = new VBox(6, cabecalho, status, registros, tempos, detalhe);
        card.setPadding(new Insets(12));
        card.setMaxWidth(Double.MAX_VALUE);
        card.setStyle(
                "-fx-background-color:#f8fafb;"
                        + "-fx-background-radius:8;"
                        + "-fx-border-color:#dce4e9 #dce4e9 #dce4e9 " + cor + ";"
                        + "-fx-border-width:1 1 1 5;"
                        + "-fx-border-radius:8;"
        );
        return card;
    }

    private String formatSync(ResultadoSincronismo r){return r.modo()+"\nArquivo: "+r.arquivo()+"\nEsperados: "+r.registrosEsperados()+" | Válidos: "+r.registrosValidos()+" | Corrompidos: "+r.registrosCorrompidos()+"\nDuração: "+fmt(r.duracaoMs())+" ms | Espera média pelo lock: "+fmt(r.tempoMedioEsperaMs())+" ms\nColisão: "+(r.houveColisao()?"DETECTADA":"não detectada")+"\n"+r.conteudoArquivo();}

    private static final class GraficoBarrasComValores
            extends BarChart<String, Number> {
        private final Map<Data<String, Number>, Text> rotulos =
                new IdentityHashMap<>();

        private GraficoBarrasComValores(
                CategoryAxis eixoX,
                NumberAxis eixoY
        ) {
            super(eixoX, eixoY);
        }

        @Override
        protected void layoutPlotChildren() {
            super.layoutPlotChildren();

            for (Series<String, Number> serie : getData()) {
                for (Data<String, Number> dado : serie.getData()) {
                    if (dado.getNode() == null) {
                        continue;
                    }

                    Text rotulo = rotulos.computeIfAbsent(dado, item -> {
                        Text texto = new Text();
                        texto.setStyle(
                                "-fx-font-size:11px;"
                                        + "-fx-font-weight:bold;"
                                        + "-fx-fill:#263238;"
                        );
                        getPlotChildren().add(texto);
                        return texto;
                    });
                    rotulo.setText(formatarValor(dado.getYValue()));

                    double larguraBarra =
                            dado.getNode().getBoundsInParent().getWidth();
                    double x = dado.getNode().getBoundsInParent().getMinX()
                            + (larguraBarra
                            - rotulo.getLayoutBounds().getWidth()) / 2;
                    double y = Math.max(
                            0,
                            dado.getNode().getBoundsInParent().getMinY()
                                    - rotulo.getLayoutBounds().getHeight()
                                    - 3
                    );
                    rotulo.relocate(x, y);
                }
            }
        }

        private static String formatarValor(Number valor) {
            double numero = valor.doubleValue();
            if (Math.abs(numero - Math.rint(numero)) < 0.000_001) {
                return String.valueOf((long) Math.rint(numero));
            }
            return String.format(
                    Locale.forLanguageTag("pt-BR"),
                    "%.2f",
                    numero
            );
        }
    }

    private void erro(String mensagem){Alert a=new Alert(Alert.AlertType.ERROR,mensagem,ButtonType.OK);a.setHeaderText("Não foi possível executar");a.showAndWait();}
}
