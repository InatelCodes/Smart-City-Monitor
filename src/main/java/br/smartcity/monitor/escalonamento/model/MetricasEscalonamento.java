package br.smartcity.monitor.escalonamento.model;

/** Métricas agregadas de uma execução completa do escalonador. */
public record MetricasEscalonamento(
        double tempoMedioEspera,
        double tempoMedioRetorno,
        double tempoMedioResposta,
        int makespan,
        int trocasContexto,
        int tempoTrocasContexto,
        double utilizacaoCpuPercentual,
        double vazao
) {
    public MetricasEscalonamento {
        validarNaoNegativo(tempoMedioEspera, "tempo médio de espera");
        validarNaoNegativo(tempoMedioRetorno, "tempo médio de retorno");
        validarNaoNegativo(tempoMedioResposta, "tempo médio de resposta");
        validarNaoNegativo(utilizacaoCpuPercentual, "utilização da CPU");
        validarNaoNegativo(vazao, "vazão");

        if (makespan < 0 || trocasContexto < 0 || tempoTrocasContexto < 0) {
            throw new IllegalArgumentException(
                    "Makespan, trocas e custo de contexto não podem ser negativos"
            );
        }
        if (utilizacaoCpuPercentual > 100.0) {
            throw new IllegalArgumentException(
                    "A utilização da CPU não pode ultrapassar 100%"
            );
        }
    }

    private static void validarNaoNegativo(double valor, String nome) {
        if (!Double.isFinite(valor) || valor < 0) {
            throw new IllegalArgumentException(
                    "Valor inválido para " + nome
            );
        }
    }
}
