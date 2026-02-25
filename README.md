# Lab Banco

Painel web de banco mock vivo para estudar comportamento de um sistema financeiro em producao:

- geracao continua de transferencias mock
- persistencia real em PostgreSQL
- deploy continuo em VPS
- observacao de gargalos de performance e resiliencia

A ideia deste projeto e evoluir gradualmente de um monolito simples para uma arquitetura distribuida (futuro: Saga).

## Objetivo do projeto

Construir um laboratorio pratico para:

- simular operacoes de transferencia continuamente
- medir efeitos de carga concorrente em banco de dados
- testar mudancas de arquitetura com feedback rapido
- evoluir para multiplos servicos (ex.: Redis, Kafka) e padroes de consistencia distribuidos (Saga)

## Stack atual

- Java 17
- Spring Boot (Web, Actuator, Data JPA)
- PostgreSQL 16
- Docker / Docker Compose
- GitHub Actions (build + push GHCR + deploy em VPS)

## Como funciona hoje

1. Um scheduler (`MockTransactionService`) executa a cada 5 segundos.
2. O scheduler escolhe contas, gera valor mock e chama `TransferService.transfer(...)`.
3. A transferencia e feita com `@Transactional`:
   - debita conta origem
   - credita conta destino
   - grava evento em `transfer_transactions`
4. O frontend em `static/index.html` consulta `/transfers` a cada 5 segundos e atualiza o painel em tempo real.

## Endpoints

- `GET /` -> `Lab Banco <versao>`
- `GET /version` -> versao atual (vem de um unico ponto no `pom.xml`)
- `GET /transfers` -> ultimas 50 transferencias persistidas no banco
- `GET /transacoes` -> alias de `/transfers`
- `GET /index.html` -> dashboard em tempo real

## Fonte unica de versao (v2, v3, ...)

Edite apenas:

- `app/pom.xml` -> propriedade `lab-banco.version.label`

Essa propriedade alimenta:

- `app/src/main/resources/application.yml` (`app.version`)
- `HelloController` (`/` e `/version`)

## Rodar localmente

Opcao 1 (desenvolvimento local com codigo atual):

1. Subir somente o banco:
   - `docker compose up -d db`
2. Rodar a aplicacao Spring Boot:
   - Windows: `.\app\mvnw.cmd -f .\app\pom.xml spring-boot:run`
3. Acessar:
   - `http://localhost:8080/index.html`

Opcao 2 (stack por imagem publicada):

1. `docker compose up -d`
2. Acessar:
   - `http://localhost/index.html`

## Deploy automatico (VPS)

Workflow: `.github/workflows/deploy.yml`

No push para `master`, o pipeline faz:

1. build da imagem
2. push para GHCR
3. copia e atualiza `docker-compose.yml` na VPS (`overwrite: true`)
4. executa `docker compose pull` e `docker compose up -d --remove-orphans`

Isso garante que mudancas futuras em `docker-compose.yml` (novos servicos como Redis/Kafka) sejam aplicadas automaticamente no servidor.

## Estrutura principal

```text
lab-banco/
|-- app/
|   |-- pom.xml
|   |-- src/main/java/com/lab/banco/
|   |   |-- Account.java
|   |   |-- TransferTransaction.java
|   |   |-- AccountRepository.java
|   |   |-- TransferTransactionRepository.java
|   |   |-- TransferService.java
|   |   |-- MockTransactionService.java
|   |   |-- TransactionController.java
|   |   `-- HelloController.java
|   `-- src/main/resources/
|       |-- application.yml
|       `-- static/index.html
|-- Dockerfile
|-- docker-compose.yml
`-- .github/workflows/deploy.yml
```

## Roadmap (evolucao para Saga)

1. Persistencia confiavel
   - adicionar volume para PostgreSQL no `docker-compose.yml`
   - incluir migrations versionadas (Flyway/Liquibase)

2. Observabilidade forte
   - expor metricas de negocio e infraestrutura
   - integrar Prometheus + Grafana
   - padronizar logs estruturados e correlation id

3. Desacoplamento de servicos
   - separar dominio de contas e dominio de transacoes
   - introduzir broker (Kafka/RabbitMQ)
   - implementar padrao Outbox

4. Saga
   - compensacoes para falhas parciais
   - idempotencia por comando/evento
   - reprocessamento seguro de eventos

5. Testes de carga e caos
   - cenarios de throughput alto
   - latencia de banco/rede
   - indisponibilidade parcial de servicos

## Observacoes importantes

- Hoje o `docker-compose.yml` nao define volume do Postgres. Se o container for removido, os dados podem ser perdidos.
- Credenciais atuais sao de laboratorio (`lab/lab123`). Para ambiente exposto, use segredos e politicas de seguranca.
