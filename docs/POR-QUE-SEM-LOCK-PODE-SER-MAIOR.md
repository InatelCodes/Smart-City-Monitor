# Por que o valor "sem sincronismo" às vezes é maior que "com sincronismo"?

Feedback recebido: *"ver por que há horas em que, sem o sincronismo, o valor fica maior que com o
sincronismo; se ainda assim houver casos em que isso acontece, explicar"*.

Resposta curta: **acontece, e não é bug.** Há duas causas diferentes, uma em cada experimento.

| Onde aparece | Causa | O que foi feito |
| --- | --- | --- |
| Página **Prova com threads reais** (duração em ms) | Ruído de medição: a primeira execução paga carga de classes, JIT e criação de arquivo; além disso o modo sem lock usa uma barreira para forçar a colisão | Aquecimento + **mediana de 15 execuções** com os modos alternados (ver [seção 2](#2-threads-reais-ruído-de-medição)) |
| Página **Sincronismo × escalonamento** (tempo lógico) | Efeito real do algoritmo: o lock **reduz preempções e trocas de contexto** do Round Robin | Documentado aqui, com cenário reproduzível (B) e teste automatizado |

---

## 1. Simulação (tempo lógico): o lock pode *melhorar* o Round Robin

### 1.1 Modelo em uma frase

Ao fim do seu burst, cada processo escreve um registro no arquivo (região crítica). Com lock, quem
chega à região crítica e encontra o lock ocupado **dorme sem usar CPU** e só volta à fila quando o
dono termina de escrever.

### 1.2 Cenário B — todos os números

Carga (unidades de tempo, **ut**): P1 chega em 0 com burst 10.000; P2 e P3 chegam em 4.000 com burst
10.000. Quantum 4.000, troca de contexto 1.000, escrita 6.000 (os últimos 6.000 ut de cada burst).

**Sem lock** — Round Robin puro, fatias iguais, 8 trocas de contexto (TC):

```text
P1 0–4k | TC | P2 5–9k | TC | P3 10–14k | TC | P1 15–19k | TC | P2 20–24k | TC | P3 25–29k | TC |
P1 30–32k | TC | P2 33–35k | TC | P3 36–38k          (duração total: 38.000)
```

**Com lock** — 5 trocas de contexto:

```text
P1 0–4k | TC | P2 5–9k | TC | P3 10–14k | TC | P1 15–21k | TC | P2 22–28k | TC | P3 29–35k
                                                  ▲                  ▲
                          P2 e P3 dormem de 19k a 21k   P3 dorme de 26k a 28k
                                                              (duração total: 35.000)
```

Passo a passo do que acontece com lock:

1. Até 14.000 tudo é igual: cada processo já executou 4.000 ut de processamento (a parte *antes* da
   escrita, que começa após 10.000 − 6.000 = 4.000 ut).
2. Em 15.000 o P1 volta, está na porta da região crítica e **pega o lock**.
3. Em 19.000 acaba a fatia do P1. Ao escolher o próximo, o escalonador encontra P2 e P3 também na
   porta da região crítica, mas o lock está com o P1: os dois **dormem** (bloqueados de 19.000 a
   21.000).
4. A fila de pronto ficou vazia, então o P1 **continua direto, sem troca de contexto**, e termina em
   21.000, liberando o lock.
5. O mesmo ocorre com o P2 (22.000–28.000, com o P3 dormindo de 26.000 a 28.000), e por fim o P3.

O lock transformou o fim de cada processo em "rodar até acabar". Resultado:

| Métrica (Round Robin) | Sem lock | Com lock | Quem tem o valor **maior** |
| --- | ---: | ---: | --- |
| Registros íntegros | 0 de 3 | 3 de 3 | — |
| Espera média | 22.333,33 | 15.333,33 | **sem lock** |
| Retorno médio | 32.333,33 | 25.333,33 | **sem lock** |
| Duração total | 38.000 | 35.000 | **sem lock** |
| Trocas de contexto | 8 (8.000 ut) | 5 (5.000 ut) | **sem lock** |
| Resposta média | 2.333,33 | 2.333,33 | igual |

### 1.3 Por quê, em termos de SO

* **Menos trocas de contexto.** Cada troca custa tempo de CPU sem trabalho útil. Processos
  bloqueados no lock não são despachados, então não geram troca. Aqui são 3.000 ut a menos, que
  aparecem direto na duração total e no retorno médio.
* **Quem está quase terminando termina primeiro.** O dono do lock não é interrompido por processos
  que não podem avançar. Terminar cedo os processos mais adiantados reduz o tempo médio de espera
  (é um efeito parecido com o que faz o SJF/SRTF ter espera média menor que o Round Robin).
* **Cargas de mesmo porte são o pior caso do Round Robin.** Com bursts iguais, o RR "empurra" o
  término de todos para o fim. Serializar o final de cada um (que é o que o lock faz) ajuda.

### 1.4 Quando o lock *piora* (e por que não é sempre melhor)

No cenário A (carga padrão, quantum 10.000) o resultado é o oposto: o P3 dorme 31.000 ut somados e a
espera média sobe de 23.500 para 30.333,33. O lock só ajuda quando a preempção que ele evita era
desperdício (muitas trocas); ajuda menos ou atrapalha quando os processos bloqueados poderiam estar
usando a CPU.

Numa amostra de **20.000 cargas aleatórias** (2 a 5 processos, Round Robin, semente fixa; o teste
`amostraAleatoriaMostraQueOLockPodeMelhorarOuPiorarOsTempos` reproduz isso):

| Métrica | Lock melhor | Igual | Lock pior |
| --- | ---: | ---: | ---: |
| Espera média | 41,6% | 47,4% | 11,0% |
| Retorno médio | 41,6% | 47,4% | 11,0% |
| Resposta média | 5,8% | 63,8% | 30,4% |
| Duração total | 24,0% | 73,0% | 3,0% |
| Trocas de contexto | 35,7% | 59,6% | 4,7% |

Leitura: o lock tende a **melhorar** espera, retorno, duração e trocas, mas **piora o tempo de
resposta** com frequência (o processo bloqueado demora mais para começar a escrever e, às vezes,
para começar a ser atendido).

### 1.5 FCFS: nunca muda

Sem preempção, ninguém interrompe uma escrita em andamento. Nunca há disputa pelo lock, então com e
sem sincronismo o FCFS é idêntico (mesmo Gantt, mesmas métricas). O teste
`comLockNuncaCorrompeEFcfsNuncaMudaEmCargasAleatoriasGrandes` verifica isso em 300 cargas aleatórias.

---

## 2. Threads reais: ruído de medição

A página **Prova com threads reais** executa threads de verdade sobre um arquivo físico e mede a
duração em milissegundos de relógio. Medindo 40 vezes a carga de exemplo antes da correção:

```text
execução 0  : sem lock = 40,38 ms   com lock = 16,05 ms   ← "sem" maior que "com"
execuções 1–39 (típico): sem lock ≈ 4,5 ms   com lock ≈ 8,8 ms
```

Ou seja, só a **primeira** execução do processo deu "sem lock" maior: era o efeito de aquecimento
(carga de classes, compilação JIT, criação do arquivo, cache de disco), pago pelo modo que roda
primeiro. Em regime, o esperado aparece: **com lock é mais lento**, porque as threads se serializam
na região crítica, enquanto sem lock elas rodam em paralelo.

Correções em `SimuladorSincronismo`:

* **Aquecimento:** 3 rodadas descartadas antes de medir (`AQUECIMENTOS_PADRAO`).
* **Mediana de 15 execuções** (`REPETICOES_PADRAO`) para duração e espera pelo lock, em vez de uma
  única medida.
* **Modos alternados** a cada rodada (sem, com, sem, com...), de modo que oscilações do computador
  afetem os dois igualmente.

Ainda assim, em milissegundos de relógio podem existir casos em que sem lock fique maior, por dois
motivos que são do desenho do experimento e não de erro:

1. **O modo sem lock usa uma barreira (`CyclicBarrier`) para forçar a colisão**, e as threads dormem
   1 ms entre fragmentos da escrita. Esse tempo de espera faz parte da duração medida dele.
2. **O `fsync` do arquivo (`force`) varia com o disco** e é contado nos dois modos.

Por isso, o experimento com threads reais deve ser lido como **demonstração de integridade do
arquivo** (colisão sem lock, arquivo íntegro com lock), e não como medida de desempenho. Para
comparar desempenho, use a página **Sincronismo × escalonamento**, que usa tempo lógico, é
determinística e dá o mesmo resultado a cada execução.

O teste `consolidarEscolheAMedianaIgnorandoUmaPrimeiraExecucaoLenta` documenta a escolha da mediana.
