GRANT SELECT, INSERT, UPDATE, DELETE, TRUNCATE
    ON payments_model
    TO psp_service;

GRANT SELECT, INSERT, UPDATE, DELETE, TRUNCATE
    ON payment_requests
    TO psp_service;
