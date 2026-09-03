package br.smartcity.monitor;

import br.smartcity.monitor.central.CentralMonitoramento;
import br.smartcity.monitor.metrics.Metricas;
import br.smartcity.monitor.model.Evento;
import br.smartcity.monitor.sensor.SensorClima;
import br.smartcity.monitor.sensor.SensorEnergia;
import br.smartcity.monitor.sensor.SensorQualidadeAr;
import br.smartcity.monitor.sensor.SensorTransito;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/** Mantém o modo texto útil para automação e medições sem ambiente gráfico. */
public final class ConsoleMain {

    private ConsoleMain() {
    }

    public static void executar(String[] args) throws InterruptedException {
        int quantidadeThreads = lerInteiro(args, 0, 2, "quantidade de threads");
        int taxaTotalEventos = lerInteiro(args, 1, 20, "taxa de eventos");
        int duracaoSegundos = lerInteiro(args, 2, 10, "duracao");
        int tempoProcessamentoMs = lerInteiro(args, 3, 100, "tempo de processamento");

        if (taxaTotalEventos <= 0 || duracaoSegundos <= 0 || tempoProcessamentoMs < 0) {
            throw new IllegalArgumentException(
                    "taxa e duracao devem ser positivas; tempo de processamento nao pode ser negativo");
        }

        BlockingQueue<Evento> fila = new LinkedBlockingQueue<>();
        Metricas metricas = new Metricas();
        CentralMonitoramento central = new CentralMonitoramento(
                fila, metricas, quantidadeThreads, tempoProcessamentoMs);
        long intervaloPorSensorMs = Math.max(1, Math.round(4_000.0 / taxaTotalEventos));

        List<Thread> sensores = List.of(
                new Thread(new SensorTransito(fila, intervaloPorSensorMs, metricas), "Sensor-Trânsito"),
                new Thread(new SensorClima(fila, intervaloPorSensorMs, metricas), "Sensor-Clima"),
                new Thread(new SensorEnergia(fila, intervaloPorSensorMs, metricas), "Sensor-Energia"),
                new Thread(new SensorQualidadeAr(fila, intervaloPorSensorMs, metricas), "Sensor-Ar")
        );

        System.out.printf(
                "Experimento: %d thread(s), ~%d evento(s)/s, %ds, processamento de %dms%n",
                quantidadeThreads, taxaTotalEventos, duracaoSegundos, tempoProcessamentoMs);
        central.iniciar();
        sensores.forEach(Thread::start);

        try {
            Thread.sleep(TimeUnit.SECONDS.toMillis(duracaoSegundos));
        } finally {
            sensores.forEach(Thread::interrupt);
            for (Thread sensor : sensores) {
                sensor.join();
            }
        }

        int gerados = metricas.getEventosGerados();
        int pendentesAoFim = central.getEventosPendentes();
        try {
            if (!central.aguardarEventosProcessados(gerados, 60, TimeUnit.SECONDS)) {
                System.out.println("Tempo limite atingido antes de esvaziar toda a fila.");
            }
        } finally {
            central.encerrar();
            metricas.finalizarColeta();
        }

        System.out.println("\nThreads configuradas: " + central.getQuantidadeThreadsConfiguradas());
        System.out.println("Eventos pendentes ao fim da geração: " + pendentesAoFim);
        System.out.println("Eventos ainda na fila: " + central.getEventosPendentes());
        metricas.exibirResumo();
    }

    private static int lerInteiro(String[] args, int posicao, int padrao, String nome) {
        if (args.length <= posicao) {
            return padrao;
        }
        try {
            return Integer.parseInt(args[posicao]);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(nome + " deve ser um numero inteiro", e);
        }
    }
}
