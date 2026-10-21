package com.abdelrahman.ledger_service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import com.abdelrahman.ledger_service.service.AccountService;
import com.abdelrahman.ledger_service.domain.Enums.Currency;
import com.abdelrahman.ledger_service.domain.Enums.AccountType;
import com.abdelrahman.ledger_service.dto.CreateAccountRequest;
import com.abdelrahman.ledger_service.dto.CreateStatusUpdateRequest;
import com.abdelrahman.ledger_service.exception.LedgerException;

public class LedgerAccountServiceTests extends LedgerDatabaseSetup {

    @Autowired
    private AccountService service;

    @Test 
    void accountCreatedSuccess(){
        CreateAccountRequest request =new CreateAccountRequest("accountCreatedSuccessTest",AccountType.liability, Currency.EGP);
        AccountService.CreateResult result = service.create(request);
        assertTrue(result.created());
        assertEquals(result.account().getName(), "accountCreatedSuccessTest");
        
    }

    @Test 
    void accountExistsWithdiffCurrency(){
        UUID testUuid =createAccount("liability");
        CreateAccountRequest request =new CreateAccountRequest("test:"+testUuid,AccountType.liability, Currency.USD);
        assertThrows(LedgerException.class,() -> service.create(request));
        
    }
    @Test 
    void accountExistsWithdiffType(){
        UUID testUuid =createAccount("liability");
        CreateAccountRequest request =new CreateAccountRequest("test:"+testUuid,AccountType.asset,Currency.EGP);
        assertThrows(LedgerException.class,() -> service.create(request));
        
    }
    @Test 
    void accountExistsMatches(){
        UUID testUuid =createAccount("liability");
        assertFalse(service.create(new CreateAccountRequest("test:"+testUuid,AccountType.liability, Currency.EGP)).created());
    }

    @Test 
    void accountStatusupdate(){
        UUID testUuid =createAccount("liability");
        boolean status = true;
        CreateStatusUpdateRequest request = new CreateStatusUpdateRequest(status);
        assertTrue(service.updateStatus("test:" + testUuid, request).updated());
    }

    @Test 
    void accountNotFoundStatusupdate(){
        boolean status = true;
        CreateStatusUpdateRequest request = new CreateStatusUpdateRequest(status);
        assertThrows(LedgerException.class,()-> service.updateStatus("badaccount", request));
    }

}