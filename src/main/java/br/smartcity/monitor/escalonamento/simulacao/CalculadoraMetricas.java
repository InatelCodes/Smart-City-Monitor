package br.smartcity.monitor.escalonamento.simulacao;

import br.smartcity.monitor.escalonamento.model.MetricasEscalonamento;
import br.smartcity.monitor.escalonamento.model.ProcessoResultado;
import br.smartcity.monitor.escalonamento.model.TrechoExecucao;
import br.smartcity.monitor.escalonamento.model.TipoTrechoExecucao;

import java.util.List;
import java.util.Objects;

/** Cálculo único das métricas usadas por todos os algoritmos. */
public final class CalculadoraMetricas {

    private CalculadoraMetricas() {
    }

    public static MetricasEscalonamento calcular(
            List<ProcessoResultado> resultados,
            List<TrechoExecucao> gantt
    ) {
        List<ProcessoResultado> resultadosSeguros =
                List.copyOf(Objects.requireNonNull(resultados, "resultados"));
        List<TrechoExecucao> ganttSeguro =
                List.copyOf(Objects.requireNonNull(gantt, "gantt"));

        if (resultadosSeguros.isEmpty() || ganttSeguro.isEmpty()) {
            throw new IllegalArgumentException(
                    "A carga e o diagrama de Gantt não podem estar vazios"
            );
        }

        double mediaEspera = resultadosSeguros.stream()
                .mapToInt(ProcessoResultado::tempoEspera)
                .average()
                .orElseThrow();
        double mediaRetorno = resultadosSeguros.stream()
                .mapToInt(ProcessoResultado::tempoRetorno)
                .average()
                .orElseThrow();
        double mediaResposta = resultadosSeguros.stream()
                .mapToInt(ProcessoResultado::tempoResposta)
                .average()
                .orElseThrow();

        int makespan = ganttSeguro.stream()
                .mapToInt(TrechoExecucao::fim)
                .max()
                .orElseThrow();
        int tempoOcupado = ganttSeguro.stream()
                .filter(trecho -> trecho.tipo() == TipoTrechoExecucao.PROCESSO)
                .mapToInt(TrechoExecucao::duracao)
                .sum();
        int tempoTrocasContexto = ganttSeguro.stream()
                .filter(TrechoExecucao::trocaContexto)
                .mapToInt(TrechoExecucao::duracao)
                .sum();

        int trocasContexto = contarTrocasContexto(ganttSeguro);
        double utilizacao = makespan == 0
                ? 0
                : tempoOcupado * 100.0 / makespan;
        double vazao = makespan == 0
                ? 0
                : resultadosSeguros.size() / (double) makespan;

        return new MetricasEscalonamento(
                mediaEspera,
                mediaRetorno,
                mediaResposta,
                makespan,
                trocasContexto,
                tempoTrocasContexto,
                utilizacao,
                vazao
        );
    }

    private static int contarTrocasContexto(List<TrechoExecucao> gantt) {
        String processoAnterior = null;
        int trocas = 0;

        for (TrechoExecucao atual : gantt) {
            if (atual.ocioso()) {
                processoAnterior = null;
                continue;
            }
            if (atual.trocaContexto()) {
                continue;
            }

            if (processoAnterior != null
                    && !processoAnterior.equals(atual.processoId())) {
                trocas++;
            }
            processoAnterior = atual.processoId();
        }

        return trocas;
    }
}
