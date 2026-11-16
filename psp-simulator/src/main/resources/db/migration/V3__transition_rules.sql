-- 1. Transition rules (updates)
CREATE FUNCTION enforce_payment_transition() RETURNS trigger AS $$
BEGIN
    IF NEW.state = OLD.state THEN
        RETURN NEW;
    END IF;

    IF (OLD.state = 'PENDING'    AND NEW.state = 'AUTHORIZED')
    OR (OLD.state = 'AUTHORIZED' AND NEW.state IN ('CAPTURED', 'VOIDED')) THEN
        RETURN NEW;
    END IF;

    RAISE EXCEPTION 'Invalid state transition: % -> %', OLD.state, NEW.state
        USING ERRCODE = 'PS409';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_payment_transition
    BEFORE UPDATE OF state ON payments_model
    FOR EACH ROW
    EXECUTE FUNCTION enforce_payment_transition();




    

-- 2. Initial state rule (inserts): new rows can't start as CAPTURED or VOIDED
CREATE FUNCTION enforce_payment_initial_state() RETURNS trigger AS $$
BEGIN
    IF NEW.state NOT IN ('PENDING', 'AUTHORIZED', 'DECLINED') THEN
        RAISE EXCEPTION 'Invalid initial state: %', NEW.state
            USING ERRCODE = 'PS409';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_payment_initial_state
    BEFORE INSERT ON payments_model
    FOR EACH ROW
    EXECUTE FUNCTION enforce_payment_initial_state();




-- 3. updated_at maintained by the database on every update
CREATE FUNCTION set_payment_updated_at() RETURNS trigger AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_payment_updated_at
    BEFORE UPDATE ON payments_model
    FOR EACH ROW
    EXECUTE FUNCTION set_payment_updated_at();


