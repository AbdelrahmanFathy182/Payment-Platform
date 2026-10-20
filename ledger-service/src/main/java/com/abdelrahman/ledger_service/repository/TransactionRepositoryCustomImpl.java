package com.abdelrahman.ledger_service.repository;

import org.springframework.stereotype.Repository;

import com.abdelrahman.ledger_service.domain.Transaction;

import jakarta.persistence.EntityManager;

@Repository
public class TransactionRepositoryCustomImpl  implements TransactionRepositoryCustom {

    private final EntityManager manager;

    public TransactionRepositoryCustomImpl(EntityManager manager) {
        this.manager = manager;
    }

    @Override
    public void insert(Transaction transaction) {
        manager.persist(transaction);
    }

}