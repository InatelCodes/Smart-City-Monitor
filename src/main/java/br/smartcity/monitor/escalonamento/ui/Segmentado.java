package br.smartcity.monitor.escalonamento.ui;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;

import java.util.List;
import java.util.function.Function;

/** Seletor em pílula: exatamente uma opção ativa por vez. */
final class Segmentado<T> extends HBox {

    private final ToggleGroup grupo = new ToggleGroup();
    private final ObjectProperty<T> valor = new SimpleObjectProperty<>();

    @SuppressWarnings("unchecked")
    Segmentado(List<T> itens, Function<T, String> rotulo) {
        getStyleClass().add("segmentado");
        for (T item : itens) {
            ToggleButton botao = new ToggleButton(rotulo.apply(item));
            botao.setToggleGroup(grupo);
            botao.setUserData(item);
            botao.getStyleClass().add("seg-btn");
            botao.setFocusTraversable(false);
            getChildren().add(botao);
        }
        grupo.selectedToggleProperty().addListener((obs, antigo, novo) -> {
            if (novo == null) {
                if (antigo != null) {
                    antigo.setSelected(true);
                }
            } else {
                valor.set((T) novo.getUserData());
            }
        });
        grupo.selectToggle(grupo.getToggles().get(0));
    }

    ObjectProperty<T> valorProperty() {
        return valor;
    }

    T valor() {
        return valor.get();
    }

    void selecionar(T item) {
        for (var toggle : grupo.getToggles()) {
            if (item.equals(toggle.getUserData())) {
                grupo.selectToggle(toggle);
            }
        }
    }
}
