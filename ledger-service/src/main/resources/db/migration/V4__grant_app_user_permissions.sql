GRANT
SELECT
,
    INSERT ON entries TO ledger_service;

GRANT
SELECT
,
    INSERT ON transactions TO ledger_service;

GRANT
SELECT
,
    INSERT ON accounts TO ledger_service;

GRANT
UPDATE (status) ON accounts TO ledger_service;