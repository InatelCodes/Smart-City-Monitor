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

    public static final int QUANTUM_PADRAO = 2;
    public static final int CUSTO_TROCA_CONTEXTO_PADRAO = 1;

    private final List<ProcessoUrbano> processos = new ArrayList<>();
    private int proximaOrdemCadastro;
    private int quantum = QUANTUM_PADRAO;
    private int custoTrocaContexto = CUSTO_TROCA_CONTEXTO_PADRAO;
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
        garantirEdicaoPermitida();
        processos.clear();
        proximaOrdemCadastro = 0;

        adicionarProcesso(
                "P1",
                TipoEvento.TRANSITO,
                "Congestionamento detectado",
                0,
                8
        );
        adicionarProcesso(
                "P2",
                TipoEvento.ENERGIA,
                "Pico de consumo detectado",
                1,
                4
        );
        adicionarProcesso(
                "P3",
                TipoEvento.QUALIDADE_AR,
                "Nível de poluentes elevado",
                2,
                2
        );
        quantum = QUANTUM_PADRAO;
        custoTrocaContexto = CUSTO_TROCA_CONTEXTO_PADRAO;
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
        this.custoTrocaContexto = custoTrocaContexto;
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
            return new SimuladorSincronismo().executar(
                    carga,
                    getModoSincronizacao(),
                    arquivo
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
