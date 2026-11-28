package com.abdelrahman.orchestrator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.abdelrahman.orchestrator.domain.Payment;
import com.abdelrahman.orchestrator.domain.PaymentStage;
import com.abdelrahman.orchestrator.domain.PaymentState;
import com.abdelrahman.orchestrator.domain.PaymentTransitions;
import com.abdelrahman.orchestrator.domain.StepStatus;

class PaymentStateMachineTests {

    @Test
    void receivedMovesOnlyToAuthorizePending() {
        Payment payment = newPayment();

        assertEquals(PaymentState.RECEIVED, payment.state());
        PaymentTransitions.transition(payment, PaymentState.AUTHORIZE_PENDING);
        assertEquals(PaymentState.AUTHORIZE_PENDING, payment.state());

        assertIllegal(payment, PaymentState.RECEIVED);
    }

    @Test
    void happyPathReachesSettled() {
        Payment payment = newPayment();

        PaymentTransitions.transition(payment, PaymentState.AUTHORIZE_PENDING);
        PaymentTransitions.transition(payment, PaymentState.AUTHORIZE_IN_FLIGHT);
        PaymentTransitions.transition(payment, PaymentState.AUTHORIZE_DONE);
        PaymentTransitions.transition(payment, PaymentState.CAPTURE_PENDING);
        PaymentTransitions.transition(payment, PaymentState.CAPTURE_IN_FLIGHT);
        PaymentTransitions.transition(payment, PaymentState.CAPTURE_DONE);
        PaymentTransitions.transition(payment, PaymentState.LEDGER_POST_PENDING);
        PaymentTransitions.transition(payment, PaymentState.LEDGER_POST_IN_FLIGHT);
        PaymentTransitions.transition(payment, PaymentState.LEDGER_POST_DONE);
        PaymentTransitions.transition(payment, PaymentState.SETTLED);

        assertEquals(PaymentState.SETTLED, payment.state());
        assertFalse(payment.needsAttention());
    }

    @Test
    void inDoubtAuthorizeResolvesWithoutStartingAnotherAttempt() {
        Payment payment = newPayment();

        PaymentTransitions.transition(payment, PaymentState.AUTHORIZE_PENDING);
        PaymentTransitions.transition(payment, PaymentState.AUTHORIZE_IN_FLIGHT);
        PaymentTransitions.transition(payment, PaymentState.AUTHORIZE_IN_DUBT);
        PaymentTransitions.transition(payment, PaymentState.AUTHORIZE_DONE);

        assertEquals(PaymentState.AUTHORIZE_DONE, payment.state());
        assertEquals(1, payment.currentAttempt());
    }

    @Test
    void captureInDoubtCannotPostToLedger() {
        Payment payment = newPayment();

        PaymentTransitions.transition(payment, PaymentState.AUTHORIZE_PENDING);
        PaymentTransitions.transition(payment, PaymentState.AUTHORIZE_IN_FLIGHT);
        PaymentTransitions.transition(payment, PaymentState.AUTHORIZE_DONE);
        PaymentTransitions.transition(payment, PaymentState.CAPTURE_PENDING);
        PaymentTransitions.transition(payment, PaymentState.CAPTURE_IN_FLIGHT);
        PaymentTransitions.transition(payment, PaymentState.CAPTURE_IN_DUBT);

        assertIllegal(payment, PaymentState.LEDGER_POST_PENDING);
    }

    @Test
    void voidResponseMustBeConfirmedByKeyBeforeTerminal() {
        Payment payment = newPayment();

        PaymentTransitions.transition(payment, PaymentState.AUTHORIZE_PENDING);
        PaymentTransitions.transition(payment, PaymentState.AUTHORIZE_IN_FLIGHT);
        PaymentTransitions.transition(payment, PaymentState.AUTHORIZE_DONE);
        PaymentTransitions.transition(payment, PaymentState.CAPTURE_PENDING);
        PaymentTransitions.transition(payment, PaymentState.CAPTURE_IN_FLIGHT);
        PaymentTransitions.transition(payment, PaymentState.CAPTURE_FAILED);
        PaymentTransitions.transition(payment, PaymentState.VOID_PENDING);
        PaymentTransitions.transition(payment, PaymentState.VOID_IN_FLIGHT);

        assertIllegal(payment, PaymentState.VOIDED);

        PaymentTransitions.transition(payment, PaymentState.VOID_IN_DUBT);
        PaymentTransitions.transition(payment, PaymentState.VOID_DONE);
        PaymentTransitions.transition(payment, PaymentState.VOIDED);

        assertEquals(PaymentState.VOIDED, payment.state());
    }

    @Test
    void transientAuthorizeFailureCanStartAnotherAttempt() {
        Payment payment = newPayment();

        PaymentTransitions.transition(payment, PaymentState.AUTHORIZE_PENDING);
        PaymentTransitions.transition(payment, PaymentState.AUTHORIZE_IN_FLIGHT);
        PaymentTransitions.transition(payment, PaymentState.AUTHORIZE_FAILED);
        PaymentTransitions.transition(payment, PaymentState.AUTHORIZE_PENDING);

        assertEquals(PaymentState.AUTHORIZE_PENDING, payment.state());
        assertEquals(2, payment.currentAttempt());
    }

    @Test
    void declineIsTerminal() {
        Payment payment = newPayment();

        PaymentTransitions.transition(payment, PaymentState.AUTHORIZE_PENDING);
        PaymentTransitions.transition(payment, PaymentState.AUTHORIZE_IN_FLIGHT);
        PaymentTransitions.transition(payment, PaymentState.DECLINED);

        assertIllegal(payment, PaymentState.AUTHORIZE_PENDING);
    }

    @Test
    void abandonedCaptureInDoubtIsTerminal() {
        Payment payment = newPayment();

        PaymentTransitions.transition(payment, PaymentState.AUTHORIZE_PENDING);
        PaymentTransitions.transition(payment, PaymentState.AUTHORIZE_IN_FLIGHT);
        PaymentTransitions.transition(payment, PaymentState.AUTHORIZE_DONE);
        PaymentTransitions.transition(payment, PaymentState.CAPTURE_PENDING);
        PaymentTransitions.transition(payment, PaymentState.CAPTURE_IN_FLIGHT);
        PaymentTransitions.transition(payment, PaymentState.CAPTURE_IN_DUBT);
        PaymentTransitions.transition(payment, PaymentState.abandoned(PaymentStage.CAPTURE));

        assertIllegal(payment, PaymentState.CAPTURE_IN_FLIGHT);
    }

    @Test
    void ledgerRejectionFlagsNeedsAttention() {
        Payment payment = newPayment();

        PaymentTransitions.transition(payment, PaymentState.AUTHORIZE_PENDING);
        PaymentTransitions.transition(payment, PaymentState.AUTHORIZE_IN_FLIGHT);
        PaymentTransitions.transition(payment, PaymentState.AUTHORIZE_DONE);
        PaymentTransitions.transition(payment, PaymentState.CAPTURE_PENDING);
        PaymentTransitions.transition(payment, PaymentState.CAPTURE_IN_FLIGHT);
        PaymentTransitions.transition(payment, PaymentState.CAPTURE_DONE);
        PaymentTransitions.transition(payment, PaymentState.LEDGER_POST_PENDING);
        PaymentTransitions.transition(payment, PaymentState.LEDGER_POST_IN_FLIGHT);
        PaymentTransitions.transition(payment, PaymentState.LEDGER_POST_FAILED);
        PaymentTransitions.transition(payment, PaymentState.needsAttention(
                PaymentStage.LEDGER_POST, StepStatus.FAILED));

        assertTrue(payment.needsAttention());
    }

    private void assertIllegal(Payment payment, PaymentState to) {
        assertThrows(IllegalStateException.class, () -> PaymentTransitions.transition(payment, to));
    }

    private Payment newPayment() {
        Instant now = Instant.now();
        return new Payment(
                UUID.randomUUID(),
                "client",
                "key",
                "hash",
                15000L,
                "EGP",
                "payer",
                "merchant",
                300L,
                14700L,
                UUID.randomUUID(),
                now.plus(Duration.ofMinutes(15)),
                now);
    }
}
