package br.smartcity.monitor.metrics;

import br.smartcity.monitor.model.Evento;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

// AtomicInteger e AtomicLong são usados para garantir que as operações de incremento e atualização sejam thread-safe, evitando condições de corrida em um ambiente multithread.

public class Metricas {

    private final AtomicInteger eventosGerados = new AtomicInteger(0);
    private final AtomicInteger eventosProcessados = new AtomicInteger(0);

    private final AtomicLong tempoTotalRespostaMs = new AtomicLong(0);
    private final AtomicLong maiorTempoRespostaMs = new AtomicLong(0);

    private final LocalDateTime inicio = LocalDateTime.now();

    public void registrarEventoGerado() {
        eventosGerados.incrementAndGet();
    }

    public void registrarEventoProcessado(Evento evento) {

        registrarEventoProcessado(evento, LocalDateTime.now());
    }

    /**
     * Registra um processamento usando o mesmo instante armazenado no resultado.
     *
     * @return tempo de resposta do evento em milissegundos
     */
    public long registrarEventoProcessado(
            Evento evento,
            LocalDateTime timestampProcessamento
    ) {

        Objects.requireNonNull(evento, "evento nao pode ser nulo");
        Objects.requireNonNull(
                timestampProcessamento,
                "timestampProcessamento nao pode ser nulo"
        );

        eventosProcessados.incrementAndGet();

        long tempoResposta = Math.max(0, Duration.between(
                evento.getTimestampCriacao(),
                timestampProcessamento
        ).toMillis());

        tempoTotalRespostaMs.addAndGet(tempoResposta);

        maiorTempoRespostaMs.updateAndGet(
                atual -> Math.max(atual, tempoResposta)
        );

        return tempoResposta;
    }

    public int getEventosGerados() {
        return eventosGerados.get();
    }

    public int getEventosProcessados() {
        return eventosProcessados.get();
    }

    public long getTempoTotalRespostaMs() {
        return tempoTotalRespostaMs.get();
    }

    public long getMaiorTempoRespostaMs() {
        return maiorTempoRespostaMs.get();
    }

    public double getTempoMedioRespostaMs() {

        int processados = eventosProcessados.get();

        if (processados == 0) {
            return 0;
        }

        return (double) tempoTotalRespostaMs.get() / processados;
    }

    public double getTaxaGeracao() {

        long tempoDecorridoMs = Duration.between(
                inicio,
                LocalDateTime.now()
        ).toMillis();

        if (tempoDecorridoMs == 0) {
            return 0;
        }

        return eventosGerados.get() /
                (tempoDecorridoMs / 1000.0);
    }

    public double getTaxaProcessamento() {

        long tempoDecorridoMs = Duration.between(
                inicio,
                LocalDateTime.now()
        ).toMillis();

        if (tempoDecorridoMs == 0) {
            return 0;
        }

        return eventosProcessados.get() /
                (tempoDecorridoMs / 1000.0);
    }

    public int getEventosPendentes(BlockingQueue<?> fila) {
        return Objects.requireNonNull(fila, "fila nao pode ser nula").size();
    }

    public void exibirResumo() {

        System.out.println("\n========== MÉTRICAS ==========");

        System.out.println(
                "Eventos gerados: " + getEventosGerados()
        );

        System.out.println(
                "Eventos processados: " + getEventosProcessados()
        );

        System.out.printf(
                "Tempo médio de resposta: %.2f ms%n",
                getTempoMedioRespostaMs()
        );

        System.out.println(
                "Maior tempo de resposta: "
                        + getMaiorTempoRespostaMs() + " ms"
        );

        System.out.printf(
                "Taxa de geração: %.2f eventos/s%n",
                getTaxaGeracao()
        );

        System.out.printf(
                "Taxa de processamento: %.2f eventos/s%n",
                getTaxaProcessamento()
        );

        System.out.println("===============================\n");
    }
}
