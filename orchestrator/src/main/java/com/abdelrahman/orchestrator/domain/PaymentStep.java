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
@Table(name = "payment_steps")
public class PaymentStep {

    @Id
    private UUID id;

    @Column(name = "payment_id", nullable = false)
    private UUID paymentId;

    @Column(name = "attempt_no", nullable = false)
    private int attemptNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "step", nullable = false)
    private StepName step;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private StepStatus status;

    @Column(name = "psp_key")
    private String pspKey;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "request_payload", nullable = false)
    private String requestPayload;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "response_payload")
    private String responsePayload;

    @Column(name = "started_at", nullable = false, insertable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "next_poll_at")
    private Instant nextPollAt;

    @Column(name = "poll_count", nullable = false)
    private int pollCount;

    @Column(name = "capture_alert_at")
    private Instant captureAlertAt;

    @Column(name = "step_alert_at")
    private Instant stepAlertAt;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    protected PaymentStep() {
    }
}
