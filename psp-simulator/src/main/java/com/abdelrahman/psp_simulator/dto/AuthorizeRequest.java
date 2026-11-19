package com.abdelrahman.psp_simulator.dto;

import com.abdelrahman.psp_simulator.domain.enums.Currency;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record AuthorizeRequest(
        @Positive long amount,
        @NotNull Currency currency) {
}
