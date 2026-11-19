package com.abdelrahman.psp_simulator.domain;

import java.time.Instant;
import java.util.UUID;

import com.abdelrahman.psp_simulator.domain.enums.Currency;
import com.abdelrahman.psp_simulator.domain.enums.PaymentState;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "payments_model")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", length = 20, nullable = false)
    private PaymentState state = PaymentState.PENDING;

    @Column(name = "amount", nullable = false)
    private long amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "currency", length = 3, nullable = false)
    private Currency currency;

    @Column(name = "decline_reason", length = 100)
    private String declineReason;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    @Column(name = "pending_queries_remaining", nullable = false)
    private int pendingQueriesRemaining;

    protected Payment() {
    }

    public Payment(long amount, Currency currency) {
        this(PaymentState.PENDING, amount, currency, null, 0);
    }

    public Payment(PaymentState state, long amount, Currency currency, String declineReason,
            int pendingQueriesRemaining) {
        this.state = state;
        this.amount = amount;
        this.currency = currency;
        this.declineReason = declineReason;
        this.pendingQueriesRemaining = pendingQueriesRemaining;
    }

    public UUID getId() {
        return id;
    }

    public PaymentState getState() {
        return state;
    }

    public void setState(PaymentState state) {
        this.state = state;
    }

    public long getAmount() {
        return amount;
    }

    public void setAmount(long amount) {
        this.amount = amount;
    }

    public Currency getCurrency() {
        return currency;
    }

    public void setCurrency(Currency currency) {
        this.currency = currency;
    }

    public String getDeclineReason() {
        return declineReason;
    }

    public void setDeclineReason(String declineReason) {
        this.declineReason = declineReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public int getPendingQueriesRemaining() {
        return pendingQueriesRemaining;
    }

    public void setPendingQueriesRemaining(int pendingQueriesRemaining) {
        this.pendingQueriesRemaining = pendingQueriesRemaining;
    }
}
