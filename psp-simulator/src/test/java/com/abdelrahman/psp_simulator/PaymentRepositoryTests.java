package com.abdelrahman.psp_simulator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataIntegrityViolationException;

import com.abdelrahman.psp_simulator.domain.Payment;
import com.abdelrahman.psp_simulator.domain.PaymentRequest;
import com.abdelrahman.psp_simulator.domain.enums.Currency;
import com.abdelrahman.psp_simulator.domain.enums.Operation;
import com.abdelrahman.psp_simulator.domain.enums.PaymentState;
import com.abdelrahman.psp_simulator.repository.PaymentRepository;
import com.abdelrahman.psp_simulator.repository.PaymentRequestRepository;

public class PaymentRepositoryTests extends pspDatabaseSetup {

    @Autowired
    private PaymentRepository payments;

    @Autowired
    private PaymentRequestRepository requests;

    @Test
    void findByIdReturnsSavedPayment() {
        UUID id = createPendingPayment();

        Optional<Payment> found = payments.findById(id);

        assertTrue(found.isPresent());
        assertEquals(id, found.get().getId());
        assertEquals(PaymentState.PENDING, found.get().getState());
        assertEquals(100L, found.get().getAmount());
        assertEquals(Currency.USD, found.get().getCurrency());
    }

    @Test
    void findByIdempotencyKeyReturnsRequest() {
        UUID id = createPendingPayment();
        tx.executeWithoutResult(s -> requests.saveAndFlush(newRequest("K1", id)));

        Optional<PaymentRequest> found = requests.findByIdempotencyKey("K1");

        assertTrue(found.isPresent());
        assertEquals(Operation.AUTHORIZE, found.get().getOperation());
        assertEquals(id, found.get().getPaymentId());
    }

    @Test
    void findByIdempotencyKeyReturnsEmptyForUnknownKey() {
        assertTrue(requests.findByIdempotencyKey("missing").isEmpty());
    }

    @Test
    void findByIdempotencyKeyWithPaymentInitializesPayment() {
        UUID id = insertPayment("AUTHORIZED", 0);
        tx.executeWithoutResult(s -> requests.saveAndFlush(newRequest("K1", id)));

        PaymentRequest found = requests.findByIdempotencyKeyWithPayment("K1").orElseThrow();

        assertEquals(id, found.getPayment().getId());
        assertEquals(PaymentState.AUTHORIZED, found.getPayment().getState());
    }

    @Test
    void duplicateIdempotencyKeyIsRejected() {
        UUID id = createPendingPayment();
        tx.executeWithoutResult(s -> requests.saveAndFlush(newRequest("K1", id)));

        assertThrows(DataIntegrityViolationException.class,
                () -> tx.executeWithoutResult(s -> requests.saveAndFlush(newRequest("K1", id))));
    }

    @Test
    void illegalTransitionSurfacesAsPs409() {
        UUID id = insertPayment("AUTHORIZED", 0);

        Exception e = assertThrows(Exception.class, () -> tx.executeWithoutResult(s -> {
            Payment payment = payments.findByIdForUpdate(id).orElseThrow();
            payment.setState(PaymentState.PENDING);
            payments.saveAndFlush(payment);
        }));

        // Spring translates PS409 as JpaSystemException (root cause: PSQLException).
        assertEquals("PS409", rootSqlState(e), () -> "thrown: " + e.getClass().getName());
    }

    @Test
    void findByIdForUpdateBlocksASecondTransaction() throws Exception {
        UUID id = insertPayment("AUTHORIZED", 0);

        CountDownLatch holderLocked = new CountDownLatch(1);
        CountDownLatch waiterAttempting = new CountDownLatch(1);
        CountDownLatch releaseHolder = new CountDownLatch(1);
        CountDownLatch waiterAcquired = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);

        try {
            Future<?> holder = pool.submit(() -> tx.executeWithoutResult(s -> {
                payments.findByIdForUpdate(id).orElseThrow();
                holderLocked.countDown();
                await(releaseHolder);
            }));

            assertTrue(holderLocked.await(5, TimeUnit.SECONDS));

            Future<?> waiter = pool.submit(() -> tx.executeWithoutResult(s -> {
                waiterAttempting.countDown();
                payments.findByIdForUpdate(id).orElseThrow();
                waiterAcquired.countDown();
            }));

            assertTrue(waiterAttempting.await(5, TimeUnit.SECONDS));
            assertFalse(waiterAcquired.await(300, TimeUnit.MILLISECONDS));

            releaseHolder.countDown();
            assertTrue(waiterAcquired.await(5, TimeUnit.SECONDS));

            holder.get(5, TimeUnit.SECONDS);
            waiter.get(5, TimeUnit.SECONDS);
        } finally {
            releaseHolder.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    void jsonbOutcomeRoundTrips() {
        UUID id = createPendingPayment();
        UUID requestId = tx.execute(s -> requests
                .saveAndFlush(newRequest("K1", id, "{\"status\":\"AUTHORIZED\"}"))
                .getId());

        String stored = requests.findById(requestId).orElseThrow().getOutcome();

        assertNotNull(stored);
        assertTrue(stored.contains("\"AUTHORIZED\""), () -> "stored outcome: " + stored);
    }

    private UUID createPendingPayment() {
        return payments.saveAndFlush(new Payment(100, Currency.USD)).getId();
    }

    private PaymentRequest newRequest(String key, UUID paymentId) {
        return newRequest(key, paymentId, "{}");
    }

    private PaymentRequest newRequest(String key, UUID paymentId, String outcome) {
        Payment payment = payments.findById(paymentId).orElseThrow();
        return new PaymentRequest(key, Operation.AUTHORIZE, payment, outcome);
    }

    private String rootSqlState(Throwable e) {
        Throwable root = NestedExceptionUtils.getMostSpecificCause(e);
        return root instanceof SQLException sql ? sql.getSQLState() : null;
    }

    private void await(CountDownLatch latch) {
        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
