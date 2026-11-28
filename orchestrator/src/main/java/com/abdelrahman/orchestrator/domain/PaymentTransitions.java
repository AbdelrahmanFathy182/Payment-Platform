package com.abdelrahman.orchestrator.domain;

import static java.util.Map.entry;
import static java.util.Set.of;

import java.util.Map;
import java.util.Set;

public final class PaymentTransitions {

    private static final Map<PaymentState, Set<PaymentState>> ALLOWED = Map.ofEntries(
            entry(PaymentState.RECEIVED, of(PaymentState.AUTHORIZE_PENDING)),

            entry(PaymentState.AUTHORIZE_PENDING, of(PaymentState.AUTHORIZE_IN_FLIGHT)),
            entry(PaymentState.AUTHORIZE_IN_FLIGHT, of(
                    PaymentState.AUTHORIZE_DONE,
                    PaymentState.AUTHORIZE_FAILED,
                    PaymentState.AUTHORIZE_IN_DUBT,
                    PaymentState.DECLINED)),
            entry(PaymentState.AUTHORIZE_IN_DUBT, of(
                    PaymentState.AUTHORIZE_DONE,
                    PaymentState.AUTHORIZE_FAILED,
                    PaymentState.AUTHORIZE_IN_FLIGHT,
                    PaymentState.DECLINED,
                    PaymentState.abandoned(PaymentStage.AUTHORIZE))),
            entry(PaymentState.AUTHORIZE_DONE, of(
                    PaymentState.CAPTURE_PENDING,
                    PaymentState.VOID_PENDING)),
            entry(PaymentState.AUTHORIZE_FAILED, of(
                    PaymentState.AUTHORIZE_PENDING,
                    PaymentState.retriesExhausted(PaymentStage.AUTHORIZE, StepStatus.FAILED),
                    PaymentState.needsAttention(PaymentStage.AUTHORIZE, StepStatus.FAILED))),

            entry(PaymentState.CAPTURE_PENDING, of(PaymentState.CAPTURE_IN_FLIGHT)),
            entry(PaymentState.CAPTURE_IN_FLIGHT, of(
                    PaymentState.CAPTURE_DONE,
                    PaymentState.CAPTURE_FAILED,
                    PaymentState.CAPTURE_IN_DUBT)),
            entry(PaymentState.CAPTURE_IN_DUBT, of(
                    PaymentState.CAPTURE_DONE,
                    PaymentState.CAPTURE_FAILED,
                    PaymentState.CAPTURE_IN_FLIGHT,
                    PaymentState.abandoned(PaymentStage.CAPTURE))),
            entry(PaymentState.CAPTURE_DONE, of(PaymentState.LEDGER_POST_PENDING)),
            entry(PaymentState.CAPTURE_FAILED, of(
                    PaymentState.VOID_PENDING,
                    PaymentState.needsAttention(PaymentStage.CAPTURE, StepStatus.FAILED))),

            entry(PaymentState.VOID_PENDING, of(PaymentState.VOID_IN_FLIGHT)),
            entry(PaymentState.VOID_IN_FLIGHT, of(
                    PaymentState.VOID_IN_DUBT,
                    PaymentState.needsAttention(PaymentStage.VOID, StepStatus.FAILED))),
            entry(PaymentState.VOID_IN_DUBT, of(
                    PaymentState.VOID_DONE,
                    PaymentState.VOID_IN_FLIGHT,
                    PaymentState.abandoned(PaymentStage.VOID),
                    PaymentState.needsAttention(PaymentStage.VOID, StepStatus.IN_DUBT))),
            entry(PaymentState.VOID_DONE, of(
                    PaymentState.AUTHORIZE_PENDING,
                    PaymentState.VOIDED,
                    PaymentState.retriesExhausted(PaymentStage.VOID, StepStatus.DONE))),

            entry(PaymentState.LEDGER_POST_PENDING, of(PaymentState.LEDGER_POST_IN_FLIGHT)),
            entry(PaymentState.LEDGER_POST_IN_FLIGHT, of(
                    PaymentState.LEDGER_POST_DONE,
                    PaymentState.LEDGER_POST_IN_DUBT,
                    PaymentState.LEDGER_POST_FAILED)),
            entry(PaymentState.LEDGER_POST_IN_DUBT, of(
                    PaymentState.LEDGER_POST_DONE,
                    PaymentState.LEDGER_POST_IN_FLIGHT,
                    PaymentState.needsAttention(PaymentStage.LEDGER_POST, StepStatus.IN_DUBT))),
            entry(PaymentState.LEDGER_POST_FAILED, of(
                    PaymentState.needsAttention(PaymentStage.LEDGER_POST, StepStatus.FAILED))),
            entry(PaymentState.LEDGER_POST_DONE, of(PaymentState.SETTLED)));

    private PaymentTransitions() {
    }

    public static void transition(Payment payment, PaymentState to) {
        PaymentState from = payment.state();
        if (!ALLOWED.getOrDefault(from, Set.of()).contains(to)) {
            throw new IllegalStateException("Illegal payment transition: " + from + " -> " + to);
        }
        if (to.equals(PaymentState.AUTHORIZE_PENDING) && from.stage() != PaymentStage.RECEIVED) {
            payment.startNextAttempt();
        }
        payment.applyState(to);
    }

    public static boolean isAllowed(PaymentState from, PaymentState to) {
        return ALLOWED.getOrDefault(from, Set.of()).contains(to);
    }
}
