package com.abdelrahman.ledger_service.service;

import java.util.List;

import com.abdelrahman.ledger_service.domain.Transaction;
import com.abdelrahman.ledger_service.dto.EntryRequest;
import com.abdelrahman.ledger_service.dto.TransactionRequest;

import jakarta.transaction.Transactional;

public class TransactionService {

    public record CreateResult(Transaction transaction, Boolean created) {
    }

    @Transactional
    public CreateResult createTransaction(TransactionRequest request) {
        if (validateEntries(request.entries())) {
        }
        if (insertEntries(request.entries())) {
        }
        
        return null;
    }

    public Boolean validateEntries(List<EntryRequest> entries) {
        return null;
    }

    public Boolean insertEntries(List<EntryRequest> entries) {
        return null;
    }
}