package com.abdelrahman.ledger_service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import com.abdelrahman.ledger_service.service.AccountService;
import com.abdelrahman.ledger_service.domain.accountEnums.AccountCurrency;
import com.abdelrahman.ledger_service.domain.accountEnums.AccountType;
import com.abdelrahman.ledger_service.dto.CreateAccountRequest;
import com.abdelrahman.ledger_service.exception.LedgerException;

public class LedgerServiceTests extends LedgerDatabaseSetup {

    @Autowired
    private AccountService service;

    @Test 
    void accountCreatedSuccess(){
        CreateAccountRequest request =new CreateAccountRequest("accountCreatedSuccessTest",AccountType.liability, AccountCurrency.EGP);
        AccountService.CreateResult result = service.create(request);
        assertTrue(result.created());
        assertEquals(result.account().getName(), "accountCreatedSuccessTest");
        
    }

    @Test 
    void accountExistsWithdiffCurrency(){
        UUID testUuid =createAccount("liability");
        CreateAccountRequest request =new CreateAccountRequest("test:"+testUuid,AccountType.liability, AccountCurrency.USD);
        assertThrows(LedgerException.class,() -> service.create(request));
        
    }
    @Test 
    void accountExistsWithdiffType(){
        UUID testUuid =createAccount("liability");
        CreateAccountRequest request =new CreateAccountRequest("test:"+testUuid,AccountType.asset, AccountCurrency.EGP);
        assertThrows(LedgerException.class,() -> service.create(request));
        
    }
    @Test 
    void accountExistsMatches(){
        UUID testUuid =createAccount("liability");
        assertFalse(service.create(new CreateAccountRequest("test:"+testUuid,AccountType.liability, AccountCurrency.EGP)).created());
    }

}