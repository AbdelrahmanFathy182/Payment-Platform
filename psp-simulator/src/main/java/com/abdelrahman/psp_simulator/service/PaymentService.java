package com.abdelrahman.psp_simulator.service;

import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.abdelrahman.psp_simulator.domain.Payment;
import com.abdelrahman.psp_simulator.domain.PaymentRequest;
import com.abdelrahman.psp_simulator.domain.enums.Operation;
import com.abdelrahman.psp_simulator.domain.enums.PaymentState;
import com.abdelrahman.psp_simulator.dto.AuthorizeRequest;
import com.abdelrahman.psp_simulator.dto.PaymentResponse;
import com.abdelrahman.psp_simulator.dto.Simulation;
import com.abdelrahman.psp_simulator.exception.PspException;
import com.abdelrahman.psp_simulator.repository.PaymentRepository;
import com.abdelrahman.psp_simulator.repository.PaymentRequestRepository;

import tools.jackson.databind.json.JsonMapper;

/**
 * Business decisions for the four provider operations. Transactions are opened
 * explicitly so a duplicate-key race can roll back and replay the winner's
 * outcome in a fresh transaction, and so RESPONSE_LOST can stall only after the
 * commit has completed.
 */
@Service
public class PaymentService {

    public static final String DECLINE_REASON = "SIMULATED_DECLINE";
    private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 255;
    private static final String PS409 = "PS409";

    private final PaymentRepository payments;
    private final PaymentRequestRepository requests;
    private final TransactionTemplate tx;
    private final JsonMapper json;
    private final SimulationExecutor simulationExecutor;

    public PaymentService(PaymentRepository payments,
            PaymentRequestRepository requests,
            TransactionTemplate tx,
            JsonMapper json,
            SimulationExecutor simulationExecutor) {
        this.payments = payments;
        this.requests = requests;
        this.tx = tx;
        this.json = json;
        this.simulationExecutor = simulationExecutor;
    }

    public PaymentResponse authorize(AuthorizeRequest request, String idempotencyKey, Simulation simulation) {
        requireKey(idempotencyKey);
        simulationExecutor.prepare(simulation, Operation.AUTHORIZE);

        Optional<PaymentResponse> replay = replay(idempotencyKey, Operation.AUTHORIZE);
        if (replay.isPresent()) {
            return replay.get();
        }

        try {
            PaymentResponse response = tx.execute(status -> {
                Payment payment = new Payment(
                        initialState(simulation),
                        request.amount(),
                        request.currency(),
                        declineReason(simulation),
                        pendingQueries(simulation));
                payments.saveAndFlush(payment);

                PaymentResponse result = PaymentResponse.from(payment);
                requests.saveAndFlush(new PaymentRequest(
                        idempotencyKey, Operation.AUTHORIZE, payment, encode(result)));
                return result;
            });

            simulationExecutor.afterCommit(simulation);
            return response;
        } catch (DataIntegrityViolationException e) {
            return replay(idempotencyKey, Operation.AUTHORIZE).orElseThrow(() -> e);
        } catch (JpaSystemException e) {
            throw translate(e);
        }
    }

    public PaymentResponse capture(UUID paymentId, String idempotencyKey, Simulation simulation) {
        return transition(paymentId, idempotencyKey, Operation.CAPTURE, PaymentState.CAPTURED, simulation);
    }

    public PaymentResponse voidPayment(UUID paymentId, String idempotencyKey, Simulation simulation) {
        return transition(paymentId, idempotencyKey, Operation.VOID, PaymentState.VOIDED, simulation);
    }

    public PaymentResponse queryById(UUID paymentId, Simulation simulation) {
        simulationExecutor.prepare(simulation, null);

        Payment payment = payments.findById(paymentId).orElseThrow(PspException::paymentNotFound);
        if (payment.getState() == PaymentState.PENDING) {
            return decrementAndResolve(paymentId);
        }
        return PaymentResponse.from(payment);
    }

    public PaymentResponse queryByKey(String idempotencyKey, Simulation simulation) {
        simulationExecutor.prepare(simulation, null);

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw PspException.invalidRequest("The key query parameter is required");
        }

        PaymentRequest request = requests.findByIdempotencyKeyWithPayment(idempotencyKey)
                .orElseThrow(PspException::paymentNotFound);

        if (request.getPayment().getState() == PaymentState.PENDING) {
            return decrementAndResolve(request.getPaymentId());
        }
        return PaymentResponse.from(request.getPayment());
    }

    private PaymentResponse transition(UUID paymentId, String idempotencyKey, Operation operation,
            PaymentState target, Simulation simulation) {
        requireKey(idempotencyKey);
        simulationExecutor.prepare(simulation, operation);

        Optional<PaymentResponse> replay = replay(idempotencyKey, operation);
        if (replay.isPresent()) {
            return replay.get();
        }

        try {
            PaymentResponse response = tx.execute(status -> {
                Payment payment = payments.findByIdForUpdate(paymentId)
                        .orElseThrow(PspException::paymentNotFound);

                // The trigger allows a no-op state update, so terminal states must
                // be rejected here. A same-key race is replayed instead of rejected.
                if (payment.getState() != PaymentState.AUTHORIZED) {
                    return replay(idempotencyKey, operation)
                            .orElseThrow(PspException::invalidTransition);
                }

                payment.setState(target);
                payments.saveAndFlush(payment);

                PaymentResponse result = PaymentResponse.from(payment);
                requests.saveAndFlush(new PaymentRequest(
                        idempotencyKey, operation, payment, encode(result)));
                return result;
            });

            simulationExecutor.afterCommit(simulation);
            return response;
        } catch (DataIntegrityViolationException e) {
            return replay(idempotencyKey, operation).orElseThrow(() -> e);
        } catch (JpaSystemException e) {
            throw translate(e);
        }
    }

    private PaymentResponse decrementAndResolve(UUID paymentId) {
        return tx.execute(status -> {
            Payment payment = payments.findByIdForUpdate(paymentId)
                    .orElseThrow(PspException::paymentNotFound);

            if (payment.getState() == PaymentState.PENDING && payment.getPendingQueriesRemaining() > 0) {
                int remaining = payment.getPendingQueriesRemaining() - 1;
                payment.setPendingQueriesRemaining(remaining);
                if (remaining == 0) {
                    payment.setState(PaymentState.AUTHORIZED);
                }
                payments.saveAndFlush(payment);
            }
            return PaymentResponse.from(payment);
        });
    }

    private Optional<PaymentResponse> replay(String idempotencyKey, Operation expected) {
        return requests.findByIdempotencyKey(idempotencyKey).map(request -> {
            if (request.getOperation() != expected) {
                throw PspException.idempotencyKeyReused();
            }
            return decode(request.getOutcome());
        });
    }

    private void requireKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw PspException.missingIdempotencyKey();
        }
        if (idempotencyKey.length() > MAX_IDEMPOTENCY_KEY_LENGTH) {
            throw PspException.invalidRequest("The Idempotency-Key must be at most 255 characters");
        }
    }

    private PaymentState initialState(Simulation simulation) {
        return switch (simulation.kind()) {
            case DECLINE -> PaymentState.DECLINED;
            case PENDING -> PaymentState.PENDING;
            default -> PaymentState.AUTHORIZED;
        };
    }

    private String declineReason(Simulation simulation) {
        return simulation.kind() == Simulation.Kind.DECLINE ? DECLINE_REASON : null;
    }

    private int pendingQueries(Simulation simulation) {
        return simulation.kind() == Simulation.Kind.PENDING ? simulation.pendingQueries() : 0;
    }

    private String encode(PaymentResponse response) {
        return json.writeValueAsString(response);
    }

    private PaymentResponse decode(String outcome) {
        return json.readValue(outcome, PaymentResponse.class);
    }

    private RuntimeException translate(JpaSystemException e) {
        return PS409.equals(sqlState(e)) ? PspException.invalidTransition() : e;
    }

    private String sqlState(Throwable e) {
        Throwable root = NestedExceptionUtils.getMostSpecificCause(e);
        return root instanceof SQLException sql ? sql.getSQLState() : null;
    }
}
