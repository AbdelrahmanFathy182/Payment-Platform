-- V__fix_reversal_mirror_multiset.sql
CREATE OR REPLACE FUNCTION guard_reversal_transactions() RETURNS trigger AS $$
BEGIN
    IF NEW.type IS DISTINCT FROM 'REVERSAL' THEN
        RETURN NULL;
    END IF;

    -- original lines, flipped, that the reversal does not contain (counting duplicates)
    IF EXISTS (
        SELECT account_id, credits, debits
        FROM entries WHERE transaction_id = NEW.reverses_transaction_id
        EXCEPT ALL
        SELECT account_id, debits, credits
        FROM entries WHERE transaction_id = NEW.id
    ) THEN
        RAISE EXCEPTION 'Reversal % misses entries of transaction %', NEW.id, NEW.reverses_transaction_id;
    END IF;

    -- reversal lines that have no flipped original line (counting duplicates)
    IF EXISTS (
        SELECT account_id, debits, credits
        FROM entries WHERE transaction_id = NEW.id
        EXCEPT ALL
        SELECT account_id, credits, debits
        FROM entries WHERE transaction_id = NEW.reverses_transaction_id
    ) THEN
        RAISE EXCEPTION 'Reversal % does not mirror transaction %', NEW.id, NEW.reverses_transaction_id;
    END IF;

    RETURN NULL;
END;
$$ LANGUAGE plpgsql;