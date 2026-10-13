package br.smartcity.monitor.escalonamento;

import br.smartcity.monitor.escalonamento.algoritmo.RoundRobin;
import br.smartcity.monitor.escalonamento.model.ProcessoUrbano;
import br.smartcity.monitor.model.TipoEvento;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class RoundRobinTest {
    private List<ProcessoUrbano> carga(){return List.of(
        new ProcessoUrbano("P1",TipoEvento.TRANSITO,"Congestionamento",0,8,0),
        new ProcessoUrbano("P2",TipoEvento.ENERGIA,"Pico de consumo",1,4,1),
        new ProcessoUrbano("P3",TipoEvento.QUALIDADE_AR,"Poluentes",2,2,2));}
    @Test void calculaCargaDeReferencia(){var r=new RoundRobin(2,1).executar(carga());assertEquals(19,r.metricas().makespan());assertEquals(5,r.metricas().trocasContexto());assertEquals(8.0,r.metricas().tempoMedioEspera(),0.01);assertEquals(12.67,r.metricas().tempoMedioRetorno(),0.01);assertEquals(2.0,r.metricas().tempoMedioResposta(),0.01);}
    @Test void rejeitaQuantumInvalido(){assertThrows(IllegalArgumentException.class,()->new RoundRobin(0,1));}
}
