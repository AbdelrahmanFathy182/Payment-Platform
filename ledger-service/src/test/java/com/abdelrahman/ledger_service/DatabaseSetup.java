package com.abdelrahman.ledger_service;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
public abstract class DatabaseSetup {

    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16")
            .withInitScript("postgres/init.sql");

    static {
        postgres.start();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", () -> "ledger_service");
        registry.add("spring.datasource.password", () -> "ledger_pass");

        registry.add("spring.flyway.url", postgres::getJdbcUrl);
        registry.add("spring.flyway.user", () -> "flyway_user");
        registry.add("spring.flyway.password", () -> "flyway_pass");
    }


    @Autowired
    protected JdbcTemplate jdbcTemplate;
    @Autowired
    protected TransactionTemplate tx;

    /**
     * JdbcTemplate connected as flyway_user (table owner), to test triggers without
     * permission limits.
     */
    protected JdbcTemplate ownerJdbc() {
        return new JdbcTemplate(new DriverManagerDataSource(
                postgres.getJdbcUrl(), "flyway_user", "flyway_pass"));
    }

    /**
     * helper method to insert a payment transaction with entries, must be called
     * within a transaction
     */
    protected void insertPayment(UUID txId, UUID debitAccount, UUID creditAccount, long amount) {
        jdbcTemplate.update(
                "INSERT INTO transactions (id, type, currency, idempotency_key) VALUES (?, 'PAYMENT', 'EGP', ?)",
                txId, UUID.randomUUID().toString());
        jdbcTemplate.update(
                "INSERT INTO entries (id, account_id, transaction_id, debits, credits, currency) VALUES (?, ?, ?, ?, NULL, 'EGP')",
                UUID.randomUUID(), debitAccount, txId, amount);
        jdbcTemplate.update(
                "INSERT INTO entries (id, account_id, transaction_id, debits, credits, currency) VALUES (?, ?, ?, NULL, ?, 'EGP')",
                UUID.randomUUID(), creditAccount, txId, amount);
    }

    /** Creates a fresh active EGP account with a unique name and returns its id. */
    protected UUID createAccount(String type) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO accounts (id, name, type, currency, status) VALUES (?, ?, ?, ?, 'active')",
                id, "test:" + id, type, "EGP");
        return id;
    }

}