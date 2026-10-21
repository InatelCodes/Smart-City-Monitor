package br.smartcity.monitor.escalonamento.algoritmo;

import br.smartcity.monitor.escalonamento.model.ProcessoResultado;
import br.smartcity.monitor.escalonamento.model.ProcessoUrbano;
import br.smartcity.monitor.escalonamento.model.ResultadoEscalonamento;
import br.smartcity.monitor.escalonamento.model.TrechoExecucao;
import br.smartcity.monitor.model.TipoEvento;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FcfsTest {

    private final Fcfs fcfs = new Fcfs();

    @Test
    void reproduzCargaSmartCityDeReferencia() {
        ResultadoEscalonamento resultado = new Fcfs(1).executar(List.of(
                processo("P1", TipoEvento.TRANSITO, 0, 8, 0),
                processo("P2", TipoEvento.ENERGIA, 1, 4, 1),
                processo("P3", TipoEvento.QUALIDADE_AR, 2, 2, 2)
        ));

        assertEquals("FCFS", resultado.algoritmo());
        assertEquals(
                List.of("P1", "P2", "P3"),
                resultado.gantt().stream()
                        .filter(trecho -> !trecho.trocaContexto())
                        .map(TrechoExecucao::processoId)
                        .toList()
        );
        assertEquals(
                List.of(8, 13, 16),
                resultado.processos().stream()
                        .map(ProcessoResultado::conclusao)
                        .toList()
        );
        assertEquals(
                List.of(0, 8, 12),
                resultado.processos().stream()
                        .map(ProcessoResultado::tempoEspera)
                        .toList()
        );
        assertEquals(6.6666666667, resultado.metricas().tempoMedioEspera(), 1e-9);
        assertEquals(11.3333333333, resultado.metricas().tempoMedioRetorno(), 1e-9);
        assertEquals(6.6666666667, resultado.metricas().tempoMedioResposta(), 1e-9);
        assertEquals(16, resultado.metricas().makespan());
        assertEquals(2, resultado.metricas().trocasContexto());
        assertEquals(2, resultado.metricas().tempoTrocasContexto());
        assertEquals(87.5, resultado.metricas().utilizacaoCpuPercentual());
        assertEquals(3.0 / 16.0, resultado.metricas().vazao());
    }

    @Test
    void reproduzCargaDeReferenciaComBurstsDeDezenasDeMilhares() {
        ResultadoEscalonamento resultado = new Fcfs(500).executar(List.of(
                processo("P1", TipoEvento.TRANSITO, 0, 40_000, 0),
                processo("P2", TipoEvento.ENERGIA, 5_000, 20_000, 1),
                processo("P3", TipoEvento.QUALIDADE_AR, 10_000, 10_000, 2)
        ));

        assertEquals(
                List.of(40_000, 60_500, 71_000),
                resultado.processos().stream().map(ProcessoResultado::conclusao).toList()
        );
        assertEquals(
                List.of(0, 35_500, 51_000),
                resultado.processos().stream().map(ProcessoResultado::tempoEspera).toList()
        );
        assertEquals(71_000, resultado.metricas().makespan());
        assertEquals(2, resultado.metricas().trocasContexto());
        assertEquals(1_000, resultado.metricas().tempoTrocasContexto());
    }

    @Test
    void representaCpuOciosaEDesempataPelaOrdemDeCadastro() {
        ProcessoUrbano segundoCadastro =
                processo("P2", TipoEvento.CLIMA, 3, 1, 1);
        ProcessoUrbano primeiroCadastro =
                processo("P1", TipoEvento.TRANSITO, 3, 2, 0);

        ResultadoEscalonamento resultado = fcfs.executar(
                List.of(segundoCadastro, primeiroCadastro)
        );

        assertTrue(resultado.gantt().get(0).ocioso());
        assertEquals(0, resultado.gantt().get(0).inicio());
        assertEquals(3, resultado.gantt().get(0).fim());
        assertEquals("P1", resultado.gantt().get(1).processoId());
        assertEquals("P2", resultado.gantt().get(2).processoId());
        assertEquals(6, resultado.metricas().makespan());
        assertEquals(50.0, resultado.metricas().utilizacaoCpuPercentual());
        assertEquals(1, resultado.metricas().trocasContexto());
    }

    @Test
    void rejeitaCargaVaziaEIdsDuplicados() {
        assertThrows(
                IllegalArgumentException.class,
                () -> fcfs.executar(List.of())
        );

        ProcessoUrbano primeiro =
                processo("P1", TipoEvento.TRANSITO, 0, 1, 0);
        ProcessoUrbano duplicado =
                processo("P1", TipoEvento.CLIMA, 1, 1, 1);

        assertThrows(
                IllegalArgumentException.class,
                () -> fcfs.executar(List.of(primeiro, duplicado))
        );
    }

    @Test
    void rejeitaCustoDeTrocaNegativo() {
        assertThrows(IllegalArgumentException.class, () -> new Fcfs(-1));
    }

    private static ProcessoUrbano processo(
            String id,
            TipoEvento tipo,
            int chegada,
            int burst,
            int ordem
    ) {
        return new ProcessoUrbano(
                id,
                tipo,
                "Evento " + id,
                chegada,
                burst,
                ordem
        );
    }
}
