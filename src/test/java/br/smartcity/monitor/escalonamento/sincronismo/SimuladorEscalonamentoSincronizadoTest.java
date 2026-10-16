package br.smartcity.monitor.escalonamento.sincronismo;

import br.smartcity.monitor.escalonamento.algoritmo.Fcfs;
import br.smartcity.monitor.escalonamento.algoritmo.RoundRobin;
import br.smartcity.monitor.escalonamento.model.ProcessoUrbano;
import br.smartcity.monitor.model.TipoEvento;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimuladorEscalonamentoSincronizadoTest {

    private final SimuladorEscalonamentoSincronizado simulador = new SimuladorEscalonamentoSincronizado();

    private List<ProcessoUrbano> carga() {
        return List.of(
                new ProcessoUrbano("P1", TipoEvento.TRANSITO, "Congestionamento detectado", 0, 8, 0),
                new ProcessoUrbano("P2", TipoEvento.ENERGIA, "Pico de consumo detectado", 1, 4, 1),
                new ProcessoUrbano("P3", TipoEvento.QUALIDADE_AR, "Nivel de poluentes elevado", 2, 2, 2));
    }

    private ExecucaoIntegrada rodar(PoliticaEscalonamento politica, ModoSincronizacao modo) {
        return simulador.executar(carga(), politica, 2, 1, 3, modo);
    }

    @Test
    void semSincronismoMantemOGanttDosAlgoritmosOriginais() {
        var fcfs = rodar(PoliticaEscalonamento.FCFS, ModoSincronizacao.SEM_SINCRONISMO);
        var rr = rodar(PoliticaEscalonamento.ROUND_ROBIN, ModoSincronizacao.SEM_SINCRONISMO);
        assertEquals(new Fcfs(1).executar(carga()).gantt(), fcfs.resultado().gantt());
        assertEquals(new RoundRobin(2, 1).executar(carga()).gantt(), rr.resultado().gantt());
        assertEquals(new RoundRobin(2, 1).executar(carga()).metricas(), rr.resultado().metricas());
    }

    @Test
    void fcfsNaoCorrompeEOLockNaoMudaNada() {
        var sem = rodar(PoliticaEscalonamento.FCFS, ModoSincronizacao.SEM_SINCRONISMO);
        var com = rodar(PoliticaEscalonamento.FCFS, ModoSincronizacao.COM_SINCRONISMO);
        assertEquals(0, sem.registrosCorrompidos());
        assertEquals(0, com.registrosCorrompidos());
        assertEquals(sem.resultado().gantt(), com.resultado().gantt());
        assertEquals(0, com.tempoBloqueadoTotal());
    }

    @Test
    void roundRobinSemLockCorrompeRegistrosQuandoPreemptaNaEscrita() {
        var sem = rodar(PoliticaEscalonamento.ROUND_ROBIN, ModoSincronizacao.SEM_SINCRONISMO);
        assertEquals(3, sem.registrosEsperados());
        assertEquals(1, sem.registrosValidos());
        assertEquals(19, sem.resultado().metricas().makespan());
    }

    @Test
    void roundRobinComLockMantemIntegridadeEPagaComEspera() {
        var com = rodar(PoliticaEscalonamento.ROUND_ROBIN, ModoSincronizacao.COM_SINCRONISMO);
        assertEquals(0, com.registrosCorrompidos());
        assertEquals(8, com.tempoBloqueadoTotal());
        assertEquals(9.67, com.resultado().metricas().tempoMedioEspera(), 0.01);
        assertEquals(5.67, com.resultado().metricas().tempoMedioResposta(), 0.01);
        assertEquals(14.33, com.resultado().metricas().tempoMedioRetorno(), 0.01);
    }

    @Test
    void resultadoEDeterministico() {
        var a = rodar(PoliticaEscalonamento.ROUND_ROBIN, ModoSincronizacao.COM_SINCRONISMO);
        var b = rodar(PoliticaEscalonamento.ROUND_ROBIN, ModoSincronizacao.COM_SINCRONISMO);
        assertEquals(a, b);
        assertTrue(a.conteudoArquivo().endsWith("\n"));
    }

    @Test
    void rejeitaParametrosInvalidos() {
        assertThrows(IllegalArgumentException.class, () -> simulador.executar(
                carga(), PoliticaEscalonamento.ROUND_ROBIN, 0, 1, 3, ModoSincronizacao.COM_SINCRONISMO));
        assertThrows(IllegalArgumentException.class, () -> simulador.executar(
                carga(), PoliticaEscalonamento.FCFS, 2, 1, 0, ModoSincronizacao.COM_SINCRONISMO));
        assertThrows(IllegalArgumentException.class, () -> simulador.executar(
                List.of(), PoliticaEscalonamento.FCFS, 2, 1, 3, ModoSincronizacao.COM_SINCRONISMO));
    }
}
