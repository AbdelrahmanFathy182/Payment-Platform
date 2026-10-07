CREATE ROLE flyway_user
    WITH LOGIN PASSWORD 'flyway_pass';

CREATE ROLE ledger_service
    WITH LOGIN PASSWORD 'ledger_pass';

GRANT USAGE, CREATE
    ON SCHEMA public
    TO flyway_user;

GRANT USAGE
    ON SCHEMA public
    TO ledger_service;