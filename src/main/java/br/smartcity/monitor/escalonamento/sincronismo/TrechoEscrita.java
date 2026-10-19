package br.smartcity.monitor.escalonamento.sincronismo;

/** Intervalo [início, fim) em que um processo executa a região crítica (escrita no arquivo). */
public record TrechoEscrita(String processoId, int inicio, int fim) {

    public TrechoEscrita {
        if (processoId == null || processoId.isBlank()) {
            throw new IllegalArgumentException("O processo da escrita é obrigatório");
        }
        if (inicio < 0 || fim <= inicio) {
            throw new IllegalArgumentException("Intervalo de escrita inválido");
        }
    }

    public int duracao() {
        return fim - inicio;
    }
}
