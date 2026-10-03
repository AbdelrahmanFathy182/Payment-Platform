CREATE FUNCTION check_transaction_balanced() RETURNS trigger AS $$
DECLARE total_credits BIGINT;

total_debits BIGINT;

BEGIN
SELECT
  COALESCE(SUM(credits), 0) INTO total_credits
FROM
  entries
WHERE
  transaction_id = NEW.transaction_id;

SELECT
  COALESCE(SUM(debits), 0) INTO total_debits
FROM
  entries
WHERE
  transaction_id = NEW.transaction_id;

IF total_credits != total_debits THEN RAISE EXCEPTION 'Transaction % is not balanced: total credits = %, total debits = %',
NEW.transaction_id,
total_credits,
total_debits;

END IF;

RETURN NULL;

END;
$$ LANGUAGE plpgsql;

CREATE CONSTRAINT TRIGGER check_transaction_balanced_trigger 
AFTER INSERT ON entries 
DEFERRABLE INITIALLY DEFERRED 
FOR EACH ROW EXECUTE FUNCTION check_transaction_balanced ();