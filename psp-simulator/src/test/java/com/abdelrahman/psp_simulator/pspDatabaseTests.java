package com.abdelrahman.psp_simulator;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.sql.Timestamp;
import java.util.List;
import java.util.concurrent.Callable;



public class pspDatabaseTests extends pspDatabaseSetup {

    // 1. Cannot insert directly as CAPTURED
    @Test
    void insertAsCapturedIsRejected() {
        assertEquals("PS409", sqlStateOf(() -> insertPayment("CAPTURED", 0)));
    }

    // 2. AUTHORIZED -> CAPTURED succeeds and updated_at changes
    @Test
    void authorizedToCapturedSucceeds() {
        UUID id = insertPayment("AUTHORIZED", 0);
        Timestamp before = jdbcTemplate.queryForObject(
                "SELECT updated_at FROM payments_model WHERE id = ?", Timestamp.class, id);

        setState(id, "CAPTURED");

        String state = jdbcTemplate.queryForObject(
                "SELECT state FROM payments_model WHERE id = ?", String.class, id);
        Timestamp after = jdbcTemplate.queryForObject(
                "SELECT updated_at FROM payments_model WHERE id = ?", Timestamp.class, id);
        assertEquals("CAPTURED", state);
        assertTrue(after.after(before));
    }

    // 3. CAPTURED -> VOIDED is rejected with PS409
    @Test
    void capturedToVoidedIsRejected() {
        UUID id = insertPayment("AUTHORIZED", 0);
        setState(id, "CAPTURED");
        assertEquals("PS409", sqlStateOf(() -> setState(id, "VOIDED")));
    }

    // 4. Counter-only update on a PENDING row succeeds
    @Test
    void counterOnlyUpdateOnPendingSucceeds() {
        UUID id = insertPayment("PENDING", 2);
        jdbcTemplate.update("UPDATE payments_model SET pending_queries_remaining = 1 WHERE id = ?", id);
        Integer counter = jdbcTemplate.queryForObject(
                "SELECT pending_queries_remaining FROM payments_model WHERE id = ?", Integer.class, id);
        assertEquals(1, counter);
    }

    // 5. A non-zero counter on a non-PENDING row is rejected (check constraint)
    @Test
    void counterOnAuthorizedIsRejected() {
        assertEquals("23514", sqlStateOf(() -> insertPayment("AUTHORIZED", 1)));
    }

    // 6. Duplicate idempotency key is rejected (unique constraint)
    @Test
    void duplicateIdempotencyKeyIsRejected() {
        UUID id = insertPayment("AUTHORIZED", 0);
        String sql = "INSERT INTO payment_requests (idempotency_key, operation, payment_id, outcome) " +
                "VALUES ('K1', 'AUTHORIZE', ?, CAST('{}' AS jsonb))";
        jdbcTemplate.update(sql, id);
        assertEquals("23505", sqlStateOf(() -> jdbcTemplate.update(sql, id)));
    }

    // 7. Capture and void race on the same AUTHORIZED row
    @Test
    void concurrentCaptureAndVoidOnlyOneWins() throws Exception {
        UUID id = insertPayment("AUTHORIZED", 0);
        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);

        Callable<Boolean> capture = () -> attempt(barrier, id, "CAPTURED");
        Callable<Boolean> voidIt = () -> attempt(barrier, id, "VOIDED");

        List<Future<Boolean>> results = pool.invokeAll(List.of(capture, voidIt));
        pool.shutdown();

        long successes = 0;
        for (Future<Boolean> f : results)
            if (f.get())
                successes++;

        String finalState = jdbcTemplate.queryForObject(
                "SELECT state FROM payments_model WHERE id = ?", String.class, id);
        assertEquals(1, successes);
        assertTrue(finalState.equals("CAPTURED") || finalState.equals("VOIDED"));
    }

    private boolean attempt(CyclicBarrier barrier, UUID id, String target) throws Exception {
        barrier.await();
        try {
            setState(id, target);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
