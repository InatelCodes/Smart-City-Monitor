package br.smartcity.monitor.escalonamento;

import br.smartcity.monitor.escalonamento.simulacao.ComparacaoEscalonamento;
import br.smartcity.monitor.escalonamento.model.ProcessoUrbano;
import br.smartcity.monitor.model.TipoEvento;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ComparacaoEscalonamentoTest {
 @Test void comparaAmbosComMesmaCarga(){var carga=List.of(new ProcessoUrbano("P1",TipoEvento.TRANSITO,"Trânsito",0,8,0),new ProcessoUrbano("P2",TipoEvento.ENERGIA,"Energia",1,4,1),new ProcessoUrbano("P3",TipoEvento.QUALIDADE_AR,"Ar",2,2,2));var r=ComparacaoEscalonamento.executar(carga,2,1);assertEquals("FCFS",r.fcfs().algoritmo());assertEquals("Round Robin",r.roundRobin().algoritmo());assertEquals(3,r.fcfs().processos().size());assertEquals(3,r.roundRobin().processos().size());}
}
