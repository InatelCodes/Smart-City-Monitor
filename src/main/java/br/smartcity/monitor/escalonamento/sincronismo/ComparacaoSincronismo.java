package br.smartcity.monitor.escalonamento.sincronismo;

import java.util.Objects;

/** Resultados dos dois modos executados sobre a mesma carga congelada. */
public record ComparacaoSincronismo(
        ResultadoSincronismo semSincronismo,
        ResultadoSincronismo comSincronismo
) {
    public ComparacaoSincronismo {
        semSincronismo = Objects.requireNonNull(
                semSincronismo,
                "semSincronismo"
        );
        comSincronismo = Objects.requireNonNull(
                comSincronismo,
                "comSincronismo"
        );

        if (semSincronismo.modo() != ModoSincronizacao.SEM_SINCRONISMO
                || comSincronismo.modo()
                != ModoSincronizacao.COM_SINCRONISMO) {
            throw new IllegalArgumentException(
                    "Os resultados devem representar os dois modos"
            );
        }
        if (semSincronismo.registrosEsperados()
                != comSincronismo.registrosEsperados()) {
            throw new IllegalArgumentException(
                    "Os modos devem usar a mesma carga"
            );
        }
    }
}
