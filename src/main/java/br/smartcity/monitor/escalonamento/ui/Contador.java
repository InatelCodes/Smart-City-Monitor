package br.smartcity.monitor.escalonamento.ui;

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;

/** Campo numérico com botões − e +, sem digitação (nada para validar). */
final class Contador extends HBox {

    private final IntegerProperty valor = new SimpleIntegerProperty();

    Contador(int inicial, int minimo, int maximo) {
        super(0);
        getStyleClass().add("contador");
        setAlignment(Pos.CENTER);

        Button menos = new Button("−");
        Button mais = new Button("+");
        menos.getStyleClass().add("contador-btn");
        mais.getStyleClass().add("contador-btn");
        Label numero = new Label();
        numero.getStyleClass().add("contador-valor");
        numero.setMaxWidth(Double.MAX_VALUE);
        numero.setAlignment(Pos.CENTER);
        HBox.setHgrow(numero, Priority.ALWAYS);
        numero.textProperty().bind(valor.asString());

        valor.set(inicial);
        menos.setOnAction(e -> valor.set(Math.max(minimo, valor.get() - 1)));
        mais.setOnAction(e -> valor.set(Math.min(maximo, valor.get() + 1)));
        getChildren().addAll(menos, numero, mais);
    }

    IntegerProperty valorProperty() {
        return valor;
    }

    int valor() {
        return valor.get();
    }
}
