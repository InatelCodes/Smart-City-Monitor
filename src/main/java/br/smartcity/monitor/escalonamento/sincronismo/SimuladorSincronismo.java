package br.smartcity.monitor.escalonamento.sincronismo;

import br.smartcity.monitor.escalonamento.model.ProcessoUrbano;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
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
 *
 * <p>Tempos em milissegundos de relógio variam entre execuções (JIT, cache de disco,
 * escalonador do sistema operacional). Para que a comparação entre os modos não dependa
 * de quem rodou primeiro, {@link #comparar} aquece a JVM, alterna os modos a cada rodada
 * e usa a <b>mediana</b> de várias execuções. Veja a seção "Por que o valor sem sincronismo
 * às vezes é maior" do README.
 */
public final class SimuladorSincronismo {

    /** Execuções descartadas antes de medir (carga de classes, JIT, cache de disco). */
    public static final int AQUECIMENTOS_PADRAO = 3;

    /** Execuções medidas; o resultado publicado usa a mediana delas. */
    public static final int REPETICOES_PADRAO = 15;

    public ComparacaoSincronismo comparar(
            List<ProcessoUrbano> processos,
            Path diretorio
    ) throws IOException, InterruptedException {
        return comparar(processos, diretorio, REPETICOES_PADRAO);
    }

    /**
     * Compara os dois modos com a mesma carga. Os modos são alternados a cada rodada
     * (sem, com, sem, com...) para que oscilações do sistema afetem ambos igualmente.
     */
    public ComparacaoSincronismo comparar(
            List<ProcessoUrbano> processos,
            Path diretorio,
            int repeticoes
    ) throws IOException, InterruptedException {
        List<ProcessoUrbano> carga = validarCarga(processos);
        validarRepeticoes(repeticoes);
        Path diretorioSeguro = Objects.requireNonNull(diretorio, "diretorio")
                .toAbsolutePath()
                .normalize();
        Path arquivoSem = diretorioSeguro.resolve("sem-sincronismo.txt");
        Path arquivoCom = diretorioSeguro.resolve("com-sincronismo.txt");

        for (int i = 0; i < AQUECIMENTOS_PADRAO; i++) {
            executar(carga, ModoSincronizacao.SEM_SINCRONISMO, arquivoSem);
            executar(carga, ModoSincronizacao.COM_SINCRONISMO, arquivoCom);
        }
        List<ResultadoSincronismo> medidosSem = new ArrayList<>();
        List<ResultadoSincronismo> medidosCom = new ArrayList<>();
        for (int i = 0; i < repeticoes; i++) {
            medidosSem.add(executar(carga, ModoSincronizacao.SEM_SINCRONISMO, arquivoSem));
            medidosCom.add(executar(carga, ModoSincronizacao.COM_SINCRONISMO, arquivoCom));
        }
        return new ComparacaoSincronismo(consolidar(medidosSem), consolidar(medidosCom));
    }

    /** Executa um único modo com aquecimento e mediana de {@code repeticoes} execuções. */
    public ResultadoSincronismo medir(
            List<ProcessoUrbano> processos,
            ModoSincronizacao modo,
            Path arquivo,
            int repeticoes
    ) throws IOException, InterruptedException {
        List<ProcessoUrbano> carga = validarCarga(processos);
        Objects.requireNonNull(modo, "modo");
        validarRepeticoes(repeticoes);
        for (int i = 0; i < AQUECIMENTOS_PADRAO; i++) {
            executar(carga, modo, arquivo);
        }
        List<ResultadoSincronismo> medidos = new ArrayList<>();
        for (int i = 0; i < repeticoes; i++) {
            medidos.add(executar(carga, modo, arquivo));
        }
        return consolidar(medidos);
    }

    /** Mediana da duração e da espera; o conteúdo do arquivo é o da execução mediana. */
    static ResultadoSincronismo consolidar(List<ResultadoSincronismo> execucoes) {
        List<ResultadoSincronismo> porDuracao = execucoes.stream()
                .sorted(Comparator.comparingDouble(ResultadoSincronismo::duracaoMs))
                .toList();
        ResultadoSincronismo mediana = porDuracao.get(porDuracao.size() / 2);
        double[] esperas = execucoes.stream()
                .mapToDouble(ResultadoSincronismo::tempoMedioEsperaMs)
                .sorted()
                .toArray();
        return new ResultadoSincronismo(
                mediana.modo(),
                mediana.arquivo(),
                mediana.registrosEsperados(),
                mediana.registrosValidos(),
                mediana.registrosCorrompidos(),
                mediana.duracaoMs(),
                esperas[esperas.length / 2],
                mediana.conteudoArquivo()
        );
    }

    private static void validarRepeticoes(int repeticoes) {
        if (repeticoes < 1) {
            throw new IllegalArgumentException("As repetições devem ser pelo menos 1");
        }
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
