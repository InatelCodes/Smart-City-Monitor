package br.smartcity.monitor.ui;

import br.smartcity.monitor.model.ResultadoProcessamento;
import br.smartcity.monitor.model.TipoEvento;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.Spinner;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.function.Function;

/**
 * Conteúdo da aba de acompanhamento em tempo real.
 */
public final class MonitoramentoView extends BorderPane {

    private static final Locale PT_BR =
            Locale.forLanguageTag("pt-BR");

    private static final NumberFormat INTEIRO =
            NumberFormat.getIntegerInstance(PT_BR);

    private static final DateTimeFormatter HORARIO =
            DateTimeFormatter.ofPattern("HH:mm:ss");

    private static final int MAX_EVENTOS_RECENTES = 100;
    private static final int MAX_PONTOS_GRAFICO = 240;

    /*
     * Configuração do experimento
     */
    private final Spinner<Integer> tempoProcessamento =
            new Spinner<>(0, 5_000, 120, 10);

    private final Slider sliderThreads =
            new Slider(1, 4, 2);

    private final Label valorThreads =
            new Label("2 Threads");

    /*
     * Botões
     */
    private final Button iniciar =
            new Button("Iniciar experimento");

    private final Button parar =
            new Button("Parar");

    private final Button resetar =
            new Button("Resetar");

    /*
     * Informações gerais
     */
    private final Label threadsAtivas =
            new Label("0 ativas");

    /*
     * Métricas
     */
    private final Label eventosGerados =
            valorMetrica("0");

    private final Label eventosProcessados =
            valorMetrica("0");

    private final Label eventosPendentes =
            valorMetrica("0");

    private final Label taxaProcessada =
            valorMetrica("0,0 ev/s");

    private final Label tempoMedio =
            valorMetrica("0 ms");

    private final Label tempoTotal =
            valorMetrica("0,0 s");

    /*
     * Gráfico
     */
    private final XYChart.Series<Number, Number> seriePendentes =
            new XYChart.Series<>();

    /*
     * Tabela de eventos
     */
    private final TableView<ResultadoProcessamento> tabelaEventos =
            new TableView<>();

    private final ObservableList<ResultadoProcessamento> eventos =
            FXCollections.observableArrayList();

    public MonitoramentoView() {

        getStyleClass().add("monitoramento-view");

        setPadding(
                new Insets(20, 24, 24, 24)
        );

        Node configuracao =
                criarConfiguracao();

        Node conteudo =
                criarConteudo();

        setTop(configuracao);
        setCenter(conteudo);

        BorderPane.setMargin(
                conteudo,
                new Insets(18, 0, 0, 0)
        );

        /*
         * Estado inicial:
         * experimento parado.
         */
        setExecutando(false);
    }

    /**
     * Cria a área de configuração do experimento.
     */
    private Node criarConfiguracao() {

        /*
         * Slider de Threads
         */
        sliderThreads.setBlockIncrement(1);
        sliderThreads.setMajorTickUnit(1);
        sliderThreads.setMinorTickCount(0);
        sliderThreads.setSnapToTicks(true);
        sliderThreads.setShowTickMarks(true);
        sliderThreads.setShowTickLabels(true);

        valorThreads
                .getStyleClass()
                .add("thread-value");

        sliderThreads.valueProperty().addListener(
                (obs, antigo, novo) -> {

                    int quantidade =
                            novo.intValue();

                    valorThreads.setText(
                            quantidade == 1
                                    ? "1 Thread"
                                    : quantidade + " Threads"
                    );
                }
        );

        /*
         * Tempo de processamento
         */
        tempoProcessamento.setEditable(true);

        /*
         * Bloco de Threads
         */
        VBox controleThreads =
                new VBox(
                        5,
                        criarRotuloCampo(
                                "THREADS DA CENTRAL"
                        ),
                        valorThreads,
                        sliderThreads
                );

        controleThreads
                .getStyleClass()
                .add("config-section");

        /*
         * Bloco de tempo
         */
        VBox controleProcessamento =
                new VBox(
                        7,
                        criarRotuloCampo(
                                "TEMPO DE PROCESSAMENTO"
                        ),
                        tempoProcessamento
                );

        controleProcessamento
                .getStyleClass()
                .add("config-section");

        controleThreads.setMaxWidth(
                Double.MAX_VALUE
        );

        controleProcessamento.setMaxWidth(
                Double.MAX_VALUE
        );

        /*
         * Grid dos controles
         */
        GridPane configuracao =
                new GridPane();

        configuracao.setHgap(32);
        configuracao.setVgap(10);

        configuracao
                .getStyleClass()
                .addAll(
                        "surface",
                        "config-card"
                );

        configuracao.add(
                controleThreads,
                0,
                0
        );

        configuracao.add(
                controleProcessamento,
                1,
                0
        );

        /*
         * Coluna das Threads
         */
        ColumnConstraints coluna1 =
                new ColumnConstraints();

        coluna1.setPercentWidth(70);
        coluna1.setHgrow(
                Priority.ALWAYS
        );

        /*
         * Coluna do tempo
         */
        ColumnConstraints coluna2 =
                new ColumnConstraints();

        coluna2.setPercentWidth(30);
        coluna2.setHgrow(
                Priority.ALWAYS
        );

        configuracao
                .getColumnConstraints()
                .addAll(
                        coluna1,
                        coluna2
                );

        /*
         * Botões
         */
        HBox botoes =
                new HBox(
                        10,
                        iniciar,
                        parar,
                        resetar
                );

        botoes.setPadding(
                new Insets(12, 0, 0, 0)
        );

        botoes
                .getStyleClass()
                .add("action-buttons");

        iniciar
                .getStyleClass()
                .add("primary-button");

        parar
                .getStyleClass()
                .add("danger-button");

        resetar
                .getStyleClass()
                .add("secondary-button");

        /*
         * Junta configurações + botões
         */
        VBox resultado =
                new VBox(
                        0,
                        configuracao,
                        botoes
                );

        return resultado;
    }

    /**
     * Cria o conteúdo principal do dashboard.
     */
    private Node criarConteudo() {

        /*
         * =========================
         * MÉTRICAS
         * =========================
         */

        GridPane metricas =
                new GridPane();

        metricas.setHgap(12);
        metricas.setVgap(12);

        /*
         * Eventos gerados
         */
        metricas.add(
                criarCardMetrica(
                        "EVENTOS GERADOS",
                        eventosGerados,
                        "Carga fixa do experimento",
                        false
                ),
                0,
                0
        );

        /*
         * Eventos processados
         */
        metricas.add(
                criarCardMetrica(
                        "EVENTOS PROCESSADOS",
                        eventosProcessados,
                        "Concluídos pela central",
                        false
                ),
                1,
                0
        );

        /*
         * Eventos pendentes
         */
        metricas.add(
                criarCardMetrica(
                        "EVENTOS PENDENTES",
                        eventosPendentes,
                        "Aguardando na fila",
                        true
                ),
                2,
                0
        );

        /*
         * Vazão
         */
        metricas.add(
                criarCardMetrica(
                        "VAZÃO",
                        taxaProcessada,
                        "Eventos processados por segundo",
                        false
                ),
                0,
                1
        );

        /*
         * Tempo médio
         */
        metricas.add(
                criarCardMetrica(
                        "TEMPO MÉDIO",
                        tempoMedio,
                        "Criação até processamento",
                        false
                ),
                1,
                1
        );

        /*
         * Tempo total
         */
        metricas.add(
                criarCardMetrica(
                        "TEMPO TOTAL",
                        tempoTotal,
                        "Duração do experimento",
                        false
                ),
                2,
                1
        );

        /*
         * Distribuição das três colunas
         */
        for (int i = 0; i < 3; i++) {

            ColumnConstraints coluna =
                    new ColumnConstraints();

            coluna.setPercentWidth(33.33);

            coluna.setHgrow(
                    Priority.ALWAYS
            );

            metricas
                    .getColumnConstraints()
                    .add(coluna);
        }

        /*
         * =========================
         * GRÁFICO
         * =========================
         */

        NumberAxis eixoX =
                new NumberAxis();

        eixoX.setLabel(
                "Tempo de execução (segundos)"
        );

        eixoX.setForceZeroInRange(true);

        NumberAxis eixoY =
                new NumberAxis();

        eixoY.setLabel(
                "Eventos pendentes"
        );

        eixoY.setForceZeroInRange(true);

        LineChart<Number, Number> grafico =
                new LineChart<>(
                        eixoX,
                        eixoY
                );

        grafico.setTitle(
                "Eventos pendentes × tempo"
        );

        grafico.setLegendVisible(false);
        grafico.setCreateSymbols(false);
        grafico.setAnimated(false);

        grafico
                .getData()
                .add(seriePendentes);

        grafico
                .getStyleClass()
                .add("live-chart");

        VBox.setVgrow(
                grafico,
                Priority.ALWAYS
        );

        /*
         * =========================
         * TABELA
         * =========================
         */

        configurarTabelaEventos();

        Label tituloEventos =
                new Label(
                        "Eventos recentes"
                );

        tituloEventos
                .getStyleClass()
                .add("section-title");

        Label detalheEventos =
                new Label(
                        "Últimos processamentos concluídos"
                );

        detalheEventos
                .getStyleClass()
                .add("section-subtitle");

        VBox tabelaCabecalho =
                new VBox(
                        3,
                        tituloEventos,
                        detalheEventos
                );

        VBox tabelaCard =
                new VBox(
                        12,
                        tabelaCabecalho,
                        tabelaEventos
                );

        tabelaCard
                .getStyleClass()
                .add("surface");

        tabelaCard.setPadding(
                new Insets(18)
        );

        VBox.setVgrow(
                tabelaEventos,
                Priority.ALWAYS
        );

        /*
         * Painel superior
         */
        VBox painelSuperior =
                new VBox(
                        14,
                        metricas,
                        grafico
                );

        VBox.setVgrow(
                grafico,
                Priority.ALWAYS
        );

        /*
         * Divisão gráfico/tabela
         */
        SplitPane divisao =
                new SplitPane(
                        painelSuperior,
                        tabelaCard
                );

        divisao.setOrientation(
                javafx.geometry.Orientation.VERTICAL
        );

        divisao.setDividerPositions(
                0.64
        );

        divisao
                .getStyleClass()
                .add("content-split");

        return divisao;
    }

    /**
     * Cria um card de métrica.
     */
    private Node criarCardMetrica(
            String titulo,
            Label valor,
            String detalhe,
            boolean destaque
    ) {

        Label rotulo =
                new Label(titulo);

        rotulo
                .getStyleClass()
                .add("metric-label");

        Label apoio =
                new Label(detalhe);

        apoio
                .getStyleClass()
                .add("metric-detail");

        VBox card =
                new VBox(
                        7,
                        rotulo,
                        valor,
                        apoio
                );

        card
                .getStyleClass()
                .addAll(
                        "surface",
                        "metric-card"
                );

        if (destaque) {

            card
                    .getStyleClass()
                    .add(
                            "metric-card-highlight"
                    );

            valor
                    .getStyleClass()
                    .add(
                            "metric-value-highlight"
                    );
        }

        card.setMaxWidth(
                Double.MAX_VALUE
        );

        GridPane.setHgrow(
                card,
                Priority.ALWAYS
        );

        return card;
    }

    /**
     * Configura a tabela de eventos processados.
     */
    private void configurarTabelaEventos() {

        tabelaEventos.setItems(
                eventos
        );

        tabelaEventos.setPlaceholder(
                new Label(
                        "Os eventos processados aparecerão aqui."
                )
        );

        tabelaEventos.setColumnResizePolicy(
                TableView.CONSTRAINED_RESIZE_POLICY
        );

        /*
         * Horário
         */
        TableColumn<
                ResultadoProcessamento,
                String
                > horario =
                coluna(
                        "HORÁRIO",
                        0.13,
                        r ->
                                r.getTimestampProcessamento()
                                        .format(HORARIO)
                );

        /*
         * Tipo
         */
        TableColumn<
                ResultadoProcessamento,
                String
                > tipo =
                coluna(
                        "TIPO",
                        0.17,
                        r ->
                                nomeTipo(
                                        r.getEvento()
                                                .getTipo()
                                )
                );

        /*
         * Descrição
         */
        TableColumn<
                ResultadoProcessamento,
                String
                > descricao =
                coluna(
                        "DESCRIÇÃO",
                        0.32,
                        r ->
                                r.getEvento()
                                        .getDescricao()
                );

        /*
         * Thread
         */
        TableColumn<
                ResultadoProcessamento,
                String
                > thread =
                coluna(
                        "THREAD",
                        0.24,
                        ResultadoProcessamento
                                ::getThreadResponsavel
                );

        /*
         * Tempo de resposta
         */
        TableColumn<
                ResultadoProcessamento,
                String
                > resposta =
                coluna(
                        "RESPOSTA",
                        0.14,
                        r ->
                                INTEIRO.format(
                                        r.getTempoRespostaMs()
                                )
                                        + " ms"
                );

        /*
         * Adiciona as colunas individualmente.
         *
         * Isso evita o warning de varargs
         * que aparecia anteriormente.
         */
        tabelaEventos
                .getColumns()
                .add(horario);

        tabelaEventos
                .getColumns()
                .add(tipo);

        tabelaEventos
                .getColumns()
                .add(descricao);

        tabelaEventos
                .getColumns()
                .add(thread);

        tabelaEventos
                .getColumns()
                .add(resposta);
    }

    /**
     * Cria uma coluna da tabela.
     */
    private TableColumn<
            ResultadoProcessamento,
            String
            > coluna(
                    String titulo,
                    double largura,
                    Function<
                            ResultadoProcessamento,
                            String
                            > valor
            ) {

        TableColumn<
                ResultadoProcessamento,
                String
                > coluna =
                new TableColumn<>(
                        titulo
                );

        coluna.setCellValueFactory(
                dado ->
                        new SimpleStringProperty(
                                valor.apply(
                                        dado.getValue()
                                )
                        )
        );

        coluna.prefWidthProperty().bind(
                tabelaEventos
                        .widthProperty()
                        .multiply(largura)
        );

        return coluna;
    }

    /**
     * Nome amigável dos tipos de evento.
     */
    private static String nomeTipo(
            TipoEvento tipo
    ) {

        return switch (tipo) {

            case TRANSITO ->
                    "Trânsito";

            case CLIMA ->
                    "Clima";

            case ENERGIA ->
                    "Energia";

            case QUALIDADE_AR ->
                    "Qualidade do ar";
        };
    }

    /**
     * Atualiza as métricas mostradas no dashboard.
     */
    public void atualizar(
            DashboardSnapshot snapshot,
            boolean registrarPonto
    ) {

        eventosGerados.setText(
                INTEIRO.format(
                        snapshot.eventosGerados()
                )
        );

        eventosProcessados.setText(
                INTEIRO.format(
                        snapshot.eventosProcessados()
                )
        );

        eventosPendentes.setText(
                INTEIRO.format(
                        snapshot.eventosPendentes()
                )
        );

        threadsAtivas.setText(
                snapshot.threadsAtivas()
                        + " ativas"
        );

        taxaProcessada.setText(
                String.format(
                        PT_BR,
                        "%.1f ev/s",
                        snapshot.taxaProcessamento()
                )
        );

        tempoMedio.setText(
                String.format(
                        PT_BR,
                        "%.0f ms",
                        snapshot.tempoMedioRespostaMs()
                )
        );

        tempoTotal.setText(
                String.format(
                        PT_BR,
                        "%.1f s",
                        snapshot.tempoDecorridoSegundos()
                )
        );

        /*
         * Adiciona ponto ao gráfico somente
         * durante a execução.
         */
        if (registrarPonto) {

            seriePendentes
                    .getData()
                    .add(
                            new XYChart.Data<>(
                                    snapshot
                                            .tempoDecorridoSegundos(),
                                    snapshot
                                            .eventosPendentes()
                            )
                    );

            if (
                    seriePendentes
                            .getData()
                            .size()
                            > MAX_PONTOS_GRAFICO
            ) {

                seriePendentes
                        .getData()
                        .remove(0);
            }
        }
    }

    /**
     * Adiciona um resultado à tabela.
     */
    public void adicionarEvento(
            ResultadoProcessamento resultado
    ) {

        eventos.add(
                0,
                resultado
        );

        if (
                eventos.size()
                        > MAX_EVENTOS_RECENTES
        ) {

            eventos.remove(
                    MAX_EVENTOS_RECENTES,
                    eventos.size()
            );
        }
    }

    /**
     * Limpa os dados do dashboard.
     */
    public void limparDados() {

        eventos.clear();

        seriePendentes
                .getData()
                .clear();

        atualizar(
                DashboardSnapshot.vazio(),
                false
        );
    }

    /**
     * Retorna a quantidade de Threads escolhida.
     */
    public int getQuantidadeThreads() {

        return (int) Math.round(
                sliderThreads.getValue()
        );
    }

    /**
     * Retorna o tempo de processamento.
     */
    public int getTempoProcessamento() {

        String texto =
                tempoProcessamento
                        .getEditor()
                        .getText()
                        .trim();

        try {

            int valor =
                    Integer.parseInt(texto);

            if (
                    valor < 0
                            || valor > 5_000
            ) {

                throw new IllegalArgumentException(
                        "O tempo de processamento deve estar entre 0 e 5.000 ms"
                );
            }

            tempoProcessamento
                    .getValueFactory()
                    .setValue(valor);

            return valor;

        } catch (
                NumberFormatException e
        ) {

            throw new IllegalArgumentException(
                    "Informe o tempo de processamento em milissegundos, usando apenas números"
            );
        }
    }

    /**
     * Atualiza os controles conforme
     * o estado do experimento.
     */
    public void setExecutando(
            boolean executando
    ) {

        sliderThreads.setDisable(
                executando
        );

        tempoProcessamento.setDisable(
                executando
        );

        iniciar.setDisable(
                executando
        );

        parar.setDisable(
                !executando
        );

        resetar.setDisable(
                executando
        );
    }

    /**
     * Estado intermediário enquanto
     * as Threads estão sendo encerradas.
     */
    public void setFinalizando() {

        iniciar.setDisable(true);
        parar.setDisable(true);
        resetar.setDisable(true);
    }

    public Button getBotaoIniciar() {
        return iniciar;
    }

    public Button getBotaoParar() {
        return parar;
    }

    public Button getBotaoResetar() {
        return resetar;
    }

    public Label getThreadsAtivas() {
        return threadsAtivas;
    }

    private static Label criarRotuloCampo(
            String texto
    ) {

        Label label =
                new Label(texto);

        label
                .getStyleClass()
                .add("field-label");

        return label;
    }

    private static Label valorMetrica(
            String texto
    ) {

        Label label =
                new Label(texto);

        label
                .getStyleClass()
                .add("metric-value");

        return label;
    }
}