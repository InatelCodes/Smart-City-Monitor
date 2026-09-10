package br.smartcity.monitor.sensor;

import br.smartcity.monitor.model.Evento;
import br.smartcity.monitor.model.TipoEvento;

import java.util.ArrayList;
import java.util.List;

public final class ListaEventos {

    /** Quantidade padrão de eventos usada em cada experimento. */
    public static final int QUANTIDADE_EVENTOS = 400;

    private static final List<ModeloEvento> MODELOS = List.of(
            new ModeloEvento(TipoEvento.TRANSITO, "Fluxo normal de veículos"),
            new ModeloEvento(TipoEvento.TRANSITO, "Fluxo intenso de veículos"),
            new ModeloEvento(TipoEvento.TRANSITO, "Congestionamento detectado"),
            new ModeloEvento(TipoEvento.TRANSITO, "Alteração no fluxo de veículos"),
            new ModeloEvento(TipoEvento.CLIMA, "Temperatura dentro da normalidade"),
            new ModeloEvento(TipoEvento.CLIMA, "Temperatura elevada detectada"),
            new ModeloEvento(TipoEvento.CLIMA, "Chuva detectada"),
            new ModeloEvento(TipoEvento.CLIMA, "Possibilidade de tempestade"),
            new ModeloEvento(TipoEvento.ENERGIA, "Consumo de energia normal"),
            new ModeloEvento(TipoEvento.ENERGIA, "Consumo de energia elevado"),
            new ModeloEvento(TipoEvento.ENERGIA, "Pico de consumo detectado"),
            new ModeloEvento(TipoEvento.ENERGIA, "Oscilação no fornecimento de energia"),
            new ModeloEvento(TipoEvento.QUALIDADE_AR, "Qualidade do ar normal"),
            new ModeloEvento(TipoEvento.QUALIDADE_AR, "Aumento de partículas detectado"),
            new ModeloEvento(TipoEvento.QUALIDADE_AR, "Qualidade do ar moderada"),
            new ModeloEvento(TipoEvento.QUALIDADE_AR, "Nível de poluentes elevado")
    );

    private ListaEventos() {
    }

    public static List<Evento> criarEventos() {
        return criarEventos(QUANTIDADE_EVENTOS);
    }

    /** Cria a carga desejada repetindo os 16 modelos de evento. */
    public static List<Evento> criarEventos(int quantidade) {
        if (quantidade < 0) {
            throw new IllegalArgumentException("quantidade não pode ser negativa");
        }

        List<Evento> eventos = new ArrayList<>(quantidade);

        for (int i = 0; i < quantidade; i++) {
            ModeloEvento modelo = MODELOS.get(i % MODELOS.size());
            eventos.add(new Evento(modelo.tipo(), modelo.descricao()));
        }

        return eventos;
    }

    private record ModeloEvento(TipoEvento tipo, String descricao) {
    }
}
