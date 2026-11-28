ALTER TABLE payments
    ADD CONSTRAINT payments_client_key_uq UNIQUE (client_id, client_key);
