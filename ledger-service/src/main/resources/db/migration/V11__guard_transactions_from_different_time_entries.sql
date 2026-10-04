CREATE FUNCTION guard_sealed_transactions() RETURNS trigger AS $$
DECLARE
    txn_created_at TIMESTAMPTZ;
BEGIN
    SELECT created_at INTO txn_created_at FROM transactions WHERE id = NEW.transaction_id;

    IF txn_created_at <> now() THEN
        RAISE EXCEPTION 'Transaction % is already committed and cannot receive new entries', NEW.transaction_id;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER guard_sealed_transactions_trigger
BEFORE INSERT ON entries
FOR EACH ROW EXECUTE FUNCTION guard_sealed_transactions();