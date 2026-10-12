package br.smartcity.monitor.escalonamento.algoritmo;

import br.smartcity.monitor.escalonamento.model.MetricasEscalonamento;
import br.smartcity.monitor.escalonamento.model.ProcessoResultado;
import br.smartcity.monitor.escalonamento.model.ProcessoUrbano;
import br.smartcity.monitor.escalonamento.model.ResultadoEscalonamento;
import br.smartcity.monitor.escalonamento.model.TrechoExecucao;
import br.smartcity.monitor.escalonamento.simulacao.CalculadoraMetricas;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** First-Come, First-Served: fila FIFO e execução sem preempção. */
public final class Fcfs implements AlgoritmoEscalonamento {

    public static final String NOME = "FCFS";

    private static final Comparator<ProcessoUrbano> ORDEM_DE_CHEGADA =
            Comparator.comparingInt(ProcessoUrbano::tempoChegada)
                    .thenComparingInt(ProcessoUrbano::ordemCadastro);

    private final int custoTrocaContexto;

    public Fcfs() {
        this(0);
    }

    public Fcfs(int custoTrocaContexto) {
        if (custoTrocaContexto < 0) {
            throw new IllegalArgumentException(
                    "O custo da troca de contexto não pode ser negativo"
            );
        }
        this.custoTrocaContexto = custoTrocaContexto;
    }

    @Override
    public ResultadoEscalonamento executar(List<ProcessoUrbano> processos) {
        List<ProcessoUrbano> ordenados = validarECopiar(processos).stream()
                .sorted(ORDEM_DE_CHEGADA)
                .toList();

        List<TrechoExecucao> gantt = new ArrayList<>();
        List<ProcessoResultado> resultados = new ArrayList<>();
        int relogio = 0;
        boolean processoAnterior = false;

        for (ProcessoUrbano processo : ordenados) {
            if (relogio < processo.tempoChegada()) {
                gantt.add(TrechoExecucao.ocioso(
                        relogio,
                        processo.tempoChegada()
                ));
                relogio = processo.tempoChegada();
                processoAnterior = false;
            } else if (processoAnterior && custoTrocaContexto > 0) {
                int fimTroca = somarTempo(relogio, custoTrocaContexto);
                gantt.add(TrechoExecucao.trocaContexto(relogio, fimTroca));
                relogio = fimTroca;
            }

            int inicio = relogio;
            relogio = somarTempo(relogio, processo.burst());

            gantt.add(TrechoExecucao.doProcesso(processo, inicio, relogio));
            resultados.add(ProcessoResultado.criar(
                    processo,
                    inicio,
                    relogio
            ));
            processoAnterior = true;
        }

        resultados.sort(
                Comparator.comparingInt(
                        resultado -> resultado.processo().ordemCadastro()
                )
        );
        MetricasEscalonamento metricas =
                CalculadoraMetricas.calcular(resultados, gantt);

        return new ResultadoEscalonamento(
                NOME,
                gantt,
                resultados,
                metricas
        );
    }

    private static int somarTempo(int atual, int incremento) {
        try {
            return Math.addExact(atual, incremento);
        } catch (ArithmeticException erro) {
            throw new IllegalArgumentException(
                    "A duração total da carga ultrapassa o limite suportado",
                    erro
            );
        }
    }

    private static List<ProcessoUrbano> validarECopiar(
            List<ProcessoUrbano> processos
    ) {
        Objects.requireNonNull(processos, "processos");
        if (processos.isEmpty()) {
            throw new IllegalArgumentException(
                    "A carga deve possuir pelo menos um processo"
            );
        }

        List<ProcessoUrbano> copia = List.copyOf(processos);
        Set<String> ids = new HashSet<>();

        for (ProcessoUrbano processo : copia) {
            Objects.requireNonNull(
                    processo,
                    "A carga não pode conter processos nulos"
            );
            if (!ids.add(processo.id())) {
                throw new IllegalArgumentException(
                        "ID de processo duplicado: " + processo.id()
                );
            }
        }

        return copia;
    }
}
