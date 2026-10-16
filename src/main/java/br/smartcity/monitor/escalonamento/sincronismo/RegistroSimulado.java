package br.smartcity.monitor.escalonamento.sincronismo;

/** Situação do registro de um processo no arquivo compartilhado simulado. */
public record RegistroSimulado(String processoId, String linhaEsperada, boolean integro) {
}
