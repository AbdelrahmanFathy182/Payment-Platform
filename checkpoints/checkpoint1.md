# Ledger project checkpoint

Paste this at the start of a new session, together with the current migration SQL.

## How to work with me (read first)

- Act as a **mentor**: ask me questions, review my attempts, and push back on gaps. Don't hand me finished answers unless I ask directly.
- I'm a recent Computer and Systems Engineering graduate aiming at backend roles, so **don't assume database or Postgres knowledge**. Explain a new term from scratch with an everyday example, and introduce one new concept at a time. I get overwhelmed when several new terms stack up.
- Keep answers short. When I'm lost, restart from the basic idea, not from the detail I got stuck on.
- Have me predict what code will do before it's run, and have me test it against real example rows.

## Project

Double-entry ledger for a payment platform: **Spring Boot + PostgreSQL + Flyway**. Learning-focused. Nothing has been run against a real Postgres yet.

## Accounting model (understood)

- Debit and credit are just left and right sides. Assets increase with debit. Liabilities and revenue increase with credit.
- Every transaction balances: `sum(debits) = sum(credits)`, within one currency.
- Entries are append-only. A mistake is fixed with a **reversal** transaction, never an edit.
- Amounts are whole piasters (`BIGINT`). Balances are derived from entries.
- User wallets are **liabilities** (the platform owes users). `bank_cash` is an **asset**. Fees are **revenue**. A catch-all "balancing" account is a bad idea because it hides errors.

**Event 2 (the standard example):** Sara pays CafeNile 150 EGP and the platform keeps a 3 EGP fee.

| Account | Debit | Credit |
|---|---|---|
| `user_wallet:sara` | 150 | |
| `revenue:fees` | | 3 |
| `user_wallet:cafenile` | | 147 |

`bank_cash` is untouched, because no real money moved.

## Schema decisions (current state)

- Tables: `accounts`, `transactions`, `entries`. UUID primary keys, `TIMESTAMPTZ`.
- `entries`: `account_id`, `transaction_id`, nullable `debits` and `credits` columns, with a `CHECK` that exactly one is positive.
- **Currency** lives on `accounts`, `transactions` and `entries`. Composite foreign keys on `(id, currency)` make an entry's account and transaction agree on currency. One currency per transaction (FX is out of scope for now).
- `transactions`: `type` (TOPUP, PAYMENT, WITHDRAWAL, REFUND, REVERSAL), `idempotency_key UNIQUE`, and `reverses_transaction_id` (`UNIQUE`, with a composite foreign key for matching currency). `CHECK`: type is REVERSAL exactly when the pointer is set.
- `accounts.type` is one of liability, asset, revenue, expense, equity. `status` is active or inactive. `name` is unique.

## Database-enforced rules (drafted, not yet run)

1. Deferred constraint trigger on `entries`: debits must equal credits per transaction at commit.
2. Deferred constraint trigger on `transactions`: a transaction must have at least one entry.
3. Immutability: `BEFORE` triggers reject `UPDATE`, `DELETE` and `TRUNCATE` on `entries` and `transactions`. On `accounts`, `DELETE` and `TRUNCATE` are rejected, and only `status` may be updated.
4. `BEFORE INSERT` on `entries`: an inactive account refuses entries unless the transaction is a REVERSAL.
5. Roles: `postgres` (setup only), `flyway_user` (owns tables, runs migrations), and the app user (`SELECT, INSERT`, plus `UPDATE (status)` on `accounts`). Flyway gets its own credentials via `spring.flyway.user` and `spring.flyway.password`. Set `spring.jpa.hibernate.ddl-auto=validate`.

## Known issues, decided to defer

- A transaction can reverse itself (`reverses_transaction_id = id` passes every constraint). Fix: `CHECK (reverses_transaction_id IS DISTINCT FROM id)`.
- No index on `entries (transaction_id)`, so the balance trigger does a full scan per entry. Fix: `CREATE INDEX entries_transaction_id_idx ON entries (transaction_id);`.
- The grants use the role name `ledger_service`, while earlier discussion used `app_user`. Pick one, and create the roles before the migration runs.
- Entries can still be added later to an already-committed transaction, if the added entries balance.
- A reversal's entries aren't forced to mirror the original's. Open question: enforce in the database or in the service?
- The inactive-account trigger reads `status` without a lock, so a concurrent deactivation can slip through.
- The migrations are one pasted file. Flyway needs separate versioned files (`V1` tables, `V2` triggers, and so on).
- Not done: `created_at DEFAULT now()`, seed accounts (`bank_cash`, `revenue:fees`), idempotency request hash, `entries (account_id, ...)` index.

## Concurrency (where we stopped)

**Problem:** Sara has 100 EGP. Two 80 EGP payments run at once, each checks the balance, and she ends at -60. Both payments balanced individually, so no constraint objected. This is a check-then-act race, an isolation problem, not an atomicity problem.

**Design decided:**
- Take a row lock on the **payer's `accounts` row** as a mutex, *before* reading the balance. Order: lock, read balance, check funds, insert entries, commit.
- Only accounts being **debited** need the lock. Credit-only accounts (CafeNile, `revenue:fees`) are not locked, which avoids a hot-account bottleneck on fees.
- Lock in a **fixed order (sorted by account id)** to prevent deadlocks. Sort in Java and lock one at a time.
- Use `SELECT id FROM accounts WHERE id = :payer_id FOR NO KEY UPDATE;` instead of `FOR UPDATE`. Incoming credits take a weak automatic lock (`FOR KEY SHARE`) on the receiver's row through the foreign key, and `FOR UPDATE` would make them wait.
- Catch deadlock error `40P01` and retry the whole payment. The idempotency key makes the retry safe.
- Keep the default isolation level (`READ COMMITTED`).

**Where I was:** I was asked to write the payment's order of operations (Sara pays CafeNile, 3 EGP fee). My first attempt had the roles swapped and was missing the funds check, the fee line, and the idempotency step. Then I asked for an explanation of Postgres lock types, which is the point we reached.

## Next steps

1. Write the order of operations: request arrives, check idempotency key, lock payer(s) in id order, read balance, reject if insufficient, insert debit, credit and fee entries, commit. Decide whether the idempotency check comes before or after the lock, and what the `UNIQUE` constraint does as a backstop.
2. Turn it into a `@Transactional` `LedgerService.post(...)` method, with a deadlock retry.
3. Load the migrations into a Postgres container (Docker) and test the bad rows: unbalanced transaction, `UPDATE` on `entries`, duplicate idempotency key, double reversal.
4. Write a Testcontainers test with parallel payments, asserting Sara never goes negative.
5. Resolve the deferred issues above, then balance strategy (derived vs cached) and indexes.
6. Write short decision records (ADRs) in `docs/decisions/`, starting with single-currency-per-transaction.