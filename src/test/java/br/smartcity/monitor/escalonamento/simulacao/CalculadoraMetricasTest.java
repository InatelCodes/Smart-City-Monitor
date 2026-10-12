package br.smartcity.monitor.escalonamento.simulacao;

import br.smartcity.monitor.escalonamento.model.MetricasEscalonamento;
import br.smartcity.monitor.escalonamento.model.ProcessoResultado;
import br.smartcity.monitor.escalonamento.model.ProcessoUrbano;
import br.smartcity.monitor.escalonamento.model.TrechoExecucao;
import br.smartcity.monitor.model.TipoEvento;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CalculadoraMetricasTest {

    @Test
    void ociosidadeReduzUtilizacaoENaoContaComoTrocaDeContexto() {
        ProcessoUrbano p1 = new ProcessoUrbano(
                "P1",
                TipoEvento.TRANSITO,
                "Fluxo intenso de veículos",
                0,
                2,
                0
        );
        ProcessoUrbano p2 = new ProcessoUrbano(
                "P2",
                TipoEvento.CLIMA,
                "Chuva detectada",
                5,
                1,
                1
        );

        MetricasEscalonamento metricas = CalculadoraMetricas.calcular(
                List.of(
                        ProcessoResultado.criar(p1, 0, 2),
                        ProcessoResultado.criar(p2, 5, 6)
                ),
                List.of(
                        TrechoExecucao.doProcesso(p1, 0, 2),
                        TrechoExecucao.ocioso(2, 5),
                        TrechoExecucao.doProcesso(p2, 5, 6)
                )
        );

        assertEquals(6, metricas.makespan());
        assertEquals(50.0, metricas.utilizacaoCpuPercentual());
        assertEquals(0, metricas.trocasContexto());
        assertEquals(2.0 / 6.0, metricas.vazao());
    }

    @Test
    void fatiasConsecutivasDoMesmoProcessoNaoGeramTroca() {
        ProcessoUrbano p1 = new ProcessoUrbano(
                "P1",
                TipoEvento.ENERGIA,
                "Consumo de energia elevado",
                0,
                4,
                0
        );
        ProcessoUrbano p2 = new ProcessoUrbano(
                "P2",
                TipoEvento.QUALIDADE_AR,
                "Nível de poluentes elevado",
                0,
                1,
                1
        );

        MetricasEscalonamento metricas = CalculadoraMetricas.calcular(
                List.of(
                        ProcessoResultado.criar(p1, 0, 4),
                        ProcessoResultado.criar(p2, 4, 5)
                ),
                List.of(
                        TrechoExecucao.doProcesso(p1, 0, 2),
                        TrechoExecucao.doProcesso(p1, 2, 4),
                        TrechoExecucao.doProcesso(p2, 4, 5)
                )
        );

        assertEquals(1, metricas.trocasContexto());
    }
}
