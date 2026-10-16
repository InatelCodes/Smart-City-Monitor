package br.smartcity.monitor.escalonamento.sincronismo;

/** Políticas de escalonamento suportadas pela simulação integrada. */
public enum PoliticaEscalonamento {
    FCFS("FCFS"),
    ROUND_ROBIN("Round Robin");

    private final String nome;

    PoliticaEscalonamento(String nome) {
        this.nome = nome;
    }

    public String nome() {
        return nome;
    }
}
