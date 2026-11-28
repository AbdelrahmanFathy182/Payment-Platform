package com.abdelrahman.orchestrator.domain;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "client_idempotency")
public class ClientIdempotency {

    @EmbeddedId
    private ClientIdempotencyId id;

    @Column(name = "payment_id", nullable = false)
    private UUID paymentId;

    @Column(name = "request_hash", nullable = false)
    private String requestHash;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "response_snapshot")
    private String responseSnapshot;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected ClientIdempotency() {
    }
}
