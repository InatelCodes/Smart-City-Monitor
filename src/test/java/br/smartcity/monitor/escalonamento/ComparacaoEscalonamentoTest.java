package br.smartcity.monitor.escalonamento;

import br.smartcity.monitor.escalonamento.simulacao.ComparacaoEscalonamento;
import br.smartcity.monitor.escalonamento.model.ProcessoUrbano;
import br.smartcity.monitor.model.TipoEvento;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ComparacaoEscalonamentoTest {
 @Test void comparaAmbosComMesmaCarga(){var carga=List.of(new ProcessoUrbano("P1",TipoEvento.TRANSITO,"Trânsito",0,8,0),new ProcessoUrbano("P2",TipoEvento.ENERGIA,"Energia",1,4,1),new ProcessoUrbano("P3",TipoEvento.QUALIDADE_AR,"Ar",2,2,2));var r=ComparacaoEscalonamento.executar(carga,2,1);assertEquals("FCFS",r.fcfs().algoritmo());assertEquals("Round Robin",r.roundRobin().algoritmo());assertEquals(3,r.fcfs().processos().size());assertEquals(3,r.roundRobin().processos().size());}
 @Test void comparaAmbosComBurstsDe40MilUt(){var carga=List.of(new ProcessoUrbano("P1",TipoEvento.TRANSITO,"Trânsito",0,40_000,0),new ProcessoUrbano("P2",TipoEvento.ENERGIA,"Energia",5_000,20_000,1),new ProcessoUrbano("P3",TipoEvento.QUALIDADE_AR,"Ar",10_000,10_000,2));var r=ComparacaoEscalonamento.executar(carga,10_000,500);assertEquals(71_000,r.fcfs().metricas().makespan());assertEquals(72_500,r.roundRobin().metricas().makespan());assertEquals(5_500.0,r.roundRobin().metricas().tempoMedioResposta(),0.01);assertEquals(28_833.33,r.fcfs().metricas().tempoMedioResposta(),0.01);}
}
