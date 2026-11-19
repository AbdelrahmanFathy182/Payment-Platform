package com.abdelrahman.psp_simulator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.abdelrahman.psp_simulator.dto.Simulation;
import com.abdelrahman.psp_simulator.exception.PspException;

public class SimulationTest {

    @Test
    void absentHeaderMeansNone() {
        assertEquals(Simulation.Kind.NONE, Simulation.parse(null).kind());
    }

    @Test
    void blankHeaderMeansNone() {
        assertEquals(Simulation.Kind.NONE, Simulation.parse("   ").kind());
    }

    @Test
    void parsesDecline() {
        Simulation simulation = Simulation.parse("DECLINE");

        assertEquals(Simulation.Kind.DECLINE, simulation.kind());
        assertNull(simulation.pendingQueries());
        assertNull(simulation.delayMs());
    }

    @Test
    void parsesTemporaryFailure() {
        assertEquals(Simulation.Kind.TEMP_FAILURE, Simulation.parse("TEMP_FAILURE").kind());
    }

    @Test
    void parsesResponseLost() {
        assertEquals(Simulation.Kind.RESPONSE_LOST, Simulation.parse("RESPONSE_LOST").kind());
    }

    @Test
    void parsesPendingCount() {
        Simulation simulation = Simulation.parse("PENDING:3");

        assertEquals(Simulation.Kind.PENDING, simulation.kind());
        assertEquals(3, simulation.pendingQueries());
    }

    @Test
    void parsesDelay() {
        Simulation simulation = Simulation.parse("DELAY:250");

        assertEquals(Simulation.Kind.DELAY, simulation.kind());
        assertEquals(250L, simulation.delayMs());
    }

    @Test
    void acceptsZeroDelay() {
        assertEquals(0L, Simulation.parse("DELAY:0").delayMs());
    }

    @Test
    void rejectsUnknownScenario() {
        PspException e = assertThrows(PspException.class, () -> Simulation.parse("EXPLODE"));

        assertEquals(PspException.INVALID_REQUEST, e.getCode());
        assertEquals(HttpStatus.BAD_REQUEST, e.getStatus());
    }

    @Test
    void rejectsPendingWithoutPositiveCount() {
        assertInvalid("PENDING");
        assertInvalid("PENDING:");
        assertInvalid("PENDING:0");
        assertInvalid("PENDING:-1");
        assertInvalid("PENDING:many");
        assertInvalid("PENDING:1:2");
    }

    @Test
    void rejectsNegativeOrMalformedDelay() {
        assertInvalid("DELAY");
        assertInvalid("DELAY:");
        assertInvalid("DELAY:-1");
        assertInvalid("DELAY:soon");
    }

    @Test
    void isCaseSensitive() {
        assertInvalid("decline");
        assertInvalid("pending:3");
    }

    private void assertInvalid(String header) {
        PspException e = assertThrows(PspException.class, () -> Simulation.parse(header));
        assertEquals(PspException.INVALID_REQUEST, e.getCode());
        assertTrue(e.getStatus().is4xxClientError());
    }
}
