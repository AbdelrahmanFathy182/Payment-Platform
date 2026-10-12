package com.abdelrahman.ledger_service.dto;
import com.abdelrahman.ledger_service.domain.AccountType;
import com.abdelrahman.ledger_service.domain.AccountCurrency;


public record CreateAccountRequest(
        String name,
        AccountType type,
        AccountCurrency currency 

){}