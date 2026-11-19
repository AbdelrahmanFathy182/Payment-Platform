package com.abdelrahman.psp_simulator.exception;

import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.abdelrahman.psp_simulator.dto.ErrorResponse;
import com.abdelrahman.psp_simulator.web.ApiHeaders;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(PspException.class)
    public ResponseEntity<ErrorResponse> handlePsp(PspException e) {
        return ResponseEntity.status(e.getStatus())
                .body(new ErrorResponse(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErrorResponse> handleMissingHeader(MissingRequestHeaderException e) {
        if (ApiHeaders.IDEMPOTENCY_KEY.equalsIgnoreCase(e.getHeaderName())) {
            return from(PspException.missingIdempotencyKey());
        }
        return from(PspException.invalidRequest("Missing required header: " + e.getHeaderName()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        String details = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + " " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));
        String message = details.isBlank() ? "Invalid request body" : details;
        return from(PspException.invalidRequest(message));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(HttpMessageNotReadableException e) {
        return from(PspException.invalidRequest("Malformed request body"));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return from(PspException.invalidRequest("Invalid value for '" + e.getName() + "'"));
    }

    private ResponseEntity<ErrorResponse> from(PspException e) {
        return ResponseEntity.status(e.getStatus())
                .body(new ErrorResponse(e.getCode(), e.getMessage()));
    }
}
