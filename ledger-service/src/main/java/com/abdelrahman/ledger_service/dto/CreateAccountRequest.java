package com.abdelrahman.ledger_service.dto;
import com.abdelrahman.ledger_service.domain.accountEnums.AccountCurrency;
import com.abdelrahman.ledger_service.domain.accountEnums.AccountType;


public record CreateAccountRequest(
        String name,
        AccountType type,
        AccountCurrency currency 

){}