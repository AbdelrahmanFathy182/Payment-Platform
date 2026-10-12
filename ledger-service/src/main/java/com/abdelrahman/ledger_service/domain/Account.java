package com.abdelrahman.ledger_service.domain;

import java.util.UUID;

import com.abdelrahman.ledger_service.domain.accountEnums.AccountCurrency;
import com.abdelrahman.ledger_service.domain.accountEnums.AccountStatus;
import com.abdelrahman.ledger_service.domain.accountEnums.AccountType;

import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.lang.String;
import java.time.Instant;

@Entity
@Table(name = "accounts")
public class Account {
    @Id
    @Column(name = "id", updatable = false)
    private UUID id;

    @Column(name = "name", length = 100, nullable = false, updatable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private AccountStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", length = 20, nullable = false, updatable = false)
    private AccountType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "currency", length = 3, nullable = false, updatable = false)
    private AccountCurrency currency;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private Instant created_at;

    protected Account() {
    }

    public Account(UUID id, String name, AccountStatus status, AccountType type,
            AccountCurrency currency) {
        this.id = id;
        this.name = name;
        this.status = status;
        this.type = type;
        this.currency = currency;
    }
    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public AccountStatus getStatus() {
        return status;
    }

    public void setStatus(AccountStatus status) {
        this.status = status;
    }

    public AccountType getType() {
        return type;
    }

    public AccountCurrency getCurrency() {
        return currency;
    }

    public Instant getCreatedAt() {
        return created_at;
    }
    
}