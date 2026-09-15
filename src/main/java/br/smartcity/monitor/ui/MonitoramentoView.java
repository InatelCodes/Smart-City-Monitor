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
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.Spinner;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.BorderPane;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.function.Function;

/** Conteúdo da aba de acompanhamento em tempo real. */
public final class MonitoramentoView extends BorderPane {

    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");
    private static final NumberFormat INTEIRO = NumberFormat.getIntegerInstance(PT_BR);
    private static final DateTimeFormatter HORARIO = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final int MAX_EVENTOS_RECENTES = 100;
    private static final int MAX_PONTOS_GRAFICO = 240;

    private final Spinner<Integer> tempoProcessamento = new Spinner<>(0, 5_000, 120, 10);
    private final Slider sliderThreads = new Slider(1, 16, 2);
    private final Label valorThreads = new Label("2 Threads");

    private final Button iniciar = new Button("Iniciar experimento");
    private final Button parar = new Button("Parar");
    private final Button resetar = new Button("Resetar");

    private final Label threadsAtivas = new Label("0 ativas");

    private final Label eventosProcessados = valorMetrica("0");
    private final Label eventosPendentes = valorMetrica("0");
    private final Label taxaProcessada = valorMetrica("0,0 ev/s");
    private final Label tempoMedio = valorMetrica("0 ms");
    private final Label tempoTotal = valorMetrica("0,0 s");

    private final CidadeMonitorView cidadeMonitor = new CidadeMonitorView();
    private final XYChart.Series<Number, Number> seriePendentes = new XYChart.Series<>();
    private LineChart<Number, Number> graficoFila;
    private final Label graficoFilaVazio = new Label("Inicie um experimento para acompanhar a fila.");

    private final TableView<ResultadoProcessamento> tabelaEventos = new TableView<>();
    private final ObservableList<ResultadoProcessamento> eventos = FXCollections.observableArrayList();

    public MonitoramentoView() {
        getStyleClass().add("monitoramento-view");
        setPadding(new Insets(18, 22, 20, 22));

        Node configuracao = criarConfiguracao();
        Node conteudo = criarConteudo();

        ScrollPane rolagem = new ScrollPane(conteudo);
        rolagem.setFitToWidth(true);
        rolagem.setPannable(true);
        rolagem.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        rolagem.getStyleClass().add("dashboard-scroll");

        setTop(configuracao);
        setCenter(rolagem);
        BorderPane.setMargin(rolagem, new Insets(14, -10, -8, -10));

        setExecutando(false);
    }

    private Node criarConfiguracao() {
        sliderThreads.setBlockIncrement(1);
        sliderThreads.setMajorTickUnit(1);
        sliderThreads.setMinorTickCount(0);
        sliderThreads.setSnapToTicks(true);
        sliderThreads.setShowTickMarks(false);
        sliderThreads.setShowTickLabels(false);

        valorThreads.getStyleClass().add("thread-value");

        sliderThreads.valueProperty().addListener((obs, antigo, novo) -> {
            int quantidade = novo.intValue();
            valorThreads.setText(quantidade == 1 ? "1 Thread" : quantidade + " Threads");
        });

        tempoProcessamento.setEditable(true);
        tempoProcessamento.setPrefWidth(118);

        Label titulo = new Label("CONFIGURAÇÃO");
        titulo.getStyleClass().add("eyebrow");

        Label carga = new Label("400 eventos fixos");
        carga.getStyleClass().add("config-badge");

        HBox cabecalho = new HBox(10, titulo, carga);
        cabecalho.setAlignment(Pos.CENTER_LEFT);

        Label min = new Label("1");
        Label max = new Label("16");
        min.getStyleClass().add("slider-limit");
        max.getStyleClass().add("slider-limit");
        HBox limites = new HBox(min, criarEspaco(), max);

        VBox controleThreads = new VBox(
                4,
                criarRotuloCampo("THREADS DA CENTRAL"),
                valorThreads,
                sliderThreads,
                limites
        );
        controleThreads.getStyleClass().add("config-section");
        HBox.setHgrow(controleThreads, Priority.ALWAYS);

        Label unidade = new Label("ms");
        unidade.getStyleClass().add("unit-label");
        HBox tempo = new HBox(8, tempoProcessamento, unidade);
        tempo.setAlignment(Pos.CENTER_LEFT);

        VBox controleTempo = new VBox(
                6,
                criarRotuloCampo("CARGA BASE POR EVENTO"),
                tempo,
                new Label("Mantida constante; custo de coordenação cresce após 8 Threads")
        );
        controleTempo.getChildren().get(2).getStyleClass().add("config-hint");

        VBox controles = new VBox(6, cabecalho, new HBox(26, controleThreads, controleTempo));
        controles.getStyleClass().addAll("surface", "config-card");
        VBox.setVgrow(controleThreads, Priority.ALWAYS);

        HBox botoes = new HBox(9, iniciar, parar, resetar);
        botoes.setAlignment(Pos.CENTER_LEFT);
        botoes.getStyleClass().add("action-buttons");
        iniciar.getStyleClass().add("primary-button");
        parar.getStyleClass().add("danger-button");
        resetar.getStyleClass().add("secondary-button");

        VBox resultado = new VBox(9, controles, botoes);
        return resultado;
    }

    private Node criarConteudo() {
        HBox metricas = new HBox(12,
                criarCardMetrica("PROCESSADOS", eventosProcessados, "Eventos concluídos"),
                criarCardMetrica("PENDENTES", eventosPendentes, "Ainda na fila"),
                criarCardMetrica("VAZÃO", taxaProcessada, "Eventos por segundo"),
                criarCardMetrica("TEMPO MÉDIO", tempoMedio, "Da criação ao processamento"),
                criarCardMetrica("TEMPO TOTAL", tempoTotal, "Duração do experimento")
        );
        metricas.getStyleClass().add("metrics-row");

        NumberAxis eixoX = new NumberAxis();
        eixoX.setLabel("Tempo (s)");
        eixoX.setForceZeroInRange(true);

        NumberAxis eixoY = new NumberAxis();
        eixoY.setLabel("Eventos na fila");
        eixoY.setForceZeroInRange(true);

        graficoFila = new LineChart<>(eixoX, eixoY);
        graficoFila.setTitle("Eventos pendentes");
        graficoFila.setLegendVisible(false);
        graficoFila.setCreateSymbols(false);
        graficoFila.setAnimated(false);
        graficoFila.setMinHeight(250);
        graficoFila.setPrefHeight(270);
        graficoFila.getData().add(seriePendentes);
        graficoFila.getStyleClass().add("live-chart");

        graficoFilaVazio.getStyleClass().add("chart-empty");
        graficoFila.setVisible(false);
        StackPane graficoContainer = new StackPane(graficoFila, graficoFilaVazio);
        graficoContainer.setMinHeight(250);
        graficoContainer.setPrefHeight(270);

        VBox cidadeCard = criarPainelVisual(
                "FLUXO EM TEMPO REAL",
                "Os eventos percorrem as regiões até a Central.",
                cidadeMonitor
        );
        VBox graficoCard = criarPainelVisual(
                "FILA",
                "Eventos que ainda aguardam processamento.",
                graficoContainer
        );

        HBox visualizacao = new HBox(14, cidadeCard, graficoCard);
        visualizacao.getStyleClass().add("visualization-row");
        HBox.setHgrow(cidadeCard, Priority.ALWAYS);
        HBox.setHgrow(graficoCard, Priority.ALWAYS);
        cidadeCard.setPrefWidth(520);
        graficoCard.setPrefWidth(520);
        cidadeCard.setMinWidth(0);
        graficoCard.setMinWidth(0);

        configurarTabelaEventos();

        Label tituloEventos = new Label("ÚLTIMOS EVENTOS");
        tituloEventos.getStyleClass().add("eyebrow");
        Label detalheEventos = new Label("Processamentos concluídos mais recentes");
        detalheEventos.getStyleClass().add("section-subtitle");

        VBox tabelaCard = new VBox(
                8,
                new VBox(2, tituloEventos, detalheEventos),
                tabelaEventos
        );
        tabelaCard.getStyleClass().addAll("surface", "events-card");
        VBox.setVgrow(tabelaEventos, Priority.ALWAYS);
        tabelaEventos.setPrefHeight(190);
        tabelaEventos.setMinHeight(160);

        VBox conteudo = new VBox(14, metricas, visualizacao, tabelaCard);
        conteudo.setPadding(new Insets(0, 0, 12, 0));
        VBox.setVgrow(visualizacao, Priority.ALWAYS);
        return conteudo;
    }

    private VBox criarPainelVisual(String titulo, String detalhe, Node conteudo) {
        Label tituloLabel = new Label(titulo);
        tituloLabel.getStyleClass().add("eyebrow");
        Label detalheLabel = new Label(detalhe);
        detalheLabel.getStyleClass().add("section-subtitle");

        VBox card = new VBox(
                8,
                new VBox(2, tituloLabel, detalheLabel),
                conteudo
        );
        card.getStyleClass().addAll("surface", "visual-card");
        card.setPadding(new Insets(14));
        VBox.setVgrow(conteudo, Priority.ALWAYS);
        return card;
    }

    private Node criarCardMetrica(String titulo, Label valor, String detalhe) {
        Label rotulo = new Label(titulo);
        rotulo.getStyleClass().add("metric-label");

        Label apoio = new Label(detalhe);
        apoio.getStyleClass().add("metric-detail");

        VBox card = new VBox(5, rotulo, valor, apoio);
        card.getStyleClass().addAll("surface", "metric-card");
        HBox.setHgrow(card, Priority.ALWAYS);
        return card;
    }

    private void configurarTabelaEventos() {
        tabelaEventos.setItems(eventos);
        tabelaEventos.setPlaceholder(new Label("Nenhum evento processado ainda."));
        tabelaEventos.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        tabelaEventos.getColumns().addAll(
                coluna("HORA", 0.12, r -> r.getTimestampProcessamento().format(HORARIO)),
                coluna("TIPO", 0.16, r -> nomeTipo(r.getEvento().getTipo())),
                coluna("EVENTO", 0.38, r -> r.getEvento().getDescricao()),
                coluna("THREAD", 0.20, ResultadoProcessamento::getThreadResponsavel),
                coluna("RESPOSTA", 0.14, r -> INTEIRO.format(r.getTempoRespostaMs()) + " ms")
        );
    }

    private TableColumn<ResultadoProcessamento, String> coluna(
            String titulo,
            double largura,
            Function<ResultadoProcessamento, String> valor
    ) {
        TableColumn<ResultadoProcessamento, String> coluna = new TableColumn<>(titulo);
        coluna.setCellValueFactory(dado -> new SimpleStringProperty(valor.apply(dado.getValue())));
        coluna.prefWidthProperty().bind(tabelaEventos.widthProperty().multiply(largura));
        return coluna;
    }

    private static String nomeTipo(TipoEvento tipo) {
        return switch (tipo) {
            case TRANSITO -> "Trânsito";
            case CLIMA -> "Clima";
            case ENERGIA -> "Energia";
            case QUALIDADE_AR -> "Qualidade do ar";
        };
    }

    public void atualizar(DashboardSnapshot snapshot, boolean registrarPonto) {
        eventosProcessados.setText(INTEIRO.format(snapshot.eventosProcessados()));
        eventosPendentes.setText(INTEIRO.format(snapshot.eventosPendentes()));
        threadsAtivas.setText(snapshot.threadsAtivas() + " ativas");

        taxaProcessada.setText(String.format(PT_BR, "%.1f ev/s", snapshot.taxaProcessamento()));
        tempoMedio.setText(String.format(PT_BR, "%.0f ms", snapshot.tempoMedioRespostaMs()));
        tempoTotal.setText(String.format(PT_BR, "%.1f s", snapshot.tempoDecorridoSegundos()));

        cidadeMonitor.atualizar(snapshot);

        graficoFila.setVisible(registrarPonto || !seriePendentes.getData().isEmpty());
        graficoFilaVazio.setVisible(!graficoFila.isVisible());

        if (registrarPonto) {
            seriePendentes.getData().add(new XYChart.Data<>(
                    snapshot.tempoDecorridoSegundos(),
                    snapshot.eventosPendentes()
            ));

            if (seriePendentes.getData().size() > MAX_PONTOS_GRAFICO) {
                seriePendentes.getData().remove(0);
            }
        }
    }

    public void adicionarEvento(ResultadoProcessamento resultado) {
        eventos.add(0, resultado);
        cidadeMonitor.animarProcessamento(resultado);

        if (eventos.size() > MAX_EVENTOS_RECENTES) {
            eventos.remove(MAX_EVENTOS_RECENTES, eventos.size());
        }
    }

    public void limparDados() {
        eventos.clear();
        seriePendentes.getData().clear();
        graficoFila.setVisible(false);
        graficoFilaVazio.setVisible(true);
        cidadeMonitor.resetar();
        atualizar(DashboardSnapshot.vazio(), false);
    }

    public int getQuantidadeThreads() {
        return (int) Math.round(sliderThreads.getValue());
    }

    public int getTempoProcessamento() {
        String texto = tempoProcessamento.getEditor().getText().trim();
        try {
            int valor = Integer.parseInt(texto);
            if (valor < 0 || valor > 5_000) {
                throw new IllegalArgumentException("O tempo de processamento deve estar entre 0 e 5.000 ms");
            }
            tempoProcessamento.getValueFactory().setValue(valor);
            return valor;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "Informe o tempo de processamento em milissegundos, usando apenas números"
            );
        }
    }

    public void setExecutando(boolean executando) {
        sliderThreads.setDisable(executando);
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

    private static Region criarEspaco() {
        Region espaco = new Region();
        HBox.setHgrow(espaco, Priority.ALWAYS);
        return espaco;
    }
}
