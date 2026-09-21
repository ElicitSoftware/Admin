package com.elicitsoftware.diagnostics;

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

import com.elicitsoftware.test.PostgresTestResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Both connections are probed and the migration history is read per module.
 *
 * <p>Traceability: UC-022 (Diagnose Database), A2, BR-089.</p>
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class DatabaseDiagnosticsTest {

    @Inject
    DatabaseDiagnostics database;

    /** UC-022 step 2 / BR-089: both the application and the owner connection are probed. */
    @Test
    void probesBothConnections() {
        DatabaseDiagnostics.ConnectionReport app = database.checkApplicationConnection();
        DatabaseDiagnostics.ConnectionReport owner = database.checkOwnerConnection();

        assertTrue(app.result().isUp(), app.result().detail());
        assertTrue(owner.result().isUp(), owner.result().detail());
        assertEquals("application", app.label());
        assertEquals("owner", owner.label());
        assertNotNull(app.connectedAs());
        assertTrue(app.result().detail().startsWith("PostgreSQL"), "the detail carries the server version");
    }

    /** UC-022 step 3 and A2: Admin's own history is present; an absent module reads as not installed. */
    @Test
    void readsMigrationHistoryPerModule() {
        List<DatabaseDiagnostics.MigrationReport> history = database.migrationHistory();

        assertEquals(3, history.size());
        DatabaseDiagnostics.MigrationReport admin = history.stream()
                .filter(r -> r.module().equals("Admin")).findFirst().orElseThrow();
        assertTrue(admin.installed(), "the Admin history table exists in the test database");
        assertNotNull(admin.version(), "the Admin migrations have been applied");
        assertTrue(admin.success());
        history.forEach(r -> assertNotNull(r.table()));
    }

    /** UC-022 step 4 and 5: the sequence check and counts answer without throwing. */
    @Test
    void reportsSequencesAndCounts() {
        CheckResult sequences = database.checkDurableSequences();

        assertNotNull(sequences.detail());
        assertTrue(sequences.status() != CheckResult.Status.UNKNOWN);
        assertTrue(database.surveyCount() >= 0);
        assertTrue(database.userCount() >= 0);
    }
}
