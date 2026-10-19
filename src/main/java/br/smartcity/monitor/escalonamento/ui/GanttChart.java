package br.smartcity.monitor.escalonamento.ui;

import br.smartcity.monitor.escalonamento.model.ProcessoResultado;
import br.smartcity.monitor.escalonamento.model.ResultadoEscalonamento;
import br.smartcity.monitor.escalonamento.model.TipoTrechoExecucao;
import br.smartcity.monitor.escalonamento.model.TrechoExecucao;
import br.smartcity.monitor.escalonamento.sincronismo.Bloqueio;
import br.smartcity.monitor.escalonamento.sincronismo.ExecucaoIntegrada;
import br.smartcity.monitor.escalonamento.sincronismo.RegistroSimulado;
import br.smartcity.monitor.escalonamento.sincronismo.TrechoEscrita;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.geometry.VPos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Gantt em raias: uma raia da CPU e uma por processo. A faixa clara de cada
 * processo é o tempo esperando na fila; o bloco sólido é o tempo na CPU.
 */
final class GanttChart extends Region {

    private static final double ESQ = 74, DIR = 18, TOPO = 28;
    private static final double RAIA_CPU = 36, RAIA = 30, FOLGA = 8, EIXO = 30;
    private static final Color TROCA = Color.web("#64748b");
    private static final Color OCIOSA = Color.web("#dbe3e8");
    private static final Color TINTA = Color.web("#102f3d");

    private final Canvas canvas = new Canvas();
    private final DoubleProperty tempo = new SimpleDoubleProperty();
    private ResultadoEscalonamento resultado;
    private List<ProcessoResultado> linhas = List.of();
    private int tempoMax = 1;
    private List<TrechoEscrita> escritas = List.of();
    private List<Bloqueio> bloqueios = List.of();
    private Set<String> corrompidos = Set.of();
    private ExecucaoIntegrada integrada;

    GanttChart() {
        getChildren().add(canvas);
        tempo.addListener((obs, a, b) -> desenhar());
    }

    /** Gantt com a região crítica (hachurada), bloqueios por lock e janelas de escrita abertas. */
    void definir(ExecucaoIntegrada execucao, int tempoMaximo) {
        integrada = execucao;
        escritas = execucao.escritas();
        bloqueios = execucao.bloqueios();
        corrompidos = execucao.registros().stream()
                .filter(r -> !r.integro())
                .map(RegistroSimulado::processoId)
                .collect(Collectors.toSet());
        definir(execucao.resultado(), tempoMaximo);
    }

    DoubleProperty tempoProperty() {
        return tempo;
    }

    void definir(ResultadoEscalonamento r, int tempoMaximo) {
        resultado = r;
        tempoMax = Math.max(1, tempoMaximo);
        linhas = r.processos().stream()
                .sorted(Comparator.comparingInt(p -> p.processo().ordemCadastro()))
                .toList();
        requestLayout();
        desenhar();
    }

    @Override
    protected double computePrefHeight(double largura) {
        return TOPO + RAIA_CPU + FOLGA + linhas.size() * RAIA + EIXO;
    }

    @Override
    protected double computeMinHeight(double largura) {
        return computePrefHeight(largura);
    }

    @Override
    protected double computePrefWidth(double altura) {
        return 600;
    }

    @Override
    protected void layoutChildren() {
        canvas.setWidth(Math.max(1, getWidth()));
        canvas.setHeight(Math.max(1, getHeight()));
        desenhar();
    }

    private static int passoDaEscala(double pxPorUnidade) {
        int[] opcoes = {1, 2, 5, 10, 20, 25, 50, 100, 200, 500, 1000, 5000, 10000};
        for (int passo : opcoes) {
            if (passo * pxPorUnidade >= 30) {
                return passo;
            }
        }
        return 20000;
    }

    private void desenhar() {
        GraphicsContext g = canvas.getGraphicsContext2D();
        double largura = canvas.getWidth();
        double altura = canvas.getHeight();
        g.clearRect(0, 0, largura, altura);
        if (resultado == null) {
            return;
        }

        double area = Math.max(10, largura - ESQ - DIR);
        double px = area / tempoMax;
        double yCpu = TOPO;
        double yProc = TOPO + RAIA_CPU + FOLGA;
        double yFim = yProc + linhas.size() * RAIA;

        // fundo das raias
        g.setFill(Color.web("#f3f7f9"));
        g.fillRoundRect(ESQ - 8, yCpu, area + 16, RAIA_CPU, 10, 10);
        for (int i = 0; i < linhas.size(); i++) {
            if (i % 2 == 0) {
                g.setFill(Color.web("#f8fafb"));
                g.fillRoundRect(ESQ - 8, yProc + i * RAIA, area + 16, RAIA, 8, 8);
            }
        }

        // grade e eixo de tempo
        int passo = passoDaEscala(px);
        g.setFont(Font.font("System", 11));
        g.setTextAlign(TextAlignment.CENTER);
        g.setTextBaseline(VPos.CENTER);
        int ultimo = -passo;
        for (int t = 0; t <= tempoMax; t += passo) {
            desenharMarca(g, t, px, yCpu, yFim);
            ultimo = t;
        }
        if (ultimo != tempoMax && (tempoMax - ultimo) * px >= 22) {
            desenharMarca(g, tempoMax, px, yCpu, yFim);
        }

        // raia da CPU
        g.setTextAlign(TextAlignment.LEFT);
        g.setFont(Font.font("System", FontWeight.BOLD, 12));
        g.setFill(TINTA);
        g.fillText("CPU", 8, yCpu + RAIA_CPU / 2);
        g.setTextAlign(TextAlignment.CENTER);
        for (TrechoExecucao trecho : resultado.gantt()) {
            double x = ESQ + trecho.inicio() * px;
            double w = trecho.duracao() * px;
            Color cor = switch (trecho.tipo()) {
                case PROCESSO -> Tema.cor(trecho.tipoEvento());
                case TROCA_CONTEXTO -> TROCA;
                case OCIOSO -> OCIOSA;
            };
            g.setFill(cor);
            g.fillRoundRect(x + 0.5, yCpu + 4, Math.max(1, w - 1), RAIA_CPU - 8, 8, 8);
            sobreporEscrita(g, trecho, px, yCpu + 4, RAIA_CPU - 8);
            String texto = switch (trecho.tipo()) {
                case PROCESSO -> trecho.processoId();
                case TROCA_CONTEXTO -> "TC";
                case OCIOSO -> "ocioso";
            };
            g.setFont(Font.font("System", FontWeight.BOLD, trecho.tipo() == TipoTrechoExecucao.PROCESSO ? 13 : 10));
            g.setFill(trecho.tipo() == TipoTrechoExecucao.OCIOSO ? Color.web("#5b6b75") : Color.WHITE);
            if (w >= texto.length() * 7.5 + 6) {
                g.fillText(texto, x + w / 2, yCpu + RAIA_CPU / 2);
            }
        }

        // raias dos processos
        for (int k = 0; k < linhas.size(); k++) {
            ProcessoResultado pr = linhas.get(k);
            double y = yProc + k * RAIA;
            Color cor = Tema.cor(pr.processo().tipoEvento());

            g.setTextAlign(TextAlignment.LEFT);
            g.setFont(Font.font("System", FontWeight.BOLD, 12));
            g.setFill(cor);
            g.fillOval(8, y + RAIA / 2 - 4, 8, 8);
            g.setFill(TINTA);
            g.fillText(pr.processo().id(), 22, y + RAIA / 2);

            double xChegada = ESQ + pr.processo().tempoChegada() * px;
            double xFim = ESQ + pr.conclusao() * px;
            g.setFill(cor.deriveColor(0, 1, 1, 0.22));
            g.fillRoundRect(xChegada, y + RAIA / 2 - 5, Math.max(2, xFim - xChegada), 10, 8, 8);

            for (Bloqueio b : bloqueios) {
                if (b.processoId().equals(pr.processo().id())) {
                    double bx = ESQ + b.inicio() * px;
                    double bw = b.duracao() * px;
                    g.setFill(Color.web("#d9534f", 0.16));
                    g.fillRoundRect(bx, y + RAIA / 2 - 8, bw, 16, 8, 8);
                    hachurar(g, bx, y + RAIA / 2 - 8, bw, 16, Color.web("#d9534f", 0.55), 0);
                    g.setStroke(Color.web("#d9534f", 0.8));
                    g.setLineWidth(1.2);
                    g.strokeRoundRect(bx + 0.5, y + RAIA / 2 - 7.5, bw - 1, 15, 8, 8);
                    if (bw >= 54) {
                        g.setFill(Color.web("#b73c38"));
                        g.setFont(Font.font("System", FontWeight.BOLD, 10));
                        g.setTextAlign(TextAlignment.CENTER);
                        g.fillText("esperando lock", bx + bw / 2, y + RAIA / 2);
                    }
                }
            }
            g.setTextAlign(TextAlignment.CENTER);
            g.setFont(Font.font("System", FontWeight.BOLD, 11));
            for (TrechoExecucao trecho : resultado.gantt()) {
                if (trecho.tipo() != TipoTrechoExecucao.PROCESSO
                        || !trecho.processoId().equals(pr.processo().id())) {
                    continue;
                }
                double x = ESQ + trecho.inicio() * px;
                double w = trecho.duracao() * px;
                g.setFill(cor);
                g.fillRoundRect(x + 0.5, y + 4, Math.max(1, w - 1), RAIA - 8, 7, 7);
                sobreporEscrita(g, trecho, px, y + 4, RAIA - 8);
                if (w >= 20) {
                    g.setFill(Color.WHITE);
                    g.fillText(String.valueOf(trecho.duracao()), x + w / 2, y + RAIA / 2);
                }
            }
            // marca de chegada
            g.setStroke(cor.darker());
            g.setLineWidth(2);
            g.strokeLine(xChegada, y + 5, xChegada, y + RAIA - 5);
        }

        if (integrada != null) {
            for (int k = 0; k < linhas.size(); k++) {
                ProcessoResultado pr = linhas.get(k);
                Integer abre = integrada.inicioEscrita(pr.processo().id());
                if (abre == null) {
                    continue;
                }
                boolean ruim = corrompidos.contains(pr.processo().id());
                double y = yProc + k * RAIA + RAIA - 2.5;
                g.setStroke(ruim ? Color.web("#d9534f") : Color.web("#102f3d", 0.55));
                g.setLineWidth(3);
                g.strokeLine(ESQ + abre * px, y, ESQ + pr.conclusao() * px, y);
            }
        }

        // cursor de reprodução
        double t = tempo.get();
        if (t < tempoMax - 1e-9) {
            double xp = ESQ + t * px;
            g.setFill(Color.web("#f4f6f8", 0.72));
            g.fillRect(xp, yCpu - 2, ESQ + area + 10 - xp, yFim - yCpu + 4);
            g.setStroke(TINTA);
            g.setLineWidth(2);
            g.strokeLine(xp, yCpu - 6, xp, yFim);
            String texto = "t = " + (int) Math.floor(t + 1e-9);
            g.setFill(TINTA);
            g.fillRoundRect(xp - 24, 2, 48, 18, 9, 9);
            g.setFill(Color.WHITE);
            g.setTextAlign(TextAlignment.CENTER);
            g.setFont(Font.font("System", FontWeight.BOLD, 11));
            g.fillText(texto, xp, 11);
        }
    }

    /** Escurece e hachura a parte do trecho em que o processo estava escrevendo no arquivo. */
    private void sobreporEscrita(GraphicsContext g, TrechoExecucao trecho, double px, double y, double h) {
        if (integrada == null || trecho.tipo() != TipoTrechoExecucao.PROCESSO) {
            return;
        }
        for (TrechoEscrita e : escritas) {
            if (!e.processoId().equals(trecho.processoId())) {
                continue;
            }
            int de = Math.max(e.inicio(), trecho.inicio());
            int ate = Math.min(e.fim(), trecho.fim());
            if (ate > de) {
                double x = ESQ + de * px + 0.5;
                double w = Math.max(1, (ate - de) * px - 1);
                g.setFill(Color.web("#102f3d", 0.28));
                g.fillRect(x, y, w, h);
                hachurar(g, x, y, w, h, Color.web("#ffffff", 0.5), 1);
            }
        }
    }

    private static void hachurar(GraphicsContext g, double x, double y, double w, double h, Color cor, int espessura) {
        g.save();
        g.beginPath();
        g.rect(x, y, w, h);
        g.clip();
        g.setStroke(cor);
        g.setLineWidth(espessura == 0 ? 1.4 : 2);
        for (double i = -h; i < w; i += 7) {
            g.strokeLine(x + i, y + h, x + i + h, y);
        }
        g.restore();
    }

    private void desenharMarca(GraphicsContext g, int t, double px, double yCpu, double yFim) {
        double x = Math.round(ESQ + t * px) + 0.5;
        g.setStroke(Color.web("#e1e8ec"));
        g.setLineWidth(1);
        g.strokeLine(x, yCpu, x, yFim);
        g.setFill(Color.web("#6b7c85"));
        g.setTextAlign(TextAlignment.CENTER);
        g.setFont(Font.font("System", 11));
        g.fillText(String.valueOf(t), x, yFim + 14);
    }
}
