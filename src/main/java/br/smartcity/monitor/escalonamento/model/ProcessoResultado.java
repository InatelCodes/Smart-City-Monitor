package br.smartcity.monitor.escalonamento.model;

import java.util.Objects;

/** Métricas finais de um processo urbano. */
public record ProcessoResultado(
        ProcessoUrbano processo,
        int primeiraExecucao,
        int conclusao,
        int tempoEspera,
        int tempoRetorno,
        int tempoResposta
) {
    public ProcessoResultado {
        processo = Objects.requireNonNull(processo, "processo");

        if (primeiraExecucao < processo.tempoChegada()) {
            throw new IllegalArgumentException(
                    "A primeira execução não pode ocorrer antes da chegada"
            );
        }
        if (conclusao < primeiraExecucao) {
            throw new IllegalArgumentException(
                    "A conclusão não pode ocorrer antes da primeira execução"
            );
        }
        if (tempoRetorno != conclusao - processo.tempoChegada()) {
            throw new IllegalArgumentException("Tempo de retorno inconsistente");
        }
        if (tempoEspera != tempoRetorno - processo.burst()) {
            throw new IllegalArgumentException("Tempo de espera inconsistente");
        }
        if (tempoResposta != primeiraExecucao - processo.tempoChegada()) {
            throw new IllegalArgumentException("Tempo de resposta inconsistente");
        }
        if (tempoEspera < 0) {
            throw new IllegalArgumentException(
                    "O tempo de espera não pode ser negativo"
            );
        }
    }

    public static ProcessoResultado criar(
            ProcessoUrbano processo,
            int primeiraExecucao,
            int conclusao
    ) {
        Objects.requireNonNull(processo, "processo");
        int retorno = conclusao - processo.tempoChegada();
        return new ProcessoResultado(
                processo,
                primeiraExecucao,
                conclusao,
                retorno - processo.burst(),
                retorno,
                primeiraExecucao - processo.tempoChegada()
        );
    }
}
