GRANT SELECT, INSERT, UPDATE, DELETE
    ON payments, client_idempotency, attempts, payment_steps, outbox
    TO orchestrator_service;
