package com.abdelrahman.orchestrator;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
public abstract class OrchestratorDatabaseSetup {

    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16")
            .withInitScript("postgres/init.sql");

    static {
        postgres.start();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", () -> "orchestrator_service");
        registry.add("spring.datasource.password", () -> "orchestrator_pass");

        registry.add("spring.flyway.url", postgres::getJdbcUrl);
        registry.add("spring.flyway.user", () -> "flyway_user");
        registry.add("spring.flyway.password", () -> "flyway_pass");
    }

    @Autowired
    protected JdbcTemplate jdbcTemplate;
}
