package com.abdelrahman.psp_simulator;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.sql.SQLException;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
public abstract class pspDatabaseSetup {

        static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16")
                        .withInitScript("postgres/init.sql");

        static {
                postgres.start();
        }

        @DynamicPropertySource
        static void configureProperties(DynamicPropertyRegistry registry) {
                registry.add("spring.datasource.url", postgres::getJdbcUrl);
                registry.add("spring.datasource.username", () -> "psp_service");
                registry.add("spring.datasource.password", () -> "psp_pass");

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

        @BeforeEach
        void clean() {
                jdbcTemplate.execute("TRUNCATE payment_requests, payments_model");
        }

        protected UUID insertPayment(String state, int counter) {
                return jdbcTemplate.queryForObject(
                                "INSERT INTO payments_model (state, amount, currency, pending_queries_remaining) " +
                                                "VALUES (?, 1, 'USD', ?) RETURNING id",
                                UUID.class, state, counter);
        }

        protected void setState(UUID id, String state) {
                jdbcTemplate.update("UPDATE payments_model SET state = ? WHERE id = ?", state, id);
        }

        protected String sqlStateOf(Runnable action) {
                Exception e = assertThrows(Exception.class, action::run);
                Throwable root = NestedExceptionUtils.getMostSpecificCause(e);
                return ((SQLException) root).getSQLState();
        }

}