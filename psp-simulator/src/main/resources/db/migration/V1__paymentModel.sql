CREATE TABLE
    payments_model (
        id UUID PRIMARY KEY DEFAULT gen_random_uuid (),
        state VARCHAR(20) NOT NULL CHECK (
            state IN (
                'PENDING',
                'AUTHORIZED',
                'DECLINED',
                'CAPTURED',
                'VOIDED'
            )
        ),
        amount BIGINT NOT NULL CHECK (amount > 0), -- minor units
        currency VARCHAR(3) NOT NULL CHECK (currency IN ('EGP', 'USD')),
        decline_reason VARCHAR(100), -- set only when DECLINED
        created_at TIMESTAMPTZ NOT NULL DEFAULT now (),
        updated_at TIMESTAMPTZ NOT NULL DEFAULT now (),
        pending_queries_remaining INT NOT NULL DEFAULT 0,
        CONSTRAINT chk_decline_reason CHECK (
            state = 'DECLINED'
            OR decline_reason IS NULL
        ),
        CONSTRAINT chk_pending_nonnegative CHECK (pending_queries_remaining >= 0)
    );