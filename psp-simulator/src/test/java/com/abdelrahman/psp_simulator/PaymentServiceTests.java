package com.abdelrahman.psp_simulator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.abdelrahman.psp_simulator.domain.Payment;
import com.abdelrahman.psp_simulator.domain.enums.Currency;
import com.abdelrahman.psp_simulator.domain.enums.Operation;
import com.abdelrahman.psp_simulator.domain.enums.PaymentState;
import com.abdelrahman.psp_simulator.dto.AuthorizeRequest;
import com.abdelrahman.psp_simulator.dto.PaymentResponse;
import com.abdelrahman.psp_simulator.dto.Simulation;
import com.abdelrahman.psp_simulator.exception.PspException;
import com.abdelrahman.psp_simulator.repository.PaymentRepository;
import com.abdelrahman.psp_simulator.repository.PaymentRequestRepository;
import com.abdelrahman.psp_simulator.service.PaymentService;
import com.abdelrahman.psp_simulator.service.SimulationExecutor;

import tools.jackson.databind.json.JsonMapper;

public class PaymentServiceTests extends pspDatabaseSetup {

    @Autowired
    private PaymentService service;

    @Autowired
    private PaymentRepository payments;

    @Autowired
    private PaymentRequestRepository requests;

    @Autowired
    private JsonMapper jsonMapper;

    @Test
    void authorizeStoresAuthorizedPaymentAndRequest() {
        PaymentResponse response = authorize("K1", none());

        assertEquals(PaymentState.AUTHORIZED, response.state());
        assertEquals(15000L, response.amount());
        assertEquals(Currency.EGP, response.currency());

        Payment stored = payments.findById(response.id()).orElseThrow();
        assertEquals(PaymentState.AUTHORIZED, stored.getState());
        assertEquals(1, count("payment_requests"));
        assertEquals(Operation.AUTHORIZE, requests.findByIdempotencyKey("K1").orElseThrow().getOperation());
    }

    @Test
    void authorizeDeclineStoresDeclinedPayment() {
        PaymentResponse response = authorize("K1", Simulation.parse("DECLINE"));

        assertEquals(PaymentState.DECLINED, response.state());
        assertEquals(PaymentService.DECLINE_REASON, response.declineReason());

        Payment stored = payments.findById(response.id()).orElseThrow();
        assertEquals(PaymentState.DECLINED, stored.getState());
        assertEquals(PaymentService.DECLINE_REASON, stored.getDeclineReason());
        assertEquals(1, count("payment_requests"));
    }

    @Test
    void authorizePendingStoresCounter() {
        PaymentResponse response = authorize("K1", Simulation.parse("PENDING:2"));

        assertEquals(PaymentState.PENDING, response.state());
        Payment stored = payments.findById(response.id()).orElseThrow();
        assertEquals(2, stored.getPendingQueriesRemaining());
    }

    @Test
    void queriesDecrementPendingAndResolve() {
        UUID id = authorize("K1", Simulation.parse("PENDING:2")).id();

        PaymentResponse first = service.queryById(id, none());
        assertEquals(PaymentState.PENDING, first.state());
        assertEquals(1, pendingQueries(id));

        PaymentResponse second = service.queryById(id, none());
        assertEquals(PaymentState.AUTHORIZED, second.state());
        assertEquals(0, pendingQueries(id));

        PaymentResponse third = service.queryById(id, none());
        assertEquals(PaymentState.AUTHORIZED, third.state());
        assertEquals(0, pendingQueries(id));
    }

    @Test
    void replayAuthorizeReturnsStoredOutcomeNotCurrentState() {
        PaymentResponse original = authorize("K1", none());
        service.capture(original.id(), "K2", none());

        PaymentResponse replay = authorize("K1", none());

        assertEquals(original.id(), replay.id());
        assertEquals(PaymentState.AUTHORIZED, replay.state());
        assertEquals(1, count("payment_requests WHERE operation = 'AUTHORIZE'"));
    }

    @Test
    void authorizeReplayDecrementsPendingPayment() {
        PaymentResponse original = authorize("K1", Simulation.parse("PENDING:2"));

        PaymentResponse replay = authorize("K1", none());

        assertEquals(original.id(), replay.id());
        assertEquals(PaymentState.PENDING, replay.state());
        assertEquals(1, pendingQueries(original.id()));
    }

    @Test
    void replayCaptureReturnsStoredOutcome() {
        UUID id = authorize("K1", none()).id();
        PaymentResponse capture = service.capture(id, "K2", none());

        PaymentResponse replay = service.capture(id, "K2", none());

        assertEquals(capture.id(), replay.id());
        assertEquals(PaymentState.CAPTURED, replay.state());
        assertEquals(1, count("payment_requests WHERE operation = 'CAPTURE'"));
    }

    @Test
    void sameKeyWithDifferentOperationIsRejected() {
        UUID id = authorize("K1", none()).id();

        PspException e = assertThrows(PspException.class, () -> service.capture(id, "K1", none()));

        assertEquals(PspException.IDEMPOTENCY_KEY_REUSED, e.getCode());
        assertEquals(409, e.getStatus().value());
    }

    @Test
    void temporaryFailureStoresNothing() {
        PspException e = assertThrows(PspException.class,
                () -> authorize("K1", Simulation.parse("TEMP_FAILURE")));

        assertEquals(PspException.TEMPORARY_FAILURE, e.getCode());
        assertEquals(503, e.getStatus().value());
        assertEquals(0, count("payments_model"));
        assertEquals(0, count("payment_requests"));
    }

    @Test
    void captureMovesAuthorizedToCaptured() {
        UUID id = authorize("K1", none()).id();

        PaymentResponse response = service.capture(id, "K2", none());

        assertEquals(PaymentState.CAPTURED, response.state());
        assertEquals(PaymentState.CAPTURED, payments.findById(id).orElseThrow().getState());
    }

    @Test
    void voidMovesAuthorizedToVoided() {
        UUID id = authorize("K1", none()).id();

        PaymentResponse response = service.voidPayment(id, "K2", none());

        assertEquals(PaymentState.VOIDED, response.state());
        assertEquals(PaymentState.VOIDED, payments.findById(id).orElseThrow().getState());
    }

    @Test
    void capturePendingIsRejected() {
        UUID id = authorize("K1", Simulation.parse("PENDING:2")).id();

        PspException e = assertThrows(PspException.class, () -> service.capture(id, "K2", none()));

        assertEquals(PspException.INVALID_TRANSITION, e.getCode());
        assertEquals(409, e.getStatus().value());
        assertEquals(1, pendingQueries(id));
    }

    @Test
    void capturePendingResolvesAndProceedsWhenCounterReachesZero() {
        UUID id = authorize("K1", Simulation.parse("PENDING:1")).id();

        PaymentResponse response = service.capture(id, "K2", none());

        assertEquals(PaymentState.CAPTURED, response.state());
        assertEquals(PaymentState.CAPTURED, payments.findById(id).orElseThrow().getState());
        assertEquals(0, pendingQueries(id));
    }

    @Test
    void voidPendingResolvesAndProceedsWhenCounterReachesZero() {
        UUID id = authorize("K1", Simulation.parse("PENDING:1")).id();

        PaymentResponse response = service.voidPayment(id, "K2", none());

        assertEquals(PaymentState.VOIDED, response.state());
        assertEquals(PaymentState.VOIDED, payments.findById(id).orElseThrow().getState());
        assertEquals(0, pendingQueries(id));
    }

    @Test
    void captureDeclinedIsRejected() {
        UUID id = authorize("K1", Simulation.parse("DECLINE")).id();

        PspException e = assertThrows(PspException.class, () -> service.capture(id, "K2", none()));

        assertEquals(PspException.INVALID_TRANSITION, e.getCode());
    }

    @Test
    void secondCaptureWithNewKeyIsRejected() {
        UUID id = authorize("K1", none()).id();
        service.capture(id, "K2", none());

        PspException e = assertThrows(PspException.class, () -> service.capture(id, "K3", none()));

        assertEquals(PspException.INVALID_TRANSITION, e.getCode());
    }

    @Test
    void voidAfterCaptureIsRejected() {
        UUID id = authorize("K1", none()).id();
        service.capture(id, "K2", none());

        PspException e = assertThrows(PspException.class, () -> service.voidPayment(id, "K3", none()));

        assertEquals(PspException.INVALID_TRANSITION, e.getCode());
    }

    @Test
    void captureUnknownPaymentIsNotFound() {
        PspException e = assertThrows(PspException.class,
                () -> service.capture(UUID.randomUUID(), "K1", none()));

        assertEquals(PspException.PAYMENT_NOT_FOUND, e.getCode());
        assertEquals(404, e.getStatus().value());
    }

    @Test
    void queryByUnknownIdIsNotFound() {
        PspException e = assertThrows(PspException.class,
                () -> service.queryById(UUID.randomUUID(), none()));

        assertEquals(PspException.PAYMENT_NOT_FOUND, e.getCode());
    }

    @Test
    void queryByUnknownKeyIsNotFound() {
        PspException e = assertThrows(PspException.class, () -> service.queryByKey("missing", none()));

        assertEquals(PspException.PAYMENT_NOT_FOUND, e.getCode());
    }

    @Test
    void queryByKeyReturnsCurrentStateForAnyOperationKey() {
        UUID id = authorize("K1", none()).id();
        service.capture(id, "K2", none());

        assertEquals(PaymentState.CAPTURED, service.queryByKey("K1", none()).state());
        assertEquals(PaymentState.CAPTURED, service.queryByKey("K2", none()).state());
    }

    @Test
    void scenariosThatDoNotApplyAreRejected() {
        UUID id = authorize("K1", none()).id();

        assertEquals(PspException.INVALID_REQUEST,
                assertThrows(PspException.class,
                        () -> service.capture(id, "K2", Simulation.parse("DECLINE"))).getCode());
        assertEquals(PspException.INVALID_REQUEST,
                assertThrows(PspException.class,
                        () -> service.voidPayment(id, "K3", Simulation.parse("PENDING:2"))).getCode());
        assertEquals(PspException.INVALID_REQUEST,
                assertThrows(PspException.class,
                        () -> service.queryById(id, Simulation.parse("DELAY:10"))).getCode());
    }

    @Test
    void blankOrOversizedKeyIsRejected() {
        assertEquals(PspException.MISSING_IDEMPOTENCY_KEY,
                assertThrows(PspException.class, () -> authorize(" ", none())).getCode());
        assertEquals(PspException.INVALID_REQUEST,
                assertThrows(PspException.class, () -> authorize("x".repeat(256), none())).getCode());
    }

    @Test
    void queryByKeyAlsoDecrementsPending() {
        UUID id = authorize("K1", Simulation.parse("PENDING:1")).id();

        PaymentResponse response = service.queryByKey("K1", none());

        assertEquals(id, response.id());
        assertEquals(PaymentState.AUTHORIZED, response.state());
        assertEquals(0, pendingQueries(id));
    }

    @Test
    void responseLostCommitsBeforeStalling() {
        PaymentService shortStall = new PaymentService(
                payments, requests, tx, jsonMapper, new SimulationExecutor(60, 0));
        long start = System.nanoTime();

        PaymentResponse response = shortStall.authorize(
                new AuthorizeRequest(15000, Currency.EGP), "K1", Simulation.parse("RESPONSE_LOST"));

        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        assertTrue(elapsedMs >= 40, () -> "stalled only " + elapsedMs + "ms");
        assertTrue(payments.findById(response.id()).isPresent());
        assertEquals(1, count("payment_requests"));
    }

    @Test
    void concurrentSameKeyCaptureReplaysStoredOutcome() throws Exception {
        UUID id = authorize("K1", none()).id();
        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);

        Callable<PaymentResponse> call = () -> {
            barrier.await();
            return service.capture(id, "K2", none());
        };

        List<Future<PaymentResponse>> results = pool.invokeAll(List.of(call, call));
        pool.shutdown();

        PaymentResponse first = results.get(0).get();
        PaymentResponse second = results.get(1).get();

        assertEquals(first.id(), second.id());
        assertEquals(PaymentState.CAPTURED, first.state());
        assertEquals(PaymentState.CAPTURED, second.state());
        assertEquals(1, count("payment_requests WHERE operation = 'CAPTURE'"));
    }

    @Test
    void concurrentCaptureAndVoidOnlyOneWins() throws Exception {
        UUID id = authorize("K1", none()).id();
        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);

        Callable<Integer> capture = () -> attempt(barrier, () -> service.capture(id, "K2", none()));
        Callable<Integer> voidIt = () -> attempt(barrier, () -> service.voidPayment(id, "K3", none()));

        List<Future<Integer>> results = pool.invokeAll(List.of(capture, voidIt));
        pool.shutdown();

        List<Integer> statuses = results.stream().map(this::get).sorted().toList();
        assertEquals(List.of(200, 409), statuses);

        PaymentState finalState = payments.findById(id).orElseThrow().getState();
        assertTrue(finalState == PaymentState.CAPTURED || finalState == PaymentState.VOIDED);
    }

    @Test
    void concurrentAuthorizeWithSameKeyStoresOnePayment() throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);

        Callable<PaymentResponse> call = () -> {
            barrier.await();
            return authorize("K1", none());
        };

        List<Future<PaymentResponse>> results = pool.invokeAll(List.of(call, call));
        pool.shutdown();

        PaymentResponse first = results.get(0).get();
        PaymentResponse second = results.get(1).get();

        assertEquals(first.id(), second.id());
        assertEquals(1, count("payments_model"));
        assertEquals(1, count("payment_requests"));
    }

    private PaymentResponse authorize(String key, Simulation simulation) {
        return service.authorize(new AuthorizeRequest(15000, Currency.EGP), key, simulation);
    }

    private Simulation none() {
        return Simulation.none();
    }

    private int pendingQueries(UUID id) {
        return payments.findById(id).orElseThrow().getPendingQueriesRemaining();
    }

    private int count(String table) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM " + table, Integer.class);
    }

    private int attempt(CyclicBarrier barrier, Runnable action) throws Exception {
        barrier.await();
        try {
            action.run();
            return 200;
        } catch (PspException e) {
            return e.getStatus().value();
        }
    }

    private int get(Future<Integer> future) {
        try {
            return future.get();
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }
}
