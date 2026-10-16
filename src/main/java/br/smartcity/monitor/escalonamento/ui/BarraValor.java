package br.smartcity.monitor.escalonamento.ui;

import javafx.scene.control.Label;
import javafx.scene.layout.Region;

/** Barra horizontal proporcional com o valor escrito na ponta. */
final class BarraValor extends Region {

    private final Region barra = new Region();
    private final Label valor = new Label();
    private final double proporcao;

    BarraValor(String texto, double proporcao, String cor, boolean destaque) {
        this.proporcao = Math.max(0, Math.min(1, proporcao));
        barra.setStyle("-fx-background-color:" + cor + ";-fx-background-radius:6;"
                + (destaque ? "" : "-fx-opacity:0.55;"));
        valor.setText(texto);
        valor.getStyleClass().add(destaque ? "barra-valor-destaque" : "barra-valor");
        getChildren().addAll(barra, valor);
        setMinHeight(24);
        setPrefHeight(24);
    }

    @Override
    protected void layoutChildren() {
        double reservado = 70;
        double largura = Math.max(6, (getWidth() - reservado) * proporcao);
        barra.resizeRelocate(0, 3, largura, getHeight() - 6);
        valor.autosize();
        valor.relocate(largura + 8, (getHeight() - valor.getHeight()) / 2);
    }

    @Override
    protected double computePrefWidth(double altura) {
        return 200;
    }
}
