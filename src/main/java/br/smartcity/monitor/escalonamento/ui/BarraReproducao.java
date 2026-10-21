package br.smartcity.monitor.escalonamento.ui;

import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.property.DoubleProperty;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.util.Duration;

import java.util.List;

/** Controles de reprodução compartilhados: o tempo lógico anima todos os Gantts da página. */
final class BarraReproducao extends HBox {

    private final DoubleProperty tempo;
    private final Slider slider = new Slider();
    private final Label posicao = Tema.rotulo("", "pos-tempo");
    private final Button reproduzir = new Button("▶  Reproduzir");
    private final Segmentado<Double> velocidade = new Segmentado<>(
            List.of(0.5, 1.0, 2.0, 4.0), v -> (v == 0.5 ? "0,5" : String.valueOf(v.intValue())) + "×");
    private Timeline animacao;
    private int tempoMax = 1;
    private int passoUnidades = 1;

    BarraReproducao(DoubleProperty tempo) {
        super(12);
        this.tempo = tempo;
        getStyleClass().addAll("card", "barra-reproducao");
        setAlignment(Pos.CENTER_LEFT);

        reproduzir.getStyleClass().add("btn-secundario");
        Button passo = new Button("⏭  Passo");
        passo.getStyleClass().add("btn-secundario");
        Button reiniciar = new Button("↺");
        reiniciar.getStyleClass().add("btn-secundario");
        slider.getStyleClass().add("slider-tempo");
        HBox.setHgrow(slider, Priority.ALWAYS);

        slider.valueChangingProperty().addListener((obs, a, arrastando) -> {
            if (arrastando && animacao != null) {
                animacao.pause();
                reproduzir.setText("▶  Reproduzir");
            }
        });
        slider.valueProperty().addListener((obs, a, b) -> {
            if (slider.isValueChanging() || slider.isFocused()) {
                tempo.set(b.doubleValue());
            }
        });
        tempo.addListener((obs, a, b) -> {
            if (!slider.isValueChanging()) {
                slider.setValue(b.doubleValue());
            }
            atualizarPosicao();
        });

        reproduzir.setOnAction(e -> alternar());
        passo.setOnAction(e -> {
            if (animacao != null) {
                animacao.pause();
            }
            reproduzir.setText("▶  Reproduzir");
            tempo.set(Math.min(tempoMax, Math.floor(tempo.get() + 1e-9) + passoUnidades));
        });
        reiniciar.setOnAction(e -> {
            if (animacao != null) {
                animacao.stop();
            }
            reproduzir.setText("▶  Reproduzir");
            tempo.set(0);
        });
        velocidade.selecionar(1.0);
        velocidade.valorProperty().addListener((obs, a, b) -> {
            if (animacao != null) {
                animacao.setRate(b);
            }
        });

        getChildren().addAll(reproduzir, passo, reiniciar, slider, posicao,
                Tema.rotulo("velocidade", "eyebrow"), velocidade);
    }

    /** Prepara a barra para uma nova execução; o resultado completo aparece de início. */
    void configurar(int tempoMaximo) {
        parar();
        tempoMax = Math.max(1, tempoMaximo);
        passoUnidades = Math.max(1, tempoMax / 40);
        slider.setMin(0);
        slider.setMax(tempoMax);
        tempo.set(tempoMax);
        slider.setValue(tempoMax);
        reproduzir.setText("▶  Reproduzir");
        atualizarPosicao();
    }

    void parar() {
        if (animacao != null) {
            animacao.stop();
            animacao = null;
        }
        reproduzir.setText("▶  Reproduzir");
    }

    private void atualizarPosicao() {
        posicao.setText("tempo " + Tema.inteiro((long) Math.floor(tempo.get() + 1e-9)) + " / " + Tema.inteiro(tempoMax));
    }

    private void alternar() {
        if (animacao != null && animacao.getStatus() == Animation.Status.RUNNING) {
            animacao.pause();
            reproduzir.setText("▶  Reproduzir");
            return;
        }
        if (animacao != null && animacao.getStatus() == Animation.Status.PAUSED && tempo.get() < tempoMax) {
            animacao.play();
            reproduzir.setText("⏸  Pausar");
            return;
        }
        double inicio = tempo.get() >= tempoMax ? 0 : tempo.get();
        tempo.set(inicio);
        double duracao = Math.min(20, Math.max(2, (tempoMax - inicio) * 0.5));
        animacao = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(tempo, inicio, Interpolator.LINEAR)),
                new KeyFrame(Duration.seconds(duracao), new KeyValue(tempo, tempoMax, Interpolator.LINEAR)));
        animacao.setRate(velocidade.valor());
        animacao.setOnFinished(e -> reproduzir.setText("▶  Reproduzir"));
        animacao.play();
        reproduzir.setText("⏸  Pausar");
    }
}
