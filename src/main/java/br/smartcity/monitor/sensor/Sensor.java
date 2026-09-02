package br.smartcity.monitor.sensor;

import br.smartcity.monitor.model.Evento;

import java.util.concurrent.BlockingQueue;

public abstract class Sensor implements Runnable {

    protected final BlockingQueue<Evento> fila;
    protected final long intervaloMs;
    private final String identificacao;

    protected Sensor(
            BlockingQueue<Evento> fila,
            long intervaloMs,
            String identificacao
    ) {
        this.fila = fila;
        this.intervaloMs = intervaloMs;
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

                System.out.println(
                        "[" + identificacao + "] Evento gerado: " + evento
                );

                Thread.sleep(intervaloMs);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}