package br.smartcity.monitor.escalonamento.ui;

import br.smartcity.monitor.escalonamento.model.*;
import br.smartcity.monitor.escalonamento.simulacao.ComparacaoEscalonamento;
import br.smartcity.monitor.escalonamento.sincronismo.*;
import br.smartcity.monitor.model.TipoEvento;
import javafx.application.Platform;
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
        VBox centro=new VBox(12,carga,resultados,sincronismo); setCenter(centro); atualizarTabela();
    }
    private String cardStyle(){return "-fx-background-color:white;-fx-background-radius:10;-fx-border-color:#dce4e9;-fx-border-radius:10;";}
    private void configurarTabela(){
        tabela.setPrefHeight(190); tabela.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        tabela.getColumns().add(col("ID",p->p.id())); tabela.getColumns().add(col("Tipo",p->p.tipoEvento().toString())); tabela.getColumns().add(col("Descrição",ProcessoUrbano::descricao)); tabela.getColumns().add(col("Chegada",p->String.valueOf(p.tempoChegada()))); tabela.getColumns().add(col("Burst",p->String.valueOf(p.burst())));
    }
    private TableColumn<ProcessoUrbano,String> col(String nome,java.util.function.Function<ProcessoUrbano,String> f){TableColumn<ProcessoUrbano,String> c=new TableColumn<>(nome);c.setCellValueFactory(v->new javafx.beans.property.ReadOnlyStringWrapper(f.apply(v.getValue())));return c;}
    private void atualizarTabela(){tabela.setItems(FXCollections.observableArrayList(controller.obterProcessos()));}
    private void adicionar(){try{String pid=id.getText().isBlank()?"P"+proximoId:id.getText().trim();controller.adicionarProcesso(pid,tipo.getValue(),descricao.getText(),chegada.getValue(),burst.getValue());proximoId++;id.clear();descricao.clear();atualizarTabela();}catch(RuntimeException ex){erro(ex.getMessage());}}
    private void comparar(){try{List<ProcessoUrbano> carga=controller.obterProcessos(); ComparacaoEscalonamento c=ComparacaoEscalonamento.executar(carga,quantum.getValue(),custo.getValue()); resultados.getChildren().setAll(painel(c.fcfs()),painel(c.roundRobin()),tabelaComparativa(c));}catch(RuntimeException ex){erro(ex.getMessage());}}
    private VBox painel(ResultadoEscalonamento r){MetricasEscalonamento m=r.metricas();Label h=new Label(r.algoritmo()+" — Gantt: "+r.gantt().stream().map(t->t.processoId()+" ["+t.inicio()+"–"+t.fim()+"]").reduce((a,b)->a+"  →  "+b).orElse("—"));h.setWrapText(true);Label stats=new Label(String.format(Locale.forLanguageTag("pt-BR"),"Espera média: %.2f | Retorno: %.2f | Resposta: %.2f | Duração: %d | Trocas: %d | CPU útil: %.1f%% | Vazão: %.3f proc/unid",m.tempoMedioEspera(),m.tempoMedioRetorno(),m.tempoMedioResposta(),m.makespan(),m.trocasContexto(),m.utilizacaoCpuPercentual(),m.vazao()));stats.setWrapText(true);return new VBox(5,h,stats);}
    private TableView<String[]> tabelaComparativa(ComparacaoEscalonamento c){TableView<String[]> t=new TableView<>();t.setPrefHeight(165);t.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);String[][] rows={{"Espera média",fmt(c.fcfs().metricas().tempoMedioEspera()),fmt(c.roundRobin().metricas().tempoMedioEspera())},{"Retorno médio",fmt(c.fcfs().metricas().tempoMedioRetorno()),fmt(c.roundRobin().metricas().tempoMedioRetorno())},{"Resposta média",fmt(c.fcfs().metricas().tempoMedioResposta()),fmt(c.roundRobin().metricas().tempoMedioResposta())},{"Duração total",String.valueOf(c.fcfs().metricas().makespan()),String.valueOf(c.roundRobin().metricas().makespan())},{"Trocas de contexto",String.valueOf(c.fcfs().metricas().trocasContexto()),String.valueOf(c.roundRobin().metricas().trocasContexto())}};for(int i=0;i<3;i++){final int n=i;TableColumn<String[],String> col=new TableColumn<>(new String[]{"Métrica","FCFS","Round Robin"}[i]);col.setCellValueFactory(v->new javafx.beans.property.ReadOnlyStringWrapper(v.getValue()[n]));t.getColumns().add(col);}t.setItems(FXCollections.observableArrayList(rows));return t;}
    private String fmt(double n){return String.format(Locale.forLanguageTag("pt-BR"),"%.2f",n);}
    private void executarSync(){List<ProcessoUrbano> carga=controller.obterProcessos();if(carga.isEmpty()){erro("Cadastre pelo menos um processo.");return;}syncOutput.setText("Executando os sensores concorrentes...");CompletableFuture.supplyAsync(()->{try{return new SimuladorSincronismo().comparar(carga,Path.of("target","experimento-sincronismo"));}catch(Exception ex){throw new RuntimeException(ex);}}).whenComplete((r,ex)->Platform.runLater(()->{if(ex!=null){syncOutput.setText("Falha: "+ex.getCause().getMessage());return;}syncOutput.setText(formatSync(r.semSincronismo())+"\n\n"+formatSync(r.comSincronismo()));}));}
    private String formatSync(ResultadoSincronismo r){return r.modo()+"\nArquivo: "+r.arquivo()+"\nEsperados: "+r.registrosEsperados()+" | Válidos: "+r.registrosValidos()+" | Corrompidos: "+r.registrosCorrompidos()+"\nDuração: "+fmt(r.duracaoMs())+" ms | Espera média pelo lock: "+fmt(r.tempoMedioEsperaMs())+" ms\nColisão: "+(r.houveColisao()?"DETECTADA":"não detectada")+"\n"+r.conteudoArquivo();}
    private void erro(String mensagem){Alert a=new Alert(Alert.AlertType.ERROR,mensagem,ButtonType.OK);a.setHeaderText("Não foi possível executar");a.showAndWait();}
}
