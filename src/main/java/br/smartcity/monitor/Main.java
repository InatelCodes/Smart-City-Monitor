package br.smartcity.monitor;

import br.smartcity.monitor.sensor.SensorTransito;
import br.smartcity.monitor.model.Evento;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class Main {

    public static void main(String[] args) throws InterruptedException {

        BlockingQueue<Evento> fila = new LinkedBlockingQueue<>();

        SensorTransito sensor = new SensorTransito(
                fila,
                2000
        );

        Thread threadSensor = new Thread(
                sensor,
                "Thread-Sensor-Transito"
        );

        threadSensor.start();

        Thread.sleep(10000);

        threadSensor.interrupt();

        threadSensor.join();

        System.out.println("\nEventos na fila: " + fila.size());
    }
}