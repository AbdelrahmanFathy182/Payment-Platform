CREATE FUNCTION check_transaction_entry_not_zero() RETURNS trigger AS $$    
BEGIN IF NOT EXISTS (
    SELECT
        1
    FROM
        entries
    WHERE
        transaction_id = NEW.id
) THEN RAISE EXCEPTION 'Transaction % has no entries',
NEW.id;

END IF;

RETURN NULL;

END;
$$ LANGUAGE plpgsql;

CREATE CONSTRAINT TRIGGER check_transaction_entry_not_zero
AFTER INSERT ON transactions
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW EXECUTE FUNCTION check_transaction_entry_not_zero();