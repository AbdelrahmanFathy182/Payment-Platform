package com.abdelrahman.ledger_service.dto;

import java.lang.String;
import java.util.List;

import com.abdelrahman.ledger_service.domain.Enums.TransactionType;
import com.abdelrahman.ledger_service.domain.Enums.Currency;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TransactionRequest(
        @NotBlank String idempotencyKey,
        @NotNull TransactionType type,
        @NotNull Currency currency,
        @NotNull @Size(min = 2, max = 50) List<EntryRequest> entries) {
}