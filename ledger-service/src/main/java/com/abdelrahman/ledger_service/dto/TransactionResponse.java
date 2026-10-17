package com.abdelrahman.ledger_service.dto;

import java.util.Currency;
import java.util.UUID;

import com.abdelrahman.ledger_service.domain.Enums.TransactionType;

public record TransactionResponse(UUID id, UUID reversesTransactionId, Currency currency, TransactionType type) {

}