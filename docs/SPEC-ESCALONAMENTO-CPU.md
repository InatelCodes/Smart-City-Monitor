# Spec — Simulador Smart City de Escalonamento de CPU

> **Atualização:** o sincronismo agora também é simulado em tempo lógico **sobre** FCFS e Round Robin
> (região crítica ao fim do burst, lock com bloqueio) e os valores padrão de teste foram escalados para
> dezenas de milhares de ut. O modelo, os cenários e os resultados estão no [README](../README.md) e em
> [POR-QUE-SEM-LOCK-PODE-SER-MAIOR.md](POR-QUE-SEM-LOCK-PODE-SER-MAIOR.md). O restante desta spec
> permanece válido como contrato dos algoritmos.


## 1. Contexto

O Trabalho Prático 3 pede um simulador capaz de representar a fila de pronto
(ready queue) de um sistema operacional e escalonar processos. A solução será
desenvolvida no repositório existente Smart City Monitor, mantendo Java 17,
JavaFX, Maven e JUnit.

O simulador deverá implementar e comparar dois algoritmos:

- **FCFS (First-Come, First-Served)** — não preemptivo;
- **Round Robin (RR)** — preemptivo e configurado por quantum.

Também deverá demonstrar concorrência sobre um arquivo físico compartilhado,
permitindo executar a mesma carga com e sem sincronização.

Os dois algoritmos sempre receberão cópias da mesma lista de processos. Assim,
os resultados poderão ser comparados sem diferenças na carga do experimento.

### 1.1 Narrativa Smart City

O simulador representará o processamento de eventos recebidos pela Central de
Monitoramento da cidade. Cada evento de sensor gera um **processo urbano** que
precisa de tempo de CPU para ser analisado. Enquanto aguarda a Central, esse
processo permanece na ready queue.

Serão reutilizados os tipos já existentes no projeto:

- TRANSITO;
- CLIMA;
- ENERGIA;
- QUALIDADE_AR.

A relação entre o sistema atual e o novo simulador será:

| Smart City Monitor atual | Simulador de CPU |
| --- | --- |
| Evento detectado por sensor | Origem de um ProcessoUrbano |
| TipoEvento | Categoria urbana do processo |
| Entrada do evento na Central | Tempo lógico de chegada |
| Tempo necessário para analisar o evento | Burst de CPU |
| Fila de eventos | Ready queue |
| Central de Monitoramento | CPU e escalonador |
| Evento processado | Processo finalizado |
| Métricas do dashboard | Espera, resposta, retorno e vazão |

O tipo urbano fornece contexto e identidade visual, mas não altera a ordem dos
algoritmos. FCFS usa somente a chegada e Round Robin usa chegada e quantum. Isso
mantém a simulação fiel aos algoritmos pedidos, sem introduzir prioridade.

O monitor concorrente existente continuará demonstrando várias threads
consumidoras. A nova aba será um experimento didático separado que simula uma
única CPU e compara políticas de escalonamento.

## 2. Objetivo

Permitir que o usuário cadastre eventos urbanos como processos, acompanhe
visualmente a execução da Central e da fila de pronto em cada algoritmo e
compare os resultados por diagramas de Gantt e métricas.

O sistema também permitirá observar o conflito de escrita entre sensores e o
efeito do lock: integridade dos registros em troca de espera pelo recurso.

O produto deve ajudar a responder durante a apresentação:

1. Em que ordem os processos usam a CPU?
2. Quando e por que um processo volta para a fila de pronto?
3. Qual algoritmo teve menor tempo médio de espera, resposta e retorno?
4. Qual é o custo das preempções do Round Robin?
5. Como o quantum altera o resultado do Round Robin?
6. O que acontece quando vários sensores escrevem no mesmo arquivo sem lock?
7. Quanto tempo de espera é introduzido quando o sincronismo é ativado?

## 3. Escopo funcional

### 3.1 Cadastro da carga

O usuário poderá adicionar, editar e remover processos urbanos antes da
simulação.

| Campo | Tipo | Regra |
| --- | --- | --- |
| ID | texto | obrigatório e único; sugestão automática P1, P2, ... |
| Tipo do evento | TipoEvento | obrigatório; usa os quatro tipos existentes |
| Descrição | texto | obrigatória; descreve o alerta urbano |
| Tempo de chegada | inteiro | maior ou igual a zero |
| Burst de CPU | inteiro | maior que zero |

Os tempos serão expressos em unidades lógicas, sem vínculo com milissegundos
reais. A velocidade da animação não poderá alterar os resultados.

A tela deverá oferecer inclusão, edição e remoção de processos, uma carga de
exemplo, limpeza da carga, restauração do exemplo e validação dos campos com
mensagem compreensível. A carga não poderá ser alterada durante uma execução.

A ready queue, a CPU e o Gantt deverão mostrar o ID e o tipo do evento. As cores
usadas para cada TipoEvento devem seguir as cores já utilizadas pelo dashboard.

### 3.2 Configuração

O usuário poderá definir o quantum do Round Robin como inteiro maior que zero.
O valor inicial recomendado é 2.

Também poderá definir:

- **sincronismo do arquivo**: ligado ou desligado;
- **custo de troca de contexto**: inteiro maior ou igual a zero, inicialmente 1
  unidade lógica.

A mesma lista, os mesmos tempos de chegada e os mesmos bursts serão usados nos
dois algoritmos. A comparação de sincronismo também reutilizará a mesma carga;
somente o uso do lock poderá mudar entre as duas execuções.

### 3.3 Execução

O simulador deverá oferecer:

- **Comparar algoritmos**: calcula FCFS e Round Robin para a mesma carga;
- **Testar sem sincronismo**: sensores escrevem concorrentemente sem lock;
- **Testar com sincronismo**: sensores escrevem no mesmo arquivo usando lock;
- **Reproduzir/Pausar**: controla a animação dos resultados calculados;
- **Próximo passo**: avança uma unidade lógica de tempo;
- **Reiniciar**: retorna a visualização ao tempo zero;
- **Nova simulação**: libera a carga e limpa os resultados.

O cálculo dos algoritmos deve ser determinístico e separado da animação. Essa
simulação será orientada a eventos ou passos lógicos e não criará uma thread
Java para cada processo. Cada algoritmo representa uma única CPU.

O experimento de sincronismo é distinto: ele usará threads reais para representar
sensores concorrentes acessando o mesmo arquivo físico.

### 3.4 Visualização obrigatória

A interface terá uma configuração comum e dois painéis de resultado, um para
FCFS e outro para Round Robin. Cada painel deverá exibir:

- relógio lógico;
- processo na CPU ou estado OCIOSA;
- fila de pronto na ordem de atendimento;
- estado de cada processo: NOVO, PRONTO, EXECUTANDO ou FINALIZADO;
- tipo e descrição do evento urbano associado;
- progresso do burst do processo em execução;
- diagrama de Gantt;
- métricas consolidadas.

Uma tabela deverá colocar os resultados lado a lado e destacar o menor valor
nas métricas em que menor é melhor. Empates serão exibidos como empate, sem
declarar vencedor geral. A interface explicará que o melhor algoritmo depende
da carga e do critério analisado.

### 3.5 Experimento de sincronismo

Todos os sensores compartilharão um arquivo de texto físico. Cada processo
urbano originará uma thread de sensor que tentará gravar um registro no formato:

~~~text
INICIO|ID|TIPO_EVENTO|DESCRICAO|FIM
~~~

Cada registro será escrito em fragmentos para tornar a região crítica
observável.

No modo **sem sincronismo**, os fragmentos de sensores diferentes poderão se
intercalar. A aplicação deverá exibir o conteúdo produzido e contar os registros
válidos e corrompidos.

No modo **com sincronismo**, um ReentrantLock protegerá o registro completo.
Enquanto um sensor escreve, os demais aguardarão. Todos os registros deverão
ficar íntegros.

Para cada modo, a interface exibirá:

- caminho do arquivo físico;
- conteúdo final;
- registros esperados, válidos e corrompidos;
- duração total real do experimento;
- tempo médio real de espera para adquirir o lock;
- indicação visual de colisão detectada ou arquivo íntegro.

As duas execuções deverão usar cópias da mesma carga. A ordem exata das threads
sem sincronismo poderá variar, mas a aplicação provocará intercalação suficiente
para tornar o conflito reproduzível durante a apresentação.

## 4. Regras dos algoritmos

### 4.1 Regras comuns

1. A CPU começa no tempo lógico 0.
2. No início de cada instante, os processos que acabam de chegar entram na fila.
3. Se a fila estiver vazia, a CPU fica ociosa até a próxima chegada; esse
   intervalo deve aparecer no Gantt.
4. Empates de chegada são resolvidos pela ordem original de cadastro.
5. Um processo nunca pode estar em mais de um estado ao mesmo tempo.
6. Nenhum processo pode executar antes de chegar.
7. A soma dos tempos executados deve ser igual ao burst.
8. A simulação termina quando todos os processos estiverem FINALIZADO.
9. Quando a CPU passar diretamente para outro processo, o custo configurado da
   troca de contexto avançará o relógio e aparecerá no Gantt.
10. Chegadas ocorridas durante uma troca entrarão normalmente na ready queue.
11. Não haverá custo na primeira alocação, após ociosidade ou quando o mesmo
    processo continuar executando.

### 4.2 FCFS

1. A fila de pronto é FIFO.
2. O primeiro processo pronto recebe a CPU.
3. Ele permanece na CPU até concluir todo o burst.
4. Não existe preempção nem quantum.

### 4.3 Round Robin

1. A fila de pronto é FIFO.
2. O primeiro processo recebe a CPU por, no máximo, um quantum.
3. Se terminar antes ou no fim do quantum, passa para FINALIZADO.
4. Se restar burst, sofre preempção e volta ao fim da fila.
5. Processos que chegarem durante a fatia entram no fim da fila quando chegam.
6. Ao fim da fatia, todas as chegadas daquele instante entram na fila antes da
   reinserção do processo preemptado.
7. Se apenas um processo estiver pronto, ele pode receber fatias consecutivas.
   Isso não conta como troca de contexto, pois o processo não mudou.
8. O custo de troca será aplicado sempre que a CPU mudar para outro processo.
   Quantum pequeno poderá aumentar trocas, espera e makespan.

## 5. Métricas

Para cada processo:

| Métrica | Fórmula |
| --- | --- |
| Conclusão | instante em que o processo termina |
| Retorno (turnaround) | conclusão − chegada |
| Espera | retorno − burst |
| Resposta | primeira execução − chegada |

Para cada algoritmo:

- tempo médio de espera;
- tempo médio de retorno;
- tempo médio de resposta;
- tempo total da carga (makespan);
- quantidade de trocas de contexto;
- tempo total gasto em trocas de contexto;
- aproveitamento útil da CPU: tempo executando bursts / makespan × 100;
- vazão: processos concluídos / makespan.

Uma troca de contexto será contabilizada quando a CPU passar diretamente de um
processo para outro processo diferente. A primeira alocação e a transição entre
CPU ociosa e processo não serão contadas.

Para o experimento concorrente:

- registros esperados;
- registros válidos;
- registros corrompidos;
- duração real em milissegundos;
- tempo médio real de espera pelo lock em milissegundos.

## 6. Exemplo de referência

Carga inicial baseada nos alertas já usados pelo Smart City Monitor:

| Processo | Tipo | Evento urbano | Chegada | Burst |
| --- | --- | --- | ---: | ---: |
| P1 | TRANSITO | Congestionamento detectado | 0 | 8 |
| P2 | ENERGIA | Pico de consumo detectado | 1 | 4 |
| P3 | QUALIDADE_AR | Nível de poluentes elevado | 2 | 2 |

Quantum do Round Robin: 2. Custo de cada troca de contexto: 1.

### 6.1 Resultado esperado no FCFS

~~~text
0        8  9      13 14   16
|   P1   |TC|  P2  |TC| P3 |
~~~

Ordem: P1 → P2 → P3.

| Processo | Conclusão | Espera | Retorno | Resposta |
| --- | ---: | ---: | ---: | ---: |
| P1 | 8 | 0 | 8 | 0 |
| P2 | 13 | 8 | 12 | 8 |
| P3 | 16 | 12 | 14 | 12 |
| **Média** | — | **6,67** | **11,33** | **6,67** |

Trocas de contexto: 2. Tempo gasto em trocas: 2. Makespan: 16.

### 6.2 Resultado esperado no Round Robin

~~~text
0  2 3  5 6  8 9 11 12 14 15    19
|P1|TC|P2|TC|P3|TC|P1|TC|P2|TC|  P1  |
~~~

Ordem: P1 → P2 → P3 → P1 → P2 → P1.

| Processo | Conclusão | Espera | Retorno | Resposta |
| --- | ---: | ---: | ---: | ---: |
| P1 | 19 | 11 | 19 | 0 |
| P2 | 14 | 9 | 13 | 2 |
| P3 | 8 | 4 | 6 | 4 |
| **Média** | — | **8,00** | **12,67** | **2,00** |

Trocas de contexto: 5. Tempo gasto em trocas: 5. Makespan: 19.

### 6.3 Diferença que deve ser apresentada

Neste exemplo, o FCFS realiza menos trocas de contexto, mas o congestionamento
P1 mantém a CPU até terminar. Os alertas posteriores de energia e qualidade do
ar aguardam mesmo tendo bursts menores. O Round Robin distribui o acesso, mas,
nesta configuração, o quantum pequeno gera cinco trocas e o custo acumulado faz
suas médias piorarem. Ao variar quantum e custo, a apresentação mostrará por que
Round Robin não é sempre superior e por que quantum muito pequeno pode reduzir
o desempenho.

### 6.4 Resultado esperado do sincronismo

Com a mesma carga em ambos os modos:

- sem sincronismo: ao menos um registro corrompido e espera por lock igual a 0;
- com sincronismo: todos os registros válidos, nenhuma colisão e espera média
  por lock maior que 0;
- o conteúdo dos dois arquivos deverá permanecer disponível para inspeção.

## 7. Integração com o repositório atual

O projeto continuará com um único pom.xml e o mesmo comando de execução. A
funcionalidade existente será preservada. O simulador será uma nova aba
principal chamada **Escalonamento de CPU**, ao lado das telas atuais.

O novo domínio reutilizará br.smartcity.monitor.model.TipoEvento e as descrições
de eventos existentes. A classe Evento não será alterada para receber campos de
escalonamento: ProcessoUrbano fará a adaptação entre o evento da cidade e os
dados didáticos de chegada e burst. Isso evita quebrar o experimento atual.

Estrutura proposta:

~~~text
src/main/java/br/smartcity/monitor/
├── escalonamento/
│   ├── algoritmo/
│   │   ├── AlgoritmoEscalonamento.java
│   │   ├── Fcfs.java
│   │   └── RoundRobin.java
│   ├── model/
│   │   ├── EstadoProcesso.java
│   │   ├── MetricasEscalonamento.java
│   │   ├── ProcessoUrbano.java
│   │   ├── ProcessoResultado.java
│   │   ├── TrechoExecucao.java
│   │   ├── TipoTrechoExecucao.java
│   │   └── ResultadoEscalonamento.java
│   ├── simulacao/
│   │   ├── CalculadoraMetricas.java
│   │   └── ComparacaoEscalonamento.java
│   ├── sincronismo/
│   │   ├── ComparacaoSincronismo.java
│   │   ├── ModoSincronizacao.java
│   │   ├── RegistroCompartilhado.java
│   │   ├── ResultadoSincronismo.java
│   │   └── SimuladorSincronismo.java
│   └── ui/
│       ├── EscalonamentoController.java
│       └── EscalonamentoView.java
└── ui/
    └── SmartCityApplication.java

src/test/java/br/smartcity/monitor/escalonamento/
├── FcfsTest.java
├── RoundRobinTest.java
├── MetricasEscalonamentoTest.java
├── SimuladorSincronismoTest.java
└── ComparacaoEscalonamentoTest.java
~~~

### 7.1 Responsabilidades

- ProcessoUrbano: entrada imutável com ID, TipoEvento, descrição, chegada,
  burst e ordem de cadastro.
- AlgoritmoEscalonamento: recebe processos e devolve um resultado.
- Fcfs e RoundRobin: regras puras, sem dependência de JavaFX.
- TrechoExecucao: intervalo [início, fim) de processo, CPU ociosa ou troca de
  contexto; trechos consecutivos do mesmo processo podem ser unidos no Gantt.
- ResultadoEscalonamento: timeline, resultados e métricas.
- SimuladorSincronismo: executa os sensores concorrentes sobre o arquivo.
- RegistroCompartilhado: fragmenta a escrita e protege o registro com lock
  apenas quando o sincronismo estiver ligado.
- ComparacaoEscalonamento: executa ambos com cópias da mesma carga.
- EscalonamentoController: valida a entrada e controla a simulação.
- EscalonamentoView: componentes JavaFX, animação e comparação.

O CSS atual poderá ser ampliado para manter o estilo do dashboard.

## 8. Requisitos não funcionais

- O domínio de escalonamento não dependerá de JavaFX.
- Uma mesma entrada sempre produzirá o mesmo resultado nos algoritmos.
- No experimento concorrente, a ordem pode variar, mas as condições de
  integridade de cada modo devem ser reproduzíveis.
- A UI não poderá bloquear a JavaFX Application Thread.
- Todos os cálculos serão testáveis sem abrir uma janela.
- Nomes e mensagens visíveis ao usuário serão em português.
- Médias serão apresentadas com duas casas decimais.
- O simulador suportará ao menos 100 processos e 100.000 unidades lógicas sem
  armazenar um objeto por unidade; o Gantt será representado por trechos.
- Entradas inválidas não poderão iniciar a simulação.

## 9. Critérios de aceitação

1. É possível cadastrar ao menos três processos urbanos, escolhendo um
   TipoEvento existente, uma descrição, chegada e burst.
2. FCFS e Round Robin executam sobre a mesma carga.
3. O quantum aceita somente inteiros maiores que zero.
4. O custo de troca aceita somente inteiros maiores ou iguais a zero.
5. Fila de pronto e CPU podem ser acompanhadas passo a passo nos dois painéis.
6. O FCFS nunca preempta um processo.
7. O Round Robin preempta ao fim do quantum quando há burst restante.
8. Chegadas simultâneas preservam a ordem original de cadastro.
9. Períodos sem processo pronto aparecem como CPU ociosa.
10. As trocas e seu custo aparecem no Gantt e afetam as métricas.
11. As métricas obedecem às fórmulas da seção 5.
12. A comparação exibe ambos lado a lado e indica melhores valores ou empate.
13. A carga da seção 6 produz exatamente a ordem, os resultados, as médias e as
    trocas de contexto especificadas.
14. Mudar a velocidade da animação não altera o resultado.
15. Mudar o quantum pode alterar somente o resultado do Round Robin.
16. Sem sincronismo, o arquivo físico apresenta registros corrompidos.
17. Com sincronismo, todos os registros ficam íntegros e a espera pelo lock é
    medida.
18. As comparações mudam uma única variável e reutilizam a mesma carga.
19. Todos os testes existentes e novos passam em conjunto.

## 10. Fora do escopo

- SJF, SRTF, prioridades ou múltiplas filas;
- múltiplos núcleos ou CPUs;
- rajadas de E/S e bloqueio;
- banco de dados;
- importação ou exportação de cargas;
- reprodução exata dos tempos reais em máquinas diferentes.

## 11. Roteiro de apresentação

1. Explicar como eventos dos sensores viram processos na Central.
2. Executar a carga sem sincronismo e mostrar a colisão no arquivo.
3. Repetir a mesma carga com sincronismo e mostrar integridade e espera.
4. Explicar chegada, burst, quantum e custo de troca.
5. Executar FCFS e Round Robin sobre a mesma fila.
6. Comparar Gantt, espera, retorno, resposta, trocas e makespan.
7. Variar somente o quantum e mostrar o impacto das trocas.
8. Aumentar o quantum e mostrar o Round Robin se aproximando do FCFS.
9. Explicar os compromissos entre paralelismo, integridade e desempenho.

## 12. Divisão do trabalho entre duas pessoas

A divisão abaixo evita que as duas pessoas alterem os mesmos arquivos durante a
maior parte do desenvolvimento. Antes de codificar, a dupla deverá revisar em
conjunto os contratos dos modelos e o exemplo da seção 6.

### 12.1 Pessoa 1 — Núcleo, FCFS e entrada de dados

Responsável pelo modelo compartilhado e pelo fluxo que recebe e valida a carga.

Arquivos principais:

~~~text
src/main/java/br/smartcity/monitor/escalonamento/
├── model/
│   ├── EstadoProcesso.java
│   ├── MetricasEscalonamento.java
│   ├── ProcessoUrbano.java
│   ├── ProcessoResultado.java
│   ├── TrechoExecucao.java
│   ├── TipoTrechoExecucao.java
│   └── ResultadoEscalonamento.java
├── algoritmo/
│   ├── AlgoritmoEscalonamento.java
│   └── Fcfs.java
├── simulacao/
│   └── CalculadoraMetricas.java
├── sincronismo/
│   ├── ComparacaoSincronismo.java
│   ├── ModoSincronizacao.java
│   ├── RegistroCompartilhado.java
│   ├── ResultadoSincronismo.java
│   └── SimuladorSincronismo.java
└── ui/
    └── EscalonamentoController.java
~~~

Entregas:

1. Criar os modelos imutáveis, reutilizando TipoEvento, e suas validações.
2. Definir o contrato AlgoritmoEscalonamento usado pelos dois algoritmos.
3. Implementar o FCFS, incluindo períodos de CPU ociosa e desempate por ordem
   de cadastro.
4. Calcular conclusão, espera, retorno, resposta, makespan, utilização, vazão e
   trocas de contexto em uma rotina comum aos algoritmos, incluindo o custo.
5. Implementar no controller o cadastro, edição, remoção e validação dos
   processos, quantum, custo de troca e modo de sincronização.
6. Disponibilizar a carga de exemplo da seção 6.
7. Implementar a escrita concorrente no arquivo físico com lock configurável.
8. Medir integridade, duração e espera pelo lock.
9. Criar os testes FcfsTest, MetricasEscalonamentoTest e
   SimuladorSincronismoTest.
10. Documentar os métodos públicos que formam o contrato com a Pessoa 2.

Critério de conclusão individual: a carga de referência deve produzir o Gantt e
as métricas FCFS definidos na seção 6, e o teste de sincronismo deve demonstrar
colisão sem lock e integridade com lock, sem depender de JavaFX.

### 12.2 Pessoa 2 — Round Robin, comparação e visualização

Responsável pelo algoritmo preemptivo e por apresentar os resultados dos dois
algoritmos.

Arquivos principais:

~~~text
src/main/java/br/smartcity/monitor/escalonamento/
├── algoritmo/
│   └── RoundRobin.java
├── simulacao/
│   └── ComparacaoEscalonamento.java
└── ui/
    ├── EscalonamentoView.java
    ├── PainelAlgoritmoView.java
    └── GanttView.java

src/main/resources/br/smartcity/monitor/ui/
└── dashboard.css
~~~

Entregas:

1. Implementar Round Robin com quantum, preempção, reinserção no fim da fila e
   tratamento das chegadas durante a fatia e durante o custo de contexto.
2. Implementar ComparacaoEscalonamento, garantindo cópias independentes da
   mesma carga para FCFS e Round Robin.
3. Criar os dois painéis com CPU, ready queue, estados e Gantt.
4. Criar a tabela de métricas lado a lado e a indicação de menor valor ou
   empate.
5. Criar o painel do experimento de sincronismo, incluindo conteúdo do arquivo,
   colisões, duração e espera.
6. Implementar os controles de reprodução, pausa, passo e reinício da animação.
7. Integrar EscalonamentoView como nova aba em SmartCityApplication.
8. Ajustar o CSS mantendo o estilo atual.
9. Criar os testes RoundRobinTest e ComparacaoEscalonamentoTest.

Critério de conclusão individual: a carga de referência com quantum 2 deve
produzir o Gantt e as métricas Round Robin definidos na seção 6.

### 12.3 Contrato de integração

Para a Pessoa 2 poder desenvolver a interface em paralelo, a dupla combinará
primeiro a API mínima:

~~~java
public interface AlgoritmoEscalonamento {
    ResultadoEscalonamento executar(List<ProcessoUrbano> processos);
}

public record ResultadoEscalonamento(
        String algoritmo,
        List<TrechoExecucao> gantt,
        List<ProcessoResultado> processos,
        MetricasEscalonamento metricas
) {}
~~~

Round Robin deverá receber quantum e custo de troca pelo construtor, assim como
FCFS recebe o custo. Os nomes finais podem ser ajustados pela dupla, mas os
dados retornados não deverão depender de classes JavaFX.

Nenhuma pessoa deverá alterar arquivos atribuídos à outra sem avisar. Mudanças
no contrato compartilhado deverão ser feitas em conjunto para evitar que um
algoritmo ou a interface deixe de compilar.

### 12.4 Trabalho conjunto

Estas atividades pertencem à dupla:

1. Revisar os contratos e as regras de desempate antes da implementação.
2. Fazer a primeira integração entre controller, comparação e view.
3. Resolver conflitos em SmartCityApplication e dashboard.css.
4. Executar todos os testes com mvn clean test.
5. Testar manualmente a carga de referência e pelo menos uma carga com CPU
   ociosa e outra com chegadas simultâneas.
6. Executar a mesma carga com sincronismo desligado e ligado.
7. Testar pelo menos três valores de quantum mantendo as demais condições.
8. Preparar a apresentação: cada pessoa explica o algoritmo que implementou; a
   comparação e as conclusões são apresentadas pelas duas.

### 12.5 Ordem recomendada de desenvolvimento

| Etapa | Pessoa 1 | Pessoa 2 |
| --- | --- | --- |
| 1 | Define modelos e contrato | Revisa contrato e prepara testes do RR |
| 2 | Implementa FCFS e custo de troca | Implementa Round Robin com o mesmo custo |
| 3 | Implementa arquivo e sincronismo | Implementa comparação e componentes visuais |
| 4 | Implementa controller e validações | Conecta resultados e sincronismo à view |
| 5 | Revisa métricas e casos-limite | Integra a nova aba e ajusta o CSS |
| 6 | Teste integrado e apresentação | Teste integrado e apresentação |

### 12.6 Estratégia de branches

- Pessoa 1: branch feat/escalonamento-fcfs-core.
- Pessoa 2: branch feat/escalonamento-rr-ui.
- A branch da Pessoa 1 deve ser integrada primeiro porque contém os contratos
  compartilhados.
- Depois da integração do núcleo, a Pessoa 2 atualiza sua branch, resolve apenas
  os ajustes de contrato e integra Round Robin e UI.
- Commits devem ser pequenos e separados por modelo, algoritmo, teste e tela.
