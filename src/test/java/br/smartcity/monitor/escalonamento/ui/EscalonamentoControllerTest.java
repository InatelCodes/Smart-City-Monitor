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
        assertEquals(2, controller.getQuantum());
        assertEquals(1, controller.getCustoTrocaContexto());
        assertEquals(
                ModoSincronizacao.COM_SINCRONISMO,
                controller.getModoSincronizacao()
        );

        ResultadoEscalonamento resultado = controller.executarFcfs();
        assertEquals(16, resultado.metricas().makespan());
        assertEquals(2, resultado.metricas().tempoTrocasContexto());
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
}
