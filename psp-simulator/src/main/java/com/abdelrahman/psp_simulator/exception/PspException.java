package com.abdelrahman.psp_simulator.exception;

import org.springframework.http.HttpStatus;

/**
 * Application error carrying the reason code the caller reacts to. The message
 * is for humans; the code is the contract.
 */
public class PspException extends RuntimeException {

    public static final String INVALID_REQUEST = "INVALID_REQUEST";
    public static final String MISSING_IDEMPOTENCY_KEY = "MISSING_IDEMPOTENCY_KEY";
    public static final String PAYMENT_NOT_FOUND = "PAYMENT_NOT_FOUND";
    public static final String INVALID_TRANSITION = "INVALID_TRANSITION";
    public static final String IDEMPOTENCY_KEY_REUSED = "IDEMPOTENCY_KEY_REUSED";
    public static final String TEMPORARY_FAILURE = "TEMPORARY_FAILURE";

    private final String code;
    private final HttpStatus status;

    public PspException(String code, HttpStatus status, String message) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public String getCode() {
        return code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public static PspException invalidRequest(String message) {
        return new PspException(INVALID_REQUEST, HttpStatus.BAD_REQUEST, message);
    }

    public static PspException missingIdempotencyKey() {
        return new PspException(MISSING_IDEMPOTENCY_KEY, HttpStatus.BAD_REQUEST,
                "The Idempotency-Key header is required");
    }

    public static PspException paymentNotFound() {
        return new PspException(PAYMENT_NOT_FOUND, HttpStatus.NOT_FOUND, "Payment not found");
    }

    public static PspException invalidTransition() {
        return new PspException(INVALID_TRANSITION, HttpStatus.CONFLICT,
                "The payment cannot move to the requested state");
    }

    public static PspException idempotencyKeyReused() {
        return new PspException(IDEMPOTENCY_KEY_REUSED, HttpStatus.CONFLICT,
                "The Idempotency-Key was already used with a different operation");
    }

    public static PspException temporaryFailure() {
        return new PspException(TEMPORARY_FAILURE, HttpStatus.SERVICE_UNAVAILABLE,
                "Temporary failure; retry with the same Idempotency-Key");
    }
}
