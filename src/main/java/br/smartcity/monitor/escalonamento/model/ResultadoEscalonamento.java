package br.smartcity.monitor.escalonamento.model;

import java.util.List;
import java.util.Objects;

/** Resultado imutável consumido pela futura interface comparativa. */
public record ResultadoEscalonamento(
        String algoritmo,
        List<TrechoExecucao> gantt,
        List<ProcessoResultado> processos,
        MetricasEscalonamento metricas
) {
    public ResultadoEscalonamento {
        if (algoritmo == null || algoritmo.isBlank()) {
            throw new IllegalArgumentException(
                    "O nome do algoritmo é obrigatório"
            );
        }
        algoritmo = algoritmo.trim();
        gantt = List.copyOf(Objects.requireNonNull(gantt, "gantt"));
        processos = List.copyOf(Objects.requireNonNull(processos, "processos"));
        metricas = Objects.requireNonNull(metricas, "metricas");
    }
}
