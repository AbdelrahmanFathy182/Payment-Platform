# Ledger: Flyway Migrations Review

Spring Boot + PostgreSQL + Flyway. Double-entry ledger for a payment platform. All migrations have run successfully on a real Postgres.

## 1. Accounting decisions

- Debit and credit are left and right sides. Assets increase with debit; liabilities and revenue increase with credit.
- Every transaction balances (`sum(debits) = sum(credits)`) within one currency. FX is out of scope.
- Entries are append-only. Mistakes are fixed with a **reversal**, never an edit.
- Amounts are whole piasters (`BIGINT`). Balances are derived from entries.
- User wallets are liabilities, `bank_cash` is an asset, fees are revenue. No catch-all "balancing" account (it hides errors).
- **Reversal vs refund:** a reversal is the exact mirror of the original (fixes a mistake, never partial). A refund is a new business event with its own entries and is not linked to the original in the schema.

Example (Sara pays CafeNile 150 EGP, 3 EGP fee): debit `user_wallet:sara` 150, credit `revenue:fees` 3, credit `user_wallet:cafenile` 147. `bank_cash` is untouched.

## 2. Schema (V1 to V6, as pasted)

| Table | Key decisions |
|---|---|
| `accounts` | UUID id, unique `name`, `status` (active/inactive), `type` (liability/asset/revenue/expense/equity), `currency` (EGP/USD), `UNIQUE (id, currency)` |
| `transactions` | `type` (TOPUP, PAYMENT, WITHDRAWAL, REFUND, REVERSAL), `idempotency_key UNIQUE`, `reverses_transaction_id UNIQUE`, composite FK for matching currency, `CHECK` reversal type iff pointer set, `CHECK` no self-reversal |
| `entries` | `account_id`, `transaction_id`, nullable `debits`/`credits` with `CHECK` that exactly one is positive, composite FKs on `(id, currency)` to accounts and transactions, index on `transaction_id` |

## 3. Database-enforced rules (V1 to V6)

1. **Balance:** deferred constraint trigger on `entries`; debits equal credits per transaction at commit.
2. **Not empty:** deferred constraint trigger on `transactions`; at least one entry.
3. **Immutability:** `UPDATE`/`DELETE`/`TRUNCATE` rejected on `entries` and `transactions`. On `accounts`, `DELETE`/`TRUNCATE` rejected and only `status` may change.
4. **Inactive accounts:** `BEFORE INSERT` on `entries` rejects entries to inactive accounts unless the transaction is a REVERSAL.
5. **Permissions:** app role `ledger_service` has `SELECT, INSERT` on all three tables plus `UPDATE (status)` on `accounts`.

## 4. Later migrations

| File | Purpose | Notes |
|---|---|---|
| V7 | `created_at DEFAULT NOW()` on all three tables | Only affects future inserts |
| V8 | `entries_account_id_idx` | Speeds up balance lookups. Plain `CREATE INDEX` is fine on an empty table; use `CONCURRENTLY` on live tables (needs Flyway's transaction wrapping disabled) |
| V9 | Seed `bank_cash` (asset) and `revenue:fees` (revenue), fixed UUIDs `...0001` and `...0002` | Fixed ids let Java refer to them as constants |
| V10 | `transactions.idempotency_hash VARCHAR(64)`, nullable, **not unique** | Hash is of the request contents only, not the key. Compared in the service when the same key reappears |
| V11 | `guard_sealed_transactions`: `BEFORE INSERT` on `entries` rejects the entry if its transaction's `created_at <> now()` | Stops adding entries to an already-committed transaction |
| V12 | `guard_reversal_transactions`: deferred constraint trigger on `transactions` | For a REVERSAL, checks equal entry count, and every R entry has a flipped partner in O and vice versa |

## 5. Decisions about where logic lives

- Structure and invariants that must never be bypassed live in the database (balance, immutability, sealed transactions, reversal mirror).
- Per-payment logic lives in one `@Transactional` Java method: idempotency check, lock, funds check, entries. The service should generate reversal entries by flipping the original, with the database as the safety net.
- Existing migrations are never edited once run. Every change is a new `V` file with one clear purpose.
- Flyway uses its own user (`spring.flyway.*`), the app uses `ledger_service`, and `ddl-auto=validate`.

## 6. Review notes and risks

1. **`created_at` is now load-bearing.** V11 compares it with `now()`. If Java/JPA sends its own timestamp on a `transactions` insert, every payment is rejected. Let the database fill it (make the column read-only in the entity).
2. **Reversal check is not a strict multiset match.** It matches by account and amount, so unusual cases with repeated identical lines in the original could slip through. Acceptable for now; worth a test.
3. **A reversal can itself be reversed.** Nothing prevents it. Decide whether that is allowed.
4. **Refunds are unlinked.** Nothing stops refunding more than the original amount or twice. Service-level rule (or a later column).
5. **Inactive-account trigger reads `status` without a lock.** A concurrent deactivation can slip through. Fix together with the payment lock.
6. **Balance trigger runs per entry row**, recomputing the sum each time. Fine at this scale; revisit if it gets slow.
7. **Typos persist.** Names inside applied migrations (functions, triggers) can't be renamed without a new migration.

## 7. Still open

- Payer row lock (`SELECT ... FOR NO KEY UPDATE`, sorted by account id, deadlock `40P01` retry), funds check, and the order of operations in `LedgerService.post(...)`
- Idempotency comparison logic in the service
- Balance strategy (derived vs cached)
- Tests: bad rows (unbalanced, `UPDATE` on `entries`, duplicate key, double reversal), and a Testcontainers test with parallel payments so Sara never goes negative
- ADRs in `docs/decisions/`, starting with single-currency-per-transaction