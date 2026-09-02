package br.smartcity.monitor.sensor;

import br.smartcity.monitor.model.Evento;
import br.smartcity.monitor.model.TipoEvento;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ThreadLocalRandom;

public class SensorClima extends Sensor {

    public SensorClima(
            BlockingQueue<Evento> fila,
            long intervaloMs
    ) {
        super(fila, intervaloMs, "SENSOR-CLIMA");
    }

    @Override
    protected Evento gerarEvento() {

        String[] eventos = {
                "Temperatura dentro da normalidade",
                "Temperatura elevada detectada",
                "Chuva detectada",
                "Possibilidade de tempestade",
                "Umidade elevada detectada"
        };

        String descricao = eventos[
                ThreadLocalRandom.current().nextInt(eventos.length)
        ];

        return new Evento(
                TipoEvento.CLIMA,
                descricao
        );
    }
}