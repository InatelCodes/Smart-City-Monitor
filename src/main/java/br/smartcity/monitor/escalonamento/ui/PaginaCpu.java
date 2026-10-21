package br.smartcity.monitor.escalonamento.ui;

import br.smartcity.monitor.escalonamento.algoritmo.Fcfs;
import br.smartcity.monitor.escalonamento.algoritmo.RoundRobin;
import br.smartcity.monitor.escalonamento.model.MetricasEscalonamento;
import br.smartcity.monitor.escalonamento.model.ProcessoResultado;
import br.smartcity.monitor.escalonamento.model.ProcessoUrbano;
import br.smartcity.monitor.escalonamento.model.ResultadoEscalonamento;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.ToDoubleFunction;

/** Página 1: FCFS, Round Robin ou os dois lado a lado. */
final class PaginaCpu extends VBox {

    enum Modo {
        FCFS("FCFS"), RR("Round Robin"), AMBOS("Comparar os dois");

        private final String rotulo;

        Modo(String rotulo) {
            this.rotulo = rotulo;
        }

        String rotulo() {
            return rotulo;
        }
    }

    private final EscalonamentoController ctrl;
    private final Segmentado<Modo> modo = new Segmentado<>(List.of(Modo.values()), Modo::rotulo);
    private final Label explicacao = Tema.rotulo("", "pagina-sub");
    private final Button executar = new Button("▶  Executar");
    private final VBox area = new VBox(16);

    private final DoubleProperty tempo = new SimpleDoubleProperty();
    private final BarraReproducao reproducao = new BarraReproducao(tempo);
    private int tempoMax;

    PaginaCpu(EscalonamentoController ctrl) {
        super(16);
        this.ctrl = ctrl;
        getStyleClass().add("pagina");

        Label titulo = Tema.rotulo("Como a CPU atende os eventos da cidade?", "pagina-titulo");
        VBox cabecalho = new VBox(4, titulo, explicacao);

        executar.getStyleClass().add("btn-primario");
        executar.setOnAction(e -> executar());
        HBox barra = new HBox(14, modo, Tema.espaco(), executar);
        barra.setAlignment(Pos.CENTER_LEFT);

        modo.valorProperty().addListener((obs, a, b) -> {
            atualizarExplicacao();
            limpar();
        });
        atualizarExplicacao();
        limpar();
        getChildren().addAll(cabecalho, barra, area);
    }

    private void atualizarExplicacao() {
        explicacao.setText(switch (modo.valor()) {
            case FCFS -> "FCFS: primeiro a chegar, primeiro a ser atendido. Sem preempção — quem pega a CPU só a solta ao terminar.";
            case RR -> "Round Robin: cada processo usa a CPU por no máximo 1 quantum; se não terminar, volta ao fim da fila.";
            case AMBOS -> "Mesma carga, mesmo custo de troca — os dois escalonadores na mesma escala de tempo, para comparar de verdade.";
        });
    }

    /** Descarta resultados (entrada mudou) e mostra o estado vazio. */
    void limpar() {
        parar();
        Label vazio = Tema.rotulo(
                "Escolha o modo acima e clique em Executar.\nA carga e os parâmetros ficam na barra lateral.",
                "vazio");
        vazio.setAlignment(Pos.CENTER);
        vazio.setMaxWidth(Double.MAX_VALUE);
        VBox caixa = new VBox(vazio);
        caixa.getStyleClass().addAll("card", "vazio-caixa");
        caixa.setAlignment(Pos.CENTER);
        area.getChildren().setAll(caixa);
    }

    private void parar() {
        reproducao.parar();
    }

    private void executar() {
        List<ProcessoUrbano> carga = ctrl.obterProcessos();
        if (carga.isEmpty()) {
            new Alert(Alert.AlertType.WARNING, "Cadastre pelo menos um processo na barra lateral.", ButtonType.OK).showAndWait();
            return;
        }
        try {
            parar();
            int quantum = ctrl.getQuantum();
            int custo = ctrl.getCustoTrocaContexto();
            Modo m = modo.valor();
            if (m != Modo.FCFS) {
                EscalonamentoController.validarFatias(carga, quantum);
            }
            ResultadoEscalonamento fcfs = m != Modo.RR ? new Fcfs(custo).executar(carga) : null;
            ResultadoEscalonamento rr = m != Modo.FCFS ? new RoundRobin(quantum, custo).executar(carga) : null;
            mostrar(carga, fcfs, rr, quantum, custo);
        } catch (RuntimeException ex) {
            new Alert(Alert.AlertType.ERROR, ex.getMessage(), ButtonType.OK).showAndWait();
        }
    }

    private static int fim(ResultadoEscalonamento r) {
        return r.gantt().get(r.gantt().size() - 1).fim();
    }

    private void mostrar(List<ProcessoUrbano> carga, ResultadoEscalonamento fcfs,
                         ResultadoEscalonamento rr, int quantum, int custo) {
        tempoMax = Math.max(fcfs != null ? fim(fcfs) : 0, rr != null ? fim(rr) : 0);
        reproducao.configurar(tempoMax);

        List<javafx.scene.Node> blocos = new ArrayList<>();
        blocos.add(reproducao);
        blocos.add(legenda());
        if (fcfs != null) {
            blocos.add(new CartaoAlgoritmo(fcfs, "não preemptivo", tempoMax, tempo));
        }
        if (rr != null) {
            blocos.add(new CartaoAlgoritmo(rr, "preemptivo · quantum " + quantum, tempoMax, tempo));
        }
        if (fcfs != null && rr != null) {
            blocos.add(placar(fcfs, rr));
            blocos.add(graficoQuantum(carga, fcfs, quantum, custo));
        } else {
            ResultadoEscalonamento unico = fcfs != null ? fcfs : rr;
            blocos.add(kpis(unico.metricas()));
            blocos.add(tabelaProcessos(unico));
        }
        area.getChildren().setAll(blocos);
    }

    // ---------------------------------------------------------------- legenda

    private HBox legenda() {
        HBox caixa = new HBox(18,
                item("#1fa89a", "executando na CPU"),
                item("#1fa89a55", "esperando na fila (cor do tipo, clara)"),
                item("#64748b", "troca de contexto (TC)"),
                item("#dbe3e8", "CPU ociosa"));
        caixa.setAlignment(Pos.CENTER_LEFT);
        caixa.setPadding(new Insets(0, 4, 0, 4));
        return caixa;
    }

    private HBox item(String cor, String texto) {
        javafx.scene.layout.Region quadro = new javafx.scene.layout.Region();
        quadro.setMinSize(14, 14);
        quadro.setMaxSize(14, 14);
        quadro.setStyle("-fx-background-color:" + cor + ";-fx-background-radius:4;");
        HBox h = new HBox(6, quadro, Tema.rotulo(texto, "legenda"));
        h.setAlignment(Pos.CENTER_LEFT);
        return h;
    }

    // ---------------------------------------------------------------- resultado único

    private HBox kpis(MetricasEscalonamento m) {
        HBox linha = new HBox(14,
                kpi("ESPERA MÉDIA", Tema.num(m.tempoMedioEspera()), "tempo parado na fila de pronto"),
                kpi("RESPOSTA MÉDIA", Tema.num(m.tempoMedioResposta()), "da chegada até a 1ª vez na CPU"),
                kpi("RETORNO MÉDIO", Tema.num(m.tempoMedioRetorno()), "da chegada até terminar"),
                kpi("DURAÇÃO TOTAL", Tema.inteiro(m.makespan()), "makespan da carga inteira (ut)"),
                kpi("TROCAS DE CONTEXTO", m.trocasContexto() + "  (" + Tema.inteiro(m.tempoTrocasContexto()) + " ut)",
                        "CPU útil " + String.format(Tema.PT, "%.1f", m.utilizacaoCpuPercentual()) + "%"));
        for (var n : linha.getChildren()) {
            HBox.setHgrow(n, Priority.ALWAYS);
        }
        return linha;
    }

    private VBox kpi(String titulo, String valor, String sub) {
        VBox v = new VBox(2, Tema.rotulo(titulo, "eyebrow"), Tema.rotulo(valor, "kpi-valor"), Tema.rotulo(sub, "kpi-sub"));
        v.getStyleClass().addAll("card", "kpi");
        v.setMaxWidth(Double.MAX_VALUE);
        return v;
    }

    private VBox tabelaProcessos(ResultadoEscalonamento r) {
        GridPane g = new GridPane();
        g.setHgap(0);
        g.setVgap(0);
        String[] cab = {"Processo", "Evento", "Chegada", "Burst", "Conclusão", "Espera", "Retorno", "Resposta"};
        for (int c = 0; c < cab.length; c++) {
            Label l = Tema.rotulo(cab[c], "tab-cab");
            l.setMaxWidth(Double.MAX_VALUE);
            g.add(l, c, 0);
            ColumnConstraints cc = new ColumnConstraints();
            cc.setHgrow(Priority.ALWAYS);
            cc.setPercentWidth(c == 0 ? 10 : c == 1 ? 33 : 9.5);
            g.getColumnConstraints().add(cc);
        }
        int linha = 1;
        for (ProcessoResultado pr : r.processos().stream()
                .sorted((a, b) -> Integer.compare(a.processo().ordemCadastro(), b.processo().ordemCadastro())).toList()) {
            var p = pr.processo();
            String[] vals = {null, Tema.nome(p.tipoEvento()) + " — " + p.descricao(), Tema.inteiro(p.tempoChegada()),
                    Tema.inteiro(p.burst()), Tema.inteiro(pr.conclusao()), Tema.inteiro(pr.tempoEspera()),
                    Tema.inteiro(pr.tempoRetorno()), Tema.inteiro(pr.tempoResposta())};
            for (int c = 0; c < vals.length; c++) {
                javafx.scene.Node n;
                if (c == 0) {
                    HBox h = new HBox(Tema.chip(p.id(), p.tipoEvento()));
                    h.getStyleClass().add("tab-cel");
                    h.setMaxWidth(Double.MAX_VALUE);
                    h.setAlignment(Pos.CENTER_LEFT);
                    n = h;
                } else {
                    Label l = c == 1 ? Tema.rotulo(vals[c], "tab-cel-texto") : Tema.rotulo(vals[c], "tab-cel", c >= 5 ? "tab-destaque" : "tab-num");
                    l.setMaxWidth(Double.MAX_VALUE);
                    n = l;
                }
                g.add(n, c, linha);
            }
            linha++;
        }
        VBox card = new VBox(10, Tema.rotulo("DETALHE POR PROCESSO", "eyebrow"), g);
        card.getStyleClass().add("card");
        card.setPadding(new Insets(16, 18, 16, 18));
        return card;
    }

    // ---------------------------------------------------------------- comparação

    /** Menor valor "redondo" (1, 2 ou 5 × 10^k) que seja pelo menos {@code bruto}. */
    private static double passoRedondo(double bruto) {
        double potencia = 1;
        while (potencia * 10 <= bruto) {
            potencia *= 10;
        }
        for (double base : new double[]{1, 2, 5, 10}) {
            if (base * potencia >= bruto) {
                return base * potencia;
            }
        }
        return 10 * potencia;
    }

    private static javafx.util.StringConverter<Number> formatadorInteiro() {
        return new javafx.util.StringConverter<>() {
            @Override
            public String toString(Number n) {
                return Tema.inteiro(Math.round(n.doubleValue()));
            }

            @Override
            public Number fromString(String s) {
                return 0;
            }
        };
    }

    private record Metrica(String nome, String dica, ToDoubleFunction<MetricasEscalonamento> f, boolean menorMelhor, boolean inteiro) {
    }

    private static final List<Metrica> METRICAS = List.of(
            new Metrica("Espera média", "menor é melhor", MetricasEscalonamento::tempoMedioEspera, true, false),
            new Metrica("Resposta média", "menor é melhor", MetricasEscalonamento::tempoMedioResposta, true, false),
            new Metrica("Retorno médio", "menor é melhor", MetricasEscalonamento::tempoMedioRetorno, true, false),
            new Metrica("Duração total", "menor é melhor", m -> m.makespan(), true, true),
            new Metrica("Trocas de contexto", "menos é melhor", m -> m.trocasContexto(), true, true),
            new Metrica("CPU útil (%)", "maior é melhor", MetricasEscalonamento::utilizacaoCpuPercentual, false, false));

    private static final String COR_FCFS = "#173b4a";
    private static final String COR_RR = "#2b9b91";

    private VBox placar(ResultadoEscalonamento f, ResultadoEscalonamento r) {
        GridPane g = new GridPane();
        g.setVgap(12);
        g.setHgap(16);
        ColumnConstraints c0 = new ColumnConstraints(150);
        ColumnConstraints c1 = new ColumnConstraints();
        c1.setHgrow(Priority.ALWAYS);
        ColumnConstraints c2 = new ColumnConstraints(110);
        g.getColumnConstraints().addAll(c0, c1, c2);

        int linha = 0;
        for (Metrica m : METRICAS) {
            double a = m.f().applyAsDouble(f.metricas());
            double b = m.f().applyAsDouble(r.metricas());
            double max = Math.max(a, b) <= 0 ? 1 : Math.max(a, b);
            boolean empate = Math.abs(a - b) < 0.005;
            boolean fcfsGanha = m.menorMelhor() ? a < b : a > b;

            VBox nome = new VBox(1, Tema.rotulo(m.nome(), "placar-nome"), Tema.rotulo(m.dica(), "placar-dica"));
            nome.setAlignment(Pos.CENTER_LEFT);
            BarraValor ba = new BarraValor(formatar(a, m), a / max, COR_FCFS, empate || fcfsGanha);
            BarraValor bb = new BarraValor(formatar(b, m), b / max, COR_RR, empate || !fcfsGanha);
            VBox barras = new VBox(2, ba, bb);

            Label selo = Tema.rotulo(empate ? "Empate" : fcfsGanha ? "FCFS" : "Round Robin", "selo");
            selo.setStyle("-fx-background-color:" + (empate ? "#e8edf0" : fcfsGanha ? COR_FCFS : COR_RR)
                    + ";-fx-text-fill:" + (empate ? "#52646d" : "white") + ";");
            HBox caixaSelo = new HBox(selo);
            caixaSelo.setAlignment(Pos.CENTER_RIGHT);
            g.add(nome, 0, linha);
            g.add(barras, 1, linha);
            g.add(caixaSelo, 2, linha);
            linha++;
        }

        HBox chave = new HBox(16, legendaCor(COR_FCFS, "FCFS"), legendaCor(COR_RR, "Round Robin"));
        chave.setAlignment(Pos.CENTER_RIGHT);
        HBox topo = new HBox(Tema.rotulo("PLACAR — MESMA CARGA, MESMOS PARÂMETROS", "eyebrow"), Tema.espaco(), chave);

        VBox leitura = new VBox(4);
        leitura.getStyleClass().add("leitura");
        leitura.getChildren().add(Tema.rotulo("LEITURA RÁPIDA", "eyebrow"));
        for (String frase : leitura(f.metricas(), r.metricas())) {
            Label l = Tema.rotulo(frase, "leitura-texto");
            l.setWrapText(true);
            leitura.getChildren().add(l);
        }

        VBox card = new VBox(14, topo, g, leitura);
        card.getStyleClass().add("card");
        card.setPadding(new Insets(16, 18, 16, 18));
        return card;
    }

    private HBox legendaCor(String cor, String texto) {
        return item(cor, texto);
    }

    private static String formatar(double v, Metrica m) {
        return m.inteiro() ? Tema.inteiro(Math.round(v)) : Tema.num(v);
    }

    private static List<String> leitura(MetricasEscalonamento f, MetricasEscalonamento r) {
        List<String> frases = new ArrayList<>();
        int vitoriasFcfs = 0;
        int vitoriasRr = 0;
        for (Metrica m : METRICAS.subList(0, 4)) {
            double a = m.f().applyAsDouble(f);
            double b = m.f().applyAsDouble(r);
            if (Math.abs(a - b) < 0.005) {
                continue;
            }
            if (a < b) {
                vitoriasFcfs++;
            } else {
                vitoriasRr++;
            }
        }
        if (vitoriasFcfs > 0 && vitoriasRr > 0) {
            frases.add("Nenhum dos dois vence em tudo: cada um é melhor em critérios diferentes.");
        } else if (vitoriasFcfs + vitoriasRr == 0) {
            frases.add("Nesta carga os dois produzem os mesmos tempos.");
        } else {
            frases.add((vitoriasFcfs > 0 ? "FCFS" : "Round Robin") + " é melhor (ou igual) em todos os tempos desta carga — o resultado depende da carga e dos parâmetros.");
        }
        frases.add("Resposta: " + (r.tempoMedioResposta() < f.tempoMedioResposta() ? "Round Robin" : f.tempoMedioResposta() < r.tempoMedioResposta() ? "FCFS" : "empate")
                + " (" + Tema.num(f.tempoMedioResposta()) + " no FCFS vs " + Tema.num(r.tempoMedioResposta()) + " no RR) — quanto tempo até cada evento começar a ser tratado.");
        frases.add("Custo: o Round Robin fez " + r.trocasContexto() + " troca(s) de contexto (" + Tema.inteiro(r.tempoTrocasContexto())
                + " ut de CPU sem trabalho útil) contra " + f.trocasContexto() + " (" + Tema.inteiro(f.tempoTrocasContexto()) + " ut) do FCFS.");
        return frases;
    }

    private VBox graficoQuantum(List<ProcessoUrbano> carga, ResultadoEscalonamento fcfs, int quantumAtual, int custo) {
        int maiorBurst = carga.stream().mapToInt(ProcessoUrbano::burst).max().orElse(1);
        long totalBurst = carga.stream().mapToLong(ProcessoUrbano::burst).sum();
        int limite = Math.max(maiorBurst, quantumAtual);
        // quantums menores que isto gerariam mais de ~100 fatias e a sobrecarga de troca esmagaria
        // a escala do gráfico: ficam fora dele (o botão Executar continua aceitando até 6.000 fatias)
        int minimo = (int) Math.max(1, Math.min(limite - 1, (totalBurst + 99) / 100));
        int passo = Math.max(1, (limite - minimo) / 29);
        Map<Integer, MetricasEscalonamento> porQuantum = new TreeMap<>();
        for (int q = minimo; q <= limite; q += passo) {
            porQuantum.put(q, new RoundRobin(q, custo).executar(carga).metricas());
        }
        porQuantum.put(limite, new RoundRobin(limite, custo).executar(carga).metricas());
        porQuantum.put(quantumAtual, new RoundRobin(quantumAtual, custo).executar(carga).metricas());
        final int quantumMinimo = porQuantum.keySet().iterator().next();

        Segmentado<Metrica> escolha = new Segmentado<>(
                List.of(METRICAS.get(0), METRICAS.get(1), METRICAS.get(3), METRICAS.get(4)), Metrica::nome);
        VBox graficoBox = new VBox();
        Runnable desenhar = () -> graficoBox.getChildren().setAll(
                construirLinha(escolha.valor(), porQuantum, fcfs.metricas(), quantumAtual, quantumMinimo, limite));
        escolha.valorProperty().addListener((o, a, b) -> desenhar.run());
        desenhar.run();

        Label nota = Tema.rotulo("Com quantum maior ou igual ao maior burst (" + Tema.inteiro(maiorBurst)
                + ") o Round Robin nunca preempta e se comporta como o FCFS. Quantum pequeno demais gera trocas demais"
                + (quantumMinimo > 1 ? " (quantums abaixo de " + Tema.inteiro(quantumMinimo) + " geram centenas de fatias e não são plotados)." : "."),
                "leitura-texto");
        nota.setWrapText(true);
        HBox topo = new HBox(12, Tema.rotulo("EFEITO DO QUANTUM NO ROUND ROBIN", "eyebrow"), Tema.espaco(), escolha);
        topo.setAlignment(Pos.CENTER_LEFT);
        VBox card = new VBox(10, topo, graficoBox, nota);
        card.getStyleClass().add("card");
        card.setPadding(new Insets(16, 18, 16, 18));
        return card;
    }

    private LineChart<Number, Number> construirLinha(Metrica m, Map<Integer, MetricasEscalonamento> dados,
                                                    MetricasEscalonamento fcfs, int quantumAtual, int minimo, int limite) {
        double passoX = passoRedondo((limite - minimo) / 8.0);
        NumberAxis x = new NumberAxis(0, Math.ceil(Math.max(minimo + 1, limite) / passoX) * passoX, passoX);
        x.setLabel("quantum (ut)");
        x.setTickLabelFormatter(formatadorInteiro());
        NumberAxis y = new NumberAxis();
        y.setTickLabelFormatter(formatadorInteiro());
        y.setForceZeroInRange(true);
        y.setLabel(m.nome());
        LineChart<Number, Number> grafico = new LineChart<>(x, y);
        grafico.setAnimated(false);
        grafico.setPrefHeight(260);
        grafico.setMinHeight(260);
        grafico.setLegendVisible(true);
        grafico.setCreateSymbols(true);

        XYChart.Series<Number, Number> sRr = new XYChart.Series<>();
        sRr.setName("Round Robin");
        dados.forEach((q, met) -> sRr.getData().add(new XYChart.Data<>(q, m.f().applyAsDouble(met))));
        XYChart.Series<Number, Number> sF = new XYChart.Series<>();
        sF.setName("FCFS (referência)");
        double vf = m.f().applyAsDouble(fcfs);
        sF.getData().add(new XYChart.Data<>(minimo, vf));
        sF.getData().add(new XYChart.Data<>(Math.max(minimo + 1, limite), vf));
        XYChart.Series<Number, Number> sA = new XYChart.Series<>();
        sA.setName("quantum escolhido (" + Tema.inteiro(quantumAtual) + ")");
        sA.getData().add(new XYChart.Data<>(quantumAtual, m.f().applyAsDouble(dados.get(quantumAtual))));
        grafico.getData().addAll(List.of(sRr, sF, sA));
        return grafico;
    }
}
