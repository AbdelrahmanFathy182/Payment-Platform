
package com.abdelrahman.ledger_service.exception;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(LedgerException.class)
    public ResponseEntity<ErrorResponse> handleLedgerException(
            LedgerException exception) {

        ErrorResponse body = new ErrorResponse(
                exception.getCode(),
                exception.getMessage());

        return ResponseEntity
                .status(exception.getHttpStatus())
                .body(body);
    }
}
