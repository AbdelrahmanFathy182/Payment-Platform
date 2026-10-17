CREATE OR REPLACE FUNCTION reject_delete() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'deleting from % is not allowed', TG_TABLE_NAME;
END;
$$ LANGUAGE plpgsql;

-- blocks DELETE statements, including JPA's delete/deleteAll/deleteAllInBatch
CREATE TRIGGER accounts_no_delete
    BEFORE DELETE ON accounts
    FOR EACH ROW EXECUTE FUNCTION reject_delete();

-- row triggers don't fire on TRUNCATE, so cover it separately
CREATE TRIGGER accounts_no_truncate
    BEFORE TRUNCATE ON accounts
    FOR EACH STATEMENT EXECUTE FUNCTION reject_delete();

    