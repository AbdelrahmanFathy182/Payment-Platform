package com.abdelrahman.ledger_service;

import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.abdelrahman.ledger_service.domain.Enums.Currency;
import com.abdelrahman.ledger_service.domain.Enums.TransactionType;
import com.abdelrahman.ledger_service.dto.Direction;
import com.abdelrahman.ledger_service.dto.EntryRequest;
import com.abdelrahman.ledger_service.dto.TransactionRequest;
import com.abdelrahman.ledger_service.service.TransactionService;

/**
 * LedgerTransactionServiceTests
 */
public class LedgerTransactionServiceTests extends LedgerDatabaseSetup{

    @Autowired 
    private TransactionService service;

    @Test
    void transactionCreatedSuccess(){
        UUID testBuyerId = createAccount("liability");
        UUID testSellerId = createAccount("liability");

        EntryRequest entryRequest1 = new EntryRequest(testBuyerId, Direction.credit, BigDecimal.TEN);
        EntryRequest entryRequest2 = new EntryRequest(testSellerId, Direction.debit, BigDecimal.TEN);

        List<EntryRequest> entries= new ArrayList<>();
        entries.add(entryRequest2);
        entries.add(entryRequest1);
        TransactionRequest request= new TransactionRequest("testkey", TransactionType.PAYMENT , Currency.EGP, entries);
        TransactionService.CreateResult result= service.createTransaction(request);

        assertTrue(result.created());
        
    }
}