package com.abdelrahman.ledger_service.exception;

public class LedgerException extends RuntimeException {
    private final String code;
    private final int httpStatus;

    public LedgerException(String code, int httpStatus, String message) {
        super(message);
        this.code = code;
        this.httpStatus = httpStatus;
    }

    public String getCode() { return code; }
    public int getHttpStatus() { return httpStatus; }
}