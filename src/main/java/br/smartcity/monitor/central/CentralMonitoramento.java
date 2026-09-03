package br.smartcity.monitor.central;

import br.smartcity.monitor.metrics.Metricas;
import br.smartcity.monitor.model.Evento;
import br.smartcity.monitor.model.ResultadoProcessamento;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Coordena as threads consumidoras que compartilham a mesma BlockingQueue.
 */
public final class CentralMonitoramento implements AutoCloseable {

    public static final int MIN_THREADS = 1;
    public static final int MAX_THREADS = 4;

    private final BlockingQueue<Evento> fila;
    private final Metricas metricas;
    private final int quantidadeThreads;
    private final long tempoProcessamentoMs;
    private final List<ProcessadorEventos> processadores = new ArrayList<>();
    private final List<Thread> threads = new ArrayList<>();
    private final ConcurrentLinkedQueue<ResultadoProcessamento> resultados =
            new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<ResultadoProcessamento> resultadosRecentes =
            new ConcurrentLinkedQueue<>();
    private final AtomicLong totalProcessadoPelaCentral = new AtomicLong();
    private final AtomicInteger eventosEmProcessamento = new AtomicInteger();

    private boolean iniciada;
    private boolean encerrada;

    public CentralMonitoramento(
            BlockingQueue<Evento> fila,
            Metricas metricas,
            int quantidadeThreads,
            long tempoProcessamentoMs
    ) {
        if (quantidadeThreads < MIN_THREADS || quantidadeThreads > MAX_THREADS) {
            throw new IllegalArgumentException("quantidadeThreads deve estar entre 1 e 4");
        }
        if (tempoProcessamentoMs < 0) {
            throw new IllegalArgumentException("tempoProcessamentoMs nao pode ser negativo");
        }

        this.fila = Objects.requireNonNull(fila, "fila nao pode ser nula");
        this.metricas = Objects.requireNonNull(metricas, "metricas nao pode ser nula");
        this.quantidadeThreads = quantidadeThreads;
        this.tempoProcessamentoMs = tempoProcessamentoMs;
    }

    public synchronized void iniciar() {
        if (iniciada) {
            throw new IllegalStateException("a Central so pode ser iniciada uma vez");
        }

        iniciada = true;

        for (int i = 1; i <= quantidadeThreads; i++) {
            ProcessadorEventos processador = new ProcessadorEventos(
                    fila,
                    metricas,
                    tempoProcessamentoMs,
                    resultado -> {
                        resultados.add(resultado);
                        resultadosRecentes.add(resultado);
                        totalProcessadoPelaCentral.incrementAndGet();
                    },
                    eventosEmProcessamento
            );
            Thread thread = new Thread(processador, "Processador-Eventos-" + i);

            processadores.add(processador);
            threads.add(thread);
            thread.start();
        }
    }

    /**
     * Aguarda ate que esta Central tenha concluido a quantidade informada.
     * Retorna false se o tempo limite for atingido.
     */
    public boolean aguardarEventosProcessados(
            long quantidadeEsperada,
            long tempoLimite,
            TimeUnit unidade
    ) throws InterruptedException {
        if (quantidadeEsperada < 0 || tempoLimite < 0) {
            throw new IllegalArgumentException("quantidades e tempos nao podem ser negativos");
        }
        Objects.requireNonNull(unidade, "unidade nao pode ser nula");

        long limite = System.nanoTime() + unidade.toNanos(tempoLimite);

        while (totalProcessadoPelaCentral.get() < quantidadeEsperada) {
            long restante = limite - System.nanoTime();
            if (restante <= 0) {
                return false;
            }

            TimeUnit.NANOSECONDS.sleep(Math.min(restante, TimeUnit.MILLISECONDS.toNanos(10)));
        }

        return true;
    }

    /** Interrompe consumidores bloqueados e espera o termino de todos eles. */
    public void encerrar() throws InterruptedException {
        List<Thread> threadsParaEncerrar;

        synchronized (this) {
            if (!iniciada || encerrada) {
                return;
            }

            encerrada = true;
            processadores.forEach(ProcessadorEventos::solicitarEncerramento);
            threadsParaEncerrar = List.copyOf(threads);
            threadsParaEncerrar.forEach(Thread::interrupt);
        }

        boolean threadAtualInterrompida = false;
        for (Thread thread : threadsParaEncerrar) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                threadAtualInterrompida = true;
                threadsParaEncerrar.forEach(Thread::interrupt);
                break;
            }
        }

        if (threadAtualInterrompida) {
            Thread.currentThread().interrupt();
            throw new InterruptedException("interrompido ao encerrar a Central");
        }
    }

    @Override
    public void close() throws InterruptedException {
        encerrar();
    }

    public int getQuantidadeThreadsConfiguradas() {
        return quantidadeThreads;
    }

    public synchronized int getQuantidadeThreadsAtivas() {
        return (int) threads.stream().filter(Thread::isAlive).count();
    }

    public int getEventosPendentes() {
        return fila.size();
    }

    public int getEventosEmProcessamento() {
        return eventosEmProcessamento.get();
    }

    public long getTotalProcessadoPelaCentral() {
        return totalProcessadoPelaCentral.get();
    }

    public List<ResultadoProcessamento> getResultados() {
        return List.copyOf(resultados);
    }

    /**
     * Entrega somente resultados ainda não lidos pelo dashboard. A fila principal
     * de resultados permanece intacta para relatórios e testes.
     */
    public List<ResultadoProcessamento> drenarResultadosRecentes() {
        List<ResultadoProcessamento> novos = new ArrayList<>();
        ResultadoProcessamento resultado;
        while ((resultado = resultadosRecentes.poll()) != null) {
            novos.add(resultado);
        }
        return novos;
    }

    public synchronized boolean isEmExecucao() {
        return iniciada && !encerrada;
    }
}
