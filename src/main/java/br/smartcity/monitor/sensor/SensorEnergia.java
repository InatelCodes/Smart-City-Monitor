package br.smartcity.monitor.sensor;

import br.smartcity.monitor.metrics.Metricas;
import br.smartcity.monitor.model.Evento;
import br.smartcity.monitor.model.TipoEvento;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ThreadLocalRandom;

public class SensorEnergia extends Sensor {

    public SensorEnergia(
            BlockingQueue<Evento> fila,
            long intervaloMs,
            Metricas metricas
    ) {
        super(fila, intervaloMs, metricas, "SENSOR-ENERGIA");
    }

    @Override
    protected Evento gerarEvento() {

        String[] eventos = {
                "Consumo de energia normal",
                "Consumo de energia elevado",
                "Pico de consumo detectado",
                "Redução no consumo de energia",
                "Oscilação no fornecimento de energia"
        };

        String descricao = eventos[
                ThreadLocalRandom.current().nextInt(eventos.length)
        ];

        return new Evento(
                TipoEvento.ENERGIA,
                descricao
        );
    }
}