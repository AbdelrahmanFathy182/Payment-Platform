-- Orchestrator owns payments, attempts, step intents, idempotency records and outbox.
-- Nothing here is shared with the PSP or Ledger databases.

CREATE TABLE payments (
    id                      UUID PRIMARY KEY,
    client_id               VARCHAR(255) NOT NULL,
    client_key              VARCHAR(255) NOT NULL,
    request_hash            VARCHAR(64)  NOT NULL,
    amount_minor            BIGINT       NOT NULL CHECK (amount_minor > 0),
    currency                VARCHAR(3)   NOT NULL,
    payer_ref               TEXT         NOT NULL,
    merchant_ref            TEXT         NOT NULL,
    fee_minor               BIGINT       NOT NULL DEFAULT 0 CHECK (fee_minor >= 0),
    merchant_amount_minor   BIGINT       NOT NULL CHECK (merchant_amount_minor >= 0),
    step                    VARCHAR(32)  NOT NULL,
    step_status             VARCHAR(16)  NOT NULL,
    current_attempt         INTEGER      NOT NULL DEFAULT 1 CHECK (current_attempt >= 1),
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deadline_at             TIMESTAMPTZ  NOT NULL,
    capture_due_at          TIMESTAMPTZ,
    next_action_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    needs_attention         BOOLEAN      NOT NULL DEFAULT false,
    terminal_outcome        VARCHAR(32),
    correlation_id          UUID         NOT NULL,
    response_snapshot       JSONB,

    CONSTRAINT payments_step_chk
        CHECK (step IN ('RECEIVED', 'AUTHORIZE', 'CAPTURE', 'VOID', 'LEDGER_POST')),
    CONSTRAINT payments_step_status_chk
        CHECK (step_status IN ('PENDING', 'IN_FLIGHT', 'IN_DUBT', 'DONE', 'FAILED'))
);

CREATE TABLE client_idempotency (
    client_id           VARCHAR(255) NOT NULL,
    client_key          VARCHAR(255) NOT NULL,
    payment_id          UUID         NOT NULL REFERENCES payments (id),
    request_hash        VARCHAR(64)  NOT NULL,
    response_snapshot   JSONB,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    expires_at          TIMESTAMPTZ  NOT NULL,

    PRIMARY KEY (client_id, client_key)
);

CREATE TABLE attempts (
    payment_id      UUID         NOT NULL REFERENCES payments (id),
    attempt_no      INTEGER      NOT NULL CHECK (attempt_no >= 1),
    status          VARCHAR(16)  NOT NULL,
    closed_reason   VARCHAR(64),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    closed_at       TIMESTAMPTZ,

    PRIMARY KEY (payment_id, attempt_no),
    CONSTRAINT attempts_status_chk CHECK (status IN ('ACTIVE', 'CLOSED'))
);

CREATE TABLE payment_steps (
    id                  UUID         PRIMARY KEY,
    payment_id          UUID         NOT NULL,
    attempt_no          INTEGER      NOT NULL,
    step                VARCHAR(32)  NOT NULL,
    status              VARCHAR(16)  NOT NULL,
    psp_key             VARCHAR(255),
    request_payload     JSONB        NOT NULL,
    response_payload    JSONB,
    started_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    next_poll_at        TIMESTAMPTZ,
    poll_count          INTEGER      NOT NULL DEFAULT 0,
    capture_alert_at    TIMESTAMPTZ,
    step_alert_at       TIMESTAMPTZ,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT payment_steps_payment_fk
        FOREIGN KEY (payment_id, attempt_no) REFERENCES attempts (payment_id, attempt_no),
    CONSTRAINT payment_steps_payment_uq UNIQUE (payment_id, attempt_no, step),
    CONSTRAINT payment_steps_step_chk
        CHECK (step IN ('AUTHORIZE', 'CAPTURE', 'VOID', 'LEDGER_POST')),
    CONSTRAINT payment_steps_status_chk
        CHECK (status IN ('IN_FLIGHT', 'IN_DUBT', 'DONE', 'FAILED'))
);

CREATE UNIQUE INDEX payment_steps_psp_key_uq
    ON payment_steps (psp_key)
    WHERE psp_key IS NOT NULL;

CREATE TABLE outbox (
    id              UUID         PRIMARY KEY,
    aggregate_id    UUID         NOT NULL,
    event_type      VARCHAR(64)  NOT NULL,
    payload         JSONB        NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    published_at    TIMESTAMPTZ
);

CREATE INDEX payments_due_work_idx
    ON payments (next_action_at)
    WHERE terminal_outcome IS NULL;

CREATE INDEX client_idempotency_expires_idx
    ON client_idempotency (expires_at);

CREATE INDEX outbox_unpublished_idx
    ON outbox (created_at)
    WHERE published_at IS NULL;
