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
 * <p>Neither datasource connects as a superuser, because neither does on a site. The
 * container's own {@code postgres} superuser runs one thing, {@code db/testcontainers-init.sql},
 * which prepares the cluster as the installation manual has an operator do it: the roles and the
 * two empty schemas. The {@code owner} datasource then connects as {@code elicit_owner}, and
 * Flyway applies the test bootstrap ({@code db/test/V0.0.0.1__TEST_BOOTSTRAP.sql}) and the full
 * {@code db/migration} history as that role; the default datasource connects as
 * {@code surveyadmin_user}, with the grants the migrations give it and no others. A migration
 * that needs more than the schema owner has, or a table the application role was never granted,
 * therefore fails here as it would on a site.</p>
 */
public class PostgresTestResource implements QuarkusTestResourceLifecycleManager {

    private static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:18")
                    .withDatabaseName("survey")
                    .withUsername("postgres")
                    .withPassword("postgres")
                    .withInitScript("db/testcontainers-init.sql");

    // The roles db/testcontainers-init.sql creates, with the passwords it gives them.
    private static final String APPLICATION_USER = "surveyadmin_user";
    private static final String APPLICATION_PASSWORD = "SAOWNERPW";
    private static final String OWNER_USER = "elicit_owner";
    private static final String OWNER_PASSWORD = "SURVEYPW";

    @Override
    public Map<String, String> start() {
        POSTGRES.start();
        String url = POSTGRES.getJdbcUrl();

        Map<String, String> config = new HashMap<>();
        // Default datasource (all Panache entities use this one).
        config.put("quarkus.datasource.jdbc.url", url);
        config.put("quarkus.datasource.username", APPLICATION_USER);
        config.put("quarkus.datasource.password", APPLICATION_PASSWORD);
        // Owner datasource (runs Flyway as elicit_owner, here as on a site; same DB).
        config.put("quarkus.datasource.owner.jdbc.url", url);
        config.put("quarkus.datasource.owner.username", OWNER_USER);
        config.put("quarkus.datasource.owner.password", OWNER_PASSWORD);
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
