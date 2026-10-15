package com.abdelrahman.ledger_service.dto;
import com.abdelrahman.ledger_service.domain.Enums.Currency;
import com.abdelrahman.ledger_service.domain.Enums.AccountType;


public record CreateAccountRequest(
        String name,
        AccountType type,
        Currency currency 

){}