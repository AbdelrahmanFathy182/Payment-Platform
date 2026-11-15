CREATE ROLE flyway_user
    WITH LOGIN PASSWORD 'flyway_pass';

CREATE ROLE psp_service
    WITH LOGIN PASSWORD 'psp_pass';

GRANT USAGE, CREATE
    ON SCHEMA public
    TO flyway_user;

GRANT USAGE
    ON SCHEMA public
    TO psp_service;