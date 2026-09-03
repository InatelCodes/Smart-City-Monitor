package br.smartcity.monitor.sensor;

import br.smartcity.monitor.model.Evento;
import br.smartcity.monitor.metrics.Metricas;

import java.util.concurrent.BlockingQueue;

public abstract class Sensor implements Runnable {

    protected final BlockingQueue<Evento> fila;
    protected final long intervaloMs;
    protected final Metricas metricas;

    private final String identificacao;

    protected Sensor(
            BlockingQueue<Evento> fila,
            long intervaloMs,
            Metricas metricas,
            String identificacao
    ) {
        this.fila = fila;
        this.intervaloMs = intervaloMs;
        this.metricas = metricas;
        this.identificacao = identificacao;
    }

    protected abstract Evento gerarEvento();

    public String getIdentificacao() {
        return identificacao;
    }

    @Override
    public void run() {

        while (!Thread.currentThread().isInterrupted()) {

            try {
                Evento evento = gerarEvento();

                fila.put(evento);

                // Registra que um novo evento foi gerado
                metricas.registrarEventoGerado();

                Thread.sleep(intervaloMs);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
