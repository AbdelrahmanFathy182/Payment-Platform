package com.abdelrahman.orchestrator.domain;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class AttemptId implements Serializable {

    @Column(name = "payment_id", nullable = false)
    private UUID paymentId;

    @Column(name = "attempt_no", nullable = false)
    private int attemptNo;

    protected AttemptId() {
    }

    public AttemptId(UUID paymentId, int attemptNo) {
        this.paymentId = paymentId;
        this.attemptNo = attemptNo;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof AttemptId that)) {
            return false;
        }
        return attemptNo == that.attemptNo && Objects.equals(paymentId, that.paymentId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(paymentId, attemptNo);
    }
}
