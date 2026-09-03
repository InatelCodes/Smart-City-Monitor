package br.smartcity.monitor;

import br.smartcity.monitor.central.CentralMonitoramento;
import br.smartcity.monitor.sensor.SensorClima;
import br.smartcity.monitor.sensor.SensorEnergia;
import br.smartcity.monitor.sensor.SensorQualidadeAr;
import br.smartcity.monitor.sensor.SensorTransito;
import br.smartcity.monitor.metrics.Metricas;
import br.smartcity.monitor.model.Evento;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class Main {

    public static void main(String[] args) throws InterruptedException {

        int quantidadeThreads = lerInteiro(args, 0, 2, "quantidade de threads");
        int taxaTotalEventos = lerInteiro(args, 1, 20, "taxa de eventos");
        int duracaoSegundos = lerInteiro(args, 2, 10, "duracao");
        int tempoProcessamentoMs = lerInteiro(args, 3, 100, "tempo de processamento");

        if (taxaTotalEventos <= 0 || duracaoSegundos <= 0 || tempoProcessamentoMs < 0) {
            throw new IllegalArgumentException(
                    "taxa e duracao devem ser positivas; tempo de processamento nao pode ser negativo"
            );
        }

        BlockingQueue<Evento> fila = new LinkedBlockingQueue<>();
        Metricas metricas = new Metricas();

        CentralMonitoramento central = new CentralMonitoramento(
                fila,
                metricas,
                quantidadeThreads,
                tempoProcessamentoMs
        );

        // Como existem quatro sensores, este intervalo produz aproximadamente a taxa total pedida.
        long intervaloPorSensorMs = Math.max(
                1,
                Math.round(4_000.0 / taxaTotalEventos)
        );

        SensorTransito sensorTransito =
                new SensorTransito(fila, intervaloPorSensorMs, metricas);

        SensorClima sensorClima =
                new SensorClima(fila, intervaloPorSensorMs, metricas);

        SensorEnergia sensorEnergia =
                new SensorEnergia(fila, intervaloPorSensorMs, metricas);

        SensorQualidadeAr sensorQualidadeAr =
                new SensorQualidadeAr(fila, intervaloPorSensorMs, metricas);

        List<Thread> threadsSensores = List.of(
                new Thread(sensorTransito, "Thread-Sensor-Transito"),
                new Thread(sensorClima, "Thread-Sensor-Clima"),
                new Thread(sensorEnergia, "Thread-Sensor-Energia"),
                new Thread(sensorQualidadeAr, "Thread-Sensor-Qualidade-Ar")
        );

        System.out.printf(
                "Experimento: %d thread(s), ~%d evento(s)/s, %ds, processamento de %dms%n",
                quantidadeThreads,
                taxaTotalEventos,
                duracaoSegundos,
                tempoProcessamentoMs
        );

        central.iniciar();
        threadsSensores.forEach(Thread::start);

        try {
            Thread.sleep(TimeUnit.SECONDS.toMillis(duracaoSegundos));
        } finally {
            threadsSensores.forEach(Thread::interrupt);
            for (Thread threadSensor : threadsSensores) {
                threadSensor.join();
            }
        }

        int eventosGerados = metricas.getEventosGerados();
        int pendentesAoFimDaGeracao = central.getEventosPendentes();

        try {
            boolean concluiuTodos = central.aguardarEventosProcessados(
                    eventosGerados,
                    60,
                    TimeUnit.SECONDS
            );

            if (!concluiuTodos) {
                System.out.println("Tempo limite atingido antes de esvaziar toda a fila.");
            }
        } finally {
            central.encerrar();
        }

        System.out.println("\nThreads configuradas: " + central.getQuantidadeThreadsConfiguradas());
        System.out.println("Eventos pendentes ao fim da geracao: " + pendentesAoFimDaGeracao);
        System.out.println("Eventos ainda na fila: " + central.getEventosPendentes());

        metricas.exibirResumo();
    }

    private static int lerInteiro(
            String[] args,
            int posicao,
            int valorPadrao,
            String nome
    ) {
        if (args.length <= posicao) {
            return valorPadrao;
        }

        try {
            return Integer.parseInt(args[posicao]);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(nome + " deve ser um numero inteiro", e);
        }
    }
}
