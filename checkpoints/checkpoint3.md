# Ledger Service: Handoff Document (checkpoint 2)

Paste this at the start of a new session, together with the migration SQL if needed.

## 1. How to work with me (read first)

- Act as a **mentor**: ask questions, review my attempts, push back on gaps. Don't hand me finished answers unless I ask directly ("give it to me" means give it).
- I'm a recent Computer and Systems Engineering graduate aiming at backend roles. **Don't assume database, Postgres, Spring or JPA knowledge.** Explain a new term from scratch with an everyday example, one new concept at a time. I get overwhelmed when several new terms stack up.
- Keep answers short. When I'm lost, restart from the basic idea, not from the detail I got stuck on.
- Have me predict what code will do before it's run.
- No lab exercises or detours; I want to build the real service. **Keep the file count minimal**; add a class only when something hurts without it.
- Stay at the level I'm working at (design vs code).
- **Tests:** don't suggest checking or asserting on error messages. Don't suggest more owner-level DELETE/TRUNCATE tests on `accounts` (already written).
- When I report a failing test or build, ask for the first line of the failure or the real `Caused by:` before guessing.
- **Build order, inside-out:** domain, repository, exception, service (plus request DTO), then controller, response DTO and error handler. Test each step before the next.

## 2. Project

Double-entry **Ledger service** for a payment platform: Spring Boot 4.1.1 (Spring 7), PostgreSQL 16, Flyway 12, Maven. One of three services in a monorepo (orchestrator, ledger-service, psp-simulator), each with its own Postgres. The Ledger has layers: controller, service, repository (plus `dto` and `domain` packages for data shapes).

The Ledger **knows nothing about payments**. It only keeps the books correct. The Orchestrator owns payment lifecycle, fee computation, refund business rules and retries, and calls the Ledger synchronously.

## 3. Accounting model

- Debit and credit are left and right sides. Assets and expenses grow with debit. Liabilities, revenue and equity grow with credit.
- Every transaction balances (`sum(debits) = sum(credits)`) within one currency. FX is out of scope.
- Entries are append-only. Mistakes are fixed with a **reversal**, never an edit.
- Amounts are whole piasters (`BIGINT`).
- User wallets are liabilities, `bank_cash` is an asset, fees are revenue. No catch-all "balancing" account.
- **Reversal vs refund:** a reversal is the exact mirror of the original (fixes a mistake, never partial). A refund is a normal new transaction (type `REFUND`), may be partial, and is not linked to the original in the schema.
- **A reversal can itself be reversed (decided: allowed).**

Example: Sara pays CafeNile 150 EGP, fee 3 EGP. Debit `user_wallet:sara` 150; credit `revenue:fees` 3; credit `user_wallet:cafenile` 147. `bank_cash` is untouched. Reversal: every line flipped, same accounts and amounts.

## 4. Database state (migrations V1 to V12 applied; see section 8 for V13)

**Tables**
- `accounts`: UUID id (no default), unique `name`, `status` (active/inactive), `type` (liability/asset/revenue/expense/equity), `currency` (EGP/USD), `created_at`, `UNIQUE (id, currency)`.
- `transactions`: UUID id (no default), `type` (TOPUP, PAYMENT, WITHDRAWAL, REFUND, REVERSAL), `currency`, `idempotency_key` (NOT NULL UNIQUE, text), `idempotency_hash VARCHAR(64)` (nullable), `reverses_transaction_id` (UNIQUE, composite FK for matching currency), `CHECK` type is REVERSAL iff pointer set, `CHECK` no self-reversal.
- `entries`: UUID id (no default), `account_id`, `transaction_id`, nullable `debits`/`credits` with `CHECK` that exactly one is positive, `currency`, composite FKs on `(id, currency)`. Indexes on `transaction_id` and `account_id`.
- `created_at` has `DEFAULT NOW()` on all three tables.
- Seed accounts, both EGP and active: `bank_cash` (asset) id `00000000-0000-0000-0000-000000000001`, `revenue:fees` (revenue) id `00000000-0000-0000-0000-000000000002`.

**Rules enforced by the database**
1. Deferred constraint trigger on `entries`: debits equal credits per transaction at commit.
2. Deferred constraint trigger on `transactions`: at least one entry.
3. Immutability (`reject_ledger_modification()`): `UPDATE`/`DELETE`/`TRUNCATE` rejected on `entries` and `transactions`. On `accounts`, `DELETE`/`TRUNCATE` rejected and only `status` may change.
4. `BEFORE INSERT` on `entries` (`guard_inactive_accounts`): an inactive account refuses entries unless the transaction type is REVERSAL.
5. `BEFORE INSERT` on `entries` (`guard_sealed_transactions`): rejected if the transaction's `created_at <> now()`.
6. Deferred constraint trigger on `transactions` (`guard_reversal_transactions`): for a REVERSAL, the reversal must mirror the original (see the multiset issue in section 8).
7. Roles: app connects as `ledger_service` (`SELECT, INSERT` on all three tables, plus `UPDATE (status)` on `accounts`). Flyway uses `flyway_user` (table owner). `spring.jpa.hibernate.ddl-auto=validate`.

**Migration rules:** never edit an applied migration; every change is a new `V` file with one clear purpose, numbered **higher than every existing file**. Never use Flyway `outOfOrder`. After changing migrations run `mvn clean test`.

**Critical constraint:** the sealed-transaction trigger compares the transaction's `created_at` with `now()`. **Java/JPA must never send its own `created_at`.** It is read-only in entities.

## 5. Service design decisions

**Endpoints (five):** 1. create account, 2. change account status (only `status` is editable), 3. post transaction (payments, top-ups, withdrawals and refunds), 4. reverse transaction ("reverse X"; the Ledger builds the mirror; accepts any transaction id, including a reversal's), 5. reads (balance, entries history).

**Create account (decided and implemented)**
- The account **name is the natural key** (no idempotency key column on accounts).
- New name: create, respond **201**. Same name, same type and currency: return the existing account, **200**. Same name, different type or currency: **409 `ACCOUNT_NAME_CONFLICT`**.
- The race (two simultaneous creates) is handled by `UNIQUE (name)` as the backstop: the service catches `DataIntegrityViolationException`, re-reads the winner and compares.
- The service generates the id (`UUID.randomUUID()`) and sets status `active`. The caller never sends id, status or timestamp.
- The create method is deliberately **not `@Transactional`**: a failed insert would abort the transaction and break the follow-up lookup.

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
The caller does **not** send transaction id, entry ids, `created_at`, hash, per-entry currency or `reverses_transaction_id`. The Ledger generates ids, copies the currency onto entries and maps `side` to the `debits`/`credits` column. `REVERSAL` type is refused on the post endpoint.

**Idempotency**
- Caller sends the key on every write. The Ledger **computes the hash itself** from a canonical form (type, entries sorted by account id, side, amount; no timestamps; key excluded).
- Order on every write: (1) look up the key: same hash returns the stored result; different hash is an error (`IDEMPOTENCY_KEY_REUSED`, 422); (2) not found: run business checks and write.
- Reversing an already-reversed transaction with a new key returns 409 `ALREADY_REVERSED`. The key check comes first so a true retry never gets a 409.
- The `UNIQUE` constraints are the backstop.

**Balance:** **derived** from entries (no cached table), computed in one query for one snapshot. Liability, revenue, equity: credits minus debits. Asset, expense: debits minus credits. `COALESCE` and a left join so an empty account returns 0.

**Concurrency (payment)**
- `READ COMMITTED`. Lock the **payer's `accounts` row before reading the balance**: lock, read balance, check funds, insert, commit.
- `SELECT id FROM accounts WHERE id = :id FOR NO KEY UPDATE;` (not `FOR UPDATE`). JPA's `@Lock` generates `FOR UPDATE`, so use a native `@Query`.
- Lock only **debited** accounts, one at a time in **sorted id order** (sort in Java).
- Deadlock = Postgres `40P01`. The retry loop goes **outside** the `@Transactional` method, in a **different class** (`LedgerFacade` retries, `LedgerService.post` is transactional). Each retry is a new transaction.

**Layer responsibilities**

| Job | Where |
|---|---|
| Required fields, request shape (`@Valid`) | Controller |
| Turn errors into reason codes and HTTP status (201/200 choice too) | Controller / `@RestControllerAdvice` |
| Idempotency decision, locking, funds decision, building transaction and entries, early debit=credit check, deadlock retry, generating ids | Service |
| Run lock/balance/insert queries, no decisions | Repository |
| Hold locks, enforce balance/immutability/sealed/mirror | Database |

**Errors:** every error carries a reason code; the Orchestrator reads the code, not the message. One shared `LedgerException(code, httpStatus, message)`.

| Kind | Examples | Orchestrator reaction |
|---|---|---|
| Retry later | Deadlock after the Ledger's retries, timeout | Retry with the **same idempotency key** |
| Stop, fix the request | Missing fields, unbalanced, key reused, `ACCOUNT_NAME_CONFLICT` | Fail and alert (a bug) |
| Stop, needs a decision | Inactive account, insufficient funds, already reversed | Needs-ops, or reject as business outcome |

**Business rules split:** the Orchestrator decides refund eligibility, amount, count and fee return. The Ledger only enforces ledger rules, so it cannot stop an over-refund. The Ledger alone validates account status.

## 6. Code state (package `com.abdelrahman.ledger_service`)

Folders: `controller/`, `service/`, `repository/`, `domain/` (with `domain/accountEnums/`), `dto/`, `exception/`. Build succeeds; `contextLoads` passes (entity matches table).

- `Account` (entity, `domain`): fields `id`, `name`, `type`, `currency`, `status`, `createdAt`. Only `status` is updatable (`updatable = false` on the rest, to match `UPDATE (status)`); `createdAt` is `Instant`, insertable/updatable false. `protected Account()`, a public 5-argument constructor `(id, name, type, currency, status)`, getters, setter only for `status`. Id has no `@GeneratedValue`.
- Enums `AccountType`, `AccountCurrency`, `AccountStatus` in `domain.accountEnums`, each public in its own file, lowercase constants matching DB values (EGP/USD for currency). Mapped with `@Enumerated(EnumType.STRING)`.
- `AccountRepository extends JpaRepository<Account, UUID>` with `Optional<Account> findByName(String)`.
- `LedgerException(code, httpStatus, message)` in `exception`.
- `AccountService`: `Result(Account account, boolean created)` record; `create(CreateAccountRequest)` as in section 5.
- `CreateAccountRequest` record `(@NotBlank String name, @NotNull AccountType type, @NotNull AccountCurrency currency)`; `AccountResponse` record `(id, name, type, currency, status)` with `from(Account)`.
- `AccountController`: `POST /accounts`, `@Valid @RequestBody`, returns 201 or 200 with the response DTO. `spring-boot-starter-validation` is a dependency.
- **Not written yet:** the `@RestControllerAdvice` that turns `LedgerException` into the HTTP response with the reason code (and `@Valid` failures into 400); no repository or service tests; the app has not been exercised over HTTP.

**Planned repositories (one per table):** `AccountRepository` (`findByName`, `findById`, `saveAndFlush`, native `lockById`); `TransactionRepository` (`findByIdempotencyKey`, `existsByReversesTransactionId`); `EntryRepository` (`saveAll`, `findByTransactionId`, history by account with `Pageable`, balance via native `@Query`). Open: which repository holds the balance query (it reads `accounts` for the type and `entries` for the sums).

**Local run:** `spring-boot:run` needs a real Postgres with the roles from `postgres/init.sql` (`ledger_service`/`ledger_pass`, `flyway_user`/`flyway_pass`). If Flyway reports a migration "not applied", reset the local database (drop and recreate, run `init.sql`, let Flyway rebuild); never edit or reorder migrations.

## 7. Testing

**Setup:** `DatabaseSetup` (abstract base, `@SpringBootTest`): a **singleton Testcontainers Postgres** started in a `static` block (`org.testcontainers.postgresql.PostgreSQLContainer`, `postgres:16`, `withInitScript("postgres/init.sql")`), `@DynamicPropertySource` for datasource (as `ledger_service`) and Flyway (as `flyway_user`), `@Autowired jdbcTemplate` and `tx` (`TransactionTemplate`), `ownerJdbc()` (a `JdbcTemplate` as `flyway_user`), helpers `createAccount(type)`, `insertPayment(...)`, `insertReversal(...)`, `insertEntry(...)`. The tests currently live in `DatabaseTests` (confirm there is only one such class and no duplicated helpers).

**Habits that matter**
- Container is fresh per run but **shared by all tests in a run**, and **test order is not the writing order**. Every test creates its own accounts; never use seeded accounts in tests that change data; never assert on whole-table counts.
- Shape: set up valid data, change exactly one thing, `assertThrows` around the whole `tx.executeWithoutResult` block (deferred triggers fail at commit), then assert the data (counts unchanged).
- Make sure only the rule under test can reject (keep entries balanced, avoid tripping CHECKs; use the owner connection to bypass permissions when testing triggers).
- Don't test plain UNIQUE/NOT NULL; double payment/reversal are covered by service tests.
- Plain unit tests for pure logic; real Postgres (Testcontainers) for DB rules, service behavior and concurrency. No H2/mocks for DB rules or locking.

**Written:** happy path; rule 1 (unbalanced); rule 2 (no entries); rule 3 (accounts: name can't change as app user and as owner, status can change, delete as app user and owner, truncate; entries and transactions: update, delete, truncate); rule 5 (sealed, two commits); rule 4 (payment touching an inactive account is rejected; a reversal touching an inactive account commits); rule 6 (correct reversal commits, repeated-lines reversal commits, wrong amount, not flipped, wrong account all rejected); reversal of a reversal commits; owner-connection control test; seed accounts test.

**Still to write**
- Rule 7: as `ledger_service`, `UPDATE entries` throws.
- Business-rule CHECKs: entry with both or neither of debits/credits, zero or negative amount, REVERSAL without pointer, PAYMENT with pointer, self-reversal.
- Reversal with a different line count (3-line original).
- Cleanups: one test class, remove helper duplicates, drop redundant assertions and `System.out.print`, consistent names, split by rule group, `100L` in the `Long` assertion.
- Repository tests (`findByName` found / empty), service tests for create account, service tests from the original plan (idempotent retry, key reuse, parallel 80 EGP payments on Sara's 100, deadlock retry).

## 8. Known issues and verify first

**Verify first**
1. **Inactive-account trigger:** at one point it was missing from the test database (`guard_inactive_accounts_trigger` not on `entries`); the test now passes but the cause was not recorded. Confirm the final migration set creates it (query `pg_trigger` on `entries`).
2. **Reversal mirror multiset gap:** `guard_reversal_transactions` checked only that each line has *some* flipped partner plus equal line counts, so a reversal with wrong repeat counts could commit. A fix was drafted as a new migration (next number after the highest, expected V13) that replaces the function using `EXCEPT ALL` in both directions (flipped original minus reversal, reversal minus flipped original). Confirm it is applied and that the "wrong repeat counts" test (original: debit A twice, debit B, credit C three times; bad reversal: credit A once, credit B twice, debit C three times) passes by throwing, with all valid reversals still committing.

**Open**
- The inactive-account trigger reads `status` without a lock; a concurrent deactivation can slip through. Fix together with the payer lock.
- Refunds are unlinked from the original in the schema (no DB protection against over-refund).
- Funds check for debited accounts of other types (e.g. `bank_cash` on a top-up) is not decided.
- The balance trigger recomputes per entry row; revisit only if slow.
- Typos in applied migration object names can't be renamed without a new migration.

**JPA notes**
- `saveAndFlush` returns an object with `createdAt` still null (the database fills it); fine for create account because the response leaves it out.
- With assigned ids, `save` runs an extra `SELECT` first; ignore for now.
- Use `saveAndFlush` where a failure must surface inside a `try/catch`.
- Repository method names refer to Java field names, not column names.

## 9. Next steps

1. Verify the two items above; run `mvn clean test`.
2. Finish the database tests (rule 7, CHECKs, line count, cleanups).
3. Finish create account: repository test, service tests (new, same, conflict), `@RestControllerAdvice`, then run the app and send real requests (curl/Postman).
4. Change status endpoint through the layers. Decide first: what to return for an unknown id, and what to do when the account is already in the requested status.
5. Transactions: `Transaction` and `Entry` entities (nullable `debits`/`credits` as `Long`; plain `UUID` ids vs `@ManyToOne` still open), repositories (lock, balance), `LedgerService.post` plus the retrying `LedgerFacade`, then reverse, then the reads.
6. Write the section 7 service and concurrency tests, starting with the parallel-payments test.
7. Short ADRs in `docs/decisions/`, starting with single-currency-per-transaction, plus: reversal of a reversal is allowed; account name is the create-account key.