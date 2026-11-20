package com.abdelrahman.psp_simulator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.abdelrahman.psp_simulator.domain.enums.Operation;
import com.abdelrahman.psp_simulator.dto.Simulation;
import com.abdelrahman.psp_simulator.exception.PspException;
import com.abdelrahman.psp_simulator.service.SimulationExecutor;

public class SimulationExecutorTest {

    @Test
    void temporaryFailureFailsBeforeAnyWork() {
        SimulationExecutor executor = new SimulationExecutor(0, 0);

        PspException e = assertThrows(PspException.class,
                () -> executor.prepare(Simulation.parse("TEMP_FAILURE"), Operation.AUTHORIZE));

        assertEquals(PspException.TEMPORARY_FAILURE, e.getCode());
    }

    @Test
    void declineAndPendingAreAuthorizeOnly() {
        SimulationExecutor executor = new SimulationExecutor(0, 0);

        assertEquals(PspException.INVALID_REQUEST,
                assertThrows(PspException.class,
                        () -> executor.prepare(Simulation.parse("DECLINE"), Operation.CAPTURE)).getCode());
        assertEquals(PspException.INVALID_REQUEST,
                assertThrows(PspException.class,
                        () -> executor.prepare(Simulation.parse("PENDING:2"), Operation.VOID)).getCode());
    }

    @Test
    void queriesRejectEveryScenario() {
        SimulationExecutor executor = new SimulationExecutor(0, 0);

        assertEquals(PspException.INVALID_REQUEST,
                assertThrows(PspException.class,
                        () -> executor.prepare(Simulation.parse("DELAY:10"), null)).getCode());
        assertEquals(PspException.INVALID_REQUEST,
                assertThrows(PspException.class,
                        () -> executor.prepare(Simulation.parse("DECLINE"), null)).getCode());
    }

    @Test
    void delaySleepsBeforeWork() {
        SimulationExecutor executor = new SimulationExecutor(0, 0);
        long start = System.nanoTime();

        executor.prepare(Simulation.parse("DELAY:60"), Operation.AUTHORIZE);

        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        assertTrue(elapsedMs >= 40, () -> "slept only " + elapsedMs + "ms");
    }

    @Test
    void responseLostSleepsAfterCommit() {
        SimulationExecutor executor = new SimulationExecutor(60, 0);
        long start = System.nanoTime();

        executor.afterCommit(Simulation.parse("RESPONSE_LOST"));

        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        assertTrue(elapsedMs >= 40, () -> "slept only " + elapsedMs + "ms");
    }

    @Test
    void randomFailureRateAppliesToWritesOnly() {
        SimulationExecutor alwaysFails = new SimulationExecutor(0, 1.0);

        assertEquals(PspException.TEMPORARY_FAILURE,
                assertThrows(PspException.class,
                        () -> alwaysFails.prepare(Simulation.none(), Operation.AUTHORIZE)).getCode());
        alwaysFails.prepare(Simulation.none(), null);
    }

    @Test
    void zeroFailureRateNeverFails() {
        SimulationExecutor neverFails = new SimulationExecutor(0, 0);

        neverFails.prepare(Simulation.none(), Operation.AUTHORIZE);
    }
}
