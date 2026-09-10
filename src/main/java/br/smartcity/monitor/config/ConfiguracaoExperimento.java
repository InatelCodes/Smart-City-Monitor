package br.smartcity.monitor.config;

/** Parâmetros imutáveis de uma execução do monitor. */
public record ConfiguracaoExperimento(
        int quantidadeThreads,
        int tempoProcessamentoMs
) {
    public ConfiguracaoExperimento {
        if (quantidadeThreads < 1 || quantidadeThreads > 16) {
            throw new IllegalArgumentException(
                    "A quantidade de threads deve estar entre 1 e 16"
            );
        }

        if (tempoProcessamentoMs < 0 || tempoProcessamentoMs > 5_000) {
            throw new IllegalArgumentException(
                    "O tempo de processamento deve estar entre 0 e 5.000 ms"
            );
        }
    }
}
