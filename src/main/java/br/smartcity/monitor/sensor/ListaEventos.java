package br.smartcity.monitor.sensor;

import br.smartcity.monitor.model.Evento;
import br.smartcity.monitor.model.TipoEvento;

import java.util.ArrayList;
import java.util.List;

public final class ListaEventos {

    private ListaEventos() {
    }

    public static List<Evento> criarEventos() {

        List<Evento> eventos = new ArrayList<>();

        // Trânsito
        eventos.add(new Evento(
                TipoEvento.TRANSITO,
                "Fluxo normal de veículos"
        ));
        eventos.add(new Evento(
                TipoEvento.TRANSITO,
                "Fluxo intenso de veículos"
        ));
        eventos.add(new Evento(
                TipoEvento.TRANSITO,
                "Congestionamento detectado"
        ));
        eventos.add(new Evento(
                TipoEvento.TRANSITO,
                "Alteração no fluxo de veículos"
        ));

        // Clima
        eventos.add(new Evento(
                TipoEvento.CLIMA,
                "Temperatura dentro da normalidade"
        ));
        eventos.add(new Evento(
                TipoEvento.CLIMA,
                "Temperatura elevada detectada"
        ));
        eventos.add(new Evento(
                TipoEvento.CLIMA,
                "Chuva detectada"
        ));
        eventos.add(new Evento(
                TipoEvento.CLIMA,
                "Possibilidade de tempestade"
        ));

        // Energia
        eventos.add(new Evento(
                TipoEvento.ENERGIA,
                "Consumo de energia normal"
        ));
        eventos.add(new Evento(
                TipoEvento.ENERGIA,
                "Consumo de energia elevado"
        ));
        eventos.add(new Evento(
                TipoEvento.ENERGIA,
                "Pico de consumo detectado"
        ));
        eventos.add(new Evento(
                TipoEvento.ENERGIA,
                "Oscilação no fornecimento de energia"
        ));

        // Qualidade do ar
        eventos.add(new Evento(
                TipoEvento.QUALIDADE_AR,
                "Qualidade do ar normal"
        ));
        eventos.add(new Evento(
                TipoEvento.QUALIDADE_AR,
                "Aumento de partículas detectado"
        ));
        eventos.add(new Evento(
                TipoEvento.QUALIDADE_AR,
                "Qualidade do ar moderada"
        ));
        eventos.add(new Evento(
                TipoEvento.QUALIDADE_AR,
                "Nível de poluentes elevado"
        ));

        return eventos;
    }
}