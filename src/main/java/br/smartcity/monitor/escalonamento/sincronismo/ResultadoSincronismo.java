package br.smartcity.monitor.escalonamento.sincronismo;

import java.nio.file.Path;
import java.util.Objects;

/** Resultado imutável de uma execução concorrente sobre o arquivo físico. */
public record ResultadoSincronismo(
        ModoSincronizacao modo,
        Path arquivo,
        int registrosEsperados,
        int registrosValidos,
        int registrosCorrompidos,
        double duracaoMs,
        double tempoMedioEsperaMs,
        String conteudoArquivo
) {
    public ResultadoSincronismo {
        modo = Objects.requireNonNull(modo, "modo");
        arquivo = Objects.requireNonNull(arquivo, "arquivo")
                .toAbsolutePath()
                .normalize();
        conteudoArquivo = Objects.requireNonNull(
                conteudoArquivo,
                "conteudoArquivo"
        );

        if (registrosEsperados <= 0
                || registrosValidos < 0
                || registrosCorrompidos < 0
                || registrosValidos + registrosCorrompidos
                != registrosEsperados) {
            throw new IllegalArgumentException(
                    "Contadores de registros inconsistentes"
            );
        }
        if (!Double.isFinite(duracaoMs)
                || duracaoMs < 0
                || !Double.isFinite(tempoMedioEsperaMs)
                || tempoMedioEsperaMs < 0) {
            throw new IllegalArgumentException(
                    "Tempos do experimento não podem ser negativos"
            );
        }
    }

    public boolean houveColisao() {
        return registrosCorrompidos > 0;
    }
}
