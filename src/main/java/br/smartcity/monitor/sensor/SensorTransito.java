package br.smartcity.monitor.sensor;

import br.smartcity.monitor.metrics.Metricas;
import br.smartcity.monitor.model.Evento;
import br.smartcity.monitor.model.TipoEvento;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ThreadLocalRandom;

public class SensorTransito extends Sensor {

    public SensorTransito(
        BlockingQueue<Evento> fila,
        long intervaloMs,
        Metricas metricas
    ) {
        super(fila, intervaloMs, metricas, "SENSOR-TRANSITO");
    }

    @Override
    protected Evento gerarEvento() {

        String[] eventos = {
                "Fluxo normal de veículos",
                "Fluxo intenso de veículos",
                "Congestionamento detectado",
                "Alteração no fluxo de veículos"
        };

        String descricao = eventos[
                ThreadLocalRandom.current().nextInt(eventos.length)
        ];

        return new Evento(
                TipoEvento.TRANSITO,
                descricao
        );
    }
}