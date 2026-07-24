package com.elicitsoftware.test;

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
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.HashMap;
import java.util.Map;

/**
 * Starts a single PostgreSQL container (Testcontainers) and points BOTH Quarkus
 * datasources — the default and the {@code owner} datasource — at it.
 *
 * <p>The app declares two datasources against the same physical database. Relying
 * on Dev Services to auto-provision them left the default datasource without a
 * URL (deactivated), so a {@code @QuarkusTest} failed to start. This resource
 * makes the wiring explicit and deterministic: one container, one {@code survey}
 * database, the same JDBC URL/credentials injected into both datasources.</p>
 *
 * <p>The container runs as the {@code postgres} superuser so the test bootstrap
 * migration ({@code db/test/V0.0.0.1__TEST_BOOTSTRAP.sql}) can {@code CREATE ROLE}.
 * Flyway (on the {@code owner} datasource) then applies the bootstrap and the
 * full {@code db/migration} history at startup.</p>
 */
public class PostgresTestResource implements QuarkusTestResourceLifecycleManager {

    private static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:18")
                    .withDatabaseName("survey")
                    .withUsername("postgres")
                    .withPassword("postgres");

    @Override
    public Map<String, String> start() {
        POSTGRES.start();
        String url = POSTGRES.getJdbcUrl();
        String user = POSTGRES.getUsername();
        String password = POSTGRES.getPassword();

        Map<String, String> config = new HashMap<>();
        // Default datasource (all Panache entities use this one).
        config.put("quarkus.datasource.jdbc.url", url);
        config.put("quarkus.datasource.username", user);
        config.put("quarkus.datasource.password", password);
        // Owner datasource (runs Flyway as elicit_owner in prod; same DB in test).
        config.put("quarkus.datasource.owner.jdbc.url", url);
        config.put("quarkus.datasource.owner.username", user);
        config.put("quarkus.datasource.owner.password", password);
        // Turn off Dev Services now that we supply explicit URLs.
        config.put("quarkus.datasource.devservices.enabled", "false");
        config.put("quarkus.datasource.owner.devservices.enabled", "false");
        return config;
    }

    @Override
    public void stop() {
        POSTGRES.stop();
    }
}
