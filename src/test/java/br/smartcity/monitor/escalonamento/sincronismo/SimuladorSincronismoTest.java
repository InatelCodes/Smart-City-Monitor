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

    private static List<ProcessoUrbano> criarCarga(int quantidade) {
        TipoEvento[] tipos = TipoEvento.values();
        return IntStream.range(0, quantidade)
                .mapToObj(indice -> new ProcessoUrbano(
                        "P" + (indice + 1),
                        tipos[indice % tipos.length],
                        "Alerta urbano " + (indice + 1),
                        indice,
                        1,
                        indice
                ))
                .toList();
    }
}
