
-- 4. Counter only meaningful while PENDING
ALTER TABLE payments_model
    ADD CONSTRAINT chk_counter_only_when_pending
    CHECK (state = 'PENDING' OR pending_queries_remaining = 0);