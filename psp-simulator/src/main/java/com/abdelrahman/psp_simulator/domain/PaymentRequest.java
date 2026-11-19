package com.abdelrahman.psp_simulator.domain;

import java.time.Instant;
import java.util.UUID;

import com.abdelrahman.psp_simulator.domain.Enums.Operation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import org.hibernate.annotations.ColumnTransformer;

@Entity
@Table(name = "payment_requests")
public class request {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false)
    private UUID id;

    @Column(name = "idempotency_key", length = 255, nullable = false, unique = true, updatable = false)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "operation", length = 20, nullable = false, updatable = false)
    private Operation operation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false, updatable = false)
    private payment payment;

    @ColumnTransformer(write = "CAST(? AS jsonb)")
    @Column(name = "outcome", columnDefinition = "jsonb", nullable = false, updatable = false)
    private String outcome;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    protected request() {
    }

    public request(String idempotencyKey, Operation operation, payment payment, String outcome) {
        this.idempotencyKey = idempotencyKey;
        this.operation = operation;
        this.payment = payment;
        this.outcome = outcome;
    }

    public UUID getId() {
        return id;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public Operation getOperation() {
        return operation;
    }

    public payment getPayment() {
        return payment;
    }

    public UUID getPaymentId() {
        return payment == null ? null : payment.getId();
    }

    public String getOutcome() {
        return outcome;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
