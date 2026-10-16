package br.smartcity.monitor.escalonamento.sincronismo;

import br.smartcity.monitor.escalonamento.model.ResultadoEscalonamento;

import java.util.List;
import java.util.Objects;

/**
 * Resultado de um escalonador executando a carga com (ou sem) lock sobre o arquivo.
 * Todos os tempos são lógicos (unidades de CPU) e, portanto, reprodutíveis.
 */
public record ExecucaoIntegrada(
        PoliticaEscalonamento politica,
        ModoSincronizacao modo,
        int quantum,
        int tamanhoEscrita,
        ResultadoEscalonamento resultado,
        List<TrechoEscrita> escritas,
        List<Bloqueio> bloqueios,
        List<RegistroSimulado> registros,
        String conteudoArquivo
) {

    public ExecucaoIntegrada {
        politica = Objects.requireNonNull(politica, "politica");
        modo = Objects.requireNonNull(modo, "modo");
        resultado = Objects.requireNonNull(resultado, "resultado");
        escritas = List.copyOf(Objects.requireNonNull(escritas, "escritas"));
        bloqueios = List.copyOf(Objects.requireNonNull(bloqueios, "bloqueios"));
        registros = List.copyOf(Objects.requireNonNull(registros, "registros"));
        conteudoArquivo = Objects.requireNonNull(conteudoArquivo, "conteudoArquivo");
    }

    public boolean comSincronismo() {
        return modo == ModoSincronizacao.COM_SINCRONISMO;
    }

    public int registrosEsperados() {
        return registros.size();
    }

    public int registrosValidos() {
        return (int) registros.stream().filter(RegistroSimulado::integro).count();
    }

    public int registrosCorrompidos() {
        return registrosEsperados() - registrosValidos();
    }

    /** Soma dos tempos em que algum processo ficou dormindo esperando o lock. */
    public int tempoBloqueadoTotal() {
        return bloqueios.stream().mapToInt(Bloqueio::duracao).sum();
    }

    /** Instante em que o processo começou a escrever (null se nunca escreveu). */
    public Integer inicioEscrita(String processoId) {
        return escritas.stream()
                .filter(e -> e.processoId().equals(processoId))
                .map(TrechoEscrita::inicio)
                .min(Integer::compare)
                .orElse(null);
    }
}
