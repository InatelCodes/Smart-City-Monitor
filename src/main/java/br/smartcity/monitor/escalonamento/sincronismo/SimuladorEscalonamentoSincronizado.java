package br.smartcity.monitor.escalonamento.sincronismo;

import br.smartcity.monitor.escalonamento.model.ProcessoResultado;
import br.smartcity.monitor.escalonamento.model.ProcessoUrbano;
import br.smartcity.monitor.escalonamento.model.ResultadoEscalonamento;
import br.smartcity.monitor.escalonamento.model.TipoTrechoExecucao;
import br.smartcity.monitor.escalonamento.model.TrechoExecucao;
import br.smartcity.monitor.escalonamento.simulacao.CalculadoraMetricas;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Executa FCFS ou Round Robin sobre uma carga em que cada processo, ao final do seu burst,
 * escreve um registro no arquivo compartilhado (região crítica).
 *
 * <p>Modelo (tempo lógico, determinístico):
 * <ul>
 *   <li>Os últimos {@code min(burst, tamanhoEscrita)} tempos de CPU de cada processo são a escrita.</li>
 *   <li>Sem sincronismo: nada muda no escalonamento; se um processo é preemptado no meio da escrita
 *       e outro escreve antes de ele terminar, os registros se misturam.</li>
 *   <li>Com sincronismo: o processo pega o lock ao começar a escrita e só o solta ao terminar.
 *       Quem chega na região crítica com o lock ocupado dorme (não usa CPU) e volta à fila de pronto
 *       quando o dono libera o lock.</li>
 * </ul>
 * Sem lock, a ordem de execução é idêntica à de {@code Fcfs}/{@code RoundRobin}.
 */
public final class SimuladorEscalonamentoSincronizado {

    public ExecucaoIntegrada executar(
            List<ProcessoUrbano> entrada,
            PoliticaEscalonamento politica,
            int quantum,
            int custoTrocaContexto,
            int tamanhoEscrita,
            ModoSincronizacao modo
    ) {
        if (entrada == null || entrada.isEmpty()) {
            throw new IllegalArgumentException("A carga deve possuir pelo menos um processo");
        }
        if (politica == null || modo == null) {
            throw new IllegalArgumentException("Política e modo são obrigatórios");
        }
        if (politica == PoliticaEscalonamento.ROUND_ROBIN && quantum <= 0) {
            throw new IllegalArgumentException("O quantum deve ser maior que zero");
        }
        if (custoTrocaContexto < 0) {
            throw new IllegalArgumentException("O custo de troca não pode ser negativo");
        }
        if (tamanhoEscrita < 1) {
            throw new IllegalArgumentException("A escrita deve durar pelo menos 1 unidade");
        }
        List<ProcessoUrbano> carga = List.copyOf(entrada);
        Set<String> ids = new HashSet<>();
        for (ProcessoUrbano p : carga) {
            if (p == null || !ids.add(p.id())) {
                throw new IllegalArgumentException("Processo nulo ou ID duplicado");
            }
        }

        boolean usaLock = modo == ModoSincronizacao.COM_SINCRONISMO;
        List<ProcessoUrbano> chegada = carga.stream()
                .sorted(Comparator.comparingInt(ProcessoUrbano::tempoChegada)
                        .thenComparingInt(ProcessoUrbano::ordemCadastro))
                .toList();

        Map<String, Integer> restante = new HashMap<>();
        Map<String, Integer> inicioEscrita = new HashMap<>();
        Map<String, Integer> primeira = new HashMap<>();
        Map<String, Integer> conclusao = new HashMap<>();
        Map<String, Integer> bloqueadoDesde = new HashMap<>();
        for (ProcessoUrbano p : carga) {
            restante.put(p.id(), p.burst());
            inicioEscrita.put(p.id(), p.burst() - Math.min(p.burst(), tamanhoEscrita));
        }

        ArrayDeque<ProcessoUrbano> prontos = new ArrayDeque<>();
        List<ProcessoUrbano> bloqueados = new ArrayList<>();
        List<Bloqueio> bloqueios = new ArrayList<>();
        List<TrechoExecucao> gantt = new ArrayList<>();
        String dono = null;
        int relogio = 0;
        int indice = 0;
        ProcessoUrbano anterior = null;

        while (conclusao.size() < carga.size()) {
            indice = admitir(chegada, indice, relogio, prontos);

            ProcessoUrbano atual = null;
            while (!prontos.isEmpty()) {
                ProcessoUrbano candidato = prontos.peekFirst();
                int executado = candidato.burst() - restante.get(candidato.id());
                boolean vaiBloquear = usaLock
                        && executado == inicioEscrita.get(candidato.id())
                        && dono != null
                        && !dono.equals(candidato.id());
                prontos.removeFirst();
                if (vaiBloquear) {
                    bloqueados.add(candidato);
                    bloqueadoDesde.put(candidato.id(), relogio);
                } else {
                    atual = candidato;
                    break;
                }
            }

            if (atual == null) {
                if (indice >= chegada.size()) {
                    throw new IllegalStateException("Nenhum processo executável: estado inesperado");
                }
                int proxima = chegada.get(indice).tempoChegada();
                if (proxima > relogio) {
                    gantt.add(TrechoExecucao.ocioso(relogio, proxima));
                    relogio = proxima;
                }
                anterior = null;
                continue;
            }

            if (anterior != null && !anterior.id().equals(atual.id()) && custoTrocaContexto > 0) {
                int fim = Math.addExact(relogio, custoTrocaContexto);
                gantt.add(TrechoExecucao.trocaContexto(relogio, fim));
                relogio = fim;
                indice = admitir(chegada, indice, relogio, prontos);
            }

            String id = atual.id();
            int limite = politica == PoliticaEscalonamento.FCFS
                    ? restante.get(id)
                    : Math.min(quantum, restante.get(id));
            int usados = 0;
            boolean bloqueou = false;
            int inicioRodada = relogio;
            while (usados < limite) {
                int resta = restante.get(id);
                int executado = atual.burst() - resta;
                int marca = inicioEscrita.get(id);
                if (usaLock && executado == marca && !id.equals(dono)) {
                    if (dono != null) {
                        bloqueou = true;
                        break;
                    }
                    dono = id;
                }
                int trecho = executado < marca
                        ? Math.min(limite - usados, marca - executado)
                        : Math.min(limite - usados, resta);
                usados += trecho;
                restante.put(id, resta - trecho);
            }
            if (usados > 0) {
                relogio = Math.addExact(relogio, usados);
                gantt.add(TrechoExecucao.doProcesso(atual, inicioRodada, relogio));
                primeira.putIfAbsent(id, inicioRodada);
            }
            indice = admitir(chegada, indice, relogio, prontos);

            if (restante.get(id) == 0) {
                conclusao.put(id, relogio);
                if (usaLock && id.equals(dono)) {
                    dono = null;
                    for (ProcessoUrbano b : bloqueados) {
                        int desde = bloqueadoDesde.get(b.id());
                        if (relogio > desde) {
                            bloqueios.add(new Bloqueio(b.id(), desde, relogio));
                        }
                        prontos.addLast(b);
                    }
                    bloqueados.clear();
                }
            } else if (bloqueou) {
                bloqueados.add(atual);
                bloqueadoDesde.put(id, relogio);
            } else {
                prontos.addLast(atual);
            }
            anterior = atual;
        }

        List<ProcessoResultado> resultados = new ArrayList<>();
        for (ProcessoUrbano p : carga.stream()
                .sorted(Comparator.comparingInt(ProcessoUrbano::ordemCadastro)).toList()) {
            resultados.add(ProcessoResultado.criar(p, primeira.get(p.id()), conclusao.get(p.id())));
        }
        ResultadoEscalonamento resultado = new ResultadoEscalonamento(
                politica.nome(), gantt, resultados, CalculadoraMetricas.calcular(resultados, gantt));

        List<TrechoEscrita> escritas = extrairEscritas(carga, gantt, inicioEscrita);
        Arquivo arquivo = montarArquivo(carga, escritas, inicioEscrita);
        return new ExecucaoIntegrada(politica, modo, politica == PoliticaEscalonamento.FCFS ? 0 : quantum,
                tamanhoEscrita, resultado, escritas, bloqueios, arquivo.registros(), arquivo.conteudo());
    }

    private static int admitir(List<ProcessoUrbano> chegada, int indice, int relogio, ArrayDeque<ProcessoUrbano> prontos) {
        while (indice < chegada.size() && chegada.get(indice).tempoChegada() <= relogio) {
            prontos.addLast(chegada.get(indice++));
        }
        return indice;
    }

    /** Converte os trechos de CPU de cada processo nos intervalos em que ele estava escrevendo. */
    private static List<TrechoEscrita> extrairEscritas(
            List<ProcessoUrbano> carga, List<TrechoExecucao> gantt, Map<String, Integer> inicioEscrita) {
        Map<String, Integer> acumulado = new HashMap<>();
        Map<String, Integer> burst = new HashMap<>();
        for (ProcessoUrbano p : carga) {
            burst.put(p.id(), p.burst());
        }
        List<TrechoEscrita> escritas = new ArrayList<>();
        for (TrechoExecucao t : gantt) {
            if (t.tipo() != TipoTrechoExecucao.PROCESSO) {
                continue;
            }
            String id = t.processoId();
            int antes = acumulado.getOrDefault(id, 0);
            int depois = antes + t.duracao();
            acumulado.put(id, depois);
            int de = Math.max(antes, inicioEscrita.get(id));
            int ate = Math.min(depois, burst.get(id));
            if (ate > de) {
                escritas.add(new TrechoEscrita(id, t.inicio() + (de - antes), t.inicio() + (ate - antes)));
            }
        }
        return escritas;
    }

    private record Arquivo(List<RegistroSimulado> registros, String conteudo) {
    }

    /**
     * Cada registro é gravado em tantos fragmentos quanto as unidades de escrita; os fragmentos
     * entram no arquivo na ordem em que a CPU os executou. Um registro só é íntegro se sua linha
     * completa aparecer no arquivo (mesmo critério do experimento com threads reais).
     */
    private static Arquivo montarArquivo(
            List<ProcessoUrbano> carga, List<TrechoEscrita> escritas, Map<String, Integer> inicioEscrita) {
        Map<String, ProcessoUrbano> porId = new HashMap<>();
        for (ProcessoUrbano p : carga) {
            porId.put(p.id(), p);
        }
        record Fragmento(int tempo, String texto) {
        }
        List<Fragmento> fragmentos = new ArrayList<>();
        Map<String, Integer> indiceUnidade = new HashMap<>();
        for (TrechoEscrita e : escritas) {
            ProcessoUrbano p = porId.get(e.processoId());
            String linha = linhaEsperada(p) + "\n";
            int unidades = p.burst() - inicioEscrita.get(p.id());
            for (int t = e.inicio(); t < e.fim(); t++) {
                int k = indiceUnidade.merge(p.id(), 1, Integer::sum) - 1;
                int de = k * linha.length() / unidades;
                int ate = (k + 1) * linha.length() / unidades;
                fragmentos.add(new Fragmento(t, linha.substring(de, ate)));
            }
        }
        fragmentos.sort(Comparator.comparingInt(Fragmento::tempo));
        StringBuilder conteudo = new StringBuilder();
        for (Fragmento f : fragmentos) {
            conteudo.append(f.texto());
        }
        Set<String> linhas = new HashSet<>(List.of(conteudo.toString().split("\n")));
        List<RegistroSimulado> registros = new ArrayList<>();
        for (ProcessoUrbano p : carga.stream()
                .sorted(Comparator.comparingInt(ProcessoUrbano::ordemCadastro)).toList()) {
            String esperada = linhaEsperada(p);
            registros.add(new RegistroSimulado(p.id(), esperada, linhas.contains(esperada)));
        }
        return new Arquivo(registros, conteudo.toString());
    }

    /** Mesmo formato do arquivo do experimento com threads reais. */
    public static String linhaEsperada(ProcessoUrbano p) {
        return "INICIO|" + sanitizar(p.id()) + "|" + p.tipoEvento() + "|" + sanitizar(p.descricao()) + "|FIM";
    }

    private static String sanitizar(String valor) {
        return valor.replace("\\", "\\\\").replace("|", "\\|").replace("\r", "\\r").replace("\n", "\\n");
    }
}
