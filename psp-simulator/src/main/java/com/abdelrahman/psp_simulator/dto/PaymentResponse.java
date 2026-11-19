package com.abdelrahman.psp_simulator.dto;

import java.util.UUID;

import com.abdelrahman.psp_simulator.domain.Payment;
import com.abdelrahman.psp_simulator.domain.enums.Currency;
import com.abdelrahman.psp_simulator.domain.enums.PaymentState;

public record PaymentResponse(
        UUID id,
        PaymentState state,
        long amount,
        Currency currency,
        String declineReason) {

    public static PaymentResponse from(Payment payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getState(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getDeclineReason());
    }
}
