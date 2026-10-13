package br.smartcity.monitor.escalonamento.algoritmo;

import br.smartcity.monitor.escalonamento.model.*;
import br.smartcity.monitor.escalonamento.simulacao.CalculadoraMetricas;
import java.util.*;

/** Round Robin preemptivo, com fila FIFO e quantum configurável. */
public final class RoundRobin implements AlgoritmoEscalonamento {
    public static final String NOME = "Round Robin";
    private final int quantum;
    private final int custoTrocaContexto;

    public RoundRobin(int quantum, int custoTrocaContexto) {
        if (quantum <= 0) throw new IllegalArgumentException("O quantum deve ser maior que zero");
        if (custoTrocaContexto < 0) throw new IllegalArgumentException("O custo de troca não pode ser negativo");
        this.quantum = quantum;
        this.custoTrocaContexto = custoTrocaContexto;
    }

    @Override public ResultadoEscalonamento executar(List<ProcessoUrbano> entrada) {
        if (entrada == null || entrada.isEmpty()) throw new IllegalArgumentException("A carga deve possuir pelo menos um processo");
        List<ProcessoUrbano> carga = List.copyOf(entrada);
        Set<String> ids = new HashSet<>();
        for (ProcessoUrbano p : carga) if (p == null || !ids.add(p.id())) throw new IllegalArgumentException("Processo nulo ou ID duplicado");
        List<ProcessoUrbano> chegada = carga.stream().sorted(Comparator.comparingInt(ProcessoUrbano::tempoChegada).thenComparingInt(ProcessoUrbano::ordemCadastro)).toList();
        Map<String,Integer> restante = new HashMap<>(), primeira = new HashMap<>(), conclusao = new HashMap<>();
        for (ProcessoUrbano p : carga) restante.put(p.id(), p.burst());
        ArrayDeque<ProcessoUrbano> prontos = new ArrayDeque<>();
        List<TrechoExecucao> gantt = new ArrayList<>();
        int relogio=0, indice=0, trocas=0; ProcessoUrbano anterior=null;
        while (conclusao.size() < carga.size()) {
            while (indice < chegada.size() && chegada.get(indice).tempoChegada() <= relogio) prontos.addLast(chegada.get(indice++));
            if (prontos.isEmpty()) {
                if (indice >= chegada.size()) break;
                int proxima=chegada.get(indice).tempoChegada();
                if (proxima > relogio) { gantt.add(TrechoExecucao.ocioso(relogio,proxima)); relogio=proxima; }
                anterior=null; continue;
            }
            ProcessoUrbano atual=prontos.removeFirst();
            if (anterior != null && !anterior.id().equals(atual.id()) && custoTrocaContexto > 0) {
                int fim=Math.addExact(relogio,custoTrocaContexto); gantt.add(TrechoExecucao.trocaContexto(relogio,fim)); relogio=fim; trocas++;
                while (indice < chegada.size() && chegada.get(indice).tempoChegada() <= relogio) prontos.addLast(chegada.get(indice++));
            }
            primeira.putIfAbsent(atual.id(),relogio);
            int executado=Math.min(quantum,restante.get(atual.id())); int fim=Math.addExact(relogio,executado);
            gantt.add(TrechoExecucao.doProcesso(atual,relogio,fim)); relogio=fim; restante.put(atual.id(),restante.get(atual.id())-executado);
            while (indice < chegada.size() && chegada.get(indice).tempoChegada() <= relogio) prontos.addLast(chegada.get(indice++));
            if (restante.get(atual.id()) == 0) conclusao.put(atual.id(),relogio); else prontos.addLast(atual);
            anterior=atual;
        }
        List<ProcessoResultado> resultados=new ArrayList<>();
        for (ProcessoUrbano p:carga) resultados.add(ProcessoResultado.criar(p,primeira.get(p.id()),conclusao.get(p.id())));
        return new ResultadoEscalonamento(NOME,gantt,resultados,CalculadoraMetricas.calcular(resultados,gantt));
    }
}
