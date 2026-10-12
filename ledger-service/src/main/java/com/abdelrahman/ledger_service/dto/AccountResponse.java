package com.abdelrahman.ledger_service.dto;
import com.abdelrahman.ledger_service.domain.accountEnums.AccountCurrency;
import com.abdelrahman.ledger_service.domain.accountEnums.AccountStatus;
import com.abdelrahman.ledger_service.domain.accountEnums.AccountType;
import com.abdelrahman.ledger_service.domain.Account;



import java.util.UUID;

public record AccountResponse(
        UUID id,
        String name,
        AccountType type,
        AccountCurrency currency,
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