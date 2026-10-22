package br.smartcity.monitor.escalonamento.ui;

import javafx.scene.Node;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Guarda o resultado já calculado de cada modo de uma página (ex.: FCFS, Round Robin, Comparar),
 * para que trocar de modo não apague o que foi executado. Os resultados só são descartados quando
 * a carga ou os parâmetros mudam (aí deixam de valer).
 */
final class ResultadosPorModo<M extends Enum<M>> {

    /** Blocos prontos para exibir e o tempo máximo do Gantt (0 quando não há reprodução). */
    record Entrada(List<Node> blocos, int tempoMax) {
    }

    private final Map<M, Entrada> porModo;

    ResultadosPorModo(Class<M> tipo) {
        this.porModo = new EnumMap<>(tipo);
    }

    void guardar(M modo, List<Node> blocos, int tempoMax) {
        porModo.put(modo, new Entrada(List.copyOf(blocos), tempoMax));
    }

    /** Resultado guardado do modo, ou {@code null} se ele ainda não foi executado. */
    Entrada obter(M modo) {
        return porModo.get(modo);
    }

    void descartar() {
        porModo.clear();
    }
}
