package com.abdelrahman.orchestrator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

class OrchestratorMigrationTests extends OrchestratorDatabaseSetup {

    @Test
    void paymentRowCanBeInsertedWithDatabaseDefaults() {
        UUID id = UUID.randomUUID();
        insertPayment(id, "client-" + id, "key-" + id);

        Integer attempts = jdbcTemplate.queryForObject(
                "SELECT current_attempt FROM payments WHERE id = ?", Integer.class, id);
        Boolean needsAttention = jdbcTemplate.queryForObject(
                "SELECT needs_attention FROM payments WHERE id = ?", Boolean.class, id);
        Timestamp createdAt = jdbcTemplate.queryForObject(
                "SELECT created_at FROM payments WHERE id = ?", Timestamp.class, id);
        Timestamp nextActionAt = jdbcTemplate.queryForObject(
                "SELECT next_action_at FROM payments WHERE id = ?", Timestamp.class, id);

        assertEquals(1, attempts);
        assertFalse(needsAttention);
        assertNotNull(createdAt);
        assertNotNull(nextActionAt);
    }

    @Test
    void duplicateClientKeyIsRejected() {
        UUID firstId = UUID.randomUUID();
        String clientId = "client-" + firstId;
        String clientKey = "key-" + firstId;
        insertPayment(firstId, clientId, clientKey);

        UUID secondId = UUID.randomUUID();
        assertThrows(DuplicateKeyException.class,
                () -> insertPayment(secondId, clientId, clientKey));

        insertPayment(secondId, "other-" + firstId, clientKey);
    }

    private void insertPayment(UUID id, String clientId, String clientKey) {
        jdbcTemplate.update(
                """
                INSERT INTO payments (
                    id, client_id, client_key, request_hash, amount_minor, currency,
                    payer_ref, merchant_ref, fee_minor, merchant_amount_minor,
                    step, step_status, deadline_at, correlation_id
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                id,
                clientId,
                clientKey,
                "hash-" + id.toString().replace("-", "") + "0".repeat(27),
                15000L,
                "EGP",
                "payer-" + id,
                "merchant-" + id,
                300L,
                14700L,
                "RECEIVED",
                "PENDING",
                Timestamp.from(Instant.now().plus(Duration.ofMinutes(15))),
                UUID.randomUUID());
    }
}
