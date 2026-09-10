package br.smartcity.monitor.sensor;

import br.smartcity.monitor.model.Evento;
import br.smartcity.monitor.model.TipoEvento;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ListaEventosTest {

    @Test
    void criaQuatrocentosEventosUnicosEDistribuidosEntreOsTipos() {
        List<Evento> eventos = ListaEventos.criarEventos();

        assertEquals(400, eventos.size());
        assertEquals(400, eventos.stream().map(Evento::getId).distinct().count());

        for (TipoEvento tipo : TipoEvento.values()) {
            assertEquals(
                    100,
                    eventos.stream().filter(evento -> evento.getTipo() == tipo).count()
            );
        }
    }

    @Test
    void permiteConfigurarOutraQuantidadeDeEventos() {
        assertEquals(800, ListaEventos.criarEventos(800).size());
        assertThrows(IllegalArgumentException.class, () -> ListaEventos.criarEventos(-1));
    }
}
