# Payment Orchestrator: Design Reference

*Working reference for building the orchestrator. Decisions are marked **Decided**, **Proposed** (my recommendation, not yet confirmed by you), or **Open** (needs your answer). Update the status as you go.*

## 1. Context

The orchestrator is the only service clients talk to. It calls two existing services over REST:

- **PSP simulator** (built): `authorize`, `capture`, `void`. All three can fail, be slow, stay pending, or lose their response. Every call accepts an idempotency key, and the PSP can be queried by that key. A query returns the status (including *pending*) or "payment not found".
- **Ledger service** (built): endpoints to post and query transactions, and to create and query accounts. *Open: whether posting accepts an idempotency key and how it signals errors (see section 10).*

The orchestrator owns payments, attempts, idempotency records, and its outbox, in its own database. It never reads another service's tables.

## 2. Core principles

1. **Record intent before the side effect.** Before any PSP or ledger call, the orchestrator durably writes what it is about to do. A crash can then never leave a real-world action with no local record.
2. **A timeout is the absence of information, not a failure.** Uncertain outcomes are first-class states, never collapsed into success or failure.
3. **Every non-terminal state has an owner and a clock.** Something is always responsible for moving a payment forward.
4. **One mechanism for normal operation and recovery.** Recovery is not a special case.
5. **The ledger records what is true; the orchestrator holds everything uncertain.** The ledger is only told about confirmed captures.
6. **A step is done only when its own status says so.** A response, or the lack of one, proves nothing. This applies especially to void.
7. **One step in flight per payment.** Never send a capture or void while an earlier step is unresolved.

## 3. Decided design choices

| # | Decision | Status |
| --- | --- | --- |
| 1 | Client contract is accept-and-poll: the request records intent and returns a payment ID | Decided |
| 2 | Orchestrator captures automatically after a confirmed authorization | Decided |
| 3 | Uncertain outcomes are resolved by waiting and checking, not by defensively voiding | Decided |
| 4 | Reconciliation is implemented as the backstop | Decided |
| 5 | One idempotency key per PSP call, derived from payment ID + attempt number + step | Decided |
| 6 | A retry within a payment is a new *attempt* | Decided |
| 7 | Every PSP call can go pending or lose its response; all such cases share one resolve-by-key procedure | Decided |
| 8 | Operators (IT or payments operations) are alerted, never end users | Decided |
| 9 | The client's idempotency key attaches to the payment, not to an attempt | Proposed |
| 10 | Request path only records intent; a worker performs all PSP and ledger work | Proposed (confirm) |

## 4. Vocabulary

- **Payment:** the client's single intent to move money. It has one ID and one final outcome.
- **Attempt:** one try at authorize, capture and, if needed, void against the PSP. Numbered 1, 2, 3...
- **Step:** authorize, capture, void, or ledger post.
- **Step status:** *in flight*, *in doubt*, *done*, or *failed*.
- **Resending:** repeating the same call with the same key. This stays within the same attempt.
- **New attempt:** new keys and a fresh authorize. Allowed only when the previous attempt is fully closed.

## 5. The resolve-by-key procedure

This is the single recovery mechanism for authorize, capture, and void.

Every PSP call ends in one of four ways:

1. **Succeeded**
2. **Definitively failed** (a decline)
3. **Pending** (the PSP says it is working on it)
4. **Unknown** (timeout or lost response)

Outcomes 3 and 4 are handled identically: **query the PSP by that call's key**. The query returns:

- **Resolved:** record the result and move on.
- **Still pending:** keep polling.
- **Not found:** resend the same call with the same key. Not-found means "not processed yet", not "will never be processed", so it is permission to resend rather than a verdict.

If the status query itself fails or times out, count it as a missed check and try again at the next tick. Authentication errors are the exception: they are configuration problems, so stop and raise an error rather than retrying.

**Key facts about the PSP that drive this:**

- Status queries count as requests, so **polling is what moves a pending operation toward resolving**. Do not use exponential backoff on polling.
- A void sent at a pending operation does nothing except decrement its counter. A pending operation **cannot be cancelled, only waited out**.
- A void response is never proof of a void. Confirm it by querying the void's own key.

## 6. Keys

- PSP key for each call: `payment ID + attempt number + step`, for example `<paymentId>:1:authorize`.
- Keys must be **recomputable after a crash** from durable data. Nothing depends on remembering what was sent.
- The attempt number is written durably **before** any PSP call for that attempt.
- **Ledger posting is keyed on the payment ID alone.** Only one attempt can ever end in a capture, so a bug that somehow captured twice still cannot post twice.

* **A resend must be an identical request.** The PSP ignores a repeated key with the same request and returns the original response. It discards a repeated key with a different request. So store the request payload with the key, or derive it deterministically from the payment row, and never include anything that changes between sends (a timestamp, for example).
* **A discarded call is a bug, not an outcome.** The PSP returns an explicit error when a key is reused with a different request. Do not retry it, poll it, or treat it as a decline. Classify it as a configuration or bug error, stop sending, flag the payment *needs attention*, and log an error with the payment ID and correlation ID. The original operation under that key is unaffected and is still resolved by querying that key. The same applies if a new attempt accidentally reuses an old attempt's key, which is why the attempt number is part of the key.

## 7. Attempts

**Rule:** only one attempt is active at a time, and a new attempt may start only after the previous one is **definitively closed**. Closed means one of: authorize declined, void confirmed, or the PSP definitively reported it never saw the authorize and the attempt was abandoned. An attempt in doubt cannot be closed by voiding. It closes only by resolving.

| Outcome of the previous attempt | Proposed handling |
| --- | --- |
| Authorize declined by the PSP | No new attempt. Payment fails (declines are final). |
| Authorize fails with a transient PSP error (an answer, not silence) | New attempt (no hold exists) |
| Authorize confirmed, capture fails with a transient error, hold voided and void confirmed | New attempt |
| Capture refused for a hard reason | No new attempt. Void the hold, confirm it, fail the payment. |
| Anything still in doubt | No new attempt |

- **Attempt cap:** 3 (Proposed), with growing randomised delay between attempts (roughly 10 s, then 30 s).
- **The deadline always wins:** if the next attempt would start after the payment deadline, the payment fails instead.
- When the cap is reached, the payment ends in a visible failed state, not a loop.
- **After a confirmed authorize, a definitive capture refusal voids the hold** (Proposed). The void must be confirmed before the attempt counts as closed.
- This split between hard failures (declines, final) and transient failures (errors) means every step's error table needs a decline-versus-error distinction.

## 8. Timing

All timings are configuration, not structure. Keep them in one config object.

| Rule | Value | Status |
| --- | --- | --- |
| Fast polling | every 3 s for the first 3 min of a step | Decided |
| Slow polling | every 15 s until resolved | Proposed (you originally said 5 s for 5 min) |
| Hard stop on polling | 24 h, then marked *abandoned* | Proposed |
| Alert, capture in doubt | 10 min from when the capture step started | Decided |
| Alert, authorize or void in doubt | 15 min from when that step started | Decided |
| Payment deadline | 15 min from payment creation | Decided |
| Capture delay | 5 s after authorization is confirmed | Decided |

### Behaviour of each rule

**Fast and slow polling.** The worker queries the in-flight step's key on each tick. Polling for a step never stops while the step is in doubt, so there is no unowned gap. Slow polling ends only through resolution or the hard stop.

**Alerts (10 and 15 min).** Measured from when *that step* started, not from payment creation. They change nothing about what the system does. When a threshold passes: (1) the payment is flagged *needs attention*, (2) an error-level log line is written with payment ID and correlation ID, (3) a metric increments. Polling continues. If the step resolves later, the flag clears and the payment continues normally.

**Payment deadline.** After 15 minutes, the payment **starts no new work**:

- No new attempts.
- An authorization that resolves late is **voided**, not captured.
- A capture or void already in flight is polled to resolution regardless of the deadline.
- Alerts are unaffected.
- Edge case: if an authorize is still pending at the deadline and later comes back not-found, close the attempt as expired without resending, but keep checking that key for a short while. If it lands late, void it.

**Capture delay.** When an authorization is confirmed, write "capture due at confirmation time + 5 s" durably before doing anything else. If the due time would fall after the deadline, void instead of capturing. That decision is made once, at the moment the authorization is confirmed. A crash in the delay window is picked up by the worker like any other pending work.

**Hard stop (24 h).** Polling stops and the payment becomes *abandoned*, a terminal state for humans. Log an error and increment a counter. A hold or charge may still exist at the PSP. **Decided: the reconciliation sweep checks abandoned payments about once a day, so a forgotten hold on a customer's card still gets released**.

## 9. Alerts and operators

- Alerts are for **IT and operations personnel**, never end users. Customers see an honest status such as *processing* or a final outcome.
- **Engineer on call:** system-level signals (growing backlog, PSP resolving nothing).
- **Payments operations:** individual payments needing a judgment call (capture never resolved, unexplained PSP charge).
- For this project an alert is: a queryable *needs attention* flag, an error-level structured log line, and a metric (count of needs-attention payments, age of the oldest stuck payment). No emails or pages. Describe the real-deployment routing in the case study.
- Operators are **view-only** (Proposed). Manual actions such as "resolve as failed" or "retry now" are described in the case study as future work, since they would need authorization rules and an audit trail.

## 10. Lifecycle by step and status

Model a payment as being at a **step** with a **status**, rather than listing a state for every combination. "Possibly charged" is simply *capture, in doubt*.

| Situation | Who moves it forward |
| --- | --- |
| Received, nothing attempted | Worker |
| Authorizing (call in flight) | Worker; if stale, treated as in doubt |
| Authorization in doubt or pending | Worker polls by key |
| Authorized, capture due | Worker, at the scheduled capture time |
| Capturing (call in flight) | Worker; if stale, treated as in doubt |
| Capture in doubt | Worker polls; reconciliation as backstop |
| Captured, ledger pending | Worker retries the ledger post |
| Voiding or void in doubt | Worker polls the void's key |
| **Terminal, finished:** settled, declined, voided | Nobody |
| **Terminal, human needed:** abandoned, retries exhausted, reconciliation found something unsafe to fix | A person |

Risk by step when in doubt:

| Step in doubt | What is at stake | Cost of waiting |
| --- | --- | --- |
| Authorize | A hold may exist that you don't know about | Customer's funds tied up |
| Capture | Money may have moved | Ledger is behind reality |
| Void | A hold may still be sitting there | Customer's funds tied up |

Capture in doubt is the most serious and gets the earliest alert.

**Safety rules:**

- The ledger posts only after capture is confirmed. Capture in doubt means no ledger entry yet.
- A late-confirming capture is **always** posted to the ledger, even past the deadline. The money moved.
- A payment is not closed until every step it started is resolved, including voids.
- All state changes go through one `transition(payment, to)` function with a table of legal transitions. Nothing else writes the state.

## 11. Reconciliation

Because intent is recorded before every side effect, every PSP action has a local payment row and a derivable key. Reconciliation therefore works by **sweeping your own unfinished payments and checking each key at the PSP**. A crash between "PSP acted" and "I saved the result" is covered.

| Finding | Action |
| --- | --- |
| PSP shows a capture for a payment held in doubt or captured-pending | Auto-resolve: move forward and post to the ledger normally |
| PSP shows an authorization with no capture, and the payment is past its deadline | Auto-resolve: void and confirm the void |
| PSP and local records disagree on amount or outcome | Escalate, never auto-fix |
| PSP shows a charge with no local record | Escalate, never auto-fix |

The last two rows can only be found with a **list-by-time-window** query on the PSP, which would catch things the system never initiated (bugs, duplicate sends, charges that should not exist). That endpoint is **optional** because key lookup covers the core design. **Decided: skip it for now. It stays an optional future addition.**

## 12. Client-facing contract (to design)

- `POST /payments` with a client idempotency key. Returns a payment ID immediately.
- `GET /payments/{id}` returns status and, for debugging, the attempt history.
- Clients see *processing*, *settled*, or *failed*. Internal states stay hidden.
- Uniqueness is enforced by a database constraint on (client, key), not by check-then-insert. The key is scoped per client and kept for 24 hours.
- Same key with a different request body returns an explicit error.
- Same key while the first is in flight returns the current payment.
- Completed duplicates replay the original response.
- Request fields: amount, payer, payee, single fixed currency. Still to confirm: anything else.

## 13. Ledger interaction (to confirm against your ledger)

The ledger has no knowledge of PSPs, so the **orchestrator translates a confirmed payment into balanced entries**.

Things to check in your ledger and record here:

- **Decided:** the post endpoint accepts an idempotency key, set to the payment ID.
- **Decided:** the ledger is assumed reliable and always returns a clear answer. A clear rejection is treated as a bug: needs attention, no retry. A call that cannot reach the ledger or times out is retried with the same key.
- **Decided:** default accounts already exist in the ledger. The ledger also has a create-account endpoint, and the orchestrator must handle it (how is still open).
- **Decided:** fees are calculated in the orchestrator, which builds the balanced set of entries (payer debited, merchant credited, fee account credited).
- **Still open:** how the orchestrator authenticates to the ledger.
- If the ledger is unreachable after a confirmed capture, the payment moves to *captured, ledger pending*, a worker retries, and intake continues. This is the same worker mechanism, not a special case.

## 14. Error table (to write)

This is the design artifact to complete before the state machine. For each step (authorize, capture, void, ledger post) and each outcome, fill in the next state and whether it is retried.

| Step | Outcome | Classification | Next state | Retry? |
| --- | --- | --- | --- | --- |
| Authorize | Confirmed | Success | Authorized, capture due | n/a |
| Authorize | Declined | Hard failure | Failed (terminal) | No |
| Authorize | Transient error | Transient | New attempt | Yes, bounded |
| Authorize | Pending, timeout, or lost response | In doubt | Poll by key | Poll, not retry |
| Capture | Confirmed | Success | Captured, ledger pending | n/a |
| Capture | Refused (hard) | Hard failure | Void the hold, then fail | No |
| Capture | Transient error | Transient | Void the hold, then new attempt | Yes, bounded |
| Capture | Pending, timeout, or lost response | In doubt | Poll by key | Poll, not retry |
| Void | Confirmed | Success | Attempt closed | n/a |
| Void | Pending, timeout, or lost response | In doubt | Poll the void's key | Poll, not retry |
| Ledger post | Success | Success | Settled | n/a |
| Ledger post | Unavailable or timeout | Transient | Captured, ledger pending | Yes, with backoff |
| Ledger post | Rejected (permanent) | Hard failure | Needs attention | No |
| Any call | Authentication failure | Configuration | Stop and raise error | No |

This table is a draft built from the decisions so far. Review each row against the real behaviour of your PSP and ledger.

**Add one row to this table:** any call, discarded by the PSP because the key was reused with a different request. Classification: bug or configuration error. Next state: needs attention. Retry: no.

## 15. Retry policy by error class

- **Transient** (timeouts, 5xx, unavailable): retry with jittered exponential backoff, bounded, ending in a visible terminal or needs-attention state.
- **Hard** (declines, validation errors): never retry.
- **Configuration** (authentication failures): never retry. Stop and raise an error. Retrying masks the problem.
- **Polling an in-doubt step is not a retry** and uses a fixed interval, because the poll itself drives resolution.

## 16. Worker model

- One loop claims due work using a row lock that skips already-claimed rows, so multiple workers cannot double-process.
- Due work is anything whose next-action time has passed: scheduled captures, polls, ledger retries, stale in-flight steps.
- A step found *in flight* beyond the call timeout is treated as *in doubt*, never as "retry the call blindly".
- The worker has its own database connection pool, separate from request handling (on your Should Have list).

## 17. Open decisions checklist

**Attempts and capture failures**

- **Decided:** a new attempt starts only after a transient error or a confirmed void. Never after a decline, and never while anything is in doubt.
- **Decided:** attempt cap of 3, with roughly 10 s then 30 s between attempts.
- **Decided:** a hard capture refusal after a good authorize voids the hold, confirms the void, then fails the payment.

**Late resolutions and abandonment**

- **Decided:** a late authorization is voided.
- **Decided:** a late capture is always posted to the ledger.
- **Decided:** abandoned payments get a rare daily recheck.
- **Decided:** a void that never confirms alerts at 15 minutes.

**Timing**

- **Decided:** fast polling every 3 s for 3 min, then every 15 s, with a 24 h hard stop.
- **Decided:** keep all three alerts: capture 10 min, authorize and void 15 min.

**Reconciliation**

- **Decided:** auto-fix anything tied to a payment you hold. Escalate mismatches and unknown charges.
- **Decided:** no list-by-time-window endpoint for now.
- **Decided:** operators are view-only. Manual actions are future work in the case study.

**Domain and ledger**

- **Decided:** the ledger post endpoint takes an idempotency key, set to the payment ID.
- **Decided:** the ledger is assumed reliable. A clear rejection goes to needs attention with no retry. An unreachable or timed-out call is retried with the same key.
- **Decided:** ledger retries use backoff and never give up. Flag needs attention after 10 minutes.
- **Decided:** the orchestrator calculates fees and builds the balanced entries.
- **Decided:** default accounts already exist in the ledger.

**Still open**

- [ ] Which default accounts exist (IDs and types), and which one receives fees?
- [ ] Fee rule: percentage or fixed, rounding, and who pays it.
- [ ] How the orchestrator handles the ledger's create-account endpoint: exposed to clients, or called internally, and when.
- [ ] How the orchestrator authenticates to the ledger.

**Client contract**

- **Decided:** clients see *processing*, *settled*, or *failed*.
- **Decided:** the idempotency key is scoped per client and kept for 24 hours.
- **Decided:** same key with a different body returns an explicit error. Same key while the first is in flight returns the current payment.

**Still open**

- [ ] Request fields beyond amount, payer and payee (single fixed currency).

**Process**

- **Decided:** the request path only records intent. A worker does every PSP and ledger call.

## 18. Suggested build order

1. Finish the open decisions above, especially the ledger contract and the error table.
2. Skeleton: own database and credentials, migrations, configuration from environment, health endpoint, correlation-ID handling, and clients for the ledger and PSP with service authentication and typed error classification.
3. Schema and state machine: payments, attempts, idempotency records, outbox; a transition table with unit tests for illegal transitions.
4. Idempotent `POST /payments` that only records intent, with a concurrent-duplicate test.
5. Worker with the happy path: authorize, wait 5 s, capture, ledger post, settled.
6. Resolve-by-key procedure for in-doubt steps, with fast and slow polling.
7. Failure paths: declines, new attempts, voids, ledger down, deadline behaviour.
8. Alerts: needs-attention flag, structured logs, metrics.
9. Outbox and relay.
10. Reconciliation sweep.
11. Cross-service failure test, concurrency test, and the written case study.

## 19. Case study notes to capture as you go

- Why polling is the recovery engine and not a passive check, and why backoff does not apply to it.
- Why "not found" authorizes a resend rather than concluding the call never happened.
- Why a pending operation cannot be cancelled, only waited out, and what that does to the attempt rule.
- Why the payment deadline stops new work but never abandons in-flight work.
- Who would receive each alert in a real deployment.
- Anything you tried and abandoned.
