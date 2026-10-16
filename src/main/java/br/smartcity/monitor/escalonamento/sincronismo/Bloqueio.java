package br.smartcity.monitor.escalonamento.sincronismo;

/** Intervalo [início, fim) em que um processo dorme esperando o lock do arquivo. */
public record Bloqueio(String processoId, int inicio, int fim) {

    public Bloqueio {
        if (processoId == null || processoId.isBlank()) {
            throw new IllegalArgumentException("O processo do bloqueio é obrigatório");
        }
        if (inicio < 0 || fim <= inicio) {
            throw new IllegalArgumentException("Intervalo de bloqueio inválido");
        }
    }

    public int duracao() {
        return fim - inicio;
    }
}
