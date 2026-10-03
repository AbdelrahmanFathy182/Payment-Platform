CREATE FUNCTION guard_inactive_accounts() RETURNS trigger AS $$
DECLARE
    account_status VARCHAR(20);
    txn_type       VARCHAR(20);
BEGIN
    SELECT status INTO account_status FROM accounts     WHERE id = NEW.account_id;
    SELECT type   INTO txn_type       FROM transactions WHERE id = NEW.transaction_id;

    IF account_status = 'inactive' AND txn_type IS DISTINCT FROM 'REVERSAL' THEN
        RAISE EXCEPTION 'Account % is inactive and cannot receive entries from a % transaction',
            NEW.account_id, txn_type;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER guard_inactive_accounts_trigger
BEFORE INSERT ON entries
FOR EACH ROW
EXECUTE FUNCTION guard_inactive_accounts();