package br.smartcity.monitor.ui;

import br.smartcity.monitor.central.CentralMonitoramento;
import br.smartcity.monitor.config.ConfiguracaoExperimento;
import br.smartcity.monitor.metrics.Metricas;
import br.smartcity.monitor.model.Evento;
import br.smartcity.monitor.model.ResultadoProcessamento;
import br.smartcity.monitor.sensor.ListaEventos;

import java.util.ArrayList;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/** Liga a interface ao domínio sem permitir que threads de trabalho toquem no JavaFX. */
public final class DashboardController {

    public enum Estado { PARADO, EXECUTANDO, FINALIZADO }

    private BlockingQueue<Evento> fila;
    private Metricas metricas;
    private CentralMonitoramento central;
    private ConfiguracaoExperimento configuracaoAtual;
    private ExperimentoResultado ultimoResultado;
    private volatile Estado estado = Estado.PARADO;
    private int proximoNumeroExperimento = 1;

    public synchronized void iniciar(ConfiguracaoExperimento configuracao) {
        if (estado == Estado.EXECUTANDO) {
            throw new IllegalStateException(
                    "Já existe um experimento em execução"
            );
        }

        configuracaoAtual = configuracao;

        fila = new LinkedBlockingQueue<>();
        metricas = new Metricas();

        /*
        * Os eventos são definidos previamente.
        * Não existe uma Thread responsável pela geração.
        */
        List<Evento> eventos = ListaEventos.criarEventos();

        for (Evento evento : eventos) {
            fila.add(evento);
            metricas.registrarEventoGerado();
        }

        central = new CentralMonitoramento(
                fila,
                metricas,
                configuracao.quantidadeThreads(),
                configuracao.tempoProcessamentoMs()
        );

        central.iniciar();

        estado = Estado.EXECUTANDO;
    }

    public synchronized ExperimentoResultado parar()
        throws InterruptedException {

        if (estado != Estado.EXECUTANDO) {
            return ultimoResultado;
        }

        central.encerrar();

        metricas.finalizarColeta();

        estado = Estado.FINALIZADO;

        DashboardSnapshot snapshot = criarSnapshot();

        ultimoResultado = new ExperimentoResultado(
                proximoNumeroExperimento++,
                LocalDateTime.now(),
                configuracaoAtual,
                snapshot.tempoDecorridoSegundos(),
                snapshot.eventosGerados(),
                snapshot.eventosProcessados(),
                snapshot.eventosPendentes(),
                snapshot.taxaProcessamento(),
                snapshot.tempoMedioRespostaMs()
        );

        return ultimoResultado;
    }

    public synchronized void resetar() {
        if (estado == Estado.EXECUTANDO) {
            throw new IllegalStateException("Pare o experimento antes de resetar");
        }
        if (fila != null) {
            fila.clear();
        }
        fila = null;
        metricas = null;
        central = null;
        configuracaoAtual = null;
        ultimoResultado = null;
        estado = Estado.PARADO;
    }

    public synchronized DashboardSnapshot obterSnapshot() {
        return criarSnapshot();
    }

    private DashboardSnapshot criarSnapshot() {
        if (metricas == null || central == null) {
            return DashboardSnapshot.vazio();
        }
        return new DashboardSnapshot(
                metricas.getEventosGerados(),
                metricas.getEventosProcessados(),
                central.getEventosPendentes(),
                central.getQuantidadeThreadsAtivas(),
                metricas.getTaxaProcessamento(),
                metricas.getTempoMedioRespostaMs(),
                metricas.getTempoDecorridoSegundos(),
                metricas.getEventosTransito(),
                metricas.getEventosClima(),
                metricas.getEventosEnergia(),
                metricas.getEventosQualidadeAr()
        );
    }

    public synchronized List<ResultadoProcessamento> drenarResultadosRecentes() {
        return central == null ? List.of() : central.drenarResultadosRecentes();
    }

    public Estado getEstado() {
        return estado;
    }
}
