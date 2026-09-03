package br.smartcity.monitor.ui;

import br.smartcity.monitor.model.ResultadoProcessamento;
import br.smartcity.monitor.model.TipoEvento;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.Spinner;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Conteúdo da aba de acompanhamento em tempo real. */
public final class MonitoramentoView extends BorderPane {

    private static final DateTimeFormatter HORARIO = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");
    private static final NumberFormat INTEIRO = NumberFormat.getIntegerInstance(PT_BR);
    private static final int MAX_EVENTOS_RECENTES = 100;
    private static final int MAX_PONTOS_GRAFICO = 240;

    private final ToggleGroup grupoThreads = new ToggleGroup();
    private final ComboBox<Integer> taxaGeracao = new ComboBox<>();
    private final Spinner<Integer> tempoProcessamento = new Spinner<>(0, 5_000, 100, 10);
    private final Button iniciar = new Button("Iniciar experimento");
    private final Button parar = new Button("Parar");
    private final Button resetar = new Button("Resetar");

    private final Label threadsAtivas = new Label("0 ativas");
    private final Label eventosGerados = valorMetrica("0");
    private final Label eventosProcessados = valorMetrica("0");
    private final Label eventosPendentes = valorMetrica("0");
    private final Label taxaEntrada = valorMetrica("0,0 ev/s");
    private final Label taxaProcessada = valorMetrica("0,0 ev/s");
    private final Label tempoMedio = valorMetrica("0 ms");

    private final XYChart.Series<Number, Number> seriePendentes = new XYChart.Series<>();
    private final TableView<ResultadoProcessamento> tabelaEventos = new TableView<>();
    private final ObservableList<ResultadoProcessamento> eventos = FXCollections.observableArrayList();

    public MonitoramentoView() {
        getStyleClass().add("monitoramento-view");
        setPadding(new Insets(20, 24, 24, 24));

        Node configuracao = criarConfiguracao();
        Node conteudo = criarConteudo();
        setTop(configuracao);
        setCenter(conteudo);
        BorderPane.setMargin(conteudo, new Insets(18, 0, 0, 0));
    }

    private Node criarConfiguracao() {
        Label titulo = new Label("Configuração do experimento");
        titulo.getStyleClass().add("section-title");
        Label subtitulo = new Label("Defina a carga e a capacidade da central antes de iniciar.");
        subtitulo.getStyleClass().add("section-subtitle");

        HBox seletorThreads = new HBox(6);
        for (int quantidade = 1; quantidade <= 4; quantidade++) {
            ToggleButton botao = new ToggleButton(String.valueOf(quantidade));
            botao.setUserData(quantidade);
            botao.setToggleGroup(grupoThreads);
            botao.getStyleClass().add("thread-toggle");
            if (quantidade == 1) {
                botao.setSelected(true);
            }
            seletorThreads.getChildren().add(botao);
        }

        taxaGeracao.getItems().addAll(5, 10, 15, 20, 25, 30);
        taxaGeracao.setValue(25);
        taxaGeracao.setMaxWidth(Double.MAX_VALUE);
        taxaGeracao.setButtonCell(new TaxaListCell());
        taxaGeracao.setCellFactory(ignored -> new TaxaListCell());
        tempoProcessamento.setEditable(true);
        tempoProcessamento.setMaxWidth(Double.MAX_VALUE);

        GridPane campos = new GridPane();
        campos.setHgap(18);
        campos.setVgap(7);
        campos.add(criarRotuloCampo("THREADS CONSUMIDORAS"), 0, 0);
        campos.add(criarRotuloCampo("TAXA TOTAL DE GERAÇÃO"), 1, 0);
        campos.add(criarRotuloCampo("TEMPO POR EVENTO"), 2, 0);
        campos.add(seletorThreads, 0, 1);
        campos.add(taxaGeracao, 1, 1);
        campos.add(tempoProcessamento, 2, 1);
        for (int i = 0; i < 3; i++) {
            ColumnConstraints coluna = new ColumnConstraints();
            coluna.setPercentWidth(33.33);
            coluna.setHgrow(Priority.ALWAYS);
            campos.getColumnConstraints().add(coluna);
        }

        iniciar.getStyleClass().add("primary-button");
        parar.getStyleClass().add("danger-button");
        resetar.getStyleClass().add("secondary-button");
        parar.setDisable(true);
        HBox acoes = new HBox(10, iniciar, parar, resetar);
        acoes.setAlignment(Pos.CENTER_RIGHT);

        Region espaco = new Region();
        HBox.setHgrow(espaco, Priority.ALWAYS);
        HBox cabecalho = new HBox(16, new VBox(3, titulo, subtitulo), espaco, acoes);
        cabecalho.setAlignment(Pos.CENTER_LEFT);

        VBox card = new VBox(16, cabecalho, new Separator(), campos);
        card.getStyleClass().addAll("surface", "config-card");
        return card;
    }

    private Node criarConteudo() {
        GridPane metricas = new GridPane();
        metricas.setHgap(12);
        metricas.setVgap(12);
        metricas.add(criarCardMetrica("EVENTOS GERADOS", eventosGerados, "Recebidos dos sensores", false), 0, 0);
        metricas.add(criarCardMetrica("EVENTOS PROCESSADOS", eventosProcessados, "Concluídos pela central", false), 1, 0);
        metricas.add(criarCardMetrica("EVENTOS PENDENTES", eventosPendentes, "Aguardando na fila", true), 2, 0);
        metricas.add(criarCardMetrica("TAXA DE ENTRADA", taxaEntrada, "Média da execução", false), 0, 1);
        metricas.add(criarCardMetrica("TAXA DE PROCESSAMENTO", taxaProcessada, "Média da execução", false), 1, 1);
        metricas.add(criarCardMetrica("TEMPO MÉDIO", tempoMedio, "Criação até processamento", false), 2, 1);
        for (int i = 0; i < 3; i++) {
            ColumnConstraints coluna = new ColumnConstraints();
            coluna.setPercentWidth(33.33);
            coluna.setHgrow(Priority.ALWAYS);
            metricas.getColumnConstraints().add(coluna);
        }

        NumberAxis eixoX = new NumberAxis();
        eixoX.setLabel("Tempo de execução (segundos)");
        eixoX.setForceZeroInRange(true);
        NumberAxis eixoY = new NumberAxis();
        eixoY.setLabel("Eventos pendentes");
        eixoY.setForceZeroInRange(true);
        LineChart<Number, Number> grafico = new LineChart<>(eixoX, eixoY);
        grafico.setTitle("Eventos pendentes × tempo");
        grafico.setLegendVisible(false);
        grafico.setCreateSymbols(false);
        grafico.setAnimated(false);
        grafico.getData().add(seriePendentes);
        grafico.getStyleClass().add("live-chart");
        VBox.setVgrow(grafico, Priority.ALWAYS);

        Label tituloEventos = new Label("Eventos recentes");
        tituloEventos.getStyleClass().add("section-title");
        Label detalheEventos = new Label("Últimos processamentos concluídos");
        detalheEventos.getStyleClass().add("section-subtitle");
        configurarTabelaEventos();
        VBox tabelaCard = new VBox(12, new VBox(3, tituloEventos, detalheEventos), tabelaEventos);
        tabelaCard.getStyleClass().add("surface");
        tabelaCard.setPadding(new Insets(18));
        VBox.setVgrow(tabelaEventos, Priority.ALWAYS);

        VBox painelSuperior = new VBox(14, metricas, grafico);
        VBox.setVgrow(grafico, Priority.ALWAYS);
        SplitPane divisao = new SplitPane(painelSuperior, tabelaCard);
        divisao.setOrientation(javafx.geometry.Orientation.VERTICAL);
        divisao.setDividerPositions(0.64);
        divisao.getStyleClass().add("content-split");
        return divisao;
    }

    private void configurarTabelaEventos() {
        tabelaEventos.setItems(eventos);
        tabelaEventos.setPlaceholder(new Label("Os eventos processados aparecerão aqui."));
        tabelaEventos.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<ResultadoProcessamento, String> horario = coluna("HORÁRIO", 0.13,
                r -> r.getTimestampProcessamento().format(HORARIO));
        TableColumn<ResultadoProcessamento, String> tipo = coluna("TIPO", 0.18,
                r -> nomeTipo(r.getEvento().getTipo()));
        TableColumn<ResultadoProcessamento, String> descricao = coluna("DESCRIÇÃO", 0.31,
                r -> r.getEvento().getDescricao());
        TableColumn<ResultadoProcessamento, String> thread = coluna("THREAD", 0.24,
                ResultadoProcessamento::getThreadResponsavel);
        TableColumn<ResultadoProcessamento, String> resposta = coluna("RESPOSTA", 0.14,
                r -> INTEIRO.format(r.getTempoRespostaMs()) + " ms");
        tabelaEventos.getColumns().addAll(horario, tipo, descricao, thread, resposta);
    }

    private TableColumn<ResultadoProcessamento, String> coluna(
            String titulo,
            double largura,
            java.util.function.Function<ResultadoProcessamento, String> valor
    ) {
        TableColumn<ResultadoProcessamento, String> coluna = new TableColumn<>(titulo);
        coluna.setCellValueFactory(dado -> new SimpleStringProperty(valor.apply(dado.getValue())));
        coluna.prefWidthProperty().bind(tabelaEventos.widthProperty().multiply(largura));
        return coluna;
    }

    private Node criarCardMetrica(String titulo, Label valor, String detalhe, boolean destaque) {
        Label rotulo = new Label(titulo);
        rotulo.getStyleClass().add("metric-label");
        Label apoio = new Label(detalhe);
        apoio.getStyleClass().add("metric-detail");
        VBox card = new VBox(7, rotulo, valor, apoio);
        card.getStyleClass().addAll("surface", "metric-card");
        if (destaque) {
            card.getStyleClass().add("metric-card-highlight");
            valor.getStyleClass().add("metric-value-highlight");
        }
        card.setMaxWidth(Double.MAX_VALUE);
        GridPane.setHgrow(card, Priority.ALWAYS);
        return card;
    }

    public void atualizar(DashboardSnapshot snapshot, boolean registrarPonto) {
        eventosGerados.setText(INTEIRO.format(snapshot.eventosGerados()));
        eventosProcessados.setText(INTEIRO.format(snapshot.eventosProcessados()));
        eventosPendentes.setText(INTEIRO.format(snapshot.eventosPendentes()));
        threadsAtivas.setText(snapshot.threadsAtivas() + " ativas");
        taxaEntrada.setText(String.format(PT_BR, "%.1f ev/s", snapshot.taxaGeracao()));
        taxaProcessada.setText(String.format(PT_BR, "%.1f ev/s", snapshot.taxaProcessamento()));
        tempoMedio.setText(String.format(PT_BR, "%.0f ms", snapshot.tempoMedioRespostaMs()));

        if (registrarPonto) {
            seriePendentes.getData().add(new XYChart.Data<>(
                    snapshot.tempoDecorridoSegundos(), snapshot.eventosPendentes()));
            if (seriePendentes.getData().size() > MAX_PONTOS_GRAFICO) {
                seriePendentes.getData().remove(0);
            }
        }
    }

    public void adicionarEvento(ResultadoProcessamento resultado) {
        eventos.add(0, resultado);
        if (eventos.size() > MAX_EVENTOS_RECENTES) {
            eventos.remove(MAX_EVENTOS_RECENTES, eventos.size());
        }
    }

    public void limparDados() {
        eventos.clear();
        seriePendentes.getData().clear();
        atualizar(DashboardSnapshot.vazio(), false);
    }

    public int getQuantidadeThreads() {
        return (int) grupoThreads.getSelectedToggle().getUserData();
    }

    public int getTaxaGeracao() {
        return taxaGeracao.getValue();
    }

    public int getTempoProcessamento() {
        String texto = tempoProcessamento.getEditor().getText().trim();
        try {
            int valor = Integer.parseInt(texto);
            if (valor < 0 || valor > 5_000) {
                throw new IllegalArgumentException(
                        "O tempo de processamento deve estar entre 0 e 5.000 ms");
            }
            tempoProcessamento.getValueFactory().setValue(valor);
            return valor;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "Informe o tempo de processamento em milissegundos, usando apenas números");
        }
    }

    public void setExecutando(boolean executando) {
        grupoThreads.getToggles().forEach(toggle -> ((ToggleButton) toggle).setDisable(executando));
        taxaGeracao.setDisable(executando);
        tempoProcessamento.setDisable(executando);
        iniciar.setDisable(executando);
        parar.setDisable(!executando);
        resetar.setDisable(executando);
    }

    public void setFinalizando() {
        iniciar.setDisable(true);
        parar.setDisable(true);
        resetar.setDisable(true);
    }

    public Button getBotaoIniciar() { return iniciar; }
    public Button getBotaoParar() { return parar; }
    public Button getBotaoResetar() { return resetar; }
    public Label getThreadsAtivas() { return threadsAtivas; }

    private static Label criarRotuloCampo(String texto) {
        Label label = new Label(texto);
        label.getStyleClass().add("field-label");
        return label;
    }

    private static Label valorMetrica(String texto) {
        Label label = new Label(texto);
        label.getStyleClass().add("metric-value");
        return label;
    }

    private static String nomeTipo(TipoEvento tipo) {
        return switch (tipo) {
            case TRANSITO -> "Trânsito";
            case CLIMA -> "Clima";
            case ENERGIA -> "Energia";
            case QUALIDADE_AR -> "Qualidade do ar";
        };
    }

    private static final class TaxaListCell extends javafx.scene.control.ListCell<Integer> {
        @Override
        protected void updateItem(Integer valor, boolean vazio) {
            super.updateItem(valor, vazio);
            setText(vazio || valor == null ? null : valor + " eventos/s");
        }
    }
}
