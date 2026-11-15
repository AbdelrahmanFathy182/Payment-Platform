CREATE TABLE payment_requests (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    idempotency_key  VARCHAR(255) NOT NULL UNIQUE,
    operation        VARCHAR(20) NOT NULL
                     CHECK (operation IN ('AUTHORIZE','CAPTURE','VOID')),
    payment_id       UUID REFERENCES payments_model(id) NOT NULL,
    outcome          JSONB NOT NULL,                  -- original response, replayed on retry
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);