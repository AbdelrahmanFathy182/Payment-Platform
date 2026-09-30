Definitely. For your project, I'd make the cheat sheet more than just CRUD. You need **psql, SQL, permissions, schemas, transactions, constraints, indexes, PostgreSQL-specific features, and debugging**.

# PostgreSQL Comprehensive Cheat Sheet

## 1. Connecting to PostgreSQL

### Open `psql` as the PostgreSQL admin

```bash
sudo -u postgres psql
```

### Connect to a database

```bash
psql -U ledger_service -d ledger_db -h localhost -W
```

Meaning:

```text
-U   user
-d   database
-h   host
-W   ask for password
```

### Connect to a specific port

```bash
psql -U ledger_service -d ledger_db -h localhost -p 5432
```

### Exit

```sql
\q
```

---

# 2. `psql` Help

These are **psql commands**, not SQL.

```sql
\?
```

Help for SQL commands:

```sql
\h
```

Specific SQL command:

```sql
\h GRANT
\h CREATE TABLE
\h ALTER TABLE
\h SELECT
```

---

# 3. `psql` Inspection Commands

### Databases

```sql
\l
```

### Connect to database

```sql
\c ledger_db
```

### Current connection

```sql
\conninfo
```

### Current user

```sql
SELECT current_user;
```

### Current database

```sql
SELECT current_database();
```

### PostgreSQL version

```sql
SELECT version();
```

### Tables

```sql
\dt
```

All schemas:

```sql
\dt *.*
```

### Describe table

```sql
\d accounts
```

Detailed:

```sql
\d+ accounts
```

### Schemas

```sql
\dn
```

With privileges:

```sql
\dn+
```

### Users / roles

```sql
\du
```

### Table/sequence privileges

```sql
\dp
```

### Indexes

```sql
\di
```

### Views

```sql
\dv
```
--------------------------------------------------------
===


```sql
\l+     database privileges
```
```sql
\dn+      schema privileges
```
```sql
\dp       table/sequence privileges
```
```sql
\du+      role attributes + memberships
```
```sql
\df+      functions/procedures
```

---

# 4. PostgreSQL Hierarchy

This is important for understanding permissions:

```text
PostgreSQL Server
│
├── Database
│   │
│   ├── Schema
│   │   │
│   │   ├── Table
│   │   ├── View
│   │   ├── Sequence
│   │   └── Function
│   │
│   └── Schema
│
└── Database
```

Privileges apply to specific objects.

For example:

```sql
GRANT ALL ON DATABASE ledger_db TO ledger_service;
```

does **not** mean:

```text
ALL tables
ALL schemas
ALL sequences
```

`ALL` applies to the object after `ON`.

---

# 5. Databases

### Create

```sql
CREATE DATABASE ledger_db;
```

### Delete

```sql
DROP DATABASE ledger_db;
```

⚠️ Destructive.

### Change owner

```sql
ALTER DATABASE ledger_db OWNER TO ledger_service;
```

### Database privileges

```sql
GRANT CONNECT ON DATABASE ledger_db TO ledger_service;
GRANT CREATE ON DATABASE ledger_db TO ledger_service;
GRANT TEMPORARY ON DATABASE ledger_db TO ledger_service;
```

Or:

```sql
GRANT ALL PRIVILEGES ON DATABASE ledger_db TO ledger_service;
```

Database-level privileges:

```text
CONNECT
CREATE
TEMPORARY
```

---

# 6. Users / Roles

PostgreSQL calls users **roles**.

### Create user

```sql
CREATE USER ledger_service
WITH PASSWORD 'password';
```

### Create role

```sql
CREATE ROLE ledger_service;
```

### Change password

```sql
ALTER USER ledger_service
WITH PASSWORD 'new_password';
```

### Delete

```sql
DROP USER ledger_service;
```

### List

```sql
\du
```

---

# 7. Roles and Membership

A role can be granted to another role.

```sql
GRANT reporting_role TO ledger_service;
```

Remove:

```sql
REVOKE reporting_role FROM ledger_service;
```

This allows role-based permission management.

---

# 8. Schemas

### Create schema

```sql
CREATE SCHEMA ledger;
```

### Create schema owned by user

```sql
CREATE SCHEMA ledger AUTHORIZATION ledger_service;
```

### Delete

```sql
DROP SCHEMA ledger;
```

Delete everything inside:

```sql
DROP SCHEMA ledger CASCADE;
```

⚠️ Dangerous.

### Schema privileges

```sql
GRANT USAGE ON SCHEMA public TO ledger_service;
```

```sql
GRANT CREATE ON SCHEMA public TO ledger_service;
```

Or:

```sql
GRANT ALL PRIVILEGES ON SCHEMA public TO ledger_service;
```

Schema privileges:

```text
USAGE
CREATE
```

---

# 9. Tables

## Create

```sql
CREATE TABLE accounts (
    id UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);
```

## Delete

```sql
DROP TABLE accounts;
```

With dependencies:

```sql
DROP TABLE accounts CASCADE;
```

⚠️ Be careful.

## Add column

```sql
ALTER TABLE accounts
ADD COLUMN status VARCHAR(20);
```

## Remove column

```sql
ALTER TABLE accounts
DROP COLUMN status;
```

## Rename column

```sql
ALTER TABLE accounts
RENAME COLUMN name TO account_name;
```

## Rename table

```sql
ALTER TABLE accounts
RENAME TO ledger_accounts;
```

---

# 10. Data Types

Common types:

```text
UUID
VARCHAR(n)
TEXT

INTEGER
BIGINT
NUMERIC(p,s)

BOOLEAN

DATE
TIMESTAMP
TIMESTAMPTZ

JSONB

BYTEA
```

### Money

Use:

```sql
amount NUMERIC(19,2)
```

Avoid:

```text
FLOAT
DOUBLE
```

for monetary values.

### UUID

```sql
id UUID PRIMARY KEY
```

---

# 11. INSERT

```sql
INSERT INTO accounts (id, name)
VALUES ('...', 'Merchant');
```

Multiple:

```sql
INSERT INTO accounts (id, name)
VALUES
    ('...', 'Merchant'),
    ('...', 'Platform');
```

---

# 12. SELECT

Everything:

```sql
SELECT * FROM accounts;
```

Specific columns:

```sql
SELECT id, name
FROM accounts;
```

Alias:

```sql
SELECT
    id AS account_id,
    name AS account_name
FROM accounts;
```

---

# 13. WHERE

```sql
SELECT *
FROM accounts
WHERE name = 'Merchant';
```

Operators:

```text
=
<>
!=
>
<
>=
<=
```

Multiple conditions:

```sql
WHERE status = 'ACTIVE'
AND name = 'Merchant';
```

```sql
WHERE status = 'ACTIVE'
OR status = 'PENDING';
```

---

# 14. Useful Conditions

### IN

```sql
WHERE status IN ('ACTIVE', 'PENDING');
```

### NOT IN

```sql
WHERE status NOT IN ('DELETED');
```

### BETWEEN

```sql
WHERE amount BETWEEN 10 AND 100;
```

### NULL

Wrong:

```sql
WHERE status = NULL;
```

Correct:

```sql
WHERE status IS NULL;
```

```sql
WHERE status IS NOT NULL;
```

### LIKE

```sql
WHERE name LIKE 'Merch%';
```

Case-insensitive PostgreSQL version:

```sql
WHERE name ILIKE 'merch%';
```

---

# 15. ORDER BY

```sql
SELECT *
FROM accounts
ORDER BY created_at ASC;
```

Descending:

```sql
ORDER BY created_at DESC;
```

Multiple:

```sql
ORDER BY status ASC, created_at DESC;
```

---

# 16. LIMIT / OFFSET

```sql
SELECT *
FROM accounts
LIMIT 10;
```

```sql
SELECT *
FROM accounts
LIMIT 10 OFFSET 20;
```

---

# 17. UPDATE

```sql
UPDATE accounts
SET status = 'ACTIVE'
WHERE id = '...';
```

Multiple columns:

```sql
UPDATE accounts
SET
    status = 'ACTIVE',
    name = 'New Name'
WHERE id = '...';
```

⚠️ Without `WHERE`:

```sql
UPDATE accounts
SET status = 'ACTIVE';
```

updates **every row**.

---

# 18. DELETE

```sql
DELETE FROM accounts
WHERE id = '...';
```

Without `WHERE`:

```sql
DELETE FROM accounts;
```

deletes every row.

---

# 19. Constraints

These are especially important for your Ledger.

### PRIMARY KEY

```sql
id UUID PRIMARY KEY
```

Guarantees:

```text
unique
NOT NULL
```

### UNIQUE

```sql
email TEXT UNIQUE
```

### NOT NULL

```sql
name TEXT NOT NULL
```

### CHECK

```sql
amount NUMERIC(19,2)
CHECK (amount > 0)
```

### FOREIGN KEY

```sql
account_id UUID
REFERENCES accounts(id)
```

---

# 20. Foreign Keys

Basic:

```sql
CREATE TABLE entries (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL
        REFERENCES accounts(id)
);
```

### ON DELETE

```sql
REFERENCES accounts(id)
ON DELETE CASCADE
```

Other options:

```text
CASCADE
RESTRICT
NO ACTION
SET NULL
SET DEFAULT
```

Be careful with `CASCADE`, especially in financial data.

---

# 21. Adding Constraints Later

```sql
ALTER TABLE accounts
ADD CONSTRAINT accounts_name_unique
UNIQUE (name);
```

Foreign key:

```sql
ALTER TABLE entries
ADD CONSTRAINT entries_account_fk
FOREIGN KEY (account_id)
REFERENCES accounts(id);
```

Check:

```sql
ALTER TABLE entries
ADD CONSTRAINT positive_amount
CHECK (amount > 0);
```

---

# 22. Aggregation

### Count

```sql
SELECT COUNT(*)
FROM accounts;
```

### Sum

```sql
SELECT SUM(amount)
FROM entries;
```

### Average

```sql
SELECT AVG(amount)
FROM entries;
```

### Minimum / maximum

```sql
SELECT MIN(amount), MAX(amount)
FROM entries;
```

---

# 23. GROUP BY

```sql
SELECT account_id, SUM(amount)
FROM entries
GROUP BY account_id;
```

Example Ledger concept:

```text
account A → +500
account B → -200
account C → -300
```

You could derive balances with:

```sql
SELECT account_id, SUM(amount)
FROM entries
GROUP BY account_id;
```

---

# 24. HAVING

`WHERE` filters rows **before grouping**.

`HAVING` filters groups **after grouping**.

```sql
SELECT account_id, SUM(amount)
FROM entries
GROUP BY account_id
HAVING SUM(amount) > 1000;
```

---

# 25. JOIN

### INNER JOIN

```sql
SELECT
    entries.amount,
    accounts.name
FROM entries
JOIN accounts
    ON entries.account_id = accounts.id;
```

### LEFT JOIN

```sql
SELECT
    accounts.name,
    entries.amount
FROM accounts
LEFT JOIN entries
    ON entries.account_id = accounts.id;
```

Common joins:

```text
INNER JOIN
LEFT JOIN
RIGHT JOIN
FULL JOIN
CROSS JOIN
```

---

# 26. Transactions

Extremely important for your Payments Platform.

```sql
BEGIN;

UPDATE accounts
SET ...

INSERT INTO entries
...

COMMIT;
```

Cancel:

```sql
ROLLBACK;
```

Mental model:

```text
BEGIN
  ↓
operation
  ↓
operation
  ↓
operation
  ↓
COMMIT
```

All succeed together.

Or:

```text
BEGIN
  ↓
operation
  ↓
💥
  ↓
ROLLBACK
```

Changes are undone.

---

# 27. Savepoints

Partial rollback:

```sql
BEGIN;

INSERT INTO accounts ...;

SAVEPOINT point1;

INSERT INTO entries ...;

ROLLBACK TO point1;

COMMIT;
```

---

# 28. Transaction Isolation

PostgreSQL supports:

```text
READ COMMITTED
REPEATABLE READ
SERIALIZABLE
```

Check current setting:

```sql
SHOW transaction_isolation;
```

Set for a transaction:

```sql
BEGIN TRANSACTION ISOLATION LEVEL SERIALIZABLE;
```

This becomes important when you start dealing with **concurrent payments and account balances**.

---

# 29. Locking

Row lock:

```sql
SELECT *
FROM accounts
WHERE id = '...'
FOR UPDATE;
```

This locks the selected rows until the transaction ends.

Other options include:

```sql
FOR UPDATE
FOR NO KEY UPDATE
FOR SHARE
FOR KEY SHARE
```

You'll eventually encounter this when dealing with concurrent Ledger operations.

---

# 30. Indexes

Create:

```sql
CREATE INDEX idx_entries_account_id
ON entries(account_id);
```

Unique:

```sql
CREATE UNIQUE INDEX idx_payment_idempotency
ON payments(idempotency_key);
```

Delete:

```sql
DROP INDEX idx_entries_account_id;
```

List:

```sql
\di
```

---

# 31. Composite Index

Index multiple columns:

```sql
CREATE INDEX idx_entries_account_created
ON entries(account_id, created_at);
```

Order matters.

For example:

```text
(account_id, created_at)
```

is different from:

```text
(created_at, account_id)
```

---

# 32. EXPLAIN

See how PostgreSQL plans a query:

```sql
EXPLAIN
SELECT *
FROM entries
WHERE account_id = '...';
```

Actual execution:

```sql
EXPLAIN ANALYZE
SELECT *
FROM entries
WHERE account_id = '...';
```

`EXPLAIN ANALYZE` actually executes the query, so be careful with `UPDATE`/`DELETE`.

---

# 33. Views

Create:

```sql
CREATE VIEW account_balances AS
SELECT
    account_id,
    SUM(amount) AS balance
FROM entries
GROUP BY account_id;
```

Query:

```sql
SELECT *
FROM account_balances;
```

Delete:

```sql
DROP VIEW account_balances;
```

---

# 34. Sequences

Commonly used for integer IDs.

Create:

```sql
CREATE SEQUENCE account_seq;
```

Next value:

```sql
SELECT nextval('account_seq');
```

Current value:

```sql
SELECT currval('account_seq');
```

However, for your project you'll likely use **UUIDs** rather than sequence-generated IDs.

---

# 35. PostgreSQL `RETURNING`

Very useful.

Instead of:

```sql
INSERT ...
```

then another:

```sql
SELECT ...
```

you can do:

```sql
INSERT INTO accounts (id, name)
VALUES ('...', 'Merchant')
RETURNING id;
```

Also:

```sql
UPDATE accounts
SET status = 'ACTIVE'
WHERE id = '...'
RETURNING *;
```

And:

```sql
DELETE FROM accounts
WHERE id = '...'
RETURNING *;
```

---

# 36. UPSERT

PostgreSQL supports:

```sql
INSERT INTO accounts (id, name)
VALUES ('...', 'Merchant')
ON CONFLICT (id)
DO UPDATE SET name = EXCLUDED.name;
```

Or ignore:

```sql
ON CONFLICT DO NOTHING;
```

This will be useful when you deal with idempotency and concurrency.

---

# 37. JSONB

PostgreSQL can store JSON:

```sql
metadata JSONB
```

Insert:

```sql
INSERT INTO payments (metadata)
VALUES ('{"provider":"test","attempt":1}');
```

Query:

```sql
SELECT *
FROM payments
WHERE metadata->>'provider' = 'test';
```

---

# 38. Dates / Times

Current timestamp:

```sql
SELECT NOW();
```

Current date:

```sql
SELECT CURRENT_DATE;
```

Timestamp with timezone:

```sql
TIMESTAMPTZ
```

For backend applications, `TIMESTAMPTZ` is generally preferable when representing an actual point in time.

---

# 39. Permissions

## Database

```sql
GRANT CONNECT ON DATABASE ledger_db TO ledger_service;
GRANT CREATE ON DATABASE ledger_db TO ledger_service;
GRANT TEMPORARY ON DATABASE ledger_db TO ledger_service;
```

All:

```sql
GRANT ALL PRIVILEGES
ON DATABASE ledger_db
TO ledger_service;
```

Database privileges:

```text
CONNECT
CREATE
TEMPORARY
```

---

## Schema

```sql
GRANT USAGE
ON SCHEMA public
TO ledger_service;
```

```sql
GRANT CREATE
ON SCHEMA public
TO ledger_service;
```

All:

```sql
GRANT ALL PRIVILEGES
ON SCHEMA public
TO ledger_service;
```

Schema privileges:

```text
USAGE
CREATE
```

---

## Table

```sql
GRANT SELECT
ON accounts
TO ledger_service;
```

```sql
GRANT SELECT, INSERT, UPDATE, DELETE
ON accounts
TO ledger_service;
```

All:

```sql
GRANT ALL PRIVILEGES
ON accounts
TO ledger_service;
```

Table privileges:

```text
SELECT
INSERT
UPDATE
DELETE
TRUNCATE
REFERENCES
TRIGGER
```

---

## Sequence

```sql
GRANT USAGE
ON SEQUENCE account_seq
TO ledger_service;
```

Privileges:

```text
USAGE
SELECT
UPDATE
```

---

## Revoke

```sql
REVOKE DELETE
ON accounts
FROM ledger_service;
```

---

# 40. Privilege Inspection

Schema:

```sql
\dn+
```

Tables:

```sql
\dp
```

Users:

```sql
\du
```

Database:

```sql
\l+
```

Check a specific privilege:

```sql
SELECT has_schema_privilege(
    'ledger_service',
    'public',
    'CREATE'
);
```

Table:

```sql
SELECT has_table_privilege(
    'ledger_service',
    'accounts',
    'SELECT'
);
```

---

# 41. Ownership

PostgreSQL has a distinction between:

```text
privilege
```

and:

```text
ownership
```

Check table owner:

```sql
\d accounts
```

Change owner:

```sql
ALTER TABLE accounts
OWNER TO ledger_service;
```

Ownership is more powerful than simply granting individual privileges.

---

# 42. `PUBLIC`

In PostgreSQL, `PUBLIC` means:

> Every role/user.

For example:

```sql
GRANT CONNECT ON DATABASE ledger_db TO PUBLIC;
```

means every role can connect, subject to other requirements.

You can revoke:

```sql
REVOKE CONNECT ON DATABASE ledger_db FROM PUBLIC;
```

Be careful with this because it can affect many users.

---

# 43. Useful PostgreSQL Server Commands

Ubuntu:

```bash
sudo systemctl status postgresql
```

Start:

```bash
sudo systemctl start postgresql
```

Stop:

```bash
sudo systemctl stop postgresql
```

Restart:

```bash
sudo systemctl restart postgresql
```

Check listening ports:

```bash
sudo ss -ltnp | grep 5432
```

---

# 44. PostgreSQL Configuration

Show a setting:

```sql
SHOW port;
```

```sql
SHOW max_connections;
```

```sql
SHOW data_directory;
```

Search settings:

```sql
SELECT name, setting
FROM pg_settings
WHERE name LIKE '%timeout%';
```

---

# 45. Flyway + PostgreSQL

Your project uses:

```text
Spring Boot
    ↓
DataSource
    ↓
PostgreSQL
    ↓
Flyway
```

Migration location:

```text
src/main/resources/db/migration/
```

Example:

```text
V1__create_accounts.sql
V2__create_postings.sql
V3__create_entries.sql
```

Basic migration:

```sql
CREATE TABLE accounts (
    id UUID PRIMARY KEY,
    name TEXT NOT NULL
);
```

Flyway tracks migrations in:

```text
flyway_schema_history
```

Inspect:

```sql
SELECT *
FROM flyway_schema_history;
```

Important rule:

```text
V1 → don't edit after applying
V2 → create a new migration for changes
V3 → ...
```

---

# 46. Useful SQL Patterns for Your Payments Project

### Calculate an account balance

```sql
SELECT COALESCE(SUM(amount), 0)
FROM entries
WHERE account_id = '...';
```

### Find an idempotency key

```sql
SELECT *
FROM payments
WHERE idempotency_key = 'abc123';
```

### Enforce uniqueness

```sql
CREATE UNIQUE INDEX
idx_payment_idempotency_key
ON payments(idempotency_key);
```

### Find duplicate-looking data

```sql
SELECT idempotency_key, COUNT(*)
FROM payments
GROUP BY idempotency_key
HAVING COUNT(*) > 1;
```

### Verify a posting balances

```sql
SELECT SUM(amount)
FROM entries
WHERE posting_id = '...';
```

Expected:

```text
0
```

That last pattern is going to become particularly important for your **double-entry Ledger**.

---

# 47. Dangerous Commands

Keep these mentally highlighted:

```sql
DROP DATABASE ...
DROP TABLE ...
DROP SCHEMA ... CASCADE

DELETE FROM table;
UPDATE table SET ...;

TRUNCATE TABLE ...
```

Especially:

```sql
DELETE FROM accounts;
```

and:

```sql
UPDATE accounts
SET status = 'ACTIVE';
```

because missing `WHERE` can affect every row.

---

# 48. The 20 Commands I'd Actually Memorize

You don't need to memorize this entire sheet. Keep it nearby.

```text
\l              databases
\c DB           connect
\dt             tables
\d table        describe table
\dn+            schemas + privileges
\du             users/roles
\dp             table privileges
\di             indexes
\q              quit

CREATE TABLE
ALTER TABLE
DROP TABLE

SELECT
INSERT
UPDATE
DELETE

BEGIN
COMMIT
ROLLBACK

GRANT
REVOKE
```

And for your **Payments Platform**, the concepts I'd prioritize learning rather than memorizing syntax are:

```text
1. Transactions
2. Constraints
3. Foreign keys
4. Indexes
5. Isolation / concurrency
6. Locks
7. Database vs schema vs table permissions
8. NUMERIC for money
9. GROUP BY / SUM
10. Flyway migrations
```

Those are going to show up repeatedly when you build the **Ledger**, especially when we get to the question of how to make the double-entry invariants hold even when multiple requests hit the system concurrently.