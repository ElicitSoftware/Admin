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

import com.elicitsoftware.diagnostics.DatabaseDiagnostics.ConnectionReport;
import com.elicitsoftware.diagnostics.DatabaseDiagnostics.MigrationReport;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UC-022: Diagnose the Database (BR-089, BR-090) — both connections are probed and the latest
 * migration of every module sharing the {@code survey} schema is read through the owner
 * connection, against {@link ScriptedDataSource} stand-ins. Every failure is reported instead of
 * thrown, and nothing is written.
 */
class DatabaseDiagnosticsProbeTest {

    private static final String TABLE_EXISTS = "FROM information_schema.tables";
    private static final String SEQUENCE_EXISTS = "FROM information_schema.sequences";

    private static DatabaseDiagnostics diagnostics(ScriptedDataSource application, ScriptedDataSource owner) {
        DatabaseDiagnostics diagnostics = new DatabaseDiagnostics();
        diagnostics.dataSource = application.dataSource();
        diagnostics.ownerDataSource = owner.dataSource();
        diagnostics.applicationUser = "elicit_app";
        diagnostics.ownerUser = "elicit_owner";
        return diagnostics;
    }

    /** A probe takes well under a minute; an arithmetic slip in the timing would not. */
    private static void assertPlausible(CheckResult result) {
        assertTrue(result.durationMs() >= 0 && result.durationMs() < 60_000, String.valueOf(result.durationMs()));
    }

    private static List<Object[]> rows(Object[]... rows) {
        return new ArrayList<>(List.of(rows));
    }

    // ---- connections (BR-089) ---------------------------------------------------------------------

    @Test
    void given_anAnsweringServer_when_checkApplicationConnection_then_upWithWhoAndTheVersion() {
        ScriptedDataSource application = ScriptedDataSource.answeringRow("elicit_app", "PostgreSQL 17.2");

        ConnectionReport report = diagnostics(application, ScriptedDataSource.refusing("unused")).checkApplicationConnection();

        assertEquals("application", report.label());
        assertEquals("elicit_app", report.configuredUser());
        assertEquals("elicit_app", report.connectedAs());
        assertTrue(report.result().isUp());
        assertEquals("application connection", report.result().name());
        assertEquals("PostgreSQL 17.2", report.result().detail());
        assertPlausible(report.result());
        assertEquals(List.of("SELECT current_user, version()"), application.executed);
        assertEquals(1, application.connectionsClosed);
    }

    @Test
    void given_aWrongOwnerPassword_when_checkOwnerConnection_then_downWithTheReasonAndTheConfiguredUser() {
        ConnectionReport report = diagnostics(ScriptedDataSource.answeringRow("x", "y"),
                ScriptedDataSource.refusing("password authentication failed for user \"elicit_owner\"")).checkOwnerConnection();

        assertEquals("owner", report.label());
        assertEquals("elicit_owner", report.configuredUser());
        assertNull(report.connectedAs());
        assertEquals(CheckResult.Status.DOWN, report.result().status());
        assertEquals("owner connection", report.result().name());
        assertEquals("password authentication failed for user \"elicit_owner\"", report.result().detail());
        assertPlausible(report.result());
    }

    @Test
    void given_aDriverThatFailsUnchecked_when_probe_then_downRatherThanThrown() {
        ScriptedDataSource broken = ScriptedDataSource.answering((sql, p) -> {
            throw new IllegalStateException("pool closed");
        });

        ConnectionReport report = diagnostics(broken, broken).checkOwnerConnection();

        assertEquals(CheckResult.down("owner connection", "pool closed", report.result().durationMs()), report.result());
        assertEquals(1, broken.connectionsClosed, "the connection is closed after a failure too");
    }

    // ---- migration history (UC-022 step 3, A2) -----------------------------------------------------

    @Test
    void given_threeHistoryTables_when_migrationHistory_then_theLatestOfEachThroughTheOwner() {
        ScriptedDataSource owner = ScriptedDataSource.answering((sql, parameters) -> {
            if (sql.contains(TABLE_EXISTS)) {
                return parameters.equals(List.of("flyway_fhhs_history")) ? rows() : rows(new Object[]{1});
            }
            if (sql.contains("FROM survey.flyway_history ")) {
                return rows(new Object[]{"19", "2026-09-01 10:00:00", true});
            }
            if (sql.contains("FROM survey.flyway_admin_history ")) {
                return rows();
            }
            throw new AssertionError(sql);
        });
        ScriptedDataSource application = ScriptedDataSource.refusing("the application user is not granted the history");

        List<MigrationReport> reports = diagnostics(application, owner).migrationHistory();

        assertEquals(List.of(
                new MigrationReport("Survey", "flyway_history", "19", "2026-09-01 10:00:00", true, true),
                new MigrationReport("Admin", "flyway_admin_history", null, null, false, true),
                new MigrationReport("Family History", "flyway_fhhs_history", null, null, false, false)), reports);
        assertEquals("SELECT version, installed_on::text, success FROM survey.flyway_history"
                + " WHERE version IS NOT NULL ORDER BY installed_rank DESC LIMIT 1", owner.executed.get(1));
        assertEquals("SELECT 1 FROM information_schema.tables WHERE table_schema = 'survey' AND table_name = ?",
                owner.executed.get(0));
        assertEquals(List.of(List.of("flyway_history"), List.of(), List.of("flyway_admin_history"), List.of(),
                List.of("flyway_fhhs_history")), owner.bound);
        assertEquals(1, owner.connectionsClosed, "one owner connection serves the three tables");
    }

    @Test
    void given_aFailedLatestMigration_when_migrationHistory_then_successIsFalse() {
        ScriptedDataSource owner = ScriptedDataSource.answering((sql, parameters) -> sql.contains(TABLE_EXISTS)
                ? rows(new Object[]{1}) : rows(new Object[]{"20", "today", false}));

        List<MigrationReport> reports = diagnostics(ScriptedDataSource.refusing("x"), owner).migrationHistory();

        assertEquals(3, reports.size());
        assertTrue(reports.stream().allMatch(r -> r.installed() && !r.success() && "20".equals(r.version())));
    }

    @Test
    void given_aHistoryTableThatCannotBeRead_when_migrationHistory_then_theReasonIsReportedForThatModule() {
        ScriptedDataSource owner = ScriptedDataSource.answering((sql, parameters) -> {
            if (sql.contains(TABLE_EXISTS)) {
                if (parameters.equals(List.of("flyway_fhhs_history"))) {
                    throw new SQLException("permission denied for schema information_schema");
                }
                return rows(new Object[]{1});
            }
            if (sql.contains("flyway_admin_history")) {
                throw new SQLException("permission denied for table flyway_admin_history");
            }
            return rows(new Object[]{"19", "then", true});
        });

        List<MigrationReport> reports = diagnostics(ScriptedDataSource.refusing("x"), owner).migrationHistory();

        assertEquals(new MigrationReport("Survey", "flyway_history", "19", "then", true, true), reports.get(0));
        assertEquals(new MigrationReport("Admin", "flyway_admin_history", null,
                "permission denied for table flyway_admin_history", false, true), reports.get(1));
        assertEquals(new MigrationReport("Family History", "flyway_fhhs_history", null,
                "permission denied for schema information_schema", false, true), reports.get(2));
    }

    @Test
    void given_noOwnerConnection_when_migrationHistory_then_everyModuleCarriesTheReason() {
        List<MigrationReport> reports = diagnostics(ScriptedDataSource.answeringRow("x", "y"),
                ScriptedDataSource.refusing("connection refused")).migrationHistory();

        assertEquals(List.of(
                new MigrationReport("Survey", "flyway_history", null, "connection refused", false, false),
                new MigrationReport("Admin", "flyway_admin_history", null, "connection refused", false, false),
                new MigrationReport("Family History", "flyway_fhhs_history", null, "connection refused", false, false)),
                reports);
    }

    // ---- durable-key sequences (UC-022 step 4) -----------------------------------------------------

    @Test
    void given_theSequence_when_checkDurableSequences_then_up() {
        ScriptedDataSource owner = ScriptedDataSource.answering((sql, parameters) -> rows(new Object[]{1}));

        CheckResult result = diagnostics(ScriptedDataSource.refusing("x"), owner).checkDurableSequences();

        assertEquals(CheckResult.Status.UP, result.status());
        assertEquals("Kimball durable-key sequences", result.name());
        assertEquals("survey.questions_durable_seq exists", result.detail());
        assertTrue(owner.executed.getFirst().contains(SEQUENCE_EXISTS + " WHERE sequence_schema = 'survey' AND sequence_name = ?"));
        assertEquals(List.of(List.of("questions_durable_seq")), owner.bound);
        assertPlausible(result);
        assertEquals(1, owner.connectionsClosed);
    }

    @Test
    void given_noSequence_when_checkDurableSequences_then_downNamingIt() {
        ScriptedDataSource owner = ScriptedDataSource.answering((sql, parameters) -> rows());

        CheckResult result = diagnostics(ScriptedDataSource.refusing("x"), owner).checkDurableSequences();

        assertEquals(CheckResult.Status.DOWN, result.status());
        assertPlausible(result);
        assertTrue(result.detail().startsWith("survey.questions_durable_seq is missing: the Survey module has not applied"),
                result.detail());
    }

    @Test
    void given_noOwnerConnection_when_checkDurableSequences_then_downWithTheReason() {
        CheckResult result = diagnostics(ScriptedDataSource.refusing("x"), ScriptedDataSource.refusing("connection refused"))
                .checkDurableSequences();

        assertEquals(CheckResult.down("Kimball durable-key sequences", "connection refused", result.durationMs()), result);
        assertPlausible(result);
    }
}
