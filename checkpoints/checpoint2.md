# Ledger Service: Handoff Document

Paste this at the start of a new session, together with the current migration SQL if needed.

## 1. How to work with me (read first)

- Act as a **mentor**: ask me questions, review my attempts, push back on gaps. Don't hand me finished answers unless I ask directly.
- I'm a recent Computer and Systems Engineering graduate aiming at backend roles. **Don't assume database or Postgres knowledge.** Explain a new term from scratch with an everyday example, one new concept at a time. I get overwhelmed when several new terms stack up.
- Keep answers short. When I'm lost, restart from the basic idea, not from the detail I got stuck on.
- Have me predict what code will do before it's run.
- I don't want lab exercises or extra detours; I want to build the real service.
- I get impatient when discussion goes into code detail while we're still at design level. Stay at the level I'm working at.

## 2. Project

Double-entry **Ledger service** for a payment platform: Spring Boot, PostgreSQL, Flyway, Maven. It is one of three services in a monorepo (orchestrator, ledger-service, psp-simulator), each with its own Postgres. The Ledger is organized in layers: controller, service, repository.

The Ledger **knows nothing about payments**. It only keeps the books correct. The Orchestrator owns payment lifecycle, fee computation, refund business rules and retries, and calls the Ledger synchronously.

## 3. Accounting model

- Debit and credit are left and right sides. Assets and expenses grow with debit. Liabilities, revenue and equity grow with credit.
- Every transaction balances (`sum(debits) = sum(credits)`) within one currency. FX is out of scope.
- Entries are append-only. Mistakes are fixed with a **reversal**, never an edit.
- Amounts are whole piasters (`BIGINT`).
- User wallets are liabilities, `bank_cash` is an asset, fees are revenue. No catch-all "balancing" account.
- **Reversal vs refund:** a reversal is the exact mirror of the original (fixes a mistake, never partial). A refund is a normal new transaction with its own entries (type `REFUND`), may be partial, and is not linked to the original in the schema.

Event 2: Sara pays CafeNile 150 EGP, platform fee 3 EGP.

| Account | Debit | Credit |
|---|---|---|
| `user_wallet:sara` | 150 | |
| `revenue:fees` | | 3 |
| `user_wallet:cafenile` | | 147 |

`bank_cash` is untouched (no real money moved). Reversal of this: every line flipped, same accounts and amounts.

## 4. Database state (migrations V1 to V12, all ran successfully)

**Tables**
- `accounts`: UUID id, unique `name`, `status` (active/inactive), `type` (liability/asset/revenue/expense/equity), `currency` (EGP/USD), `created_at`, `UNIQUE (id, currency)`.
- `transactions`: `type` (TOPUP, PAYMENT, WITHDRAWAL, REFUND, REVERSAL), `currency`, `idempotency_key` (NOT NULL UNIQUE), `idempotency_hash VARCHAR(64)` (nullable, not unique), `reverses_transaction_id` (UNIQUE, composite FK for matching currency), `CHECK` type is REVERSAL iff pointer set, `CHECK` no self-reversal.
- `entries`: `account_id`, `transaction_id`, nullable `debits`/`credits` with `CHECK` that exactly one is positive, `currency`, composite FKs on `(id, currency)` to accounts and transactions. Indexes on `transaction_id` and `account_id`.
- `created_at` has `DEFAULT NOW()` on all three tables.
- Seed accounts: `bank_cash` (asset) id `00000000-0000-0000-0000-000000000001`, `revenue:fees` (revenue) id `00000000-0000-0000-0000-000000000002`, both EGP, active.

**Rules enforced by the database**
1. Deferred constraint trigger on `entries`: debits equal credits per transaction at commit.
2. Deferred constraint trigger on `transactions`: at least one entry.
3. Immutability: `UPDATE`/`DELETE`/`TRUNCATE` rejected on `entries` and `transactions`. On `accounts`, `DELETE`/`TRUNCATE` rejected and only `status` may change.
4. `BEFORE INSERT` on `entries`: inactive account refuses entries unless the transaction is a REVERSAL.
5. `BEFORE INSERT` on `entries` (sealed transactions): rejected if the transaction's `created_at <> now()`, so entries can't be added to an already-committed transaction.
6. Deferred constraint trigger on `transactions` (reversal mirror): for a REVERSAL, same entry count as the original, every reversal entry has a flipped partner in the original (same account and amount, opposite side) and vice versa.
7. Roles: app connects as `ledger_service` (`SELECT, INSERT` on all three tables, plus `UPDATE (status)` on `accounts`). Flyway uses its own user via `spring.flyway.user/password`. `spring.jpa.hibernate.ddl-auto=validate`.

**Migration rules:** never edit an applied migration; every change is a new `V` file with one clear purpose.

**Critical constraint:** the sealed-transaction trigger compares `created_at` with `now()`. **Java/JPA must never send its own `created_at`** on a `transactions` insert, or every payment is rejected. Let the database fill it (read-only in the entity).

## 5. Service design decisions

**Endpoints**
1. Create account
2. Change account status (only `status` is editable)
3. Post transaction (payments, top-ups, withdrawals and refunds all come through here)
4. Reverse transaction (request is just "reverse X"; the Ledger builds the mirror itself, and the V12 trigger is only a safety net)
5. Reads: account balance, entries history

**Post request shape**
```json
{
  "idempotencyKey": "abc",
  "type": "PAYMENT",
  "currency": "EGP",
  "entries": [
    { "accountId": "<sara>",     "side": "DEBIT",  "amount": 150 },
    { "accountId": "<cafenile>", "side": "CREDIT", "amount": 147 },
    { "accountId": "<fees>",     "side": "CREDIT", "amount": 3 }
  ]
}
```
The caller does **not** send transaction id, entry ids, `created_at`, the hash, per-entry currency, or `reverses_transaction_id`. The Ledger generates ids, copies the transaction currency onto each entry, and maps `side` to the `debits`/`credits` column. The idempotency key may be in the body or an `Idempotency-Key` header (be consistent across endpoints). `REVERSAL` type is refused on the post endpoint.

**Idempotency**
- Caller sends the key on every write (including reverse). The Ledger **computes the hash itself** from a canonical form of the request: type, entries sorted by a fixed rule (account id, side, amount), no timestamps, key excluded. The same contents must always give the same hash.
- Check order on every write: (1) look up the key; found with same hash returns the stored result; found with different hash is an error (`IDEMPOTENCY_KEY_REUSED`, 422); (2) not found: run the business checks and write.
- Reversing an already-reversed transaction with a new key returns 409 `ALREADY_REVERSED`. The key check comes first so a true retry never gets a 409.
- The `UNIQUE` constraints are the backstop.

**Balance:** **derived** from entries (no cached balance table). Computed in the database in one query so the totals come from one snapshot. Formula depends on `accounts.type`: liability, revenue, equity use credits minus debits; asset and expense use debits minus credits. Use `COALESCE` and a left join so an account with no entries returns 0.

**Concurrency (payment)**
- Isolation stays `READ COMMITTED`.
- Check-then-act race (two 80 EGP payments against 100 EGP) is prevented by locking the **payer's `accounts` row before reading the balance**: lock, read balance, check funds, insert entries, commit. The check must happen inside the lock.
- Lock statement: `SELECT id FROM accounts WHERE id = :payer_id FOR NO KEY UPDATE;` (not `FOR UPDATE`, which would block incoming credits that take a weak `FOR KEY SHARE` lock through the foreign key).
- Only **debited** accounts are locked. Credit-only accounts (merchant, `revenue:fees`) are not, to avoid a hot-account bottleneck.
- With several locked accounts, lock one at a time in **sorted account id order** (sort in Java) to prevent deadlocks.
- Deadlock = Postgres error `40P01`. Postgres only cancels the transaction; the service retries. The retry loop goes **outside** the `@Transactional` method, in a **different class** (for example `LedgerFacade` retries, `LedgerService.post` is transactional), because a cancelled transaction is dead and Spring's proxy skips same-class calls. Each retry starts a brand-new transaction.

**Responsibilities by layer**

| Job | Where |
|---|---|
| Required fields, positive amounts, request shape | Controller |
| Turn errors into reason codes and HTTP status | Controller |
| Idempotency decision (compare hash, return stored or reject) | Service (repository only fetches by key) |
| When to lock, funds decision, build transaction and entries, early debit=credit check, deadlock retry | Service |
| Run lock/balance/insert queries | Repository (no decisions) |
| Hold locks, execute queries, enforce balance/immutability/sealed/mirror | Database |

Debits equal credits is checked in two places: early in the service (clean reason code), and by the database trigger (cannot be bypassed).

**Error design (every Ledger error carries a reason code; the Orchestrator reads the code, not the message)**

| Kind | Examples | Orchestrator reaction |
|---|---|---|
| Retry later | Deadlock (after the Ledger's own retries), timeout/no answer | Retry with the **same idempotency key** (safe) |
| Stop, fix the request | Missing fields, unbalanced entries, key reused with different contents | Fail the payment and alert (a bug) |
| Stop, needs a decision | Inactive account, insufficient funds, already reversed | Move to needs-ops, or reject as a business outcome |

**Division of business rules:** the Orchestrator decides refund eligibility, amount, count, fee return, and tracks "refunded so far". The Ledger only enforces ledger rules, so it cannot stop an over-refund. The Ledger alone validates account status.

## 6. Testing strategy

| What | Test with |
|---|---|
| Pure Java logic (hash, validation, side mapping, hash stable under entry order) | Plain unit tests |
| Database rules (unbalanced transaction, `UPDATE` on `entries`, duplicate key, double reversal, sealed transaction, bad reversal mirror) | Real Postgres (Testcontainers) |
| Service behavior (idempotent retry returns stored result, key reuse rejected) | Real Postgres |
| Concurrency (parallel 80 EGP payments on Sara's 100, never negative; deadlock retry works) | Real Postgres, parallel threads |

H2 or mocks are not acceptable for database rules or locking.

## 7. Known issues and open questions

- The inactive-account trigger reads `status` without a lock; a concurrent deactivation can slip through. Fix together with the payer lock.
- The reversal mirror check matches by account and amount, not as a strict multiset; repeated identical lines in the original could slip through. Add a test.
- A reversal can itself be reversed; decide whether that is allowed.
- Refunds are unlinked from the original in the schema (no DB protection against over-refund).
- Funds check applies to debited user wallets (liabilities); the rule for debited accounts of other types (e.g. `bank_cash` asset on a top-up) is not decided yet.
- The balance trigger recomputes per entry row; revisit only if slow.
- Typos in applied migration object names can't be renamed without a new migration.

## 8. Next steps

1. **Write the payment's order of operations** (not yet done by me): request arrives, validate, check idempotency key and hash, lock payer(s) in id order, read balance, reject if insufficient, insert transaction and entries (debit Sara, credit CafeNile, credit fees), commit. Then turn it into `LedgerService.post(...)` plus the retrying facade.
2. Implement reverse, create account, change status, and the read endpoints.
3. Write the tests from section 6, starting with the parallel-payments test.
4. Decide the open questions in section 7.
5. Write short decision records (ADRs) in `docs/decisions/`, starting with single-currency-per-transaction.