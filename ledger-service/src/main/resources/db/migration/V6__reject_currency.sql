CREATE FUNCTION guard_reversal_currency() RETURNS trigger AS $$
DECLARE original_currency VARCHAR(3);

BEGIN IF NEW.reverses_transaction_id IS NOT NULL THEN
SELECT
    currency INTO original_currency
FROM
    transactions
WHERE
    id = NEW.reverses_transaction_id;

IF original_currency IS DISTINCT
FROM
    NEW.currency THEN RAISE EXCEPTION 'Reversal currency % does not match original currency %',NEW.currency,original_currency;

END IF;

END IF;

RETURN NEW;

END;
$$ LANGUAGE plpgsql;