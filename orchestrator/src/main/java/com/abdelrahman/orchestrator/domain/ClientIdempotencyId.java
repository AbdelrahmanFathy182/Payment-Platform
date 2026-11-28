package com.abdelrahman.orchestrator.domain;

import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class ClientIdempotencyId implements Serializable {

    @Column(name = "client_id", nullable = false)
    private String clientId;

    @Column(name = "client_key", nullable = false)
    private String clientKey;

    protected ClientIdempotencyId() {
    }

    public ClientIdempotencyId(String clientId, String clientKey) {
        this.clientId = clientId;
        this.clientKey = clientKey;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ClientIdempotencyId that)) {
            return false;
        }
        return Objects.equals(clientId, that.clientId) && Objects.equals(clientKey, that.clientKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(clientId, clientKey);
    }
}
