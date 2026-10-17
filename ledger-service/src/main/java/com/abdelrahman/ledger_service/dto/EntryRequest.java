package com.abdelrahman.ledger_service.dto;

import java.math.BigDecimal;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.UUID;

public record EntryRequest(
        @NotNull UUID accountId,
        @NotNull Direction direction,
        @NotNull @Positive BigDecimal amount) {
}