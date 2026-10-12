package br.smartcity.monitor.escalonamento.model;

import br.smartcity.monitor.model.TipoEvento;

import java.util.Objects;

/** Intervalo semiaberto [início, fim) exibido no diagrama de Gantt. */
public record TrechoExecucao(
        String processoId,
        TipoEvento tipoEvento,
        int inicio,
        int fim,
        TipoTrechoExecucao tipo
) {
    public static final String CPU_OCIOSA = "OCIOSA";
    public static final String TROCA_CONTEXTO = "TROCA_CONTEXTO";

    public TrechoExecucao {
        tipo = Objects.requireNonNull(tipo, "O tipo do trecho é obrigatório");
        if (inicio < 0 || fim <= inicio) {
            throw new IllegalArgumentException(
                    "O trecho deve possuir início não negativo e fim maior que o início"
            );
        }

        if (tipo == TipoTrechoExecucao.PROCESSO) {
            if (processoId == null || processoId.isBlank()) {
                throw new IllegalArgumentException(
                        "O processo do trecho é obrigatório"
                );
            }
            processoId = processoId.trim();
            tipoEvento = Objects.requireNonNull(
                    tipoEvento,
                    "O tipo do processo é obrigatório"
            );
        } else {
            processoId = tipo == TipoTrechoExecucao.OCIOSO
                    ? CPU_OCIOSA
                    : TROCA_CONTEXTO;
            tipoEvento = null;
        }
    }

    public static TrechoExecucao doProcesso(
            ProcessoUrbano processo,
            int inicio,
            int fim
    ) {
        Objects.requireNonNull(processo, "processo");
        return new TrechoExecucao(
                processo.id(),
                processo.tipoEvento(),
                inicio,
                fim,
                TipoTrechoExecucao.PROCESSO
        );
    }

    public static TrechoExecucao ocioso(int inicio, int fim) {
        return new TrechoExecucao(
                CPU_OCIOSA,
                null,
                inicio,
                fim,
                TipoTrechoExecucao.OCIOSO
        );
    }

    public static TrechoExecucao trocaContexto(int inicio, int fim) {
        return new TrechoExecucao(
                TROCA_CONTEXTO,
                null,
                inicio,
                fim,
                TipoTrechoExecucao.TROCA_CONTEXTO
        );
    }

    public boolean ocioso() {
        return tipo == TipoTrechoExecucao.OCIOSO;
    }

    public boolean trocaContexto() {
        return tipo == TipoTrechoExecucao.TROCA_CONTEXTO;
    }

    public int duracao() {
        return fim - inicio;
    }
}
