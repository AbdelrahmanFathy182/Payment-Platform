package com.abdelrahman.ledger_service.dto;
import com.abdelrahman.ledger_service.domain.Account;
import com.abdelrahman.ledger_service.domain.Enums.Currency;
import com.abdelrahman.ledger_service.domain.Enums.AccountStatus;
import com.abdelrahman.ledger_service.domain.Enums.AccountType;

import java.util.UUID;

public record AccountResponse(
        UUID id,
        String name,
        AccountType type,
        Currency currency,
        AccountStatus status) {

    public static AccountResponse from(Account account) {
        return new AccountResponse(
                account.getId(),
                account.getName(),
                account.getType(),
                account.getCurrency(),
                account.getStatus());
    }
}