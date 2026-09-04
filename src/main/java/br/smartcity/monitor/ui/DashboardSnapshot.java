package br.smartcity.monitor.ui;

public record DashboardSnapshot(
        int eventosGerados,
        int eventosProcessados,
        int eventosPendentes,
        int threadsAtivas,
        double taxaProcessamento,
        double tempoMedioRespostaMs,
        double tempoDecorridoSegundos,
        int eventosTransito,
        int eventosClima,
        int eventosEnergia,
        int eventosQualidadeAr
) {

    public static DashboardSnapshot vazio() {
        return new DashboardSnapshot(
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0
        );
    }
}
