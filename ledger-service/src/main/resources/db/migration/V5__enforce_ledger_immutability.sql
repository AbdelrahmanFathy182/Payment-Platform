CREATE FUNCTION reject_ledger_modification() RETURNS trigger AS $$    
BEGIN RAISE EXCEPTION '% is not allowed on table "%": ledger tables are immutable',
TG_OP,
TG_TABLE_NAME;

END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER reject_entries_modification_trigger
BEFORE UPDATE OR DELETE ON entries
FOR EACH ROW
EXECUTE FUNCTION reject_ledger_modification();

CREATE TRIGGER reject_entries_truncate_trigger
BEFORE TRUNCATE ON entries
FOR EACH STATEMENT
EXECUTE FUNCTION reject_ledger_modification();

CREATE TRIGGER reject_transactions_modification_trigger
BEFORE UPDATE OR DELETE ON transactions
FOR EACH ROW
EXECUTE FUNCTION reject_ledger_modification();

CREATE TRIGGER reject_transactions_truncate_trigger
BEFORE TRUNCATE ON transactions
FOR EACH STATEMENT
EXECUTE FUNCTION reject_ledger_modification();


CREATE TRIGGER reject_accounts_modification_trigger
BEFORE DELETE ON accounts
FOR EACH ROW
EXECUTE FUNCTION reject_ledger_modification();

CREATE TRIGGER reject_accounts_truncate_trigger
BEFORE TRUNCATE ON accounts
FOR EACH STATEMENT
EXECUTE FUNCTION reject_ledger_modification();


CREATE FUNCTION guard_account_update() RETURNS trigger AS $$
BEGIN
IF NEW.id IS DISTINCT
FROM
    OLD.id
    OR NEW.name IS DISTINCT
FROM
    OLD.name
    OR NEW.type IS DISTINCT
FROM
    OLD.type
    OR NEW.currency IS DISTINCT
FROM
    OLD.currency
    OR NEW.created_at IS DISTINCT
FROM
    OLD.created_at THEN RAISE EXCEPTION 'Only "status" may be changed on table "%"',
    TG_TABLE_NAME;

END IF;

RETURN NEW;
END;
$$ LANGUAGE plpgsql;


CREATE TRIGGER guard_account_update_trigger
BEFORE UPDATE ON accounts
FOR EACH ROW
EXECUTE FUNCTION guard_account_update();


