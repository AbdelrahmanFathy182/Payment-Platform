package com.abdelrahman.orchestrator.domain;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "payments")
public class Payment {

    @Id
    private UUID id;

    @Column(name = "client_id", nullable = false)
    private String clientId;

    @Column(name = "client_key", nullable = false)
    private String clientKey;

    @Column(name = "request_hash", nullable = false)
    private String requestHash;

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    @Column(name = "currency", nullable = false)
    private String currency;

    @Column(name = "payer_ref", nullable = false)
    private String payerRef;

    @Column(name = "merchant_ref", nullable = false)
    private String merchantRef;

    @Column(name = "fee_minor", nullable = false)
    private long feeMinor;

    @Column(name = "merchant_amount_minor", nullable = false)
    private long merchantAmountMinor;

    @Enumerated(EnumType.STRING)
    @Column(name = "step", nullable = false)
    private PaymentStage step;

    @Enumerated(EnumType.STRING)
    @Column(name = "step_status", nullable = false)
    private StepStatus stepStatus;

    @Column(name = "current_attempt", nullable = false)
    private int currentAttempt;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "deadline_at", nullable = false)
    private Instant deadlineAt;

    @Column(name = "capture_due_at")
    private Instant captureDueAt;

    @Column(name = "next_action_at", nullable = false)
    private Instant nextActionAt;

    @Column(name = "needs_attention", nullable = false)
    private boolean needsAttention;

    @Column(name = "terminal_outcome")
    private String terminalOutcome;

    @Column(name = "correlation_id", nullable = false)
    private UUID correlationId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "response_snapshot")
    private String responseSnapshot;

    protected Payment() {
    }
}
