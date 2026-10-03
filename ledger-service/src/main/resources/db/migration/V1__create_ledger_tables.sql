CREATE TABLE
    accounts (
        id UUID PRIMARY KEY,
        name VARCHAR(100) NOT NULL UNIQUE,
        status VARCHAR(20) CHECK (status IN ('active', 'inactive')) NOT NULL,
        type VARCHAR(20) CHECK (
            type IN (
                'liability',
                'asset',
                'revenue',
                'expense',
                'equity'
            )
        ) NOT NULL,
        currency VARCHAR(3) NOT NULL check (currency IN ('EGP', 'USD')),
        UNIQUE (id, currency),
        created_at TIMESTAMPTZ NOT NULL
    );

CREATE TABLE
    transactions (
        id UUID PRIMARY KEY,
        reverses_transaction_id UUID UNIQUE,
        type VARCHAR(20) CHECK (
            type IN (
                'TOPUP',
                'PAYMENT',
                'WITHDRAWAL',
                'REFUND',
                'REVERSAL'
            )
        ) NOT NULL,
        currency VARCHAR(3) NOT NULL check (currency IN ('EGP', 'USD')),
        created_at TIMESTAMPTZ NOT NULL,
        idempotency_key VARCHAR NOT NULL UNIQUE,
        CHECK (
            type = 'REVERSAL'
            AND reverses_transaction_id IS NOT NULL
            OR type != 'REVERSAL'
            AND reverses_transaction_id IS NULL
        ),
        UNIQUE (id, currency),
        FOREIGN KEY (reverses_transaction_id, currency) REFERENCES transactions (id, currency),
        CHECK (
            reverses_transaction_id IS DISTINCT
            FROM
                id
        )
    );

CREATE TABLE
    entries (
        id UUID PRIMARY KEY,
        account_id UUID NOT NULL,
        transaction_id UUID NOT NULL,
        credits BIGINT,
        debits BIGINT,
        currency VARCHAR(3) NOT NULL check (currency IN ('EGP', 'USD')),
        created_at TIMESTAMPTZ NOT NULL,
        CHECK (
            (
                credits IS NOT NULL
                AND credits > 0
                AND debits IS NULL
            )
            OR (
                debits IS NOT NULL
                AND debits > 0
                AND credits IS NULL
            )
        ),
        FOREIGN KEY (account_id, currency) REFERENCES accounts (id, currency),
        FOREIGN KEY (transaction_id, currency) REFERENCES transactions (id, currency)
    );

CREATE INDEX entries_transaction_id_idx ON entries (transaction_id);