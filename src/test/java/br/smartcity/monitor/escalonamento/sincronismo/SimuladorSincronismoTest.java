package br.smartcity.monitor.escalonamento.sincronismo;

import br.smartcity.monitor.escalonamento.model.ProcessoUrbano;
import br.smartcity.monitor.model.TipoEvento;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimuladorSincronismoTest {

    @TempDir
    Path diretorioTemporario;

    @Test
    void mesmaCargaColideSemLockEFicaIntegraComLock() throws Exception {
        List<ProcessoUrbano> carga = criarCarga(12);
        SimuladorSincronismo simulador = new SimuladorSincronismo();

        ComparacaoSincronismo comparacao = simulador.comparar(
                carga,
                diretorioTemporario
        );
        ResultadoSincronismo semSincronismo = comparacao.semSincronismo();
        ResultadoSincronismo comSincronismo = comparacao.comSincronismo();

        assertTrue(Files.isRegularFile(semSincronismo.arquivo()));
        assertTrue(semSincronismo.houveColisao());
        assertTrue(semSincronismo.registrosCorrompidos() > 0);
        assertEquals(carga.size(), semSincronismo.registrosEsperados());

        assertTrue(Files.isRegularFile(comSincronismo.arquivo()));
        assertFalse(comSincronismo.houveColisao());
        assertEquals(carga.size(), comSincronismo.registrosValidos());
        assertEquals(
                carga.size(),
                comSincronismo.conteudoArquivo().lines().count()
        );
        assertTrue(
                comSincronismo.tempoMedioEsperaMs()
                        > semSincronismo.tempoMedioEsperaMs()
        );
    }

    @Test
    void compararUsaMedianaEAlternaOsModosSemDependerDaOrdem() throws Exception {
        List<ProcessoUrbano> carga = criarCarga(6);
        SimuladorSincronismo simulador = new SimuladorSincronismo();

        ComparacaoSincronismo comparacao = simulador.comparar(carga, diretorioTemporario, 5);

        assertTrue(comparacao.semSincronismo().houveColisao());
        assertFalse(comparacao.comSincronismo().houveColisao());
        assertTrue(comparacao.semSincronismo().duracaoMs() > 0);
        assertTrue(comparacao.comSincronismo().duracaoMs() > 0);
    }

    @Test
    void medirDevolveAMedianaDasDuracoesEDasEsperas() throws Exception {
        List<ProcessoUrbano> carga = criarCarga(4);
        Path arquivo = diretorioTemporario.resolve("unico.txt");
        ResultadoSincronismo medido = new SimuladorSincronismo()
                .medir(carga, ModoSincronizacao.COM_SINCRONISMO, arquivo, 3);

        assertEquals(4, medido.registrosValidos());
        assertTrue(medido.tempoMedioEsperaMs() > 0);
    }

    @Test
    void consolidarEscolheAMedianaIgnorandoUmaPrimeiraExecucaoLenta() {
        Path arquivo = diretorioTemporario.resolve("x.txt");
        // a primeira execução (fria) é 10x mais lenta que as demais, como no experimento real
        List<ResultadoSincronismo> execucoes = List.of(
                resultado(arquivo, 40.0, 0.5),
                resultado(arquivo, 4.5, 0.4),
                resultado(arquivo, 5.0, 0.6),
                resultado(arquivo, 4.8, 0.5),
                resultado(arquivo, 5.2, 0.7)
        );

        ResultadoSincronismo mediana = SimuladorSincronismo.consolidar(execucoes);

        assertEquals(5.0, mediana.duracaoMs(), 1e-9);
        assertEquals(0.5, mediana.tempoMedioEsperaMs(), 1e-9);
    }

    private static ResultadoSincronismo resultado(Path arquivo, double duracaoMs, double esperaMs) {
        return new ResultadoSincronismo(ModoSincronizacao.SEM_SINCRONISMO, arquivo, 3, 1, 2, duracaoMs, esperaMs, "x\n");
    }

    private static List<ProcessoUrbano> criarCarga(int quantidade) {
        TipoEvento[] tipos = TipoEvento.values();
        return IntStream.range(0, quantidade)
                .mapToObj(indice -> new ProcessoUrbano(
                        "P" + (indice + 1),
                        tipos[indice % tipos.length],
                        "Alerta urbano " + (indice + 1),
                        indice * 5_000,
                        40_000,
                        indice
                ))
                .toList();
    }
}
