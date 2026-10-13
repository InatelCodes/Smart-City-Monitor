package br.smartcity.monitor.escalonamento.simulacao;

import br.smartcity.monitor.escalonamento.algoritmo.*;
import br.smartcity.monitor.escalonamento.model.*;
import java.util.List;
import java.util.Objects;

/** Executa ambos os algoritmos sobre a mesma carga imutável. */
public record ComparacaoEscalonamento(ResultadoEscalonamento fcfs, ResultadoEscalonamento roundRobin) {
    public ComparacaoEscalonamento {
        Objects.requireNonNull(fcfs,"fcfs"); Objects.requireNonNull(roundRobin,"roundRobin");
    }
    public static ComparacaoEscalonamento executar(List<ProcessoUrbano> carga, int quantum, int custoTroca) {
        List<ProcessoUrbano> copia=List.copyOf(carga);
        return new ComparacaoEscalonamento(new Fcfs(custoTroca).executar(List.copyOf(copia)),new RoundRobin(quantum,custoTroca).executar(List.copyOf(copia)));
    }
}
