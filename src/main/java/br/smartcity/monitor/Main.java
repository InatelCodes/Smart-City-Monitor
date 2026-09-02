package br.smartcity.monitor;

import br.smartcity.monitor.sensor.SensorClima;
import br.smartcity.monitor.sensor.SensorEnergia;
import br.smartcity.monitor.sensor.SensorQualidadeAr;
import br.smartcity.monitor.sensor.SensorTransito;
import br.smartcity.monitor.model.Evento;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class Main {

    public static void main(String[] args) throws InterruptedException {

        BlockingQueue<Evento> fila = new LinkedBlockingQueue<>();

        // Criando os quatro sensores
        SensorTransito sensorTransito = new SensorTransito(fila, 2000);
        SensorClima sensorClima = new SensorClima(fila, 2000);
        SensorEnergia sensorEnergia = new SensorEnergia(fila, 2000);
        SensorQualidadeAr sensorQualidadeAr =
                new SensorQualidadeAr(fila, 2000);

        // Criando uma Thread para cada sensor
        Thread threadTransito =
                new Thread(sensorTransito, "Thread-Sensor-Transito");

        Thread threadClima =
                new Thread(sensorClima, "Thread-Sensor-Clima");

        Thread threadEnergia =
                new Thread(sensorEnergia, "Thread-Sensor-Energia");

        Thread threadQualidadeAr =
                new Thread(sensorQualidadeAr, "Thread-Sensor-Qualidade-Ar");

        // Iniciando as Threads
        threadTransito.start();
        threadClima.start();
        threadEnergia.start();
        threadQualidadeAr.start();

        // Deixa os sensores funcionando por 10 segundos
        Thread.sleep(10000);

        // Interrompe as Threads
        threadTransito.interrupt();
        threadClima.interrupt();
        threadEnergia.interrupt();
        threadQualidadeAr.interrupt();

        // Aguarda todas terminarem
        threadTransito.join();
        threadClima.join();
        threadEnergia.join();
        threadQualidadeAr.join();

        System.out.println("\nEventos na fila: " + fila.size());
    }
}