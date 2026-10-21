package com.abdelrahman.ledger_service.dto;

import java.util.UUID;
import com.abdelrahman.ledger_service.domain.Enums.Currency;
import com.abdelrahman.ledger_service.domain.Transaction;
import com.abdelrahman.ledger_service.domain.Enums.TransactionType;

public record TransactionResponse(
                UUID id,
                Currency currency,
                TransactionType type) {

        public static TransactionResponse from(Transaction transaction) {
                return new TransactionResponse(
                                transaction.getId(),
                                transaction.getCurrency(),
                                transaction.getType());
        }
}