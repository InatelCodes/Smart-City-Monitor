package br.smartcity.monitor.escalonamento.sincronismo;

import br.smartcity.monitor.escalonamento.algoritmo.Fcfs;
import br.smartcity.monitor.escalonamento.algoritmo.RoundRobin;
import br.smartcity.monitor.escalonamento.model.MetricasEscalonamento;
import br.smartcity.monitor.escalonamento.model.ProcessoUrbano;
import br.smartcity.monitor.escalonamento.model.TipoTrechoExecucao;
import br.smartcity.monitor.escalonamento.ui.CenarioExemplo;
import br.smartcity.monitor.model.TipoEvento;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Os testes usam os cenários de apresentação (bursts de 10.000 a 40.000 ut) e também a carga
 * didática pequena (8/4/2) multiplicada por uma escala, para provar que o comportamento
 * não depende do tamanho dos números.
 */
class SimuladorEscalonamentoSincronizadoTest {

    private static final int[] ESCALAS = {1, 1_000, 5_000};

    private final SimuladorEscalonamentoSincronizado simulador = new SimuladorEscalonamentoSincronizado();

    private static List<ProcessoUrbano> cargaDe(CenarioExemplo cenario) {
        List<ProcessoUrbano> carga = new ArrayList<>();
        for (CenarioExemplo.ProcessoModelo p : cenario.processos()) {
            carga.add(new ProcessoUrbano(p.id(), p.tipoEvento(), p.descricao(),
                    p.tempoChegada(), p.burst(), carga.size()));
        }
        return carga;
    }

    private ExecucaoIntegrada rodar(CenarioExemplo c, PoliticaEscalonamento politica, ModoSincronizacao modo) {
        return simulador.executar(cargaDe(c), politica, c.quantum(), c.custoTrocaContexto(), c.tamanhoEscrita(), modo);
    }

    /** Carga didática 8/4/2 (quantum 2, troca 1, escrita 3) multiplicada por {@code escala}. */
    private ExecucaoIntegrada rodarEscalada(int escala, PoliticaEscalonamento politica, ModoSincronizacao modo) {
        List<ProcessoUrbano> carga = List.of(
                new ProcessoUrbano("P1", TipoEvento.TRANSITO, "Congestionamento detectado", 0, 8 * escala, 0),
                new ProcessoUrbano("P2", TipoEvento.ENERGIA, "Pico de consumo detectado", escala, 4 * escala, 1),
                new ProcessoUrbano("P3", TipoEvento.QUALIDADE_AR, "Nivel de poluentes elevado", 2 * escala, 2 * escala, 2));
        return simulador.executar(carga, politica, 2 * escala, escala, 3 * escala, modo);
    }

    // ------------------------------------------------------------------ cenário A (40.000 ut)

    @Test
    void cenarioAFcfsNaoCorrompeEOLockNaoMudaNada() {
        var sem = rodar(CenarioExemplo.COLISAO_NO_ROUND_ROBIN, PoliticaEscalonamento.FCFS, ModoSincronizacao.SEM_SINCRONISMO);
        var com = rodar(CenarioExemplo.COLISAO_NO_ROUND_ROBIN, PoliticaEscalonamento.FCFS, ModoSincronizacao.COM_SINCRONISMO);
        assertEquals(0, sem.registrosCorrompidos());
        assertEquals(0, com.registrosCorrompidos());
        assertEquals(sem.resultado().gantt(), com.resultado().gantt());
        assertEquals(0, com.tempoBloqueadoTotal());
        assertEquals(71_000, com.resultado().metricas().makespan());
        assertEquals(28_833.33, com.resultado().metricas().tempoMedioEspera(), 0.01);
    }

    @Test
    void cenarioARoundRobinSemLockCorrompeDoisDeTresRegistros() {
        var sem = rodar(CenarioExemplo.COLISAO_NO_ROUND_ROBIN, PoliticaEscalonamento.ROUND_ROBIN, ModoSincronizacao.SEM_SINCRONISMO);
        assertEquals(3, sem.registrosEsperados());
        assertEquals(1, sem.registrosValidos());
        MetricasEscalonamento m = sem.resultado().metricas();
        assertEquals(72_500, m.makespan());
        assertEquals(5, m.trocasContexto());
        assertEquals(23_500.0, m.tempoMedioEspera(), 0.01);
        assertEquals(5_500.0, m.tempoMedioResposta(), 0.01);
        assertEquals(46_833.33, m.tempoMedioRetorno(), 0.01);
    }

    @Test
    void cenarioARoundRobinComLockFicaIntegroMasPagaComEspera() {
        var sem = rodar(CenarioExemplo.COLISAO_NO_ROUND_ROBIN, PoliticaEscalonamento.ROUND_ROBIN, ModoSincronizacao.SEM_SINCRONISMO);
        var com = rodar(CenarioExemplo.COLISAO_NO_ROUND_ROBIN, PoliticaEscalonamento.ROUND_ROBIN, ModoSincronizacao.COM_SINCRONISMO);
        assertEquals(0, com.registrosCorrompidos());
        assertEquals(31_000, com.tempoBloqueadoTotal());
        assertEquals(30_333.33, com.resultado().metricas().tempoMedioEspera(), 0.01);
        assertEquals(19_333.33, com.resultado().metricas().tempoMedioResposta(), 0.01);
        assertTrue(com.resultado().metricas().tempoMedioEspera() > sem.resultado().metricas().tempoMedioEspera());
    }

    // ------------------------------------------------------------------ cenário B: o lock MELHORA os tempos

    @Test
    void cenarioBLockReduzTrocasEsperaERetornoDoRoundRobin() {
        var sem = rodar(CenarioExemplo.LOCK_MELHORA_OS_TEMPOS, PoliticaEscalonamento.ROUND_ROBIN, ModoSincronizacao.SEM_SINCRONISMO);
        var com = rodar(CenarioExemplo.LOCK_MELHORA_OS_TEMPOS, PoliticaEscalonamento.ROUND_ROBIN, ModoSincronizacao.COM_SINCRONISMO);
        MetricasEscalonamento a = sem.resultado().metricas();
        MetricasEscalonamento b = com.resultado().metricas();

        assertEquals(0, sem.registrosValidos());
        assertEquals(3, com.registrosValidos());

        // "valor sem sincronismo maior que com sincronismo": acontece e tem explicação
        assertEquals(22_333.33, a.tempoMedioEspera(), 0.01);
        assertEquals(15_333.33, b.tempoMedioEspera(), 0.01);
        assertEquals(32_333.33, a.tempoMedioRetorno(), 0.01);
        assertEquals(25_333.33, b.tempoMedioRetorno(), 0.01);
        assertEquals(38_000, a.makespan());
        assertEquals(35_000, b.makespan());
        assertEquals(8, a.trocasContexto());
        assertEquals(5, b.trocasContexto());
        assertTrue(b.trocasContexto() < a.trocasContexto(), "menos trocas de contexto explicam o ganho");
    }

    // ------------------------------------------------------------------ propriedades em qualquer escala

    @Test
    void semSincronismoMantemOGanttDosAlgoritmosOriginaisEmQualquerEscala() {
        for (int escala : ESCALAS) {
            var fcfs = rodarEscalada(escala, PoliticaEscalonamento.FCFS, ModoSincronizacao.SEM_SINCRONISMO);
            var rr = rodarEscalada(escala, PoliticaEscalonamento.ROUND_ROBIN, ModoSincronizacao.SEM_SINCRONISMO);
            List<ProcessoUrbano> carga = fcfs.resultado().processos().stream().map(r -> r.processo()).toList();
            assertEquals(new Fcfs(escala).executar(carga).gantt(), fcfs.resultado().gantt(), "FCFS escala " + escala);
            assertEquals(new RoundRobin(2 * escala, escala).executar(carga).metricas(), rr.resultado().metricas(), "RR escala " + escala);
        }
    }

    @Test
    void metricasEscalamLinearmenteComOTamanhoDosNumeros() {
        var base = rodarEscalada(1, PoliticaEscalonamento.ROUND_ROBIN, ModoSincronizacao.COM_SINCRONISMO);
        for (int escala : ESCALAS) {
            var grande = rodarEscalada(escala, PoliticaEscalonamento.ROUND_ROBIN, ModoSincronizacao.COM_SINCRONISMO);
            MetricasEscalonamento a = base.resultado().metricas();
            MetricasEscalonamento b = grande.resultado().metricas();
            assertEquals(a.tempoMedioEspera() * escala, b.tempoMedioEspera(), 1e-6 * escala);
            assertEquals(a.tempoMedioResposta() * escala, b.tempoMedioResposta(), 1e-6 * escala);
            assertEquals(a.tempoMedioRetorno() * escala, b.tempoMedioRetorno(), 1e-6 * escala);
            assertEquals((long) a.makespan() * escala, b.makespan());
            assertEquals(a.trocasContexto(), b.trocasContexto());
            assertEquals(base.registrosValidos(), grande.registrosValidos());
            assertEquals((long) base.tempoBloqueadoTotal() * escala, grande.tempoBloqueadoTotal());
        }
    }

    @Test
    void comLockNuncaCorrompeEFcfsNuncaMudaEmCargasAleatoriasGrandes() {
        Random sorteio = new Random(2026);
        TipoEvento[] tipos = TipoEvento.values();
        for (int rodada = 0; rodada < 300; rodada++) {
            int n = 2 + sorteio.nextInt(5);
            List<ProcessoUrbano> carga = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                carga.add(new ProcessoUrbano("P" + (i + 1), tipos[sorteio.nextInt(4)], "Alerta " + i,
                        sorteio.nextInt(50_000), 1_000 + sorteio.nextInt(40_000), i));
            }
            int quantum = 2_000 + sorteio.nextInt(20_000);
            int custo = sorteio.nextInt(2_000);
            int escrita = 500 + sorteio.nextInt(30_000);

            var fcfsSem = simulador.executar(carga, PoliticaEscalonamento.FCFS, quantum, custo, escrita, ModoSincronizacao.SEM_SINCRONISMO);
            var fcfsCom = simulador.executar(carga, PoliticaEscalonamento.FCFS, quantum, custo, escrita, ModoSincronizacao.COM_SINCRONISMO);
            var rrSem = simulador.executar(carga, PoliticaEscalonamento.ROUND_ROBIN, quantum, custo, escrita, ModoSincronizacao.SEM_SINCRONISMO);
            var rrCom = simulador.executar(carga, PoliticaEscalonamento.ROUND_ROBIN, quantum, custo, escrita, ModoSincronizacao.COM_SINCRONISMO);

            assertEquals(0, fcfsSem.registrosCorrompidos(), "FCFS não preempta: sem corrupção");
            assertEquals(fcfsSem.resultado().gantt(), fcfsCom.resultado().gantt(), "lock não altera o FCFS");
            assertEquals(0, rrCom.registrosCorrompidos(), "com lock nunca corrompe");
            assertEquals(new Fcfs(custo).executar(carga).gantt(), fcfsSem.resultado().gantt());
            assertEquals(new RoundRobin(quantum, custo).executar(carga).gantt(), rrSem.resultado().gantt());
            long cpu = carga.stream().mapToLong(ProcessoUrbano::burst).sum();
            long executado = rrCom.resultado().gantt().stream()
                    .filter(t -> t.tipo() == TipoTrechoExecucao.PROCESSO).mapToLong(t -> t.duracao()).sum();
            assertEquals(cpu, executado, "todo o burst é executado, com ou sem lock");
        }
    }

    /**
     * Documenta o ponto levantado em sala: "às vezes o valor sem sincronismo fica maior que com".
     * Numa amostra aleatória (semente fixa) o lock reduz a espera média em boa parte das cargas
     * do Round Robin e a aumenta em outras. Os percentuais citados no README vêm desta amostra.
     */
    @Test
    void amostraAleatoriaMostraQueOLockPodeMelhorarOuPiorarOsTempos() {
        Random sorteio = new Random(7);
        TipoEvento[] tipos = TipoEvento.values();
        int total = 20_000;
        int lockMelhorouEspera = 0;
        int lockPiorouEspera = 0;
        int lockReduziuTrocas = 0;
        int semLockCorrompeu = 0;
        for (int rodada = 0; rodada < total; rodada++) {
            int n = 2 + sorteio.nextInt(4);
            List<ProcessoUrbano> carga = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                carga.add(new ProcessoUrbano("P" + (i + 1), tipos[sorteio.nextInt(4)], "d",
                        sorteio.nextInt(8), 1 + sorteio.nextInt(9), i));
            }
            int quantum = 1 + sorteio.nextInt(5);
            int custo = sorteio.nextInt(3);
            int escrita = 1 + sorteio.nextInt(6);
            var sem = simulador.executar(carga, PoliticaEscalonamento.ROUND_ROBIN, quantum, custo, escrita, ModoSincronizacao.SEM_SINCRONISMO);
            var com = simulador.executar(carga, PoliticaEscalonamento.ROUND_ROBIN, quantum, custo, escrita, ModoSincronizacao.COM_SINCRONISMO);
            double espSem = sem.resultado().metricas().tempoMedioEspera();
            double espCom = com.resultado().metricas().tempoMedioEspera();
            if (espCom < espSem - 1e-9) {
                lockMelhorouEspera++;
            } else if (espCom > espSem + 1e-9) {
                lockPiorouEspera++;
            }
            if (com.resultado().metricas().trocasContexto() < sem.resultado().metricas().trocasContexto()) {
                lockReduziuTrocas++;
            }
            if (sem.registrosCorrompidos() > 0) {
                semLockCorrompeu++;
            }
        }
        assertTrue(lockMelhorouEspera > total * 0.30, "o lock melhora a espera em muitas cargas: " + lockMelhorouEspera);
        assertTrue(lockPiorouEspera > total * 0.05, "e piora em outras: " + lockPiorouEspera);
        assertTrue(lockReduziuTrocas > total * 0.25, "porque elimina trocas de contexto: " + lockReduziuTrocas);
        assertTrue(semLockCorrompeu > total * 0.40, "sem lock há muitas colisões: " + semLockCorrompeu);
    }

    @Test
    void resultadoEDeterministico() {
        var a = rodar(CenarioExemplo.COLISAO_NO_ROUND_ROBIN, PoliticaEscalonamento.ROUND_ROBIN, ModoSincronizacao.COM_SINCRONISMO);
        var b = rodar(CenarioExemplo.COLISAO_NO_ROUND_ROBIN, PoliticaEscalonamento.ROUND_ROBIN, ModoSincronizacao.COM_SINCRONISMO);
        assertEquals(a, b);
        assertTrue(a.conteudoArquivo().endsWith("\n"));
    }

    @Test
    void rejeitaParametrosInvalidos() {
        List<ProcessoUrbano> carga = cargaDe(CenarioExemplo.COLISAO_NO_ROUND_ROBIN);
        assertThrows(IllegalArgumentException.class, () -> simulador.executar(
                carga, PoliticaEscalonamento.ROUND_ROBIN, 0, 1, 3, ModoSincronizacao.COM_SINCRONISMO));
        assertThrows(IllegalArgumentException.class, () -> simulador.executar(
                carga, PoliticaEscalonamento.FCFS, 2, 1, 0, ModoSincronizacao.COM_SINCRONISMO));
        assertThrows(IllegalArgumentException.class, () -> simulador.executar(
                List.of(), PoliticaEscalonamento.FCFS, 2, 1, 3, ModoSincronizacao.COM_SINCRONISMO));
    }
}
