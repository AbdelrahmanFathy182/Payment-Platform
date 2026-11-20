package com.abdelrahman.psp_simulator.service;

import java.util.concurrent.ThreadLocalRandom;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.abdelrahman.psp_simulator.domain.enums.Operation;
import com.abdelrahman.psp_simulator.dto.Simulation;
import com.abdelrahman.psp_simulator.exception.PspException;

/**
 * Applies the X-Simulate controls around the business work: reject scenarios
 * that do not belong to the operation, fail before touching the database,
 * add latency, and stall after commit for RESPONSE_LOST.
 */
@Component
public class SimulationExecutor {

    private final long responseLostMs;
    private final double randomFailureRate;

    public SimulationExecutor(
            @Value("${psp.simulation.response-lost-ms:30000}") long responseLostMs,
            @Value("${psp.simulation.random-failure-rate:0}") double randomFailureRate) {
        this.responseLostMs = responseLostMs;
        this.randomFailureRate = randomFailureRate;
    }

    /**
     * Called before any database work. {@code operation == null} means a query.
     */
    public void prepare(Simulation simulation, Operation operation) {
        validate(simulation.kind(), operation);

        if (simulation.kind() == Simulation.Kind.TEMP_FAILURE) {
            throw PspException.temporaryFailure();
        }

        if (simulation.kind() == Simulation.Kind.DELAY) {
            sleep(simulation.delayMs());
        }

        if (operation != null && ThreadLocalRandom.current().nextDouble() < randomFailureRate) {
            throw PspException.temporaryFailure();
        }
    }

    /**
     * Called only after the write transaction has committed.
     */
    public void afterCommit(Simulation simulation) {
        if (simulation.kind() == Simulation.Kind.RESPONSE_LOST) {
            sleep(responseLostMs);
        }
    }

    private void validate(Simulation.Kind kind, Operation operation) {
        if (operation == null) {
            if (kind != Simulation.Kind.NONE) {
                throw PspException.invalidRequest("X-Simulate is not supported on query");
            }
            return;
        }

        if ((kind == Simulation.Kind.DECLINE || kind == Simulation.Kind.PENDING)
                && operation != Operation.AUTHORIZE) {
            throw PspException.invalidRequest(kind + " is only supported on authorize");
        }
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw PspException.temporaryFailure();
        }
    }
}
