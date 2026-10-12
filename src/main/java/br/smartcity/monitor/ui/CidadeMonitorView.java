package br.smartcity.monitor.ui;

import br.smartcity.monitor.model.ResultadoProcessamento;
import br.smartcity.monitor.model.TipoEvento;

import javafx.animation.FadeTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.TranslateTransition;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

import java.util.EnumMap;
import java.util.Map;

/** Visualização compacta do fluxo de eventos entre as regiões e a Central. */
public final class CidadeMonitorView extends StackPane {

    private static final double LARGURA = 580;
    private static final double ALTURA = 270;

    private final Pane mapa = new Pane();
    private final Map<TipoEvento, Point> origens = new EnumMap<>(TipoEvento.class);
    private final Map<TipoEvento, Label> contadores = new EnumMap<>(TipoEvento.class);

    private final Label threadsCentral = new Label("0 Threads");
    private final Circle central = new Circle(32);

    public CidadeMonitorView() {
        setMinHeight(ALTURA);
        setPrefHeight(ALTURA);
        setMaxHeight(ALTURA);
        getStyleClass().add("city-map-card");

        mapa.setPrefSize(LARGURA, ALTURA);
        mapa.setMinSize(LARGURA, ALTURA);
        construirMapa();
        getChildren().add(mapa);
    }

    private void construirMapa() {
        Rectangle fundo = new Rectangle(LARGURA, ALTURA);
        fundo.setFill(Color.web("#101d29"));
        mapa.getChildren().add(fundo);

        // Malha urbana discreta.
        for (double x = 20; x < LARGURA; x += 50) {
            adicionarLinha(x, 0, x, ALTURA, "city-grid");
        }
        for (double y = 20; y < ALTURA; y += 45) {
            adicionarLinha(0, y, LARGURA, y, "city-grid");
        }

        adicionarRua(45, 78, 535, 78);
        adicionarRua(45, 135, 535, 135);
        adicionarRua(45, 192, 535, 192);
        adicionarRua(160, 30, 160, 240);
        adicionarRua(290, 30, 290, 240);
        adicionarRua(420, 30, 420, 240);

        adicionarRegiao(TipoEvento.TRANSITO, "TRÂNSITO", 20, 32);
        adicionarRegiao(TipoEvento.CLIMA, "CLIMA", 20, 165);
        adicionarRegiao(TipoEvento.ENERGIA, "ENERGIA", 425, 32);
        adicionarRegiao(TipoEvento.QUALIDADE_AR, "QUALIDADE DO AR", 425, 165);

        central.setCenterX(290);
        central.setCenterY(135);
        central.setFill(Color.web("#1f9d91"));
        central.setStroke(Color.web("#bce9e4"));
        central.setStrokeWidth(4);

        Circle nucleo = new Circle(8, Color.web("#e8fffc"));
        nucleo.setCenterX(290);
        nucleo.setCenterY(135);

        Label centralLabel = new Label("CENTRAL");
        centralLabel.setLayoutX(259);
        centralLabel.setLayoutY(126);
        centralLabel.getStyleClass().add("city-central-label");

        threadsCentral.setLayoutX(247);
        threadsCentral.setLayoutY(154);
        threadsCentral.getStyleClass().add("city-central-info");

        mapa.getChildren().addAll(
                central,
                nucleo,
                centralLabel,
                threadsCentral
        );
    }

    private void adicionarLinha(double x1, double y1, double x2, double y2, String classe) {
        Line linha = new Line(x1, y1, x2, y2);
        linha.getStyleClass().add(classe);
        mapa.getChildren().add(linha);
    }

    private void adicionarRua(double x1, double y1, double x2, double y2) {
        Line rua = new Line(x1, y1, x2, y2);
        rua.getStyleClass().add("city-road");
        mapa.getChildren().add(rua);
    }

    private void adicionarRegiao(TipoEvento tipo, String nome, double x, double y) {
        Rectangle bloco = new Rectangle(135, 68);
        bloco.setLayoutX(x);
        bloco.setLayoutY(y);
        bloco.setArcWidth(12);
        bloco.setArcHeight(12);
        bloco.setFill(corFundo(tipo));
        bloco.setStroke(corBorda(tipo));
        bloco.setStrokeWidth(1.2);

        Label titulo = new Label(nome);
        titulo.setLayoutX(x + 11);
        titulo.setLayoutY(y + 10);
        titulo.getStyleClass().add("city-zone-title");

        Label contador = new Label("0 processados");
        contador.setLayoutX(x + 11);
        contador.setLayoutY(y + 39);
        contador.getStyleClass().add("city-zone-count");
        contadores.put(tipo, contador);

        origens.put(tipo, new Point(x + 67.5, y + 34));
        mapa.getChildren().addAll(bloco, titulo, contador);
    }

    public void atualizar(DashboardSnapshot snapshot) {
        contadores.get(TipoEvento.TRANSITO).setText(snapshot.eventosTransito() + " processados");
        contadores.get(TipoEvento.CLIMA).setText(snapshot.eventosClima() + " processados");
        contadores.get(TipoEvento.ENERGIA).setText(snapshot.eventosEnergia() + " processados");
        contadores.get(TipoEvento.QUALIDADE_AR).setText(snapshot.eventosQualidadeAr() + " processados");

        threadsCentral.setText(snapshot.threadsAtivas() == 1
                ? "1 Thread ativa"
                : snapshot.threadsAtivas() + " Threads ativas");
    }

    public void animarProcessamento(ResultadoProcessamento resultado) {
        Point origem = origens.get(resultado.getEvento().getTipo());
        if (origem == null) return;

        Circle ponto = new Circle(5, corDoTipo(resultado.getEvento().getTipo()));
        ponto.setStroke(Color.web("#f5fffd"));
        ponto.setStrokeWidth(1.2);
        ponto.setCenterX(origem.x());
        ponto.setCenterY(origem.y());
        mapa.getChildren().add(ponto);

        TranslateTransition movimento = new TranslateTransition(Duration.millis(500), ponto);
        movimento.setToX(290 - origem.x());
        movimento.setToY(135 - origem.y());

        ScaleTransition pulso = new ScaleTransition(Duration.millis(220), ponto);
        pulso.setFromX(1);
        pulso.setFromY(1);
        pulso.setToX(1.5);
        pulso.setToY(1.5);
        pulso.setAutoReverse(true);
        pulso.setCycleCount(2);

        FadeTransition desaparece = new FadeTransition(Duration.millis(160), ponto);
        desaparece.setFromValue(1);
        desaparece.setToValue(0);

        movimento.setOnFinished(evento -> {
            pulso.play();
            pulso.setOnFinished(e -> {
                desaparece.play();
                desaparece.setOnFinished(f -> mapa.getChildren().remove(ponto));
            });
        });

        movimento.play();
    }

    public void resetar() {
        for (Label contador : contadores.values()) {
            contador.setText("0 processados");
        }
        threadsCentral.setText("0 Threads ativas");
    }

    private Color corDoTipo(TipoEvento tipo) {
        return switch (tipo) {
            case TRANSITO -> Color.web("#4dc7bb");
            case CLIMA -> Color.web("#76a7d0");
            case ENERGIA -> Color.web("#e3aa57");
            case QUALIDADE_AR -> Color.web("#a890d0");
        };
    }

    private Color corFundo(TipoEvento tipo) {
        return switch (tipo) {
            case TRANSITO -> Color.web("#173b40");
            case CLIMA -> Color.web("#183346");
            case ENERGIA -> Color.web("#3b3020");
            case QUALIDADE_AR -> Color.web("#302842");
        };
    }

    private Color corBorda(TipoEvento tipo) {
        return switch (tipo) {
            case TRANSITO -> Color.web("#276c68");
            case CLIMA -> Color.web("#35627d");
            case ENERGIA -> Color.web("#8a6935");
            case QUALIDADE_AR -> Color.web("#65538a");
        };
    }

    private record Point(double x, double y) {}
}
