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

import com.elicitsoftware.admin.i18n.Translations;
import com.elicitsoftware.model.Survey;
import com.elicitsoftware.model.User;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Probes both database connections and reads the migration state of every module that shares
 * the {@code survey} schema (UC-022).
 * <p>
 * The readiness endpoint probes the application connection only; the owner connection is the
 * one Flyway uses, so a wrong owner password is invisible to readiness (BR-089). Nothing here
 * migrates, repairs or baselines (BR-090).
 */
@ApplicationScoped
public class DatabaseDiagnostics {

    /** The Kimball durable-key sequence every module's V3 migrations depend on. */
    static final String DURABLE_SEQUENCE = "questions_durable_seq";

    /**
     * Outcome of probing one connection.
     *
     * @param label         which connection ("application" or "owner")
     * @param configuredUser the username the configuration names
     * @param result        the probe outcome; the detail carries the server version when up
     * @param connectedAs   the user the server reports, or null when the probe failed
     */
    public record ConnectionReport(String label, String configuredUser, CheckResult result, String connectedAs) {
    }

    /**
     * Latest migration of one module.
     *
     * @param module      the module that owns the history table
     * @param table       the history table name in the {@code survey} schema
     * @param version     the latest version applied, or null when the table is absent
     * @param installedOn when it was applied, as the server formats it, or null
     * @param success     whether that migration succeeded
     * @param installed   whether the history table exists at all (UC-022 A2)
     */
    public record MigrationReport(String module, String table, String version, String installedOn,
                                  boolean success, boolean installed) {
    }

    private record HistoryTable(String module, String table) {
    }

    private static final List<HistoryTable> HISTORY_TABLES = List.of(
            new HistoryTable("Survey", "flyway_history"),
            new HistoryTable("Admin", "flyway_admin_history"),
            new HistoryTable("Family History", "flyway_fhhs_history"));

    @Inject
    DataSource dataSource;

    @Inject
    @io.quarkus.agroal.DataSource("owner")
    DataSource ownerDataSource;

    @ConfigProperty(name = "quarkus.datasource.username", defaultValue = "")
    String applicationUser;

    @ConfigProperty(name = "quarkus.datasource.owner.username", defaultValue = "")
    String ownerUser;

    public DatabaseDiagnostics() {
        // CDI managed bean
    }

    public ConnectionReport checkApplicationConnection() {
        return probe("application", applicationUser, dataSource);
    }

    public ConnectionReport checkOwnerConnection() {
        return probe("owner", ownerUser, ownerDataSource);
    }

    ConnectionReport probe(String label, String configuredUser, DataSource source) {
        long start = System.nanoTime();
        try (Connection connection = source.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT current_user, version()")) {
            rs.next();
            String user = rs.getString(1);
            String version = rs.getString(2);
            long ms = (System.nanoTime() - start) / 1_000_000;
            return new ConnectionReport(label, configuredUser,
                    CheckResult.up(label + " connection", version, ms), user);
        } catch (SQLException | RuntimeException e) {
            long ms = (System.nanoTime() - start) / 1_000_000;
            return new ConnectionReport(label, configuredUser,
                    CheckResult.down(label + " connection", e.getMessage(), ms), null);
        }
    }

    /**
     * The latest migration of each module, read through the owner connection because the
     * application user is not granted the history tables.
     */
    public List<MigrationReport> migrationHistory() {
        List<MigrationReport> reports = new ArrayList<>();
        try (Connection connection = ownerDataSource.getConnection()) {
            for (HistoryTable history : HISTORY_TABLES) {
                reports.add(readLatest(connection, history));
            }
        } catch (SQLException e) {
            for (HistoryTable history : HISTORY_TABLES) {
                reports.add(new MigrationReport(history.module(), history.table(), null,
                        e.getMessage(), false, false));
            }
        }
        return reports;
    }

    private static MigrationReport readLatest(Connection connection, HistoryTable history) {
        try {
            if (!tableExists(connection, history.table())) {
                return new MigrationReport(history.module(), history.table(), null, null, false, false);
            }
            String sql = "SELECT version, installed_on::text, success FROM survey." + history.table()
                    + " WHERE version IS NOT NULL ORDER BY installed_rank DESC LIMIT 1";
            try (Statement statement = connection.createStatement();
                 ResultSet rs = statement.executeQuery(sql)) {
                if (rs.next()) {
                    return new MigrationReport(history.module(), history.table(), rs.getString(1),
                            rs.getString(2), rs.getBoolean(3), true);
                }
                return new MigrationReport(history.module(), history.table(), null, null, false, true);
            }
        } catch (SQLException e) {
            return new MigrationReport(history.module(), history.table(), null, e.getMessage(), false, true);
        }
    }

    private static boolean tableExists(Connection connection, String table) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM information_schema.tables WHERE table_schema = 'survey' AND table_name = ?")) {
            statement.setString(1, table);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next();
            }
        }
    }

    /** Whether the Kimball durable-key sequences exist (UC-022 step 4). */
    public CheckResult checkDurableSequences() {
        long start = System.nanoTime();
        try (Connection connection = ownerDataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT 1 FROM information_schema.sequences WHERE sequence_schema = 'survey' AND sequence_name = ?")) {
            statement.setString(1, DURABLE_SEQUENCE);
            try (ResultSet rs = statement.executeQuery()) {
                long ms = (System.nanoTime() - start) / 1_000_000;
                if (rs.next()) {
                    return CheckResult.up("Kimball durable-key sequences",
                            Translations.get("systemDatabaseView.durableSequences.exists", DURABLE_SEQUENCE), ms);
                }
                return CheckResult.down("Kimball durable-key sequences",
                        Translations.get("systemDatabaseView.durableSequences.missing", DURABLE_SEQUENCE), ms);
            }
        } catch (SQLException | RuntimeException e) {
            return CheckResult.down("Kimball durable-key sequences", e.getMessage(), (System.nanoTime() - start) / 1_000_000);
        }
    }

    @Transactional
    public long surveyCount() {
        return Survey.count();
    }

    @Transactional
    public long userCount() {
        return User.count();
    }
}
