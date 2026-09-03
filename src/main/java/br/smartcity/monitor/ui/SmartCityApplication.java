package br.smartcity.monitor.ui;

import br.smartcity.monitor.config.ConfiguracaoExperimento;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Aplicação JavaFX do Smart City Monitor. */
public final class SmartCityApplication extends Application {

    private final DashboardController controller = new DashboardController();
    private final ExecutorService tarefas = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "Controle-do-Experimento");
        thread.setDaemon(true);
        return thread;
    });

    private final MonitoramentoView monitoramento = new MonitoramentoView();
    private final ResultadosView resultados = new ResultadosView();
    private final Label status = new Label();
    private Timeline atualizador;
    private volatile boolean fechando;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        BorderPane raiz = new BorderPane();
        raiz.getStyleClass().add("app-root");
        raiz.setTop(criarCabecalho());

        Tab monitoramentoTab = new Tab("Monitoramento", monitoramento);
        Tab resultadosTab = new Tab("Resultados", resultados);
        monitoramentoTab.setClosable(false);
        resultadosTab.setClosable(false);
        TabPane abas = new TabPane(monitoramentoTab, resultadosTab);
        abas.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        raiz.setCenter(abas);

        monitoramento.getBotaoIniciar().setOnAction(evento -> iniciarExperimento());
        monitoramento.getBotaoParar().setOnAction(evento -> pararExperimento());
        monitoramento.getBotaoResetar().setOnAction(evento -> resetarExperimento());

        atualizador = new Timeline(new KeyFrame(Duration.millis(500), evento -> atualizarPainel()));
        atualizador.setCycleCount(Timeline.INDEFINITE);
        atualizador.play();
        aplicarEstado(DashboardController.Estado.PARADO);

        Scene cena = new Scene(raiz, 1280, 860);
        cena.getStylesheets().add(
                SmartCityApplication.class.getResource("/br/smartcity/monitor/ui/dashboard.css")
                        .toExternalForm());
        stage.setTitle("Smart City Monitor");
        stage.setMinWidth(980);
        stage.setMinHeight(700);
        stage.setScene(cena);
        stage.show();
    }

    private HBox criarCabecalho() {
        Label marca = new Label("SC");
        marca.getStyleClass().add("brand-mark");
        Label titulo = new Label("SMART CITY MONITOR");
        titulo.getStyleClass().add("brand-title");
        Label subtitulo = new Label("CENTRAL DE OPERAÇÕES");
        subtitulo.getStyleClass().add("brand-subtitle");
        VBox textos = new VBox(1, titulo, subtitulo);

        Region espaco = new Region();
        HBox.setHgrow(espaco, Priority.ALWAYS);
        monitoramento.getThreadsAtivas().getStyleClass().add("active-threads");
        HBox estado = new HBox(10, monitoramento.getThreadsAtivas(), status);
        estado.setAlignment(Pos.CENTER_RIGHT);

        HBox cabecalho = new HBox(12, marca, textos, espaco, estado);
        cabecalho.setAlignment(Pos.CENTER_LEFT);
        cabecalho.setPadding(new Insets(14, 24, 14, 24));
        cabecalho.getStyleClass().add("app-header");
        return cabecalho;
    }

    private void iniciarExperimento() {
        try {
            ConfiguracaoExperimento configuracao = new ConfiguracaoExperimento(
                    monitoramento.getQuantidadeThreads(),
                    monitoramento.getTaxaGeracao(),
                    monitoramento.getTempoProcessamento());
            monitoramento.limparDados();
            controller.iniciar(configuracao);
            aplicarEstado(DashboardController.Estado.EXECUTANDO);
            atualizarPainel();
        } catch (RuntimeException e) {
            mostrarErro("Não foi possível iniciar", e.getMessage());
        }
    }

    private void pararExperimento() {
        monitoramento.setFinalizando();
        definirStatus("FINALIZANDO", "status-finalizando");
        tarefas.submit(() -> {
            try {
                ExperimentoResultado resultado = controller.parar();
                Platform.runLater(() -> {
                    atualizarPainel();
                    resultados.adicionarResultado(resultado);
                    aplicarEstado(DashboardController.Estado.FINALIZADO);
                });
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                if (!fechando) {
                    Platform.runLater(() -> mostrarErro(
                            "Parada interrompida", "Não foi possível encerrar todas as threads."));
                }
            }
        });
    }

    private void resetarExperimento() {
        try {
            controller.resetar();
            monitoramento.limparDados();
            aplicarEstado(DashboardController.Estado.PARADO);
        } catch (RuntimeException e) {
            mostrarErro("Não foi possível resetar", e.getMessage());
        }
    }

    private void atualizarPainel() {
        DashboardSnapshot snapshot = controller.obterSnapshot();
        boolean executando = controller.getEstado() == DashboardController.Estado.EXECUTANDO;
        monitoramento.atualizar(snapshot, executando);
        controller.drenarResultadosRecentes().forEach(monitoramento::adicionarEvento);
    }

    private void aplicarEstado(DashboardController.Estado estado) {
        monitoramento.setExecutando(estado == DashboardController.Estado.EXECUTANDO);
        switch (estado) {
            case PARADO -> definirStatus("PARADO", "status-parado");
            case EXECUTANDO -> definirStatus("EXECUTANDO", "status-executando");
            case FINALIZADO -> definirStatus("FINALIZADO", "status-finalizado");
        }
    }

    private void definirStatus(String texto, String classe) {
        status.setText("●  " + texto);
        status.getStyleClass().setAll("status-badge", classe);
    }

    private void mostrarErro(String titulo, String mensagem) {
        Alert alerta = new Alert(Alert.AlertType.ERROR);
        alerta.setTitle("Smart City Monitor");
        alerta.setHeaderText(titulo);
        alerta.setContentText(mensagem == null ? "Erro inesperado." : mensagem);
        alerta.showAndWait();
    }

    @Override
    public void stop() {
        fechando = true;
        if (atualizador != null) {
            atualizador.stop();
        }
        try {
            controller.parar();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            tarefas.shutdownNow();
        }
    }
}
