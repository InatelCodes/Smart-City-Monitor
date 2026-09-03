package br.smartcity.monitor.ui;

/** Leitura consistente dos valores exibidos em uma atualização da interface. */
public record DashboardSnapshot(
        int eventosGerados,
        int eventosProcessados,
        int eventosPendentes,
        int threadsAtivas,
        double taxaGeracao,
        double taxaProcessamento,
        double tempoMedioRespostaMs,
        double tempoDecorridoSegundos
) {
    public static DashboardSnapshot vazio() {
        return new DashboardSnapshot(0, 0, 0, 0, 0, 0, 0, 0);
    }
}
