package com.abdelrahman.ledger_service.dto;

import java.math.BigDecimal;
import java.util.UUID;

import com.abdelrahman.ledger_service.domain.Enums.Currency;

public record BalanceResponse(UUID accountId, BigDecimal balance, Currency currency) {
}