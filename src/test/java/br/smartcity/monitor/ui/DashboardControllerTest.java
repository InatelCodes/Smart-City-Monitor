package br.smartcity.monitor.ui;

import br.smartcity.monitor.config.ConfiguracaoExperimento;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DashboardControllerTest {

    @Test
    void executaParaEConsolidaUmExperimentoReal() throws InterruptedException {
        DashboardController controller = new DashboardController();
        controller.iniciar(new ConfiguracaoExperimento(1, 30, 100));

        Thread.sleep(550);
        DashboardSnapshot duranteExecucao = controller.obterSnapshot();
        assertEquals(DashboardController.Estado.EXECUTANDO, controller.getEstado());
        assertEquals(1, duranteExecucao.threadsAtivas());
        assertTrue(duranteExecucao.eventosGerados() > 0);
        assertTrue(duranteExecucao.eventosProcessados() > 0);

        ExperimentoResultado resultado = controller.parar();
        assertEquals(DashboardController.Estado.FINALIZADO, controller.getEstado());
        assertEquals(1, resultado.configuracao().quantidadeThreads());
        assertEquals(0, controller.obterSnapshot().threadsAtivas());
        assertEquals(resultado.eventosProcessados(), controller.obterSnapshot().eventosProcessados());

        double taxaFinal = controller.obterSnapshot().taxaProcessamento();
        Thread.sleep(30);
        assertEquals(taxaFinal, controller.obterSnapshot().taxaProcessamento());
    }

    @Test
    void resetLimpaLeiturasMasPreservaPossibilidadeDeNovaExecucao() throws InterruptedException {
        DashboardController controller = new DashboardController();
        controller.iniciar(new ConfiguracaoExperimento(2, 10, 20));
        Thread.sleep(120);
        controller.parar();
        controller.resetar();

        assertEquals(DashboardController.Estado.PARADO, controller.getEstado());
        assertEquals(DashboardSnapshot.vazio(), controller.obterSnapshot());

        controller.iniciar(new ConfiguracaoExperimento(4, 20, 10));
        assertEquals(4, controller.obterSnapshot().threadsAtivas());
        controller.parar();
    }
}
