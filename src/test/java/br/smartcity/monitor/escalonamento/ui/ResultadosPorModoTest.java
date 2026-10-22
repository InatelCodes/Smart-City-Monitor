package br.smartcity.monitor.escalonamento.ui;

import javafx.scene.Node;
import javafx.scene.layout.Region;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class ResultadosPorModoTest {

    @Test
    void cadaModoGuardaOSeuResultadoSemApagarOsOutros() {
        ResultadosPorModo<PaginaCpu.Modo> guardados = new ResultadosPorModo<>(PaginaCpu.Modo.class);
        Node fcfs = new Region();
        Node rr = new Region();

        guardados.guardar(PaginaCpu.Modo.FCFS, List.of(fcfs), 71_000);
        guardados.guardar(PaginaCpu.Modo.RR, List.of(rr), 72_500);

        assertSame(fcfs, guardados.obter(PaginaCpu.Modo.FCFS).blocos().get(0));
        assertEquals(71_000, guardados.obter(PaginaCpu.Modo.FCFS).tempoMax());
        assertSame(rr, guardados.obter(PaginaCpu.Modo.RR).blocos().get(0));
        assertNull(guardados.obter(PaginaCpu.Modo.AMBOS), "modo ainda não executado");
    }

    @Test
    void executarDeNovoSubstituiSoOResultadoDaqueleModo() {
        ResultadosPorModo<PaginaCpu.Modo> guardados = new ResultadosPorModo<>(PaginaCpu.Modo.class);
        Node antigo = new Region();
        Node novo = new Region();
        Node rr = new Region();
        guardados.guardar(PaginaCpu.Modo.FCFS, List.of(antigo), 10);
        guardados.guardar(PaginaCpu.Modo.RR, List.of(rr), 20);

        guardados.guardar(PaginaCpu.Modo.FCFS, List.of(novo), 30);

        assertSame(novo, guardados.obter(PaginaCpu.Modo.FCFS).blocos().get(0));
        assertSame(rr, guardados.obter(PaginaCpu.Modo.RR).blocos().get(0));
    }

    @Test
    void mudarACargaOuOsParametrosDescartaTodosOsResultados() {
        ResultadosPorModo<PaginaCpu.Modo> guardados = new ResultadosPorModo<>(PaginaCpu.Modo.class);
        guardados.guardar(PaginaCpu.Modo.FCFS, List.of(new Region()), 10);
        guardados.guardar(PaginaCpu.Modo.AMBOS, List.of(new Region()), 20);

        guardados.descartar();

        assertNull(guardados.obter(PaginaCpu.Modo.FCFS));
        assertNull(guardados.obter(PaginaCpu.Modo.AMBOS));
    }
}
