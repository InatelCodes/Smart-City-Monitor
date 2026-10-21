package br.smartcity.monitor.escalonamento;

import br.smartcity.monitor.escalonamento.algoritmo.Fcfs;
import br.smartcity.monitor.escalonamento.algoritmo.RoundRobin;
import br.smartcity.monitor.escalonamento.model.MetricasEscalonamento;
import br.smartcity.monitor.escalonamento.model.ProcessoUrbano;
import br.smartcity.monitor.escalonamento.model.ResultadoEscalonamento;
import br.smartcity.monitor.model.TipoEvento;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Testes com valores realistas de sensores: bursts de 10.000 a 40.000 ut e quantum de 10.000 ut
 * (no lugar dos valores didáticos 8/4/2 com quantum 2).
 */
class EscalonamentoGrandeEscalaTest {

    private static List<ProcessoUrbano> carga() {
        return List.of(
                new ProcessoUrbano("P1", TipoEvento.TRANSITO, "Congestionamento", 0, 40_000, 0),
                new ProcessoUrbano("P2", TipoEvento.ENERGIA, "Pico de consumo", 5_000, 20_000, 1),
                new ProcessoUrbano("P3", TipoEvento.QUALIDADE_AR, "Poluentes", 10_000, 10_000, 2));
    }

    @Test
    void fcfsComBurstDe40MilUt() {
        ResultadoEscalonamento r = new Fcfs(500).executar(carga());
        MetricasEscalonamento m = r.metricas();
        assertEquals(List.of(40_000, 60_500, 71_000),
                r.processos().stream().map(p -> p.conclusao()).toList());
        assertEquals(71_000, m.makespan());
        assertEquals(2, m.trocasContexto());
        assertEquals(1_000, m.tempoTrocasContexto());
        assertEquals(28_833.33, m.tempoMedioEspera(), 0.01);
        assertEquals(28_833.33, m.tempoMedioResposta(), 0.01);
        assertEquals(52_166.67, m.tempoMedioRetorno(), 0.01);
    }

    @Test
    void roundRobinComQuantumDe10MilUt() {
        ResultadoEscalonamento r = new RoundRobin(10_000, 500).executar(carga());
        MetricasEscalonamento m = r.metricas();
        assertEquals(72_500, m.makespan());
        assertEquals(5, m.trocasContexto());
        assertEquals(2_500, m.tempoTrocasContexto());
        assertEquals(23_500.0, m.tempoMedioEspera(), 0.01);
        assertEquals(5_500.0, m.tempoMedioResposta(), 0.01);
        assertEquals(46_833.33, m.tempoMedioRetorno(), 0.01);
    }

    @Test
    void quantumMaiorQueOMaiorBurstFazORoundRobinVirarFcfs() {
        MetricasEscalonamento fcfs = new Fcfs(500).executar(carga()).metricas();
        MetricasEscalonamento rr = new RoundRobin(40_000, 500).executar(carga()).metricas();
        assertEquals(fcfs, rr);
    }

    @Test
    void quantumPequenoAumentaTrocasEMakespan() {
        MetricasEscalonamento grande = new RoundRobin(20_000, 500).executar(carga()).metricas();
        MetricasEscalonamento pequeno = new RoundRobin(2_500, 500).executar(carga()).metricas();
        assertEquals(20, pequeno.trocasContexto());
        assertEquals(3, grande.trocasContexto());
        assertEquals(80_000, pequeno.makespan());
        assertEquals(71_500, grande.makespan());
    }

    @Test
    void cargaComUmMilhaoDeUtNaoEstouraOsContadores() {
        List<ProcessoUrbano> enorme = List.of(
                new ProcessoUrbano("P1", TipoEvento.CLIMA, "Tempestade", 0, 1_000_000, 0),
                new ProcessoUrbano("P2", TipoEvento.ENERGIA, "Apagão", 1_000, 1_000_000, 1));
        MetricasEscalonamento m = new RoundRobin(250_000, 1_000).executar(enorme).metricas();
        assertEquals(2_000_000 + m.tempoTrocasContexto(), m.makespan());
    }
}
