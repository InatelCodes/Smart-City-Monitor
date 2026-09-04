# Smart City Monitor

Sistema de monitoramento de uma cidade inteligente desenvolvido em Java, com foco no uso de Threads para processamento concorrente de eventos.

## Objetivo

Avaliar como o aumento do número de Threads da Central de Monitoramento influencia o processamento de uma mesma carga de eventos.

O experimento utiliza uma lista fixa de eventos e varia somente a quantidade de Threads consumidoras.

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
     Métricas
        ↓
     Dashboard
````

Os eventos são inseridos na fila antes do início do processamento. A Central utiliza de 1 a 4 Threads para consumir e processar os eventos.

Não existem Threads de sensores gerando eventos durante o experimento.

## Métricas

O sistema apresenta:

* Eventos gerados
* Eventos processados
* Eventos pendentes
* Vazão (eventos por segundo)
* Tempo médio de resposta
* Tempo total do experimento
* Quantidade de eventos processados por tipo
* Registro dos eventos processados e da Thread responsável

## Dashboard

O dashboard permite:

* Selecionar a quantidade de Threads (1 a 4)
* Definir o tempo de processamento de cada evento
* Iniciar e interromper o experimento
* Acompanhar as métricas em tempo real
* Visualizar os eventos processados
* Comparar os resultados dos experimentos

## Resultados

A aba de resultados permite comparar os experimentos realizados, observando principalmente:

* Threads × processamento
* Threads × tempo médio de resposta
* Comportamento da fila durante o processamento

Como a carga de eventos permanece a mesma, é possível analisar o efeito do aumento do paralelismo sobre o processamento.

## Tecnologias

* Java
* JavaFX
* Maven
* JUnit
* `BlockingQueue`
* Threads / concorrência

## Como executar

Na pasta raiz do projeto:

```bash
mvn clean javafx:run
```

## Testes

Para executar os testes automatizados:

```bash
mvn clean test
```

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

#### Projeto desenvolvido por [Bruna Magalhães](https://github.com/BrunaDev) e [Vinicius Simoni](https://github.com/vinigs22) - Matéria de C12 - Sistemas Operacionais
