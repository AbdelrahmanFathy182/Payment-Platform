# PSP Simulator --- Scope & Behavior Reference

**Purpose:** A concise functional reference for designing the PSP
Simulator when implementation begins. This records the scope and
decisions agreed so far; it is not a technical architecture or
implementation plan.

## 1. Role and boundaries

The PSP Simulator represents an **external payment service provider
(PSP)** that the Orchestrator communicates with.

-   The **Orchestrator** coordinates the platform's payment workflow and
    decides what to do next.
-   The **PSP Simulator** simulates provider-side payment decisions,
    processing, failures, and records.
-   The **Ledger Service** records the platform's financial activity
    separately.
-   The simulator does not contact real banks or card networks, move
    real money, or manage the platform's merchants, customers, or ledger
    accounts.
-   The PSP Simulator does not initiate calls to the Orchestrator.

## 2. Core capabilities in scope

The simulator should support:

1.  **Authorize** a payment: approve or decline an attempted payment.
2.  **Capture** an approved authorization: complete the payment for the
    full authorized amount.
3.  **Void** an authorization: cancel it before capture.
4.  **Check status:** let the Orchestrator ask what state the PSP
    currently records.
5.  **Reconciliation:** return the PSP's records for a requested time
    window so the Orchestrator can compare them with its own records and
    recover missed outcomes.

## 3. Payment states

The agreed main states are:

  -----------------------------------------------------------------------
  State                               Meaning
  ----------------------------------- -----------------------------------
  `PROCESSING`                        The PSP is still handling an
                                      operation. The Orchestrator can
                                      observe this state.

  `AUTHORIZED`                        The authorization was approved. It
                                      may later be captured, voided, or
                                      expire.

  `DECLINED`                          The authorization was refused.

  `CAPTURED`                          The full authorized amount was
                                      successfully captured.

  `VOIDED`                            An authorization was cancelled
                                      before capture.

  `EXPIRED`                           An authorization reached its expiry
                                      without being captured or voided.
  -----------------------------------------------------------------------

A timeout, lost response, or temporary failure is **not automatically a
payment state**. These describe a request or communication outcome. For
example, the PSP may have captured a payment even though the
Orchestrator did not receive the response.

## 4. Agreed payment behavior

-   **Immediate or delayed capture:** The Orchestrator may capture soon
    after authorization or leave the authorization open and decide
    later.
-   **Full capture only:** Partial captures and multiple captures are
    out of scope. An authorization for 1,000 must be captured as 1,000
    in one operation.
-   **Void before capture:** An authorization may be voided while it is
    still eligible for voiding.
-   **Authorization expiry:** Uncaptured authorizations expire
    automatically. The expiry period should be configurable for testing.
-   **Expired means expired:** An expired authorization cannot
    subsequently be captured. `EXPIRED` is distinct from `VOIDED`.
-   **Invalid operations are rejected:** For example, capturing a
    declined, voided, expired, or nonexistent payment is rejected
    without changing its state. The same applies to voiding a captured
    payment.
-   **No real settlement or payout:** `CAPTURED` means the simulator
    recorded a successful capture; it does not mean funds have reached a
    merchant bank account.

## 5. Processing, failures, and uncertain outcomes

The simulator should be able to represent these different situations:

-   **Normal response:** The PSP finishes the operation and returns its
    result.
-   **Still processing:** The PSP reports `PROCESSING`; the Orchestrator
    can check again later.
-   **Temporary failure before processing:** The operation did not
    happen. A retry can be made.
-   **Successful operation with lost response:** The operation happened,
    but the Orchestrator did not receive the result.
-   **Delayed response/processing:** The operation takes longer than
    usual.

These cases must remain distinguishable. In particular, a timeout or
lost response must not be treated as proof that the operation failed.

### Orchestrator waiting and recovery

-   The Orchestrator normally waits for the PSP's response to an
    operation.
-   If the PSP reports `PROCESSING`, the Orchestrator checks again after
    a wait.
-   The Orchestrator checks a limited number of times rather than
    waiting indefinitely.
-   If it still cannot establish the outcome, it treats the payment as
    **unresolved from its own perspective**. This is not an additional
    PSP payment state.
-   It can recover later through another status check or reconciliation.

## 6. Safe retries

**Agreed rule: retrying the same operation must not repeat its effect.**

If the PSP already processed an operation but its response was lost, a
retry of that same operation returns the original outcome rather than
processing it again.

A repeated request that reuses the same request identity but changes the
operation's details must be rejected. A genuinely new operation must not
be mistaken for a retry of an earlier one.

## 7. Failure and test controls

The simulator should support both:

-   **General configurable behavior**, such as rates of temporary
    failures or delayed responses.
-   **Deliberately triggered scenarios**, so a specific case can be
    reproduced on demand.

Scenarios should cover authorization, capture, and void where
applicable, including failures before processing and successful
operations whose responses are lost. Capture should also be able to
experience temporary failures.

## 8. Reconciliation and provider records

Reconciliation should return the PSP's actual records for the requested
time window, rather than only returning records for payments the
Orchestrator already knows to ask about.

The records should make it possible for the Orchestrator to discover and
investigate situations such as:

-   A capture or authorization that succeeded but whose response was
    lost.
-   An authorization that remains uncaptured.
-   A decline or void that the Orchestrator did not learn about.
-   A payment whose state changed while the Orchestrator was
    unavailable.

The simulator must retain its provider-side records so status checks and
reconciliation can support recovery after an Orchestrator or simulator
restart.

## 9. Explicitly out of scope

-   Real bank, card-network, or payment-provider integration.
-   Actual movement, clearing, settlement, or payout of money.
-   Refunds.
-   Partial capture or multiple captures for one authorization.
-   Multiple provider profiles or provider selection.
-   Merchant, customer, or Ledger account management.
-   The PSP initiating requests to the Orchestrator.
-   A monitoring or administration dashboard.

## 10. Decisions still open

These were not settled and should be decided only when they matter to
the design:

-   Which currencies the first version should support.
-   The exact authorization expiry duration and how it is configured.
-   The exact waiting interval and retry/check limit used by the
    Orchestrator.
-   The precise set of failure-rate controls and which scenarios can be
    triggered for each operation.

Do not let these open details expand the agreed scope unnecessarily.

## 11. Suggested implementation order

Build and verify the normal business flow before adding failure
complexity:

1.  Authorization, including approval and decline.
2.  Full capture and void.
3.  Status checks and valid state transitions.
4.  Safe retries and duplicate-request behavior.
5.  Authorization expiry.
6.  Processing delays, temporary failures, and lost responses.
7.  Reconciliation and recovery scenarios.
8.  Configurable failure rates and deliberate scenario controls.

**Guiding principle:** Keep the simulator focused on behaving like an
external PSP from the Orchestrator's point of view. It should be
realistic enough to exercise payment lifecycle, failure handling,
retries, and recovery without simulating the entire banking system.
