package com.elicitsoftware.common.health;

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

import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import javax.sql.DataSource;
import java.sql.Connection;

/**
 * Health check implementation for database connectivity.
 * <p>
 * This health check verifies that the application can successfully connect
 * to the database and execute queries. It is used for readiness probes in
 * containerized environments.
 * </p>
 */
@Readiness
@ApplicationScoped
public class DatabaseHealthCheck implements HealthCheck {

    /**
     * Default constructor for DatabaseHealthCheck.
     * <p>
     * Creates a new DatabaseHealthCheck instance. The datasource is injected
     * by CDI after construction.
     * </p>
     */
    public DatabaseHealthCheck() {
        // Default constructor
    }

    @Inject
    DataSource dataSource;

    @Override
    public HealthCheckResponse call() {
        try (Connection connection = dataSource.getConnection();
             var statement = connection.createStatement()) {
            // Simple connectivity test
            statement.execute("SELECT 1");
            
            return HealthCheckResponse.up("Database connection healthy");
        } catch (Exception e) {
            return HealthCheckResponse.down("Database connection failed: " + e.getMessage());
        }
    }
}
