package br.smartcity.monitor.sensor;

import br.smartcity.monitor.metrics.Metricas;
import br.smartcity.monitor.model.Evento;
import br.smartcity.monitor.model.TipoEvento;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ThreadLocalRandom;

public class SensorQualidadeAr extends Sensor {

    public SensorQualidadeAr(
            BlockingQueue<Evento> fila,
            long intervaloMs,
            Metricas metricas
    ) {
        super(fila, intervaloMs, metricas, "SENSOR-QUALIDADE-AR");
    }

    @Override
    protected Evento gerarEvento() {

        String[] eventos = {
                "Qualidade do ar normal",
                "Aumento de partículas detectado",
                "Qualidade do ar moderada",
                "Nível de poluentes elevado",
                "Qualidade do ar melhorou"
        };

        String descricao = eventos[
                ThreadLocalRandom.current().nextInt(eventos.length)
        ];

        return new Evento(
                TipoEvento.QUALIDADE_AR,
                descricao
        );
    }
}