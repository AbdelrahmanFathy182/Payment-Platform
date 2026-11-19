package com.abdelrahman.psp_simulator.dto;

import com.abdelrahman.psp_simulator.exception.PspException;

/**
 * Parsed value of the {@code X-Simulate} header. An absent header means
 * {@link Kind#NONE}: normal success.
 */
public record Simulation(Kind kind, Integer pendingQueries, Long delayMs) {

    public enum Kind {
        NONE,
        DECLINE,
        TEMP_FAILURE,
        RESPONSE_LOST,
        PENDING,
        DELAY
    }

    public static Simulation none() {
        return new Simulation(Kind.NONE, null, null);
    }

    public static Simulation parse(String header) {
        if (header == null || header.isBlank()) {
            return none();
        }

        String value = header.trim();

        return switch (value) {
            case "DECLINE" -> new Simulation(Kind.DECLINE, null, null);
            case "TEMP_FAILURE" -> new Simulation(Kind.TEMP_FAILURE, null, null);
            case "RESPONSE_LOST" -> new Simulation(Kind.RESPONSE_LOST, null, null);
            default -> parseParameterized(value);
        };
    }

    private static Simulation parseParameterized(String value) {
        if (value.startsWith("PENDING:")) {
            return new Simulation(Kind.PENDING, positiveInt(value.substring("PENDING:".length())), null);
        }
        if (value.startsWith("DELAY:")) {
            return new Simulation(Kind.DELAY, null, nonNegativeLong(value.substring("DELAY:".length())));
        }
        throw PspException.invalidRequest("Unsupported X-Simulate value: " + value);
    }

    private static int positiveInt(String raw) {
        try {
            int count = Integer.parseInt(raw);
            if (count < 1) {
                throw new NumberFormatException();
            }
            return count;
        } catch (NumberFormatException e) {
            throw PspException.invalidRequest("PENDING requires a positive count");
        }
    }

    private static long nonNegativeLong(String raw) {
        try {
            long millis = Long.parseLong(raw);
            if (millis < 0) {
                throw new NumberFormatException();
            }
            return millis;
        } catch (NumberFormatException e) {
            throw PspException.invalidRequest("DELAY requires a non-negative millisecond value");
        }
    }
}
