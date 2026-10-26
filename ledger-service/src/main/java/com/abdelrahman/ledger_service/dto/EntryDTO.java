package com.abdelrahman.ledger_service.dto;

import java.math.BigDecimal;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.UUID;

import com.abdelrahman.ledger_service.domain.Entry;

public record EntryDTO(
                @NotNull UUID accountId,
                @NotNull Direction direction,
                @NotNull @Positive BigDecimal amount) {

        public static EntryDTO from(Entry entry) {
                return new EntryDTO(entry.getAccount().getId(), entry.getDirection(), entry.getAmount());
        }
}