package com.elicitsoftware.service;

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
import org.testcontainers.utility.DockerImageName;

import java.util.Map;

/**
 * Starts a PostgreSQL 17 Testcontainers container before Quarkus boots and
 * injects the JDBC URLs for both the default and owner datasources as
 * high-priority config overrides. This bypasses the unreachable production
 * host "db" and allows Flyway migrations to run against the in-test container.
 */
public class PostgreSQLTestResource implements QuarkusTestResourceLifecycleManager {

    @SuppressWarnings("resource")
    private static final PostgreSQLContainer<?> CONTAINER =
            new PostgreSQLContainer<>(
                    DockerImageName.parse("postgres:17").asCompatibleSubstituteFor("postgres"))
                    .withDatabaseName("survey")
                    .withUsername("elicit_owner")
                    .withPassword("SURVEYPW");

    @Override
    public Map<String, String> start() {
        CONTAINER.start();
        // Register a JVM shutdown hook so the container is removed even if stop() is
        // never called (e.g. abnormal JVM exit or Quarkus test framework skipping cleanup).
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (CONTAINER.isRunning()) {
                CONTAINER.stop();
            }
        }, "testcontainer-shutdown"));
        String jdbcUrl = CONTAINER.getJdbcUrl();
        // Both datasources connect to the same container as elicit_owner.
        // The default datasource production user (surveyadmin_user) is overridden here.
        return Map.of(
                "quarkus.datasource.jdbc.url", jdbcUrl,
                "quarkus.datasource.username", "elicit_owner",
                "quarkus.datasource.password", "SURVEYPW",
                "quarkus.datasource.owner.jdbc.url", jdbcUrl,
                "quarkus.datasource.owner.username", "elicit_owner",
                "quarkus.datasource.owner.password", "SURVEYPW"
        );
    }

    @Override
    public void stop() {
        if (CONTAINER.isRunning()) {
            CONTAINER.stop();
        }
    }
}
