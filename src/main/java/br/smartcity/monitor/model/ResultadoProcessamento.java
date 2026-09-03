package br.smartcity.monitor.model;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Resultado imutavel produzido pela Central ao concluir um evento.
 */
public final class ResultadoProcessamento {

    private final Evento evento;
    private final String threadResponsavel;
    private final LocalDateTime timestampProcessamento;
    private final long tempoRespostaMs;

    public ResultadoProcessamento(
            Evento evento,
            String threadResponsavel,
            LocalDateTime timestampProcessamento,
            long tempoRespostaMs
    ) {
        this.evento = Objects.requireNonNull(evento, "evento nao pode ser nulo");
        this.threadResponsavel = Objects.requireNonNull(
                threadResponsavel,
                "threadResponsavel nao pode ser nula"
        );
        this.timestampProcessamento = Objects.requireNonNull(
                timestampProcessamento,
                "timestampProcessamento nao pode ser nulo"
        );
        this.tempoRespostaMs = tempoRespostaMs;
    }

    public Evento getEvento() {
        return evento;
    }

    public String getThreadResponsavel() {
        return threadResponsavel;
    }

    public LocalDateTime getTimestampProcessamento() {
        return timestampProcessamento;
    }

    public long getTempoRespostaMs() {
        return tempoRespostaMs;
    }
}
