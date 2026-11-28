ALTER TABLE payments
    ADD CONSTRAINT payments_terminal_outcome_chk
    CHECK (
        terminal_outcome IS NULL
        OR terminal_outcome IN (
            'SETTLED',
            'DECLINED',
            'VOIDED',
            'ABANDONED',
            'RETRIES_EXHAUSTED',
            'NEEDS_ATTENTION'
        )
    );
