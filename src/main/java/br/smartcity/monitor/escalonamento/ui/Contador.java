package br.smartcity.monitor.escalonamento.ui;

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;

/**
 * Campo numérico com − e +. Aceita digitação (Enter ou sair do campo confirma) e os botões
 * andam de {@code passo} em {@code passo}, para funcionar bem com valores de dezenas de milhares.
 */
final class Contador extends HBox {

    private final IntegerProperty valor = new SimpleIntegerProperty();
    private final TextField campo = new TextField();
    private final int minimo;
    private final int maximo;

    Contador(int inicial, int minimo, int maximo, int passo) {
        super(0);
        this.minimo = minimo;
        this.maximo = maximo;
        getStyleClass().add("contador");
        setAlignment(Pos.CENTER);

        Button menos = new Button("−");
        Button mais = new Button("+");
        menos.getStyleClass().add("contador-btn");
        mais.getStyleClass().add("contador-btn");
        campo.getStyleClass().add("contador-campo");
        campo.setAlignment(Pos.CENTER);
        HBox.setHgrow(campo, Priority.ALWAYS);

        valor.addListener((obs, a, b) -> campo.setText(Tema.inteiro(b.intValue())));
        valor.set(inicial);
        campo.setText(Tema.inteiro(inicial));

        menos.setOnAction(e -> definir((long) valor.get() - passo));
        mais.setOnAction(e -> definir((long) valor.get() + passo));
        campo.setOnAction(e -> confirmar());
        campo.focusedProperty().addListener((obs, a, focado) -> {
            if (!focado) {
                confirmar();
            }
        });
        getChildren().addAll(menos, campo, mais);
    }

    /** Lê o que foi digitado (ignora pontos e letras) e ajusta aos limites. */
    void confirmar() {
        String digitos = campo.getText() == null ? "" : campo.getText().replaceAll("\\D", "");
        if (digitos.isEmpty()) {
            campo.setText(Tema.inteiro(valor.get()));
            return;
        }
        long lido = digitos.length() > 9 ? Integer.MAX_VALUE : Long.parseLong(digitos);
        definir(lido);
        campo.setText(Tema.inteiro(valor.get()));
    }

    private void definir(long novo) {
        valor.set((int) Math.max(minimo, Math.min(maximo, novo)));
    }

    IntegerProperty valorProperty() {
        return valor;
    }

    int valor() {
        confirmar();
        return valor.get();
    }
}
