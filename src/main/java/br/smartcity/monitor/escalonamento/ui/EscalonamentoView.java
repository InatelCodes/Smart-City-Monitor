package br.smartcity.monitor.escalonamento.ui;

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
import javafx.scene.control.*;
import javafx.scene.layout.*;
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
    private final ComboBox<String> algoritmoReproducao = new ComboBox<>(FXCollections.observableArrayList("FCFS", "Round Robin"));
    private final ListView<TrechoExecucao> listaGantt = new ListView<>();
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
        Button sync=new Button("Executar comparação de sincronismo"); sync.setOnAction(e->executarSync()); syncOutput.setEditable(false); syncOutput.setPrefRowCount(7); syncOutput.setWrapText(true);
        VBox sincronismo=new VBox(8,new Label("SINCRONISMO DE ARQUIVO COMPARTILHADO"),new Label("Cada processo representa um sensor que grava no mesmo arquivo físico."),sync,syncOutput); sincronismo.setPadding(new Insets(12)); sincronismo.setStyle(cardStyle());
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
            resultados.getChildren().setAll(painel(c.fcfs()),painel(c.roundRobin()),tabelaComparativa(c));
            definirControlesHabilitados(true);
            carregarReproducaoSelecionada();
        } catch(RuntimeException ex){erro(ex.getMessage());}
    }

    private VBox criarPainelReproducao(){
        Label titulo = new Label("REPRODUÇÃO DO ESCALONAMENTO");
        Label ajuda = new Label("Escolha o algoritmo e acompanhe cada trecho da CPU. O avanço é didático: cada passo mostra um intervalo do diagrama de Gantt.");
        ajuda.setWrapText(true);
        listaGantt.setPrefHeight(180);
        listaGantt.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(TrechoExecucao trecho, boolean vazio) {
                super.updateItem(trecho, vazio);
                if (vazio || trecho == null) { setText(null); setStyle(""); return; }
                String nome = trecho.processoId();
                if (trecho.tipo() == TipoTrechoExecucao.OCIOSO) nome = "CPU ociosa";
                else if (trecho.tipo() == TipoTrechoExecucao.TROCA_CONTEXTO) nome = "Troca de contexto";
                setText(String.format(Locale.ROOT, "%3d – %3d   |   %s", trecho.inicio(), trecho.fim(), nome));
                setStyle(trecho.tipo() == TipoTrechoExecucao.PROCESSO
                        ? "-fx-padding: 7 10 7 10;"
                        : "-fx-padding: 7 10 7 10; -fx-text-fill: #65717b;");
            }
        });
        algoritmoReproducao.setOnAction(e -> { if (resultadoFcfs != null) carregarReproducaoSelecionada(); });
        reproduzir.setOnAction(e -> iniciarReproducao());
        pausar.setOnAction(e -> { timeline.pause(); statusReproducao.setText("Reprodução pausada. Use Reproduzir para continuar ou Próximo passo para avançar."); });
        avancar.setOnAction(e -> { timeline.pause(); avancarPasso(); });
        reiniciar.setOnAction(e -> { pararTimeline(); carregarReproducaoSelecionada(); });
        HBox controles = new HBox(8, new Label("Algoritmo:"), algoritmoReproducao, reproduzir, pausar, avancar, reiniciar);
        controles.setPadding(new Insets(8,0,4,0));
        VBox painel = new VBox(8, titulo, ajuda, controles, statusReproducao, listaGantt);
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
        if (r == null) { listaGantt.getItems().clear(); statusReproducao.setText("Execute a comparação para habilitar a reprodução."); return; }
        listaGantt.setItems(FXCollections.observableArrayList(r.gantt()));
        indiceReproducao = -1;
        listaGantt.getSelectionModel().clearSelection();
        statusReproducao.setText(r.algoritmo() + ": pronto para reproduzir — " + r.gantt().size() + " trechos no total.");
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
        listaGantt.getSelectionModel().select(indiceReproducao);
        listaGantt.scrollTo(indiceReproducao);
        String nome = trecho.processoId();
        if (trecho.tipo() == TipoTrechoExecucao.OCIOSO) nome = "CPU ociosa";
        else if (trecho.tipo() == TipoTrechoExecucao.TROCA_CONTEXTO) nome = "troca de contexto";
        statusReproducao.setText(String.format(Locale.ROOT, "%s | passo %d/%d: %s, tempo %d–%d.", r.algoritmo(), indiceReproducao+1, r.gantt().size(), nome, trecho.inicio(), trecho.fim()));
        if (indiceReproducao == r.gantt().size()-1) {
            pararTimeline();
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
    private String fmt(double n){return String.format(Locale.forLanguageTag("pt-BR"),"%.2f",n);}
    private void executarSync(){List<ProcessoUrbano> carga=controller.obterProcessos();if(carga.isEmpty()){erro("Cadastre pelo menos um processo.");return;}syncOutput.setText("Executando os sensores concorrentes...");CompletableFuture.supplyAsync(()->{try{return new SimuladorSincronismo().comparar(carga,Path.of("target","experimento-sincronismo"));}catch(Exception ex){throw new RuntimeException(ex);}}).whenComplete((r,ex)->Platform.runLater(()->{if(ex!=null){syncOutput.setText("Falha: "+ex.getCause().getMessage());return;}syncOutput.setText(formatSync(r.semSincronismo())+"\n\n"+formatSync(r.comSincronismo()));}));}
    private String formatSync(ResultadoSincronismo r){return r.modo()+"\nArquivo: "+r.arquivo()+"\nEsperados: "+r.registrosEsperados()+" | Válidos: "+r.registrosValidos()+" | Corrompidos: "+r.registrosCorrompidos()+"\nDuração: "+fmt(r.duracaoMs())+" ms | Espera média pelo lock: "+fmt(r.tempoMedioEsperaMs())+" ms\nColisão: "+(r.houveColisao()?"DETECTADA":"não detectada")+"\n"+r.conteudoArquivo();}
    private void erro(String mensagem){Alert a=new Alert(Alert.AlertType.ERROR,mensagem,ButtonType.OK);a.setHeaderText("Não foi possível executar");a.showAndWait();}
}
