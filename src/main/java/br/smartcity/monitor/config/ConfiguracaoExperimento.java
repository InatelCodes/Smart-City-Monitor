package br.smartcity.monitor.config;

/** Parâmetros imutáveis de uma execução do monitor. */
public record ConfiguracaoExperimento(
        int quantidadeThreads,
        int taxaGeracao,
        int tempoProcessamentoMs
) {
    public ConfiguracaoExperimento {
        if (quantidadeThreads < 1 || quantidadeThreads > 4) {
            throw new IllegalArgumentException("A quantidade de threads deve estar entre 1 e 4");
        }
        if (taxaGeracao <= 0) {
            throw new IllegalArgumentException("A taxa de geração deve ser positiva");
        }
        if (tempoProcessamentoMs < 0) {
            throw new IllegalArgumentException("O tempo de processamento não pode ser negativo");
        }
    }
}
