-- Estrutura inicial do billing-service. Roda no schema "billing" (spring.flyway.default-schema).

CREATE TABLE assinatura (
    id                     UUID         NOT NULL DEFAULT gen_random_uuid(),
    titular_sub            VARCHAR(255) NOT NULL,
    plano                  VARCHAR(20)  NOT NULL,
    status                 VARCHAR(20)  NOT NULL,
    stripe_customer_id     VARCHAR(255),
    stripe_subscription_id VARCHAR(255),
    inicio_em              TIMESTAMPTZ  NOT NULL,
    fim_periodo_em         TIMESTAMPTZ,
    cancelada_em           TIMESTAMPTZ,
    criado_em              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    atualizado_em          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pk_assinatura PRIMARY KEY (id),
    CONSTRAINT uk_assinatura_titular_sub UNIQUE (titular_sub),
    CONSTRAINT uk_assinatura_stripe_subscription_id UNIQUE (stripe_subscription_id),
    CONSTRAINT ck_assinatura_plano CHECK (plano IN ('TRIAL', 'PREMIUM', 'B2B')),
    CONSTRAINT ck_assinatura_status CHECK (status IN ('ATIVA', 'INADIMPLENTE', 'CANCELADA', 'EXPIRADA'))
);

COMMENT ON COLUMN assinatura.titular_sub IS 'Claim "sub" no Keycloak de quem paga (familiar ou organização)';
COMMENT ON COLUMN assinatura.stripe_customer_id IS 'Preenchido pela integração com o Stripe (HU-043)';

CREATE TABLE uso_trial (
    id          UUID         NOT NULL DEFAULT gen_random_uuid(),
    titular_sub VARCHAR(255) NOT NULL,
    iniciado_em TIMESTAMPTZ  NOT NULL,
    expira_em   TIMESTAMPTZ  NOT NULL,
    criado_em   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pk_uso_trial PRIMARY KEY (id),
    CONSTRAINT uk_uso_trial_titular_sub UNIQUE (titular_sub),
    CONSTRAINT ck_uso_trial_periodo CHECK (expira_em > iniciado_em)
);

COMMENT ON TABLE uso_trial IS 'Um trial por titular: a linha fica mesmo depois que a assinatura muda ou é cancelada';

CREATE TABLE outbox_evento (
    id            UUID         NOT NULL DEFAULT gen_random_uuid(),
    tipo_agregado VARCHAR(100) NOT NULL,
    agregado_id   UUID         NOT NULL,
    tipo_evento   VARCHAR(150) NOT NULL,
    payload       JSONB        NOT NULL,
    criado_em     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    publicado_em  TIMESTAMPTZ,
    tentativas    INTEGER      NOT NULL DEFAULT 0,
    CONSTRAINT pk_outbox_evento PRIMARY KEY (id)
);

COMMENT ON COLUMN outbox_evento.tipo_evento IS 'Routing key na exchange si.events, ex.: subscription.changed';

-- O relay só lê pendentes, em ordem de criação.
CREATE INDEX ix_outbox_evento_pendentes ON outbox_evento (criado_em) WHERE publicado_em IS NULL;

-- Idempotência do consumidor da fila billing.q: o id da mensagem (message_id = id do evento) entra uma vez só.
CREATE TABLE evento_processado (
    id            UUID         NOT NULL,
    tipo_evento   VARCHAR(150) NOT NULL,
    processado_em TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pk_evento_processado PRIMARY KEY (id)
);
