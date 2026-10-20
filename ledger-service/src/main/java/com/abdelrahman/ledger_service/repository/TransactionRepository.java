package com.abdelrahman.ledger_service.repository;

import com.abdelrahman.ledger_service.domain.Transaction;
import org.springframework.data.repository.Repository;
import java.util.Optional;
import java.util.UUID;

public interface TransactionRepository extends Repository<Transaction, UUID> ,TransactionRepositoryCustom{

    Optional<Transaction> findById(UUID id);

    Optional<Transaction> findByIdempotencyKey(String key);

    boolean existsByreversesTransactionId(UUID transactionId);

    Optional<Transaction> findByreversesTransactionId(UUID transactionId);

}