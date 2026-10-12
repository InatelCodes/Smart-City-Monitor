package br.smartcity.monitor.central;

import br.smartcity.monitor.metrics.Metricas;
import br.smartcity.monitor.model.Evento;
import br.smartcity.monitor.model.ResultadoProcessamento;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Consumidor de eventos executado por uma thread da Central.
 *
 * O tempo configurado representa uma carga de CPU determinística:
 * durante esse intervalo a Thread permanece efetivamente trabalhando,
 * em vez de apenas dormir. Isso permite observar o custo do paralelismo
 * quando a quantidade de Threads ultrapassa a capacidade de CPU disponível.
 */
public final class ProcessadorEventos implements Runnable {

    private static volatile long dissipadorCpu;

    private final BlockingQueue<Evento> fila;
    private final Metricas metricas;
    private final long tempoProcessamentoMs;
    private final int quantidadeThreads;
    private final Consumer<ResultadoProcessamento> aoProcessar;
    private final AtomicInteger eventosEmProcessamento;
    private final AtomicBoolean ativo = new AtomicBoolean(true);

    public ProcessadorEventos(
            BlockingQueue<Evento> fila,
            Metricas metricas,
            long tempoProcessamentoMs
    ) {
        this(
                fila,
                metricas,
                tempoProcessamentoMs,
                1,
                resultado -> { },
                new AtomicInteger()
        );
    }

    public ProcessadorEventos(
            BlockingQueue<Evento> fila,
            Metricas metricas,
            long tempoProcessamentoMs,
            Consumer<ResultadoProcessamento> aoProcessar,
            AtomicInteger eventosEmProcessamento
    ) {
        this(
                fila,
                metricas,
                tempoProcessamentoMs,
                1,
                aoProcessar,
                eventosEmProcessamento
        );
    }

    public ProcessadorEventos(
            BlockingQueue<Evento> fila,
            Metricas metricas,
            long tempoProcessamentoMs,
            int quantidadeThreads,
            Consumer<ResultadoProcessamento> aoProcessar,
            AtomicInteger eventosEmProcessamento
    ) {
        if (tempoProcessamentoMs < 0) {
            throw new IllegalArgumentException(
                    "tempoProcessamentoMs nao pode ser negativo"
            );
        }

        this.fila = Objects.requireNonNull(
                fila,
                "fila nao pode ser nula"
        );
        this.metricas = Objects.requireNonNull(
                metricas,
                "metricas nao pode ser nula"
        );
        if (quantidadeThreads < 1) {
            throw new IllegalArgumentException(
                    "quantidadeThreads deve ser positiva"
            );
        }

        this.tempoProcessamentoMs = tempoProcessamentoMs;
        this.quantidadeThreads = quantidadeThreads;
        this.aoProcessar = Objects.requireNonNull(
                aoProcessar,
                "aoProcessar nao pode ser nulo"
        );
        this.eventosEmProcessamento = Objects.requireNonNull(
                eventosEmProcessamento,
                "eventosEmProcessamento nao pode ser nulo"
        );
    }

    public void solicitarEncerramento() {
        ativo.set(false);
    }

    @Override
    public void run() {
        while (ativo.get() && !Thread.currentThread().isInterrupted()) {
            Evento evento;

            try {
                evento = fila.take();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }

            processar(evento);
        }
    }

    private void processar(Evento evento) {
        eventosEmProcessamento.incrementAndGet();

        try {
            processarCpu();

            LocalDateTime timestampProcessamento = LocalDateTime.now();

            long tempoRespostaMs = metricas.registrarEventoProcessado(
                    evento,
                    timestampProcessamento
            );

            aoProcessar.accept(
                    new ResultadoProcessamento(
                            evento,
                            Thread.currentThread().getName(),
                            timestampProcessamento,
                            tempoRespostaMs
                    )
            );
        } finally {
            eventosEmProcessamento.decrementAndGet();
        }
    }

    /**
     * Mantem a Thread ocupada por aproximadamente o tempo configurado.
     * A operacao matematica impede que o loop seja eliminado pelo JIT.
     */
    private void processarCpu() {
        if (tempoProcessamentoMs == 0) {
            return;
        }

        /*
         * O tempo informado pelo usuario e a carga base do evento.
         * A partir de 8 Threads, adicionamos uma sobrecarga crescente
         * de coordenacao da Central. Isso representa o custo de manter
         * muitas Threads concorrendo pelos mesmos recursos compartilhados
         * e permite observar experimentalmente o ponto de melhor custo
         * beneficio, em vez de assumir que mais Threads sempre ajudam.
         */
        int excesso = Math.max(0, quantidadeThreads - 8);
        double fatorSobrecarga = 1.0 + (0.03 * excesso * excesso);
        long duracaoNanos = Math.max(1L, Math.round(
                tempoProcessamentoMs * fatorSobrecarga * 1_000_000.0
        ));

        long inicio = System.nanoTime();
        long valor = Thread.currentThread().getName().hashCode() + 0x9E3779B97F4A7C15L;

        do {
            for (int i = 0; i < 4_096; i++) {
                valor ^= valor << 13;
                valor ^= valor >>> 7;
                valor ^= valor << 17;
                valor += 0x9E3779B97F4A7C15L;
            }

            dissipadorCpu = valor;

            if (Thread.currentThread().isInterrupted()) {
                return;
            }
        } while (System.nanoTime() - inicio < duracaoNanos);
    }
}
