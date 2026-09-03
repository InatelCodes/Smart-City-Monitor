package br.smartcity.monitor.ui;

import br.smartcity.monitor.central.CentralMonitoramento;
import br.smartcity.monitor.config.ConfiguracaoExperimento;
import br.smartcity.monitor.metrics.Metricas;
import br.smartcity.monitor.model.Evento;
import br.smartcity.monitor.model.ResultadoProcessamento;
import br.smartcity.monitor.sensor.SensorClima;
import br.smartcity.monitor.sensor.SensorEnergia;
import br.smartcity.monitor.sensor.SensorQualidadeAr;
import br.smartcity.monitor.sensor.SensorTransito;

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
    private List<Thread> threadsSensores = List.of();
    private ConfiguracaoExperimento configuracaoAtual;
    private ExperimentoResultado ultimoResultado;
    private volatile Estado estado = Estado.PARADO;
    private int proximoNumeroExperimento = 1;

    public synchronized void iniciar(ConfiguracaoExperimento configuracao) {
        if (estado == Estado.EXECUTANDO) {
            throw new IllegalStateException("Já existe um experimento em execução");
        }

        configuracaoAtual = configuracao;
        fila = new LinkedBlockingQueue<>();
        metricas = new Metricas();
        central = new CentralMonitoramento(
                fila,
                metricas,
                configuracao.quantidadeThreads(),
                configuracao.tempoProcessamentoMs()
        );

        long intervaloPorSensorMs = Math.max(
                1,
                Math.round(4_000.0 / configuracao.taxaGeracao())
        );

        threadsSensores = List.of(
                new Thread(new SensorTransito(fila, intervaloPorSensorMs, metricas),
                        "Sensor-Trânsito"),
                new Thread(new SensorClima(fila, intervaloPorSensorMs, metricas),
                        "Sensor-Clima"),
                new Thread(new SensorEnergia(fila, intervaloPorSensorMs, metricas),
                        "Sensor-Energia"),
                new Thread(new SensorQualidadeAr(fila, intervaloPorSensorMs, metricas),
                        "Sensor-Qualidade-do-Ar")
        );

        central.iniciar();
        threadsSensores.forEach(Thread::start);
        estado = Estado.EXECUTANDO;
    }

    /** Para produtores e consumidores e devolve o resumo definitivo da execução. */
    public synchronized ExperimentoResultado parar() throws InterruptedException {
        if (estado != Estado.EXECUTANDO) {
            return ultimoResultado;
        }

        threadsSensores.forEach(Thread::interrupt);
        for (Thread sensor : threadsSensores) {
            sensor.join();
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
                snapshot.taxaGeracao(),
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
        threadsSensores = List.of();
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
                metricas.getTaxaGeracao(),
                metricas.getTaxaProcessamento(),
                metricas.getTempoMedioRespostaMs(),
                metricas.getTempoDecorridoSegundos()
        );
    }

    public synchronized List<ResultadoProcessamento> drenarResultadosRecentes() {
        return central == null ? List.of() : central.drenarResultadosRecentes();
    }

    public Estado getEstado() {
        return estado;
    }
}
