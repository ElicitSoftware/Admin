package com.elicitsoftware.common.health;

/*-
 * ***LICENSE_START***
 * Elicit Survey
 * %%
 * Copyright (C) 2025 - 2026 The Regents of the University of Michigan - Rogel Cancer Center
 * %%
 * PolyForm Noncommercial License 1.0.0
 * <https://polyformproject.org/licenses/noncommercial/1.0.0>
 * ***LICENSE_END***
 */

import org.eclipse.microprofile.health.HealthCheckResponse;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * UC-022: Diagnose the Database (BR-089) — the readiness probe of the application connection:
 * up when the connection runs {@code SELECT 1}, down with the reason when it cannot, against a
 * hand-written JDBC stand-in.
 */
class DatabaseHealthCheckUnitTest {

    private final List<String> calls = new ArrayList<>();

    private DataSource dataSource(SQLException connectFailure, SQLException queryFailure) {
        Statement statement = (Statement) Proxy.newProxyInstance(Statement.class.getClassLoader(),
                new Class<?>[]{Statement.class}, (p, method, args) -> {
                    calls.add(method.getName() + (args == null ? "" : " " + args[0]));
                    if (method.getName().equals("execute") && queryFailure != null) {
                        throw queryFailure;
                    }
                    return method.getName().equals("execute") ? Boolean.TRUE : null;
                });
        Connection connection = (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class}, (p, method, args) -> {
                    calls.add("connection." + method.getName());
                    return method.getName().equals("createStatement") ? statement : null;
                });
        return (DataSource) Proxy.newProxyInstance(DataSource.class.getClassLoader(),
                new Class<?>[]{DataSource.class}, (p, method, args) -> {
                    if (connectFailure != null) {
                        throw connectFailure;
                    }
                    return connection;
                });
    }

    private HealthCheckResponse call(DataSource dataSource) {
        DatabaseHealthCheck check = new DatabaseHealthCheck();
        check.dataSource = dataSource;
        return check.call();
    }

    @Test
    void given_aWorkingConnection_when_called_then_upAndTheConnectionClosed() {
        HealthCheckResponse response = call(dataSource(null, null));

        assertEquals(HealthCheckResponse.Status.UP, response.getStatus());
        assertEquals("Database connection healthy", response.getName());
        assertEquals(List.of("connection.createStatement", "execute SELECT 1", "close", "connection.close"), calls);
    }

    @Test
    void given_aRefusedConnection_when_called_then_downWithTheReason() {
        HealthCheckResponse response = call(dataSource(new SQLException("connection refused"), null));

        assertEquals(HealthCheckResponse.Status.DOWN, response.getStatus());
        assertEquals("Database connection failed: connection refused", response.getName());
    }

    @Test
    void given_aFailingQuery_when_called_then_downAndTheConnectionStillClosed() {
        HealthCheckResponse response = call(dataSource(null, new SQLException("relation does not exist")));

        assertEquals(HealthCheckResponse.Status.DOWN, response.getStatus());
        assertEquals("Database connection failed: relation does not exist", response.getName());
        assertEquals("connection.close", calls.getLast());
    }
}
