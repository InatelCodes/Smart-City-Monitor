package br.smartcity.monitor.escalonamento.ui;

import br.smartcity.monitor.model.TipoEvento;
import javafx.scene.control.Label;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;

import java.util.Locale;

/** Cores, nomes e pequenos construtores compartilhados pela aba. */
final class Tema {

    static final Locale PT = Locale.forLanguageTag("pt-BR");

    private Tema() {
    }

    static Color cor(TipoEvento tipo) {
        return switch (tipo) {
            case TRANSITO -> Color.web("#1fa89a");
            case CLIMA -> Color.web("#4f8fc4");
            case ENERGIA -> Color.web("#e0932a");
            case QUALIDADE_AR -> Color.web("#8f72c9");
        };
    }

    static String hex(Color c) {
        return String.format(
                "#%02x%02x%02x",
                Math.round(c.getRed() * 255),
                Math.round(c.getGreen() * 255),
                Math.round(c.getBlue() * 255)
        );
    }

    static String hex(TipoEvento tipo) {
        return hex(cor(tipo));
    }

    static String nome(TipoEvento tipo) {
        return switch (tipo) {
            case TRANSITO -> "Trânsito";
            case CLIMA -> "Clima";
            case ENERGIA -> "Energia";
            case QUALIDADE_AR -> "Qualidade do ar";
        };
    }

    static String num(double valor) {
        return String.format(PT, "%.2f", valor);
    }

    static Label rotulo(String texto, String... classes) {
        Label label = new Label(texto);
        label.getStyleClass().addAll(classes);
        return label;
    }

    static Region espaco() {
        Region r = new Region();
        javafx.scene.layout.HBox.setHgrow(r, Priority.ALWAYS);
        javafx.scene.layout.VBox.setVgrow(r, Priority.ALWAYS);
        return r;
    }

    /** Chip arredondado com a cor do tipo de evento. */
    static Label chip(String id, TipoEvento tipo) {
        Label chip = new Label(id);
        chip.getStyleClass().add("chip-processo");
        chip.setStyle("-fx-background-color:" + hex(tipo) + ";");
        return chip;
    }
}
