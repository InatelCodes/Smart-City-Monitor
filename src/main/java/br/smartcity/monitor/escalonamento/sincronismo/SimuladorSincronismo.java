package br.smartcity.monitor.escalonamento.sincronismo;

import br.smartcity.monitor.escalonamento.model.ProcessoUrbano;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Executa uma thread de sensor por processo sobre o mesmo arquivo físico.
 */
public final class SimuladorSincronismo {

    public ComparacaoSincronismo comparar(
            List<ProcessoUrbano> processos,
            Path diretorio
    ) throws IOException, InterruptedException {
        List<ProcessoUrbano> carga = validarCarga(processos);
        Path diretorioSeguro = Objects.requireNonNull(diretorio, "diretorio")
                .toAbsolutePath()
                .normalize();

        ResultadoSincronismo semSincronismo = executar(
                carga,
                ModoSincronizacao.SEM_SINCRONISMO,
                diretorioSeguro.resolve("sem-sincronismo.txt")
        );
        ResultadoSincronismo comSincronismo = executar(
                carga,
                ModoSincronizacao.COM_SINCRONISMO,
                diretorioSeguro.resolve("com-sincronismo.txt")
        );
        return new ComparacaoSincronismo(semSincronismo, comSincronismo);
    }

    public ResultadoSincronismo executar(
            List<ProcessoUrbano> processos,
            ModoSincronizacao modo,
            Path arquivo
    ) throws IOException, InterruptedException {
        List<ProcessoUrbano> carga = validarCarga(processos);
        Objects.requireNonNull(modo, "modo");
        Path arquivoSeguro = Objects.requireNonNull(arquivo, "arquivo")
                .toAbsolutePath()
                .normalize();

        Path diretorio = arquivoSeguro.getParent();
        if (diretorio != null) {
            Files.createDirectories(diretorio);
        }

        CyclicBarrier barreira = modo == ModoSincronizacao.SEM_SINCRONISMO
                ? new CyclicBarrier(carga.size())
                : null;
        ExecutorService sensores = Executors.newFixedThreadPool(carga.size());
        long inicio = System.nanoTime();
        double esperaMedia;

        try (RegistroCompartilhado registro =
                     new RegistroCompartilhado(arquivoSeguro)) {
            List<? extends Future<?>> tarefas = carga.stream()
                    .map(processo -> sensores.submit(() -> {
                        registro.registrar(processo, modo, barreira);
                        return null;
                    }))
                    .toList();

            aguardar(tarefas);
            registro.persistir();
            esperaMedia = registro.getTempoMedioEsperaMs();
        } finally {
            sensores.shutdownNow();
        }

        double duracaoMs = (System.nanoTime() - inicio) / 1_000_000.0;
        String conteudo = Files.readString(
                arquivoSeguro,
                StandardCharsets.UTF_8
        );
        int validos = contarRegistrosValidos(carga, conteudo);

        return new ResultadoSincronismo(
                modo,
                arquivoSeguro,
                carga.size(),
                validos,
                carga.size() - validos,
                duracaoMs,
                esperaMedia,
                conteudo
        );
    }

    private static void aguardar(List<? extends Future<?>> tarefas)
            throws IOException, InterruptedException {
        for (Future<?> tarefa : tarefas) {
            try {
                tarefa.get();
            } catch (ExecutionException erro) {
                Throwable causa = erro.getCause();
                if (causa instanceof IOException io) {
                    throw io;
                }
                if (causa instanceof InterruptedException interrompida) {
                    Thread.currentThread().interrupt();
                    throw interrompida;
                }
                throw new IllegalStateException(
                        "Falha durante a escrita concorrente",
                        causa
                );
            }
        }
    }

    private static int contarRegistrosValidos(
            List<ProcessoUrbano> carga,
            String conteudo
    ) {
        Set<String> linhas = new HashSet<>(conteudo.lines().toList());
        int validos = 0;

        for (ProcessoUrbano processo : carga) {
            String esperado = RegistroCompartilhado
                    .linhaCompleta(processo)
                    .stripTrailing();
            if (linhas.contains(esperado)) {
                validos++;
            }
        }
        return validos;
    }

    private static List<ProcessoUrbano> validarCarga(
            List<ProcessoUrbano> processos
    ) {
        Objects.requireNonNull(processos, "processos");
        if (processos.isEmpty()) {
            throw new IllegalArgumentException(
                    "A carga deve possuir pelo menos um processo"
            );
        }

        List<ProcessoUrbano> copia = List.copyOf(processos);
        Set<String> ids = new HashSet<>();
        for (ProcessoUrbano processo : copia) {
            if (!ids.add(processo.id())) {
                throw new IllegalArgumentException(
                        "ID de processo duplicado: " + processo.id()
                );
            }
        }
        return copia;
    }
}
