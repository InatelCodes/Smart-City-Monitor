package br.smartcity.monitor.escalonamento.ui;

import br.smartcity.monitor.model.TipoEvento;

import java.util.List;

/**
 * Cenários prontos (carga + parâmetros) usados na apresentação e na documentação.
 * Os tempos estão em unidades de tempo (ut) abstratas, em escala de dezenas de milhares.
 */
public enum CenarioExemplo {

    COLISAO_NO_ROUND_ROBIN(
            "A · Colisão no Round Robin",
            "Sem lock, a preempção corta a escrita do P2 e o P3 escreve dentro dela. Com lock, "
                    + "os registros ficam íntegros, mas o P3 espera e os tempos pioram.",
            10_000, 500, 15_000,
            List.of(
                    new ProcessoModelo("P1", TipoEvento.TRANSITO, "Congestionamento detectado", 0, 40_000),
                    new ProcessoModelo("P2", TipoEvento.ENERGIA, "Pico de consumo detectado", 5_000, 20_000),
                    new ProcessoModelo("P3", TipoEvento.QUALIDADE_AR, "Nível de poluentes elevado", 10_000, 10_000)
            )
    ),

    LOCK_MELHORA_OS_TEMPOS(
            "B · O lock melhora os tempos",
            "Três processos de mesmo porte cuja escrita ocupa quase todo o burst. Com lock, o Round Robin "
                    + "vira na prática \"rodar até acabar\": menos trocas de contexto e menor espera média "
                    + "do que sem lock.",
            4_000, 1_000, 6_000,
            List.of(
                    new ProcessoModelo("P1", TipoEvento.TRANSITO, "Congestionamento detectado", 0, 10_000),
                    new ProcessoModelo("P2", TipoEvento.ENERGIA, "Pico de consumo detectado", 4_000, 10_000),
                    new ProcessoModelo("P3", TipoEvento.QUALIDADE_AR, "Nível de poluentes elevado", 4_000, 10_000)
            )
    );

    /** Dados de um processo do cenário. */
    public record ProcessoModelo(
            String id, TipoEvento tipoEvento, String descricao, int tempoChegada, int burst) {
    }

    private final String titulo;
    private final String descricao;
    private final int quantum;
    private final int custoTrocaContexto;
    private final int tamanhoEscrita;
    private final List<ProcessoModelo> processos;

    CenarioExemplo(String titulo, String descricao, int quantum, int custoTrocaContexto,
                   int tamanhoEscrita, List<ProcessoModelo> processos) {
        this.titulo = titulo;
        this.descricao = descricao;
        this.quantum = quantum;
        this.custoTrocaContexto = custoTrocaContexto;
        this.tamanhoEscrita = tamanhoEscrita;
        this.processos = processos;
    }

    public String titulo() {
        return titulo;
    }

    public String descricao() {
        return descricao;
    }

    public int quantum() {
        return quantum;
    }

    public int custoTrocaContexto() {
        return custoTrocaContexto;
    }

    public int tamanhoEscrita() {
        return tamanhoEscrita;
    }

    public List<ProcessoModelo> processos() {
        return processos;
    }
}
