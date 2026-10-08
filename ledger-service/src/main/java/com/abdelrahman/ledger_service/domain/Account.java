package com.abdelrahman.ledger_service.domain;

import java.util.UUID;

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

    public Account() {
    }

    public Account(UUID id, String name, AccountStatus status, AccountType type,
                   AccountCurrency currency, Instant created_at) {
        this.id = id;
        this.name = name;
        this.status = status;
        this.type = type;
        this.currency = currency;
        this.created_at = created_at;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
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

    public void setType(AccountType type) {
        this.type = type;
    }

    public AccountCurrency getCurrency() {
        return currency;
    }

    public void setCurrency(AccountCurrency currency) {
        this.currency = currency;
    }

    public Instant getCreatedAt() {
        return created_at;
    }

    public void setCreatedAt(Instant createdAt) {
        this.created_at = createdAt;
    }
}