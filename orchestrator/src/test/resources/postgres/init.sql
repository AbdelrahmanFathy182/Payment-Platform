CREATE ROLE flyway_user
    WITH LOGIN PASSWORD 'flyway_pass';

CREATE ROLE orchestrator_service
    WITH LOGIN PASSWORD 'orchestrator_pass';

GRANT USAGE, CREATE
    ON SCHEMA public
    TO flyway_user;

GRANT USAGE
    ON SCHEMA public
    TO orchestrator_service;
