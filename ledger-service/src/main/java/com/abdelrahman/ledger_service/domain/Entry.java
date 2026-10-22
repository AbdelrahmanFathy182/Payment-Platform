package com.abdelrahman.ledger_service.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Table;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;

import com.abdelrahman.ledger_service.domain.Enums.Currency;
import com.abdelrahman.ledger_service.dto.Direction;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;

@Entity
@Table(name = "entries")
public class Entry {

    @Id
    @Column(name = "id", updatable = false)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id")
    private Account account;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id")
    private Transaction transaction;

    @Column(name = "credits", updatable = false, nullable = true)
    private BigDecimal credits;

    @Column(name = "debits", updatable = false, nullable = true)
    private BigDecimal debits;

    @Enumerated(EnumType.STRING)
    @Column(name = "currency", length = 3, nullable = false, updatable = false)
    private Currency currency;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private Instant createdAt;

    protected Entry() {
    }

    public Entry(UUID id, Account account, Transaction transaction, BigDecimal credits,
            BigDecimal debits, Currency currency) {
        this.id = id;
        this.account = account;
        this.transaction = transaction;
        this.credits = credits;
        this.debits = debits;
        this.currency = currency;
    }

    public UUID getId() {
        return id;
    }

    public Account getAccount() {
        return account;
    }

    public Transaction getTransaction() {
        return transaction;
    }

    public BigDecimal getCredits() {
        return credits;
    }

    public BigDecimal getDebits() {
        return debits;
    }

    public Currency getCurrency() {
        return currency;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public BigDecimal getAmount() {
        if (this.credits == null && this.debits != null) {
            return this.debits;
        } else if (this.credits != null && this.debits == null) {
            return this.credits;
        } else if (this.credits == null && this.debits == null) {
            throw new DataIntegrityViolationException("credits and debits both are null");
        } else {
            throw new DataIntegrityViolationException("credits and debits both have value");
        }
    }

    public Direction getDirection() {
        if (this.credits == null && this.debits != null) {
            return Direction.debit;
        } else if (this.credits != null && this.debits == null) {
            return Direction.credit;
        } else if (this.credits == null && this.debits == null) {
            throw new DataIntegrityViolationException("credits and debits both are null");
        } else {
            throw new DataIntegrityViolationException("credits and debits both have value");
        }
    }
}