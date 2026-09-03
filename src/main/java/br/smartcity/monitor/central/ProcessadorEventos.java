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
 */
public final class ProcessadorEventos implements Runnable {

    private final BlockingQueue<Evento> fila;
    private final Metricas metricas;
    private final long tempoProcessamentoMs;
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
        if (tempoProcessamentoMs < 0) {
            throw new IllegalArgumentException("tempoProcessamentoMs nao pode ser negativo");
        }

        this.fila = Objects.requireNonNull(fila, "fila nao pode ser nula");
        this.metricas = Objects.requireNonNull(metricas, "metricas nao pode ser nula");
        this.tempoProcessamentoMs = tempoProcessamentoMs;
        this.aoProcessar = Objects.requireNonNull(aoProcessar, "aoProcessar nao pode ser nulo");
        this.eventosEmProcessamento = Objects.requireNonNull(
                eventosEmProcessamento,
                "eventosEmProcessamento nao pode ser nulo"
        );
    }

    /** Solicita o encerramento. A Central tambem interrompe a thread para liberar fila.take(). */
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
        boolean interrompidaDuranteProcessamento = false;
        eventosEmProcessamento.incrementAndGet();

        try {
            try {
                Thread.sleep(tempoProcessamentoMs);
            } catch (InterruptedException e) {
                // Um evento ja retirado da fila e sempre contabilizado antes do encerramento.
                interrompidaDuranteProcessamento = true;
            }

            LocalDateTime timestampProcessamento = LocalDateTime.now();
            long tempoRespostaMs = metricas.registrarEventoProcessado(
                    evento,
                    timestampProcessamento
            );

            aoProcessar.accept(new ResultadoProcessamento(
                    evento,
                    Thread.currentThread().getName(),
                    timestampProcessamento,
                    tempoRespostaMs
            ));
        } finally {
            eventosEmProcessamento.decrementAndGet();

            if (interrompidaDuranteProcessamento) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
