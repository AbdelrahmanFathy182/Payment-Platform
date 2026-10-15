package com.abdelrahman.ledger_service.domain;

import java.time.Instant;
import java.util.UUID;

import com.abdelrahman.ledger_service.domain.Enums.Currency;
import com.abdelrahman.ledger_service.domain.Enums.TransactionType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "transactions")
public class Transaction {
    @Id
    @Column(name = "id", updatable = false)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY) 
    @JoinColumn(name = "reverses_transaction_id")
    private Transaction reverses_transaction_id;

    @Enumerated(EnumType.STRING)
    @Column(name = "currency", length = 3, nullable = false, updatable = false)
    private Currency currency;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private Instant created_at;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", length = 20, nullable = false, updatable = false)
    private TransactionType type;

    @Column(name = "idempotency_key", nullable = false, updatable = false)
    private String idempotencyKey;
}