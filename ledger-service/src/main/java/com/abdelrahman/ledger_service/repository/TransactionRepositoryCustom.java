package com.abdelrahman.ledger_service.repository;

import com.abdelrahman.ledger_service.domain.Transaction;

public interface TransactionRepositoryCustom {

    void insert(Transaction transaction);
}