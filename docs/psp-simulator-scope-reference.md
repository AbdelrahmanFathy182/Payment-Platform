# PSP Simulator — Scope & Behavior Reference

**Purpose:** Authoritative reference for the PSP Simulator. It records the
agreed scope and behavior only; it is not an implementation plan.

**Guiding principle:** The simulator is a deliberately minimal fake payment
provider called by a separate Orchestrator. Keep it focused on behaving like
an external PSP from the Orchestrator's point of view. Do not expand scope.

## 1. Role and boundaries

The Orchestrator coordinates the platform's payment workflow and decides what
to do next. The PSP Simulator simulates provider-side decisions, failures, and
records. The Ledger Service records the platform's financial activity
separately.

- The simulator owns **provider-side state and provider IDs only**.
- No retries, fees, refunds, or ledger IDs.
- The simulator does not contact real banks or card networks, move real money,
  or manage merchants, customers, or ledger accounts.
- The simulator does not initiate calls to the Orchestrator.

## 2. Operations

1. **Authorize** a payment.
2. **Capture** an authorized payment — full amount only.
3. **Void** an authorized payment.
4. **Query** the current state of a payment.

Capture and void use the simulator-generated provider ID in the path.
`Idempotency-Key` is required on authorize, capture, and void.

## 3. Payment states and transitions

States: `PENDING`, `AUTHORIZED`, `DECLINED`, `CAPTURED`, `VOIDED`.

Allowed transitions:

- `PENDING` → `AUTHORIZED`
- `AUTHORIZED` → `CAPTURED`
- `AUTHORIZED` → `VOIDED`

All other transitions are terminal. An invalid transition returns HTTP 409.

`PENDING` always resolves to `AUTHORIZED`. There are no timers.

## 4. Idempotency

- The `Idempotency-Key` header is required on authorize, capture, and void.
- The same key used again replays the stored outcome with no new side effects.
- The same key used with a different operation returns HTTP 409.
- A missing key returns HTTP 400.

## 5. Scenario controls (`X-Simulate`)

Absent header means normal success. Supported values:

- `DECLINE` — HTTP 200 with status `DECLINED` and a reason. This is a business
  result, not an error. Authorize only.
- `TEMP_FAILURE` — HTTP 503 before anything is stored. A retry is a fresh
  attempt.
- `RESPONSE_LOST` — apply and commit first, then stall past the client timeout
  (duration configured via a property). Stall after commit, never holding a
  lock.
- `PENDING:<n>` — authorize returns `PENDING`; each query decrements the
  counter; at 0 the state becomes `AUTHORIZED`. No timers. Authorize only.
- `DELAY:<ms>` — fixed latency.

An optional random failure-rate property exists; default is `0` and tests do
not use it.

`DECLINE` and `PENDING` apply to authorize only. There are no scenarios on
query.

## 6. Errors

| Cause | Status |
| --- | --- |
| Bad request / missing idempotency key | 400 |
| Unknown ID, or unknown caller key on query | 404 |
| Invalid transition, or key reused with a different operation | 409 |
| Temporary failure (`TEMP_FAILURE`) | 503 |

## 7. Query

- Query by provider ID or by caller key.
- Returns the current state.
- An unknown caller key returns HTTP 404.

## 8. Database

Schema is already designed (see the actual Flyway migrations).

`payments_model`: `id UUID`, `state`, `amount BIGINT` (minor units),
`currency`, `decline_reason`, `created_at`, `updated_at`,
`pending_queries_remaining`.

`payment_requests`: `id`, `idempotency_key` (unique), `operation`,
`payment_id` (NOT NULL, FK), `outcome` (JSONB, NOT NULL), `created_at`.

Database rules:

- Triggers raise SQLSTATE `PS409` for illegal state transitions and illegal
  initial states, and maintain `updated_at`.
- A check requires `pending_queries_remaining = 0` unless the state is
  `PENDING`; resolution must update both in one statement.
- A duplicate idempotency key raises SQLSTATE `23505`.

## 9. Concurrency

- Use `SELECT … FOR UPDATE` on the payment row.
- The unique key decides duplicate races.
- The loser rolls back and replays the stored outcome.
- Service maps `PS409` → HTTP 409 and `23505` → replay.

## 10. Explicitly out of scope

- Real bank, card-network, or payment-provider integration.
- Actual movement, clearing, settlement, or payout of money.
- Refunds, partial captures, multiple captures.
- Authorization expiry.
- Reconciliation endpoints.
- Retries, fees, or ledger IDs in provider responses.
- Multiple provider profiles or provider selection.
- Merchant, customer, or Ledger account management.
- The PSP initiating requests to the Orchestrator.
- A monitoring or administration dashboard.

## 11. Implementation order

1. Fix test DB access and verify the DB rules.
2. Define the API contract (DTOs, error mapping, `X-Simulate` parsing).
3. Authorize: success, decline, duplicate replay, key mismatch, temporary
   failure.
4. Query by ID and key, including the `PENDING` countdown.
5. Capture.
6. Void.
7. `DELAY` and `RESPONSE_LOST`.
8. HTTP-level concurrency tests.
9. Dockerfile and Compose service.
10. Read-only review of the Orchestrator's PSP client against this contract.
