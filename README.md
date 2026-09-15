# Smart City Monitor

Sistema de monitoramento de uma cidade inteligente desenvolvido em Java, com foco no processamento concorrente de eventos usando Threads.

## Objetivo

Avaliar como diferentes quantidades de Threads consumidoras influenciam o processamento de uma mesma carga de eventos.

O experimento mantém a lista de eventos fixa e altera principalmente a quantidade de Threads da Central.

## Funcionamento

```text
Lista fixa de eventos
        ↓
   BlockingQueue
        ↓
Central de Monitoramento
        ↓
  Threads consumidoras
        ↓
Processamento concorrente
        ↓
     Métricas
        ↓
     Dashboard
```

Os eventos são inseridos na fila antes do início do experimento. Não existem Threads responsáveis pela geração durante a execução.

## Experimento

A aplicação permite utilizar de **1 a 16 Threads** e configurar o tempo de processamento de cada evento.

Para comparar as configurações, recomenda-se manter a mesma carga e o mesmo tempo de processamento, alterando somente o número de Threads.

A carga padrão é de **400 eventos fixos**, distribuídos entre Trânsito, Clima, Energia e Qualidade do ar.

O processamento utiliza uma carga real de CPU durante o tempo configurado. A partir de 8 Threads, o experimento também modela um custo crescente de coordenação da Central. Esse custo representa a sobrecarga de administrar muitas Threads concorrentes sobre recursos compartilhados e permite identificar experimentalmente um ponto de melhor custo-benefício.

## Dashboard

A aba **Monitoramento** apresenta:

- Eventos gerados, processados e pendentes;
- Vazão em eventos por segundo;
- Tempo médio de resposta;
- Tempo total;
- Gráfico de eventos pendentes ao longo da execução;
- Tabela dos eventos processados;
- Representação 2D da cidade.

Na representação 2D, cada região representa um tipo de evento. Quando um evento é concluído, ele é animado visualmente da região correspondente até a Central. A visualização representa o fluxo de processamento e não cria Threads para os sensores.

## Resultados

A aba **Resultados** compara:

- Threads × vazão;
- Threads × tempo médio de resposta;
- Threads × tempo total.

O gráfico de tempo total permite observar o comportamento do sistema conforme o paralelismo aumenta. O dashboard também identifica a configuração que apresentou o menor tempo entre os experimentos registrados como **ponto ótimo atual**.

O objetivo é observar experimentalmente o ponto em que aumentar o número de Threads deixa de trazer ganhos proporcionais e pode passar a aumentar o custo de processamento.

## Tecnologias

- Java 17+
- JavaFX
- Maven
- JUnit 5
- `BlockingQueue`
- `AtomicInteger` / `AtomicLong`
- Threads / concorrência

## Como executar

Na pasta raiz do projeto:

```bash
mvn clean javafx:run
```

## Testes

```bash
mvn clean test
```

Os testes verificam processamento sem perdas ou duplicações, encerramento das Threads, quantidade configurada de Threads e execução do fluxo do dashboard.

## Estrutura principal

```text
src/
├── main/
│   ├── java/br/smartcity/monitor/
│   │   ├── central/
│   │   ├── config/
│   │   ├── metrics/
│   │   ├── model/
│   │   ├── sensor/
│   │   └── ui/
│   └── resources/
│       └── br/smartcity/monitor/ui/
│
└── test/
    └── java/br/smartcity/monitor/
```
