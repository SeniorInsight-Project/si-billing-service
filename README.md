# si-billing-service

Serviço de cobrança: trial de 14 dias, assinatura Premium e faturas B2B pelo Stripe (Checkout, Customer Portal e webhooks) e cálculo do direito de acesso, publicado em `subscription.changed`.

Parte do SeniorInsight — ver o Documento de Arquitetura de Software (seção 6) para as convenções.

Branches: `main` (produção) e `develop` (testes, branch padrão). Trabalhe na branch criada pelo card e abra PR para `develop` — ver [CONTRIBUTING.md](CONTRIBUTING.md).

## Stack

Java 21, Spring Boot 3.5, PostgreSQL (schema `billing`, migrações com Flyway), RabbitMQ (exchange topic `si.events`) e Keycloak como emissor dos tokens (o serviço é só resource server).

## Organização do código

Pacote base `com.seniorinsight.billing`, em arquitetura hexagonal:

| Pacote | O que tem | Pode depender de |
|---|---|---|
| `domain` | Modelo, eventos e portas de domínio (ex.: `pagamento.GatewayPagamento`), Java puro | nada (nem Spring, nem JPA) |
| `application` | Casos de uso e portas (ex.: `porta.saida.PublicadorEventos`) | `domain` |
| `adapters.in` | Entrada: HTTP, segurança, OpenAPI e consumidor RabbitMQ | `application`, `domain` |
| `adapters.out` | Saída: banco, outbox, RabbitMQ (e o Stripe, na HU-043) | `application`, `domain` |

`adapters.in` e `adapters.out` não se conhecem. As regras são verificadas pelo `ArquiteturaTest` (ArchUnit) a cada build.

## Rodando localmente

Pré-requisitos: JDK 21, Maven 3.9+ e Docker.

### Sem infraestrutura própria (Testcontainers)

Sobe o serviço com PostgreSQL e RabbitMQ em containers descartáveis:

```bash
KEYCLOAK_ISSUER_URI=http://localhost:8180/realms/seniorinsight mvn spring-boot:test-run
```

Sem um Keycloak no ar o serviço sobe normalmente: o health e o Swagger funcionam, e as rotas protegidas respondem 401.

### Com PostgreSQL, RabbitMQ e Keycloak já rodando

```bash
cp .env.example .env   # preencha as senhas
set -a && source .env && set +a
mvn spring-boot:run
```

O usuário do banco precisa poder criar o schema `billing` (ou o schema precisa existir com ele como dono): o Flyway cria o schema e aplica as migrações na subida.

### Endpoints

| Rota | Acesso |
|---|---|
| `GET /actuator/health` (e `/liveness`, `/readiness`) | público |
| `/swagger-ui.html` e `/v3/api-docs` | público |
| todo o resto | token do Keycloak |

## Variáveis de ambiente

O `application.yml` não tem segredos; tudo vem do ambiente. O modelo está no [`.env.example`](.env.example).

| Variável | Padrão | Descrição |
|---|---|---|
| `DB_HOST` | `localhost` | Host do PostgreSQL |
| `DB_PORT` | `5432` | Porta do PostgreSQL |
| `DB_NAME` | `seniorinsight` | Banco (cada serviço usa o seu schema) |
| `DB_USERNAME` | — | Usuário do banco |
| `DB_PASSWORD` | — | Senha do banco |
| `RABBITMQ_HOST` | `localhost` | Host do RabbitMQ |
| `RABBITMQ_PORT` | `5672` | Porta AMQP |
| `RABBITMQ_VHOST` | `/` | Virtual host |
| `RABBITMQ_USERNAME` | — | Usuário do RabbitMQ |
| `RABBITMQ_PASSWORD` | — | Senha do RabbitMQ |
| `KEYCLOAK_ISSUER_URI` | — | Issuer do realm, ex.: `http://localhost:8180/realms/seniorinsight` |
| `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_JWKSETURI` | — | Opcional: URL das chaves públicas quando o issuer não é acessível pelo serviço (ex.: no Compose) |
| `SERVER_PORT` | `8080` | Porta HTTP |

`KEYCLOAK_ISSUER_URI` precisa ser **exatamente** o valor do claim `iss` dos tokens. As chaves públicas vêm do issuer, a não ser que `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_JWKSETURI` esteja definida: é o caso do Compose, em que o issuer é `http://localhost:8180/...` (visto pelo navegador) e o JWKS é buscado em `http://keycloak:8080/...` pela rede dos containers.

Ajustes opcionais (valores padrão no `application.yml`): `BILLING_OUTBOX_RELAY_INTERVALO` (`PT1S`), `BILLING_OUTBOX_RELAY_TAMANHO_LOTE` (`100`), `BILLING_OUTBOX_RELAY_TIMEOUT_CONFIRMACAO` (`PT5S`) e `BILLING_MENSAGERIA_ROUTING_KEYS` (`user.created`, routing keys de `si.events` ligadas à `billing.q`, separadas por vírgula).

## Segurança

O serviço não tem login nem emite tokens: valida o JWT do Keycloak pelo issuer, sem sessão (stateless). As roles de `realm_access.roles` viram authorities `ROLE_<role>`, com o nome da role como está no realm (ex.: `familiar` → `ROLE_familiar`); os scopes do token viram `SCOPE_<scope>`.

## Eventos (outbox)

Quem precisa publicar um evento chama a porta `PublicadorEventos` dentro da transação do caso de uso. O evento é gravado na tabela `outbox_evento` junto com a mudança de estado, e um job agendado (`RelayOutbox`) publica os pendentes:

- exchange topic `si.events` (declarada pelo serviço), routing key = tipo do evento (ex.: `subscription.changed`);
- corpo JSON com os campos do evento; `message_id` = id da linha na outbox, `type` = tipo do evento, headers `tipo_agregado` e `agregado_id`;
- publisher confirms: só marca `publicado_em` depois do ack do broker; em falha incrementa `tentativas` e tenta de novo no próximo ciclo;
- `FOR UPDATE SKIP LOCKED` permite várias réplicas rodando o relay.

A entrega é "pelo menos uma vez": consumidores devem ser idempotentes, deduplicando pelo `message_id`.

## Eventos recebidos (fila `billing.q`)

O serviço declara a fila durável `billing.q`, ligada a `si.events` pelas routing keys de `billing.mensageria.routing-keys`, com dead-letter para a exchange `billing.dlx` → fila `billing.dlq`.

- ack manual: a mensagem só é confirmada depois que a transação do processamento foi confirmada;
- idempotente pelo id do evento (`message_id`): o id é gravado na tabela `evento_processado` na mesma transação, com `ON CONFLICT DO NOTHING`; reentregas são confirmadas sem efeito;
- mensagem sem `message_id` (UUID) ou com erro no processamento recebe nack sem requeue e vai para a `billing.dlq`, sem novas tentativas automáticas.

Por enquanto o consumidor só registra o evento: as regras (ex.: iniciar o trial em `user.created`) entram nas próximas HUs.

## Pagamento

A porta `domain.pagamento.GatewayPagamento` (checkout e portal do cliente) existe sem implementação. A integração com o Stripe é a HU-043.

## Testes

```bash
mvn verify
```

Precisa de Docker: os testes de integração sobem PostgreSQL e RabbitMQ com Testcontainers. O mesmo comando roda no CI (`.github/workflows/ci.yml`) em push e PR para `develop` e `main`.

## Docker

```bash
docker build -t si-billing-service .
docker run --rm -p 8080:8080 --env-file .env si-billing-service
```

Build multi-stage (Maven → JRE 21 Alpine), roda com usuário não root e tem `HEALTHCHECK` em `/actuator/health`. Opções extras da JVM vão em `JAVA_TOOL_OPTIONS`. Com `--env-file`, lembre que `localhost` dentro do container é o próprio container.

### Entrada para o Docker Compose do si-infra

Trecho pronto para colar em `services:` do `compose.yaml` do si-infra, no mesmo formato dos outros serviços:

```yaml
  billing-service:
    build:
      context: ../si-billing-service
      dockerfile: Dockerfile
    restart: unless-stopped
    environment:
      SERVER_PORT: 8080
      DB_HOST: postgres
      DB_PORT: 5432
      DB_NAME: ${DB_NAME:-seniorinsight}
      DB_USERNAME: ${DB_USERNAME:-seniorinsight}
      DB_PASSWORD: ${DB_PASSWORD:-seniorinsight}
      RABBITMQ_HOST: rabbitmq
      RABBITMQ_PORT: 5672
      RABBITMQ_VHOST: /
      RABBITMQ_USERNAME: ${RABBITMQ_USERNAME:-seniorinsight}
      RABBITMQ_PASSWORD: ${RABBITMQ_PASSWORD:-seniorinsight}
      KEYCLOAK_ISSUER_URI: http://localhost:${KEYCLOAK_PORT:-8180}/realms/seniorinsight
      SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_JWKSETURI: http://keycloak:8080/realms/seniorinsight/protocol/openid-connect/certs
    healthcheck:
      test: ["CMD-SHELL", "wget -q -O /dev/null http://127.0.0.1:8080/actuator/health || exit 1"]
      interval: 15s
      timeout: 3s
      start_period: 60s
      retries: 3
    expose:
      - "8080"
    depends_on:
      postgres:
        condition: service_healthy
      rabbitmq:
        condition: service_healthy
      keycloak:
        condition: service_started
```

Com isso o gateway pode usar `depends_on: { billing-service: { condition: service_healthy } }`.
