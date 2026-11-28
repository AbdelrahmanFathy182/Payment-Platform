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

    @Enumerated(EnumType.STRING)
    @Column(name = "terminal_outcome")
    private TerminalOutcome terminalOutcome;

    @Column(name = "correlation_id", nullable = false)
    private UUID correlationId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "response_snapshot")
    private String responseSnapshot;

    protected Payment() {
    }

    public Payment(
            UUID id,
            String clientId,
            String clientKey,
            String requestHash,
            long amountMinor,
            String currency,
            String payerRef,
            String merchantRef,
            long feeMinor,
            long merchantAmountMinor,
            UUID correlationId,
            Instant deadlineAt,
            Instant nextActionAt) {
        this.id = id;
        this.clientId = clientId;
        this.clientKey = clientKey;
        this.requestHash = requestHash;
        this.amountMinor = amountMinor;
        this.currency = currency;
        this.payerRef = payerRef;
        this.merchantRef = merchantRef;
        this.feeMinor = feeMinor;
        this.merchantAmountMinor = merchantAmountMinor;
        this.correlationId = correlationId;
        this.deadlineAt = deadlineAt;
        this.nextActionAt = nextActionAt;
        this.step = PaymentStage.RECEIVED;
        this.stepStatus = StepStatus.PENDING;
        this.currentAttempt = 1;
        this.needsAttention = false;
    }

    public PaymentState state() {
        return new PaymentState(step, stepStatus, terminalOutcome);
    }

    public boolean needsAttention() {
        return needsAttention;
    }

    public int currentAttempt() {
        return currentAttempt;
    }

    void startNextAttempt() {
        currentAttempt++;
    }

    void applyState(PaymentState state) {
        this.step = state.stage();
        this.stepStatus = state.status();
        this.terminalOutcome = state.outcome();
        if (state.outcome() == TerminalOutcome.NEEDS_ATTENTION) {
            this.needsAttention = true;
        } else if (state.status() == StepStatus.DONE || state.status() == StepStatus.FAILED) {
            this.needsAttention = false;
        }
    }
}
