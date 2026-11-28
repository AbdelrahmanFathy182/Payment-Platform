package com.abdelrahman.orchestrator.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

@Entity
@Table(name = "attempts")
public class Attempt {

    @EmbeddedId
    private AttemptId id;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private AttemptStatus status;

    @Column(name = "closed_reason")
    private String closedReason;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    protected Attempt() {
    }
}
