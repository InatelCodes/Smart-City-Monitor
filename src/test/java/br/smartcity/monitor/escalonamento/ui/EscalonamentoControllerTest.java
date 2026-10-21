package br.smartcity.monitor.escalonamento.ui;

import br.smartcity.monitor.escalonamento.model.ProcessoUrbano;
import br.smartcity.monitor.escalonamento.model.ResultadoEscalonamento;
import br.smartcity.monitor.escalonamento.sincronismo.ModoSincronizacao;
import br.smartcity.monitor.model.TipoEvento;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EscalonamentoControllerTest {

    @Test
    void iniciaComCargaSmartCityDaSpec() {
        EscalonamentoController controller = new EscalonamentoController();

        List<ProcessoUrbano> processos = controller.obterProcessos();
        assertEquals(3, processos.size());
        assertEquals("Congestionamento detectado", processos.get(0).descricao());
        assertEquals(TipoEvento.ENERGIA, processos.get(1).tipoEvento());
        assertEquals(TipoEvento.QUALIDADE_AR, processos.get(2).tipoEvento());
        assertEquals(10_000, controller.getQuantum());
        assertEquals(500, controller.getCustoTrocaContexto());
        assertEquals(15_000, controller.getTamanhoEscrita());
        assertEquals(40_000, processos.get(0).burst());
        assertEquals(5_000, processos.get(1).tempoChegada());
        assertEquals(
                ModoSincronizacao.COM_SINCRONISMO,
                controller.getModoSincronizacao()
        );

        ResultadoEscalonamento resultado = controller.executarFcfs();
        assertEquals(71_000, resultado.metricas().makespan());
        assertEquals(1_000, resultado.metricas().tempoTrocasContexto());
        assertFalse(controller.isEdicaoBloqueada());
    }

    @Test
    void permiteEditarCargaEImpedeIdsDuplicados() {
        EscalonamentoController controller = new EscalonamentoController();

        ProcessoUrbano editado = controller.editarProcesso(
                "P2",
                "ENERGIA-1",
                TipoEvento.ENERGIA,
                "Oscilação no fornecimento de energia",
                4,
                3
        );

        assertEquals("ENERGIA-1", editado.id());
        assertEquals(1, editado.ordemCadastro());
        assertThrows(
                IllegalArgumentException.class,
                () -> controller.adicionarProcesso(
                        "P1",
                        TipoEvento.CLIMA,
                        "Possibilidade de tempestade",
                        3,
                        2
                )
        );
    }

    @Test
    void bloqueiaAlteracoesEnquantoCargaEstaEmSimulacao() {
        EscalonamentoController controller = new EscalonamentoController();

        List<ProcessoUrbano> carga = controller.iniciarSimulacao();
        assertEquals(3, carga.size());
        assertTrue(controller.isEdicaoBloqueada());
        assertThrows(IllegalStateException.class, controller::limparCarga);
        assertThrows(
                IllegalStateException.class,
                () -> controller.setQuantum(4)
        );
        assertThrows(
                IllegalStateException.class,
                () -> controller.setCustoTrocaContexto(2)
        );

        controller.finalizarSimulacao();
        controller.setQuantum(4);
        controller.setCustoTrocaContexto(2);
        controller.setModoSincronizacao(
                ModoSincronizacao.SEM_SINCRONISMO
        );
        assertEquals(4, controller.getQuantum());
        assertEquals(2, controller.getCustoTrocaContexto());
        assertEquals(
                ModoSincronizacao.SEM_SINCRONISMO,
                controller.getModoSincronizacao()
        );
    }

    @Test
    void rejeitaQuantumInvalidoECargaVazia() {
        EscalonamentoController controller = new EscalonamentoController();

        assertThrows(
                IllegalArgumentException.class,
                () -> controller.setQuantum(0)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> controller.setCustoTrocaContexto(-1)
        );

        controller.limparCarga();
        assertThrows(
                IllegalStateException.class,
                controller::iniciarSimulacao
        );
    }

    @Test
    void carregaOCenarioBEAjustaOsParametros() {
        EscalonamentoController controller = new EscalonamentoController();

        controller.carregarCenario(CenarioExemplo.LOCK_MELHORA_OS_TEMPOS);

        assertEquals(3, controller.obterProcessos().size());
        assertEquals(4_000, controller.getQuantum());
        assertEquals(1_000, controller.getCustoTrocaContexto());
        assertEquals(6_000, controller.getTamanhoEscrita());
        assertEquals(10_000, controller.obterProcessos().get(2).burst());

        controller.restaurarCargaExemplo();
        assertEquals(10_000, controller.getQuantum());
        assertEquals(15_000, controller.getTamanhoEscrita());
    }

    @Test
    void estimaFatiasERecusaQuantumQueTornariaOGanttIlegivel() {
        EscalonamentoController controller = new EscalonamentoController();
        List<ProcessoUrbano> carga = controller.obterProcessos();

        assertEquals(7, EscalonamentoController.estimarFatias(carga, 10_000));
        assertEquals(70_000, EscalonamentoController.estimarFatias(carga, 1));
        EscalonamentoController.validarFatias(carga, 10_000);
        IllegalArgumentException erro = assertThrows(
                IllegalArgumentException.class,
                () -> EscalonamentoController.validarFatias(carga, 1)
        );
        assertTrue(erro.getMessage().contains("70.000"));
    }

    @Test
    void aceitaValoresGrandesEImpedeEstouroDeInteiro() {
        EscalonamentoController controller = new EscalonamentoController();

        controller.setQuantum(EscalonamentoController.VALOR_MAXIMO);
        controller.setTamanhoEscrita(40_000);
        controller.adicionarProcesso("P9", TipoEvento.CLIMA, "Tempestade", 100_000, 1_000_000);
        assertThrows(IllegalArgumentException.class,
                () -> controller.setQuantum(EscalonamentoController.VALOR_MAXIMO + 1));
        assertThrows(IllegalArgumentException.class,
                () -> controller.adicionarProcesso("P10", TipoEvento.CLIMA, "Chuva", 0, Integer.MAX_VALUE));
    }
}
