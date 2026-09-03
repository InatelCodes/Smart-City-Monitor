package br.smartcity.monitor.ui;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Histórico e comparações construídas exclusivamente com execuções reais. */
public final class ResultadosView extends ScrollPane {

    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");
    private static final NumberFormat INTEIRO = NumberFormat.getIntegerInstance(PT_BR);
    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM HH:mm:ss");

    private final ObservableList<ExperimentoResultado> resultados = FXCollections.observableArrayList();
    private final BarChart<String, Number> graficoProcessamento;
    private final LineChart<Number, Number> graficoLatencia;
    private final LineChart<Number, Number> graficoCarga;
    private final TableView<ExperimentoResultado> tabela = new TableView<>(resultados);

    public ResultadosView() {
        setFitToWidth(true);
        setPannable(true);
        getStyleClass().add("results-scroll");

        Label titulo = new Label("Resultados dos experimentos");
        titulo.getStyleClass().add("page-title");
        Label subtitulo = new Label(
                "Compare capacidade, latência e estabilidade da fila entre configurações.");
        subtitulo.getStyleClass().add("page-subtitle");

        graficoProcessamento = criarGraficoProcessamento();
        graficoLatencia = criarGraficoLatencia();
        graficoCarga = criarGraficoCarga();

        GridPane comparacoes = new GridPane();
        comparacoes.setHgap(16);
        comparacoes.setVgap(16);
        comparacoes.add(criarCardGrafico(graficoProcessamento), 0, 0);
        comparacoes.add(criarCardGrafico(graficoLatencia), 1, 0);
        comparacoes.add(criarCardGrafico(graficoCarga), 0, 1, 2, 1);
        GridPane.setHgrow(comparacoes.getChildren().get(0), Priority.ALWAYS);
        GridPane.setHgrow(comparacoes.getChildren().get(1), Priority.ALWAYS);
        comparacoes.getColumnConstraints().addAll(
                colunaPercentual(50), colunaPercentual(50));

        configurarTabela();
        Label tituloHistorico = new Label("Histórico completo");
        tituloHistorico.getStyleClass().add("section-title");
        Label detalheHistorico = new Label("Cada parada concluída registra uma nova linha.");
        detalheHistorico.getStyleClass().add("section-subtitle");
        VBox cardTabela = new VBox(12, new VBox(3, tituloHistorico, detalheHistorico), tabela);
        cardTabela.getStyleClass().add("surface");
        cardTabela.setPadding(new Insets(18));
        tabela.setPrefHeight(300);

        VBox conteudo = new VBox(18, new VBox(4, titulo, subtitulo), comparacoes, cardTabela);
        conteudo.setPadding(new Insets(24));
        setContent(conteudo);
    }

    private BarChart<String, Number> criarGraficoProcessamento() {
        CategoryAxis eixoX = new CategoryAxis();
        eixoX.setLabel("Threads consumidoras");
        NumberAxis eixoY = new NumberAxis();
        eixoY.setLabel("Eventos por segundo");
        BarChart<String, Number> grafico = new BarChart<>(eixoX, eixoY);
        grafico.setTitle("Threads × processamento");
        grafico.setLegendVisible(false);
        grafico.setAnimated(false);
        grafico.setCategoryGap(28);
        grafico.setPrefHeight(330);
        return grafico;
    }

    private LineChart<Number, Number> criarGraficoLatencia() {
        NumberAxis eixoX = new NumberAxis(1, 4, 1);
        eixoX.setLabel("Threads consumidoras");
        NumberAxis eixoY = new NumberAxis();
        eixoY.setLabel("Tempo médio (ms)");
        LineChart<Number, Number> grafico = new LineChart<>(eixoX, eixoY);
        grafico.setTitle("Threads × tempo médio de resposta");
        grafico.setLegendVisible(false);
        grafico.setAnimated(false);
        grafico.setPrefHeight(330);
        return grafico;
    }

    private LineChart<Number, Number> criarGraficoCarga() {
        NumberAxis eixoX = new NumberAxis();
        eixoX.setLabel("Taxa configurada (eventos/s)");
        NumberAxis eixoY = new NumberAxis();
        eixoY.setLabel("Taxa observada (eventos/s)");
        LineChart<Number, Number> grafico = new LineChart<>(eixoX, eixoY);
        grafico.setTitle("Taxa de geração × capacidade de processamento");
        grafico.setAnimated(false);
        grafico.setPrefHeight(350);
        return grafico;
    }

    private Node criarCardGrafico(Node grafico) {
        VBox card = new VBox(grafico);
        card.getStyleClass().addAll("surface", "chart-card");
        card.setMaxWidth(Double.MAX_VALUE);
        VBox.setVgrow(grafico, Priority.ALWAYS);
        return card;
    }

    private void configurarTabela() {
        tabela.setPlaceholder(new Label("Finalize um experimento para iniciar a comparação."));
        tabela.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        tabela.getColumns().addAll(
                coluna("#", r -> String.valueOf(r.numero())),
                coluna("FINALIZADO", r -> r.finalizadoEm().format(DATA_HORA)),
                coluna("THREADS", r -> String.valueOf(r.configuracao().quantidadeThreads())),
                coluna("ENTRADA", r -> r.configuracao().taxaGeracao() + " ev/s"),
                coluna("PROC./EVENTO", r -> r.configuracao().tempoProcessamentoMs() + " ms"),
                coluna("DURAÇÃO", r -> String.format(PT_BR, "%.1f s", r.duracaoSegundos())),
                coluna("GERADOS", r -> INTEIRO.format(r.eventosGerados())),
                coluna("PROCESSADOS", r -> INTEIRO.format(r.eventosProcessados())),
                coluna("PENDENTES", r -> INTEIRO.format(r.eventosPendentes())),
                coluna("TAXA PROC.", r -> String.format(PT_BR, "%.1f ev/s", r.taxaProcessamento())),
                coluna("LATÊNCIA", r -> String.format(PT_BR, "%.0f ms", r.tempoMedioRespostaMs()))
        );
    }

    private TableColumn<ExperimentoResultado, String> coluna(
            String titulo,
            java.util.function.Function<ExperimentoResultado, String> valor
    ) {
        TableColumn<ExperimentoResultado, String> coluna = new TableColumn<>(titulo);
        coluna.setCellValueFactory(dado -> new SimpleStringProperty(valor.apply(dado.getValue())));
        return coluna;
    }

    public void adicionarResultado(ExperimentoResultado resultado) {
        if (resultado == null || resultados.stream().anyMatch(r -> r.numero() == resultado.numero())) {
            return;
        }
        resultados.add(resultado);
        reconstruirGraficos();
    }

    private void reconstruirGraficos() {
        // Para a comparação por threads, o experimento mais recente de cada quantidade é usado.
        Map<Integer, ExperimentoResultado> maisRecentePorThreads = new LinkedHashMap<>();
        resultados.stream()
                .sorted(Comparator.comparingInt(ExperimentoResultado::numero))
                .forEach(r -> maisRecentePorThreads.put(
                        r.configuracao().quantidadeThreads(), r));

        XYChart.Series<String, Number> processamento = new XYChart.Series<>();
        maisRecentePorThreads.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> processamento.getData().add(new XYChart.Data<>(
                        entry.getKey() + (entry.getKey() == 1 ? " thread" : " threads"),
                        entry.getValue().taxaProcessamento())));
        graficoProcessamento.getData().setAll(processamento);

        XYChart.Series<Number, Number> latencia = new XYChart.Series<>();
        maisRecentePorThreads.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> latencia.getData().add(new XYChart.Data<>(
                        entry.getKey(), entry.getValue().tempoMedioRespostaMs())));
        graficoLatencia.getData().setAll(latencia);

        XYChart.Series<Number, Number> entrada = new XYChart.Series<>();
        entrada.setName("Entrada observada");
        XYChart.Series<Number, Number> saida = new XYChart.Series<>();
        saida.setName("Processamento observado");
        resultados.stream()
                .sorted(Comparator.comparingInt(r -> r.configuracao().taxaGeracao()))
                .forEach(r -> {
                    entrada.getData().add(new XYChart.Data<>(
                            r.configuracao().taxaGeracao(), r.taxaGeracaoReal()));
                    saida.getData().add(new XYChart.Data<>(
                            r.configuracao().taxaGeracao(), r.taxaProcessamento()));
                });
        graficoCarga.getData().setAll(entrada, saida);
    }

    private static javafx.scene.layout.ColumnConstraints colunaPercentual(double percentual) {
        javafx.scene.layout.ColumnConstraints coluna = new javafx.scene.layout.ColumnConstraints();
        coluna.setPercentWidth(percentual);
        coluna.setHgrow(Priority.ALWAYS);
        return coluna;
    }
}
