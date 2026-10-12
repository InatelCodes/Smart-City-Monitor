package br.smartcity.monitor.escalonamento.sincronismo;

import br.smartcity.monitor.escalonamento.model.ProcessoUrbano;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Recurso físico compartilhado. Cada registro é escrito em três fragmentos
 * para tornar a região crítica observável quando o lock está desligado.
 */
final class RegistroCompartilhado implements AutoCloseable {

    private static final OpenOption[] OPCOES = {
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE
    };

    private final FileChannel canal;
    private final ReentrantLock lock = new ReentrantLock(true);
    private final LongAdder esperaTotalNanos = new LongAdder();
    private final LongAdder aquisicoesDoLock = new LongAdder();

    RegistroCompartilhado(Path arquivo) throws IOException {
        canal = FileChannel.open(arquivo, OPCOES);
    }

    void registrar(
            ProcessoUrbano processo,
            ModoSincronizacao modo,
            CyclicBarrier barreiraSemSincronismo
    ) throws IOException, InterruptedException, BrokenBarrierException,
            TimeoutException {
        if (modo == ModoSincronizacao.COM_SINCRONISMO) {
            registrarComLock(processo);
            return;
        }
        escreverFragmentos(processo, barreiraSemSincronismo);
    }

    private void registrarComLock(ProcessoUrbano processo)
            throws IOException, InterruptedException {
        long inicioEspera = System.nanoTime();
        lock.lockInterruptibly();
        esperaTotalNanos.add(System.nanoTime() - inicioEspera);
        aquisicoesDoLock.increment();

        try {
            escreverFragmentos(processo, null);
        } catch (BrokenBarrierException | TimeoutException impossivel) {
            throw new IllegalStateException(
                    "Não existe barreira no modo sincronizado",
                    impossivel
            );
        } finally {
            lock.unlock();
        }
    }

    private void escreverFragmentos(
            ProcessoUrbano processo,
            CyclicBarrier barreira
    ) throws IOException, InterruptedException, BrokenBarrierException,
            TimeoutException {
        String[] fragmentos = criarFragmentos(processo);

        for (int i = 0; i < fragmentos.length; i++) {
            escrever(fragmentos[i]);

            if (barreira != null && i < fragmentos.length - 1) {
                barreira.await(5, TimeUnit.SECONDS);
            }

            if (i < fragmentos.length - 1) {
                Thread.sleep(1);
            }
        }
    }

    void persistir() throws IOException {
        canal.force(true);
    }

    double getTempoMedioEsperaMs() {
        long aquisicoes = aquisicoesDoLock.sum();
        if (aquisicoes == 0) {
            return 0;
        }
        return esperaTotalNanos.sum() / 1_000_000.0 / aquisicoes;
    }

    static String linhaCompleta(ProcessoUrbano processo) {
        String[] fragmentos = criarFragmentos(processo);
        return fragmentos[0] + fragmentos[1] + fragmentos[2];
    }

    private static String[] criarFragmentos(ProcessoUrbano processo) {
        return new String[]{
                "INICIO|" + sanitizar(processo.id()) + "|",
                processo.tipoEvento() + "|" + sanitizar(processo.descricao()),
                "|FIM\n"
        };
    }

    private static String sanitizar(String valor) {
        return valor
                .replace("\\", "\\\\")
                .replace("|", "\\|")
                .replace("\r", "\\r")
                .replace("\n", "\\n");
    }

    private void escrever(String fragmento) throws IOException {
        ByteBuffer dados = StandardCharsets.UTF_8.encode(fragmento);
        while (dados.hasRemaining()) {
            canal.write(dados);
        }
    }

    @Override
    public void close() throws IOException {
        canal.close();
    }
}
