package com.elicitsoftware.integration;

/*-
 * ***LICENSE_START***
 * Elicit Survey
 * %%
 * Copyright (C) 2025 The Regents of the University of Michigan - Rogel Cancer Center
 * %%
 * PolyForm Noncommercial License 1.0.0
 * <https://polyformproject.org/licenses/noncommercial/1.0.0>
 * ***LICENSE_END***
 */

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.Map;

/**
 * Testcontainers resource for PostgreSQL database integration tests.
 * <p>
 * This resource starts a PostgreSQL container before tests run and stops it after.
 * It provides a real PostgreSQL database instance for integration testing,
 * ensuring tests run against the actual database the application uses in production.
 * 
 * @author Elicit Software
 * @since 1.0.0
 */
public class PostgresTestResource implements QuarkusTestResourceLifecycleManager {

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17")
            .withDatabaseName("test")
            .withUsername("test")
            .withPassword("test")
            .withReuse(true);

    @Override
    public Map<String, String> start() {
        POSTGRES.start();
        
        return Map.of(
            "quarkus.datasource.jdbc.url", POSTGRES.getJdbcUrl(),
            "quarkus.datasource.username", POSTGRES.getUsername(),
            "quarkus.datasource.password", POSTGRES.getPassword(),
            "quarkus.datasource.app.jdbc.url", POSTGRES.getJdbcUrl(),
            "quarkus.datasource.app.username", POSTGRES.getUsername(),
            "quarkus.datasource.app.password", POSTGRES.getPassword()
        );
    }

    @Override
    public void stop() {
        // Container is reused across test runs for performance
    }
}
