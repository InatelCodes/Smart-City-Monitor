package br.smartcity.monitor.ui;

import br.smartcity.monitor.config.ConfiguracaoExperimento;

import java.time.LocalDateTime;

/** Resumo imutável mantido na aba de resultados. */
public record ExperimentoResultado(
        int numero,
        LocalDateTime finalizadoEm,
        ConfiguracaoExperimento configuracao,
        double duracaoSegundos,
        int eventosGerados,
        int eventosProcessados,
        int eventosPendentes,
        double taxaProcessamento,
        double tempoMedioRespostaMs
) {
}
