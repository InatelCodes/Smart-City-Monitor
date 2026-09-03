package br.smartcity.monitor.central;

import br.smartcity.monitor.metrics.Metricas;
import br.smartcity.monitor.model.Evento;
import br.smartcity.monitor.model.ResultadoProcessamento;
import br.smartcity.monitor.model.TipoEvento;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CentralMonitoramentoTest {

    @ParameterizedTest(name = "processa sem perdas ou duplicacoes com {0} thread(s)")
    @ValueSource(ints = {1, 2, 3, 4})
    void processaTodosOsEventosComQuantidadeConfiguradaDeThreads(int quantidadeThreads)
            throws InterruptedException {
        int totalEventos = 60;
        BlockingQueue<Evento> fila = new LinkedBlockingQueue<>();
        Metricas metricas = new Metricas();

        for (int i = 0; i < totalEventos; i++) {
            fila.add(new Evento(TipoEvento.TRANSITO, "Evento de teste " + i));
            metricas.registrarEventoGerado();
        }

        CentralMonitoramento central = new CentralMonitoramento(
                fila,
                metricas,
                quantidadeThreads,
                3
        );

        central.iniciar();
        assertEquals(quantidadeThreads, central.getQuantidadeThreadsAtivas());
        assertTrue(central.aguardarEventosProcessados(totalEventos, 5, TimeUnit.SECONDS));
        central.encerrar();

        List<ResultadoProcessamento> resultados = central.getResultados();
        Set<String> idsProcessados = new HashSet<>();
        Set<String> threadsQueProcessaram = new HashSet<>();

        for (ResultadoProcessamento resultado : resultados) {
            idsProcessados.add(resultado.getEvento().getId());
            threadsQueProcessaram.add(resultado.getThreadResponsavel());
            assertTrue(resultado.getTempoRespostaMs() >= 0);
        }

        assertEquals(totalEventos, resultados.size());
        assertEquals(totalEventos, idsProcessados.size());
        assertEquals(quantidadeThreads, threadsQueProcessaram.size());
        assertEquals(totalEventos, metricas.getEventosProcessados());
        assertEquals(0, central.getEventosPendentes());
        assertEquals(0, central.getQuantidadeThreadsAtivas());
        assertFalse(central.isEmExecucao());
    }

    @Test
    void encerraThreadsBloqueadasEmFilaVazia() {
        CentralMonitoramento central = new CentralMonitoramento(
                new LinkedBlockingQueue<>(),
                new Metricas(),
                4,
                100
        );

        assertTimeout(Duration.ofSeconds(2), () -> {
            central.iniciar();
            central.encerrar();
        });

        assertEquals(0, central.getQuantidadeThreadsAtivas());
    }

    @Test
    void finalizaEventoJaRetiradoDaFilaAntesDeEncerrar() throws InterruptedException {
        BlockingQueue<Evento> fila = new LinkedBlockingQueue<>();
        Metricas metricas = new Metricas();
        fila.add(new Evento(TipoEvento.CLIMA, "Evento em processamento"));
        metricas.registrarEventoGerado();

        CentralMonitoramento central = new CentralMonitoramento(fila, metricas, 1, 10_000);
        central.iniciar();

        long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (central.getEventosEmProcessamento() == 0 && System.nanoTime() < limite) {
            Thread.onSpinWait();
        }

        assertEquals(1, central.getEventosEmProcessamento());
        central.encerrar();

        assertEquals(1, metricas.getEventosProcessados());
        assertEquals(1, central.getResultados().size());
        assertEquals(0, central.getEventosPendentes());
    }

    @Test
    void aceitaSomenteDeUmaAQuatroThreads() {
        BlockingQueue<Evento> fila = new LinkedBlockingQueue<>();
        Metricas metricas = new Metricas();

        assertThrows(
                IllegalArgumentException.class,
                () -> new CentralMonitoramento(fila, metricas, 0, 100)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new CentralMonitoramento(fila, metricas, 5, 100)
        );
    }

    private static void assertTimeout(Duration timeout, OperacaoComInterrupcao operacao) {
        long inicio = System.nanoTime();

        try {
            operacao.executar();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("teste interrompido", e);
        }

        assertTrue(
                System.nanoTime() - inicio < timeout.toNanos(),
                "a operacao excedeu " + timeout
        );
    }

    @FunctionalInterface
    private interface OperacaoComInterrupcao {
        void executar() throws InterruptedException;
    }
}
