# Lab Banco

Painel web de banco mock vivo para estudar concorrencia e gargalos em sistema financeiro.

Estado atual do laboratorio:

- PostgreSQL real para saldo e historico de transferencias
- 2 instancias da aplicacao rodando em paralelo na VPS
- cenario fixo de disputa: `Joao -> Maria`
- monitor de consistencia com deteccao de anomalias
- dashboard unico com transferencias + health + issues

Objetivo: evoluir este ambiente ate arquitetura distribuida com Saga.

## Objetivo do projeto

Construir um laboratorio pratico para:

- simular operacoes financeiras concorrentes
- reproduzir condicoes de corrida de forma controlada
- observar inconsistencias em tempo real
- preparar a evolucao para multiplos servicos (Redis, Kafka, Saga)

## Stack

- Java 17
- Spring Boot (Web, Actuator, Data JPA)
- PostgreSQL 16
- Docker / Docker Compose
- GitHub Actions (build + push GHCR + deploy em VPS)

## Comportamento atual

1. Existem duas instancias do servico (`lab-banco` e `lab-banco-worker`).
2. Ambas executam scheduler de mock transfer.
3. Cada instancia roda 1 ciclo por minuto (`APP_MOCK_TRANSFER_FIXED_RATE_MS=60000` no compose da VPS).
4. O fluxo principal e sempre:
   - origem: `Joao`
   - destino: `Maria`
5. Se o saldo do Joao cai abaixo do minimo configurado, entra um fluxo tecnico de recarga:
   - `Maria -> Joao` com categoria `REBALANCE`
6. O dashboard mostra transferencias e saude de consistencia em uma unica pagina.

## Endpoints

- `GET /` -> `Lab Banco <versao>`
- `GET /version` -> versao atual
- `GET /transfers` -> ultimas 50 transferencias `MOCK`
- `GET /transfers/all` -> ultimas 50 transferencias de todas as categorias (`MOCK` + `REBALANCE`)
- `GET /transacoes` -> alias de `/transfers`
- `GET /consistency` -> snapshot de saude de consistencia
- `GET /consistency/issues` -> lista de issues registradas
- `GET /index.html` -> dashboard unico

## Categorias de transferencia

- `MOCK`: transferencia principal de teste (`Joao -> Maria`)
- `REBALANCE`: transferencia tecnica para manter liquidez do Joao e nao parar o teste

## Verificacoes de consistencia

Servico: `ConsistencyMonitorService` (agendado).

Checks atuais:

- saldo negativo
- self-transfer (origem = destino)
- valor de transferencia nao positivo
- transacao com referencia para conta inexistente
- divergencia saldo x historico para Joao/Maria
- drift de saldo total Joao+Maria

## Fonte unica de versao

Edite somente:

- `app/pom.xml` -> propriedade `lab-banco.version.label`

Essa propriedade alimenta `app.version` e os endpoints `/` e `/version`.

## Configuracoes relevantes

Arquivo: `app/src/main/resources/application.yml`

- `app.mock-transfer.fixed-rate-ms`
- `app.mock-transfer.initial-delay-ms`
- `app.mock-transfer.minimum-origin-balance`
- `app.mock-transfer.rebalance-target-balance`
- `app.consistency-check.fixed-rate-ms`
- `app.consistency-check.initial-delay-ms`

No deploy da VPS, o `docker-compose.yml` sobrescreve o ritmo do mock transfer para 60s por instancia.

## Rodar localmente

Opcao 1 (app local + banco em container):

1. `docker compose up -d db`
2. Windows: `.\app\mvnw.cmd -f .\app\pom.xml spring-boot:run`
3. Abrir `http://localhost:8080/index.html`

Opcao 2 (stack por imagem):

1. `docker compose up -d`
2. Abrir `http://localhost/index.html`

## Deploy automatico (VPS)

Workflow: `.github/workflows/deploy.yml`

No push para `master`, o pipeline:

1. builda imagem
2. publica no GHCR
3. atualiza `docker-compose.yml` na VPS (com `overwrite: true`)
4. roda `docker compose pull`
5. roda `docker compose up -d --remove-orphans`

Isso garante que novas definicoes de servicos no compose sejam aplicadas automaticamente.

## Estrutura principal

```text
lab-banco/
|-- app/
|   |-- pom.xml
|   |-- src/main/java/com/lab/banco/
|   |   |-- Account.java
|   |   |-- TransferTransaction.java
|   |   |-- TransferCategory.java
|   |   |-- AccountRepository.java
|   |   |-- TransferTransactionRepository.java
|   |   |-- TransferService.java
|   |   |-- MockTransactionService.java
|   |   |-- TransactionController.java
|   |   |-- ConsistencyMonitorService.java
|   |   |-- ConsistencyController.java
|   |   `-- HelloController.java
|   `-- src/main/resources/
|       |-- application.yml
|       `-- static/index.html
|-- Dockerfile
|-- docker-compose.yml
`-- .github/workflows/deploy.yml
```

## Observacoes

- `ddl-auto: update` e util para laboratorio, mas nao recomendado para ambiente critico.
- O Postgres ainda sem volume dedicado no compose pode perder dados ao recriar container.
- Credenciais atuais sao de laboratorio (`lab/lab123`); para ambiente publico, use segredos e hardening.

## Proximos passos sugeridos

1. Adicionar volume persistente para PostgreSQL.
2. Adicionar migrations (Flyway/Liquibase).
3. Expor metricas com Prometheus + Grafana.
4. Incluir lock otimista/pessimista para aprofundar estudo de concorrencia.
5. Evoluir para arquitetura orientada a eventos com Saga.
