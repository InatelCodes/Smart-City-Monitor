package br.smartcity.monitor.escalonamento.ui;

import br.smartcity.monitor.escalonamento.algoritmo.Fcfs;
import br.smartcity.monitor.escalonamento.model.ProcessoUrbano;
import br.smartcity.monitor.escalonamento.model.ResultadoEscalonamento;
import br.smartcity.monitor.escalonamento.sincronismo.ComparacaoSincronismo;
import br.smartcity.monitor.escalonamento.sincronismo.ModoSincronizacao;
import br.smartcity.monitor.escalonamento.sincronismo.ResultadoSincronismo;
import br.smartcity.monitor.escalonamento.sincronismo.SimuladorSincronismo;
import br.smartcity.monitor.model.TipoEvento;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Mantém a carga editável sem acoplar as regras de escalonamento ao JavaFX.
 */
public final class EscalonamentoController {

    /**
     * Escala dos valores de teste: as unidades de tempo são abstratas e a carga padrão usa
     * bursts de dezenas de milhares (40.000 ut), com quantum proporcional. Valores pequenos
     * (como 8 ou 2) continuam aceitos, mas escondem o efeito do quantum.
     */
    public static final int QUANTUM_PADRAO = 10_000;
    public static final int CUSTO_TROCA_CONTEXTO_PADRAO = 500;
    public static final int TAMANHO_ESCRITA_PADRAO = 15_000;

    /** Maior valor aceito para tempos, burst, quantum e escrita (evita estouro de int). */
    public static final int VALOR_MAXIMO = 1_000_000;

    /** Limite de fatias de CPU por execução, para o Gantt continuar legível e fluido. */
    public static final int LIMITE_FATIAS = 6_000;

    private final List<ProcessoUrbano> processos = new ArrayList<>();
    private int proximaOrdemCadastro;
    private int quantum = QUANTUM_PADRAO;
    private int custoTrocaContexto = CUSTO_TROCA_CONTEXTO_PADRAO;
    private int tamanhoEscrita = TAMANHO_ESCRITA_PADRAO;
    private ModoSincronizacao modoSincronizacao =
            ModoSincronizacao.COM_SINCRONISMO;
    private boolean edicaoBloqueada;

    public EscalonamentoController() {
        restaurarCargaExemplo();
    }

    public synchronized ProcessoUrbano adicionarProcesso(
            String id,
            TipoEvento tipoEvento,
            String descricao,
            int tempoChegada,
            int burst
    ) {
        garantirEdicaoPermitida();
        validarLimites(tempoChegada, burst);
        ProcessoUrbano processo = new ProcessoUrbano(
                id,
                tipoEvento,
                descricao,
                tempoChegada,
                burst,
                proximaOrdemCadastro
        );
        garantirIdDisponivel(processo.id(), null);
        processos.add(processo);
        proximaOrdemCadastro++;
        return processo;
    }

    public synchronized ProcessoUrbano editarProcesso(
            String idAtual,
            String novoId,
            TipoEvento tipoEvento,
            String descricao,
            int tempoChegada,
            int burst
    ) {
        garantirEdicaoPermitida();
        validarLimites(tempoChegada, burst);
        int indice = encontrarIndice(idAtual);
        ProcessoUrbano atual = processos.get(indice);
        ProcessoUrbano atualizado = new ProcessoUrbano(
                novoId,
                tipoEvento,
                descricao,
                tempoChegada,
                burst,
                atual.ordemCadastro()
        );
        garantirIdDisponivel(atualizado.id(), atual.id());
        processos.set(indice, atualizado);
        return atualizado;
    }

    public synchronized boolean removerProcesso(String id) {
        garantirEdicaoPermitida();
        return processos.removeIf(processo -> processo.id().equals(id));
    }

    public synchronized void limparCarga() {
        garantirEdicaoPermitida();
        processos.clear();
        proximaOrdemCadastro = 0;
    }

    public synchronized void restaurarCargaExemplo() {
        carregarCenario(CenarioExemplo.COLISAO_NO_ROUND_ROBIN);
    }

    /** Substitui a carga e os parâmetros pelos de um cenário pronto. */
    public synchronized void carregarCenario(CenarioExemplo cenario) {
        Objects.requireNonNull(cenario, "cenario");
        garantirEdicaoPermitida();
        processos.clear();
        proximaOrdemCadastro = 0;
        for (CenarioExemplo.ProcessoModelo p : cenario.processos()) {
            adicionarProcesso(p.id(), p.tipoEvento(), p.descricao(), p.tempoChegada(), p.burst());
        }
        quantum = cenario.quantum();
        custoTrocaContexto = cenario.custoTrocaContexto();
        tamanhoEscrita = cenario.tamanhoEscrita();
        modoSincronizacao = ModoSincronizacao.COM_SINCRONISMO;
    }

    public synchronized List<ProcessoUrbano> obterProcessos() {
        return List.copyOf(processos);
    }

    public synchronized int getQuantum() {
        return quantum;
    }

    public synchronized void setQuantum(int quantum) {
        garantirEdicaoPermitida();
        validarQuantum(quantum);
        this.quantum = quantum;
    }

    public static void validarQuantum(int quantum) {
        if (quantum <= 0) {
            throw new IllegalArgumentException(
                    "O quantum deve ser maior que zero"
            );
        }
        if (quantum > VALOR_MAXIMO) {
            throw new IllegalArgumentException(
                    "O quantum deve ser no máximo " + VALOR_MAXIMO
            );
        }
    }

    /** Estimativa de quantas fatias de CPU o Round Robin gera para a carga. */
    public static long estimarFatias(List<ProcessoUrbano> carga, int quantum) {
        validarQuantum(quantum);
        long fatias = 0;
        for (ProcessoUrbano processo : carga) {
            fatias += (processo.burst() + (long) quantum - 1) / quantum;
        }
        return fatias;
    }

    /** Recusa quantum tão pequeno que o Gantt viraria uma faixa ilegível. */
    public static void validarFatias(List<ProcessoUrbano> carga, int quantum) {
        long fatias = estimarFatias(carga, quantum);
        if (fatias > LIMITE_FATIAS) {
            throw new IllegalArgumentException(String.format(
                    java.util.Locale.forLanguageTag("pt-BR"),
                    "Com quantum %,d a carga gera %,d fatias de CPU (limite: %,d). "
                            + "Aumente o quantum ou reduza os bursts.",
                    quantum, fatias, LIMITE_FATIAS
            ));
        }
    }

    private static void validarLimites(int tempoChegada, int burst) {
        if (tempoChegada > VALOR_MAXIMO || burst > VALOR_MAXIMO) {
            throw new IllegalArgumentException(
                    "Chegada e burst devem ser no máximo " + VALOR_MAXIMO
            );
        }
    }

    public synchronized int getCustoTrocaContexto() {
        return custoTrocaContexto;
    }

    public synchronized void setCustoTrocaContexto(int custoTrocaContexto) {
        garantirEdicaoPermitida();
        if (custoTrocaContexto < 0) {
            throw new IllegalArgumentException(
                    "O custo da troca de contexto não pode ser negativo"
            );
        }
        if (custoTrocaContexto > VALOR_MAXIMO) {
            throw new IllegalArgumentException(
                    "O custo da troca deve ser no máximo " + VALOR_MAXIMO
            );
        }
        this.custoTrocaContexto = custoTrocaContexto;
    }

    /** Tempo de CPU (no fim de cada burst) gasto escrevendo no arquivo compartilhado. */
    public synchronized int getTamanhoEscrita() {
        return tamanhoEscrita;
    }

    public synchronized void setTamanhoEscrita(int tamanhoEscrita) {
        garantirEdicaoPermitida();
        if (tamanhoEscrita < 1) {
            throw new IllegalArgumentException(
                    "A escrita no arquivo deve durar pelo menos 1 unidade"
            );
        }
        if (tamanhoEscrita > VALOR_MAXIMO) {
            throw new IllegalArgumentException(
                    "A escrita deve durar no máximo " + VALOR_MAXIMO
            );
        }
        this.tamanhoEscrita = tamanhoEscrita;
    }

    public synchronized ModoSincronizacao getModoSincronizacao() {
        return modoSincronizacao;
    }

    public synchronized void setModoSincronizacao(
            ModoSincronizacao modoSincronizacao
    ) {
        garantirEdicaoPermitida();
        this.modoSincronizacao = Objects.requireNonNull(
                modoSincronizacao,
                "modoSincronizacao"
        );
    }

    /**
     * Valida e congela a carga enquanto a camada de simulação/animação a usa.
     */
    public synchronized List<ProcessoUrbano> iniciarSimulacao() {
        if (processos.isEmpty()) {
            throw new IllegalStateException(
                    "Cadastre pelo menos um processo antes de simular"
            );
        }
        validarQuantum(quantum);
        edicaoBloqueada = true;
        return List.copyOf(processos);
    }

    public synchronized void finalizarSimulacao() {
        edicaoBloqueada = false;
    }

    public synchronized boolean isEdicaoBloqueada() {
        return edicaoBloqueada;
    }

    /** Atalho útil enquanto apenas o algoritmo da Pessoa 1 está disponível. */
    public ResultadoEscalonamento executarFcfs() {
        List<ProcessoUrbano> carga = iniciarSimulacao();
        try {
            return new Fcfs(getCustoTrocaContexto()).executar(carga);
        } finally {
            finalizarSimulacao();
        }
    }

    public ResultadoSincronismo executarExperimentoSincronismo(Path arquivo)
            throws IOException, InterruptedException {
        List<ProcessoUrbano> carga = iniciarSimulacao();
        try {
            return new SimuladorSincronismo().medir(
                    carga,
                    getModoSincronizacao(),
                    arquivo,
                    SimuladorSincronismo.REPETICOES_PADRAO
            );
        } finally {
            finalizarSimulacao();
        }
    }

    public ComparacaoSincronismo compararSincronismo(Path diretorio)
            throws IOException, InterruptedException {
        List<ProcessoUrbano> carga = iniciarSimulacao();
        try {
            return new SimuladorSincronismo().comparar(carga, diretorio);
        } finally {
            finalizarSimulacao();
        }
    }

    private int encontrarIndice(String id) {
        if (id == null) {
            throw new IllegalArgumentException("O ID atual é obrigatório");
        }
        for (int i = 0; i < processos.size(); i++) {
            if (processos.get(i).id().equals(id)) {
                return i;
            }
        }
        throw new IllegalArgumentException("Processo não encontrado: " + id);
    }

    private void garantirIdDisponivel(String id, String idIgnorado) {
        boolean duplicado = processos.stream()
                .map(ProcessoUrbano::id)
                .filter(idExistente -> !Objects.equals(idExistente, idIgnorado))
                .anyMatch(idExistente -> idExistente.equals(id));

        if (duplicado) {
            throw new IllegalArgumentException(
                    "Já existe um processo com o ID " + id
            );
        }
    }

    private void garantirEdicaoPermitida() {
        if (edicaoBloqueada) {
            throw new IllegalStateException(
                    "A carga não pode ser alterada durante a simulação"
            );
        }
    }
}
