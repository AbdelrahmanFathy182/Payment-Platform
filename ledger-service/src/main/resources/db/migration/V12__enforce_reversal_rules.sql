CREATE FUNCTION guard_reversal_transactions() RETURNS trigger AS $$
DECLARE
    o_count BIGINT;
    r_count BIGINT;
BEGIN
    IF NEW.type IS DISTINCT FROM 'REVERSAL' THEN
        RETURN NULL;
    END IF;

    SELECT COUNT(*) INTO o_count FROM entries WHERE transaction_id = NEW.reverses_transaction_id;
    SELECT COUNT(*) INTO r_count FROM entries WHERE transaction_id = NEW.id;

    IF o_count <> r_count THEN
        RAISE EXCEPTION 'Reversal % has % entries, original % has %',
            NEW.id, r_count, NEW.reverses_transaction_id, o_count;
    END IF;

    -- every reversal entry must mirror an original entry
    IF EXISTS (
        SELECT 1 FROM entries r
        WHERE r.transaction_id = NEW.id
          AND NOT EXISTS (
              SELECT 1 FROM entries o
              WHERE o.transaction_id = NEW.reverses_transaction_id
                AND o.account_id = r.account_id
                AND (o.debits = r.credits OR o.credits = r.debits)
          )
    ) THEN
        RAISE EXCEPTION 'Reversal % does not mirror transaction %', NEW.id, NEW.reverses_transaction_id;
    END IF;

    -- every original entry must be mirrored by a reversal entry
    IF EXISTS (
        SELECT 1 FROM entries o
        WHERE o.transaction_id = NEW.reverses_transaction_id
          AND NOT EXISTS (
              SELECT 1 FROM entries r
              WHERE r.transaction_id = NEW.id
                AND r.account_id = o.account_id
                AND (r.debits = o.credits OR r.credits = o.debits)
          )
    ) THEN
        RAISE EXCEPTION 'Reversal % misses entries of transaction %', NEW.id, NEW.reverses_transaction_id;
    END IF;

    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE CONSTRAINT TRIGGER guard_reversal_transactions_trigger
AFTER INSERT ON transactions
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW EXECUTE FUNCTION guard_reversal_transactions();