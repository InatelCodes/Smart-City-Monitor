package br.smartcity.monitor.escalonamento.ui;

import br.smartcity.monitor.escalonamento.model.ProcessoUrbano;
import br.smartcity.monitor.model.TipoEvento;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.Optional;

/**
 * Aba "Escalonamento de CPU & Sincronismo".
 * Barra lateral recolhível (carga + parâmetros) e duas páginas: CPU e Sincronismo.
 */
public final class EscalonamentoView extends BorderPane {

    private static final String PAGINA_CPU = "Escalonamento de CPU";
    private static final String PAGINA_INTEGRADA = "Sincronismo × escalonamento";
    private static final String PAGINA_THREADS = "Prova com threads reais";

    private final EscalonamentoController controller = new EscalonamentoController();
    private Contador quantum;
    private Contador custo;
    private Contador escrita;
    private final VBox listaProcessos = new VBox(8);
    private final Label contagem = Tema.rotulo("0", "contagem");
    private final PaginaCpu paginaCpu = new PaginaCpu(controller);
    private final PaginaIntegrada paginaIntegrada = new PaginaIntegrada(controller);
    private final PaginaSincronismo paginaSync = new PaginaSincronismo(controller);

    public EscalonamentoView() {
        getStyleClass().add("esc-root");
        getStylesheets().add(getClass().getResource("escalonamento.css").toExternalForm());

        Node lateral = criarLateral();
        lateral.managedProperty().bind(lateral.visibleProperty());

        ToggleButton alternarLateral = new ToggleButton("☰  Carga e parâmetros");
        alternarLateral.getStyleClass().add("btn-secundario");
        alternarLateral.setSelected(true);
        lateral.visibleProperty().bind(alternarLateral.selectedProperty());

        Segmentado<String> paginas = new Segmentado<>(
                List.of(PAGINA_CPU, PAGINA_INTEGRADA, PAGINA_THREADS), s -> s);
        paginas.getStyleClass().add("segmentado-grande");

        Label titulo = Tema.rotulo("Smart City Monitor", "topo-marca");
        Label sub = Tema.rotulo("Escalonamento & Sincronismo", "topo-titulo");
        HBox topo = new HBox(14, alternarLateral, new VBox(0, titulo, sub), Tema.espaco(), paginas);
        topo.setAlignment(Pos.CENTER_LEFT);
        topo.getStyleClass().add("topo");

        StackPane paginasPilha = new StackPane(paginaCpu, paginaIntegrada, paginaSync);
        paginaIntegrada.setVisible(false);
        paginaSync.setVisible(false);
        paginaCpu.setVisible(true);
        paginas.valorProperty().addListener((obs, a, b) -> {
            paginaCpu.setVisible(PAGINA_CPU.equals(b));
            paginaIntegrada.setVisible(PAGINA_INTEGRADA.equals(b));
            paginaSync.setVisible(PAGINA_THREADS.equals(b));
        });
        ScrollPane rolagem = new ScrollPane(paginasPilha);
        rolagem.setFitToWidth(true);
        rolagem.getStyleClass().add("rolagem-principal");

        setTop(topo);
        setLeft(lateral);
        setCenter(rolagem);
        atualizarLista();
    }

    // ------------------------------------------------------------------ lateral

    private Node criarLateral() {
        Button novo = new Button("＋  Novo processo");
        novo.getStyleClass().add("btn-primario");
        novo.setMaxWidth(Double.MAX_VALUE);
        novo.setOnAction(e -> abrirDialogo(null));
        MenuButton exemplo = new MenuButton("Cenários");
        exemplo.getStyleClass().add("btn-secundario");
        exemplo.setMinWidth(Region.USE_PREF_SIZE);
        exemplo.setMaxWidth(Double.MAX_VALUE);
        for (CenarioExemplo cenario : CenarioExemplo.values()) {
            MenuItem item = new MenuItem(cenario.titulo());
            item.setOnAction(e -> {
                alterarCarga(() -> controller.carregarCenario(cenario));
                sincronizarParametros();
            });
            exemplo.getItems().add(item);
        }
        Button limpar = new Button("Limpar");
        limpar.getStyleClass().add("btn-secundario");
        limpar.setMinWidth(Region.USE_PREF_SIZE);
        limpar.setMaxWidth(Double.MAX_VALUE);
        limpar.setOnAction(e -> alterarCarga(controller::limparCarga));
        HBox.setHgrow(exemplo, Priority.ALWAYS);
        HBox.setHgrow(limpar, Priority.ALWAYS);

        ScrollPane rolagem = new ScrollPane(listaProcessos);
        rolagem.setFitToWidth(true);
        rolagem.getStyleClass().add("rolagem-lista");
        VBox.setVgrow(rolagem, Priority.ALWAYS);

        HBox cabecalho = new HBox(8, Tema.rotulo("CARGA DE PROCESSOS", "eyebrow"), contagem);
        cabecalho.setAlignment(Pos.CENTER_LEFT);

        quantum = new Contador(controller.getQuantum(), 1, EscalonamentoController.VALOR_MAXIMO, 1_000);
        quantum.valorProperty().addListener((obs, a, b) -> {
            controller.setQuantum(b.intValue());
            paginaCpu.limpar();
            paginaIntegrada.limpar();
        });
        custo = new Contador(controller.getCustoTrocaContexto(), 0, EscalonamentoController.VALOR_MAXIMO, 100);
        custo.valorProperty().addListener((obs, a, b) -> {
            controller.setCustoTrocaContexto(b.intValue());
            paginaCpu.limpar();
            paginaIntegrada.limpar();
        });
        escrita = new Contador(controller.getTamanhoEscrita(), 1, EscalonamentoController.VALOR_MAXIMO, 1_000);
        escrita.valorProperty().addListener((obs, a, b) -> {
            controller.setTamanhoEscrita(b.intValue());
            paginaIntegrada.limpar();
        });

        VBox parametros = new VBox(8,
                Tema.rotulo("PARÂMETROS", "eyebrow"),
                parametro("Quantum (Round Robin)", "fatia máxima de CPU por vez (ut)", quantum),
                parametro("Troca de contexto", "custo de cada troca (ut)", custo),
                parametro("Escrita no arquivo", "fim do burst, região crítica (ut)", escrita));

        VBox lateral = new VBox(14, cabecalho, novo, rolagem, new HBox(8, exemplo, limpar), parametros);
        lateral.getStyleClass().add("lateral");
        lateral.setPrefWidth(300);
        lateral.setMinWidth(300);
        return lateral;
    }

    /** Reflete nos contadores os parâmetros que o controller acabou de receber (ex.: cenário). */
    private void sincronizarParametros() {
        quantum.valorProperty().set(controller.getQuantum());
        custo.valorProperty().set(controller.getCustoTrocaContexto());
        escrita.valorProperty().set(controller.getTamanhoEscrita());
    }

    private VBox parametro(String nome, String dica, Contador contador) {
        VBox v = new VBox(2, Tema.rotulo(nome, "param-nome"), Tema.rotulo(dica, "param-dica"), contador);
        v.setPadding(new Insets(0, 0, 4, 0));
        return v;
    }

    private void atualizarLista() {
        listaProcessos.getChildren().clear();
        List<ProcessoUrbano> processos = controller.obterProcessos();
        contagem.setText(String.valueOf(processos.size()));
        int maior = processos.stream().mapToInt(ProcessoUrbano::burst).max().orElse(1);
        for (ProcessoUrbano p : processos) {
            listaProcessos.getChildren().add(cartaoProcesso(p, maior));
        }
        if (processos.isEmpty()) {
            listaProcessos.getChildren().add(Tema.rotulo("Nenhum processo.\nAdicione um para começar.", "vazio"));
        }
    }

    private Node cartaoProcesso(ProcessoUrbano p, int maiorBurst) {
        String cor = Tema.hex(p.tipoEvento());
        Region faixa = new Region();
        faixa.setMinWidth(5);
        faixa.setMaxWidth(5);
        faixa.setStyle("-fx-background-color:" + cor + ";-fx-background-radius:5 0 0 5;");

        Label id = Tema.rotulo(p.id(), "proc-id");
        Label tipo = Tema.rotulo(Tema.nome(p.tipoEvento()), "proc-tipo");
        tipo.setStyle("-fx-text-fill:" + cor + ";");
        Label desc = Tema.rotulo(p.descricao(), "proc-desc");
        desc.setWrapText(true);

        Region fundoBarra = new Region();
        fundoBarra.getStyleClass().add("burst-fundo");
        Region barra = new Region();
        barra.setStyle("-fx-background-color:" + cor + ";-fx-background-radius:3;");
        barra.setMaxHeight(6);
        StackPane burst = new StackPane(fundoBarra, barra);
        burst.setAlignment(Pos.CENTER_LEFT);
        burst.setMaxHeight(6);
        burst.widthProperty().addListener((o, a, w) -> barra.setPrefWidth(w.doubleValue() * p.burst() / maiorBurst));
        barra.setPrefWidth(10);
        barra.setMaxWidth(Region.USE_PREF_SIZE);

        Label tempos = Tema.rotulo("chega em " + Tema.inteiro(p.tempoChegada()) + "  ·  burst " + Tema.inteiro(p.burst()), "proc-tempos");

        Button editar = new Button("✎");
        editar.getStyleClass().add("icone-btn");
        editar.setOnAction(e -> abrirDialogo(p));
        Button remover = new Button("✕");
        remover.getStyleClass().addAll("icone-btn", "icone-perigo");
        remover.setOnAction(e -> alterarCarga(() -> controller.removerProcesso(p.id())));

        HBox linha1 = new HBox(8, id, tipo, Tema.espaco(), editar, remover);
        linha1.setAlignment(Pos.CENTER_LEFT);
        VBox corpo = new VBox(4, linha1, desc, tempos, burst);
        corpo.setPadding(new Insets(8, 10, 9, 10));
        HBox.setHgrow(corpo, Priority.ALWAYS);

        HBox cartao = new HBox(faixa, corpo);
        cartao.getStyleClass().add("proc-cartao");
        return cartao;
    }

    private void alterarCarga(Runnable acao) {
        try {
            acao.run();
        } catch (RuntimeException ex) {
            new Alert(Alert.AlertType.ERROR, ex.getMessage(), ButtonType.OK).showAndWait();
            return;
        }
        atualizarLista();
        paginaCpu.limpar();
        paginaIntegrada.limpar();
        paginaSync.limpar();
    }

    // ------------------------------------------------------------------ diálogo

    private void abrirDialogo(ProcessoUrbano existente) {
        Dialog<ButtonType> dialogo = new Dialog<>();
        dialogo.setTitle(existente == null ? "Novo processo" : "Editar " + existente.id());
        dialogo.getDialogPane().getStylesheets().add(getClass().getResource("escalonamento.css").toExternalForm());
        dialogo.getDialogPane().getStyleClass().add("dialogo");
        dialogo.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        TextField id = new TextField(existente != null ? existente.id() : proximoId());
        ComboBox<TipoEvento> tipo = new ComboBox<>();
        tipo.getItems().addAll(TipoEvento.values());
        tipo.setValue(existente != null ? existente.tipoEvento() : TipoEvento.TRANSITO);
        tipo.setMaxWidth(Double.MAX_VALUE);
        tipo.setCellFactory(l -> celulaTipo());
        tipo.setButtonCell(celulaTipo());
        TextField descricao = new TextField(existente != null ? existente.descricao() : "");
        descricao.setPromptText("Ex.: Congestionamento na Av. Central");
        Contador chegada = new Contador(existente != null ? existente.tempoChegada() : 0,
                0, EscalonamentoController.VALOR_MAXIMO, 1_000);
        Contador burst = new Contador(existente != null ? existente.burst() : 10_000,
                1, EscalonamentoController.VALOR_MAXIMO, 1_000);

        GridPane g = new GridPane();
        g.setHgap(12);
        g.setVgap(10);
        g.setPadding(new Insets(10, 4, 4, 4));
        g.addRow(0, Tema.rotulo("ID", "param-nome"), id);
        g.addRow(1, Tema.rotulo("Tipo de evento", "param-nome"), tipo);
        g.addRow(2, Tema.rotulo("Descrição", "param-nome"), descricao);
        g.addRow(3, Tema.rotulo("Tempo de chegada (ut)", "param-nome"), chegada);
        g.addRow(4, Tema.rotulo("Burst — tempo de CPU (ut)", "param-nome"), burst);
        dialogo.getDialogPane().setContent(g);

        Optional<ButtonType> resposta = dialogo.showAndWait();
        if (resposta.isPresent() && resposta.get() == ButtonType.OK) {
            int c = chegada.valor();
            int b = burst.valor();
            alterarCarga(() -> {
                if (existente == null) {
                    controller.adicionarProcesso(id.getText(), tipo.getValue(), descricao.getText(), c, b);
                } else {
                    controller.editarProcesso(existente.id(), id.getText(), tipo.getValue(), descricao.getText(), c, b);
                }
            });
        }
    }

    private ListCell<TipoEvento> celulaTipo() {
        return new ListCell<>() {
            @Override
            protected void updateItem(TipoEvento item, boolean vazio) {
                super.updateItem(item, vazio);
                setText(vazio || item == null ? null : Tema.nome(item));
            }
        };
    }

    private String proximoId() {
        int n = controller.obterProcessos().size() + 1;
        while (true) {
            String candidato = "P" + n;
            boolean existe = controller.obterProcessos().stream().anyMatch(p -> p.id().equals(candidato));
            if (!existe) {
                return candidato;
            }
            n++;
        }
    }
}
