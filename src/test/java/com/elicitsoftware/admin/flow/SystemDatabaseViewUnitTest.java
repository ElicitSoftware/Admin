package com.elicitsoftware.admin.flow;

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

import com.elicitsoftware.diagnostics.CheckResult;
import com.elicitsoftware.diagnostics.DatabaseDiagnostics;
import com.elicitsoftware.diagnostics.DatabaseDiagnostics.ConnectionReport;
import com.elicitsoftware.diagnostics.DatabaseDiagnostics.MigrationReport;
import com.elicitsoftware.service.DefaultAccountCheck;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Paragraph;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UC-022: Diagnose Database — {@link SystemDatabaseView} in a plain browserless UI with stand-ins
 * for the database diagnostics and the seeded-account check. Covers both connections checked
 * (BR-089), the migration state of every module read as it is (BR-090) and the content facts,
 * including the seeded default accounts (UC-021).
 */
class SystemDatabaseViewUnitTest extends PlainSmallViewTestSupport {

    private List<String> seeded = List.of();
    private SystemDatabaseView view;

    private SystemDatabaseView init() {
        view = new SystemDatabaseView();
        view.database = new DatabaseDiagnostics() {
            @Override
            public ConnectionReport checkApplicationConnection() {
                return new ConnectionReport("application", "survey", CheckResult.up("app", "PostgreSQL 17", 3), "survey");
            }

            @Override
            public ConnectionReport checkOwnerConnection() {
                return new ConnectionReport("owner", "owner", CheckResult.down("owner", "password rejected", 41), null);
            }

            @Override
            public List<MigrationReport> migrationHistory() {
                return List.of(
                        new MigrationReport("Survey", "flyway_history", "3.12", "2026-10-01 10:00", true, true),
                        new MigrationReport("Admin", "flyway_admin_history", "3.4", null, false, true),
                        new MigrationReport("Family History", "flyway_fhhs_history", null, null, true, true),
                        new MigrationReport("Other", "flyway_x_history", null, null, false, false));
            }

            @Override
            public CheckResult checkDurableSequences() {
                return CheckResult.up("seq", "survey.questions_durable_seq exists", 0);
            }

            @Override
            public long surveyCount() {
                return 2;
            }

            @Override
            public long userCount() {
                return 7;
            }
        };
        view.defaultAccounts = new DefaultAccountCheck() {
            @Override
            public List<String> findDefaultAccounts() {
                return seeded;
            }
        };
        view.init();
        UI.getCurrent().add(view);
        return view;
    }

    @SuppressWarnings("unchecked")
    private <T> Grid<T> grid(String id) {
        return (Grid<T>) byId(view, id, Grid.class);
    }

    @Test
    void given_twoConnections_when_opened_then_bothAreShownWithStateUserAndRoundTrip() {
        init();

        List<Component> parts = view.getChildren().toList();
        assertEquals(t("systemDatabaseView.title"), ((H3) parts.get(0)).getText());
        assertEquals(t("systemDatabaseView.intro"), ((Paragraph) parts.get(1)).getText());
        assertEquals(t("systemDatabaseView.connections"), ((H4) parts.get(2)).getText());
        assertEquals(t("systemDatabaseView.migrations"), ((H4) parts.get(4)).getText());
        assertEquals(t("systemDatabaseView.content"), ((H4) parts.get(6)).getText());
        assertTrue(view.hasClassName("system-view"));
        assertEquals(t("systemDatabaseView.pageTitle"), view.getPageTitle());

        Grid<ConnectionReport> connections = grid(SystemDatabaseView.CONNECTIONS_GRID_ID);
        assertSummaryGrid(connections);
        assertSummaryGrid(grid(SystemDatabaseView.MIGRATIONS_GRID_ID));
        assertSummaryGrid(grid(SystemDatabaseView.CONTENT_GRID_ID));
        assertEquals(List.of(t("systemDatabaseView.grid.connection"), t("systemDatabaseView.grid.configuredUser"),
                t("system.grid.state"), t("systemDatabaseView.grid.connectedAs"), t("system.grid.detail"),
                t("systemDatabaseView.grid.roundTrip")), headers(connections));
        assertEquals(List.of(t("systemDatabaseView.connection.application"), t("systemDatabaseView.connection.owner")),
                column(connections, 0));
        assertEquals(List.of("survey", "owner"), column(connections, 1));
        assertEquals(List.of(t("system.badge.ok") + "|success", t("system.badge.failed") + "|error"),
                componentColumn(connections, 2).stream().map(PlainSmallViewTestSupport::badge).toList());
        assertEquals(List.of("survey", ""), column(connections, 3));
        assertEquals(List.of("PostgreSQL 17", "password rejected"), column(connections, 4));
        assertEquals(List.of(t("system.milliseconds", "3"), t("system.milliseconds", "41")), column(connections, 5));
    }

    @Test
    void given_migrationHistories_when_opened_then_eachModuleShowsVersionAndState() {
        init();

        Grid<MigrationReport> migrations = grid(SystemDatabaseView.MIGRATIONS_GRID_ID);
        assertEquals(List.of(t("systemDatabaseView.grid.module"), t("systemDatabaseView.grid.historyTable"),
                t("systemDatabaseView.grid.latestVersion"), t("systemDatabaseView.grid.applied"),
                t("system.grid.state")), headers(migrations));
        assertEquals(List.of("Survey", "Admin", "Family History", "Other"), column(migrations, 0));
        assertEquals(List.of("flyway_history", "flyway_admin_history", "flyway_fhhs_history", "flyway_x_history"),
                column(migrations, 1));
        assertEquals(List.of("3.12", "3.4", t("systemDatabaseView.version.none"),
                t("systemDatabaseView.version.notInstalled")), column(migrations, 2));
        assertEquals(List.of("2026-10-01 10:00", "", "", ""), column(migrations, 3));
        assertEquals(List.of(t("system.badge.ok") + "|success", t("system.badge.failed") + "|error",
                        t("systemDatabaseView.badge.empty") + "|contrast",
                        t("systemDatabaseView.badge.notInstalled") + "|contrast"),
                componentColumn(migrations, 4).stream().map(PlainSmallViewTestSupport::badge).toList());
    }

    @Test
    void given_noSeededAccounts_when_opened_then_contentSaysNone() {
        init();

        Grid<SystemDatabaseView.Row> content = grid(SystemDatabaseView.CONTENT_GRID_ID);
        assertEquals(List.of(t("system.grid.item"), t("system.grid.value")), headers(content));
        assertEquals(List.of(t("systemDatabaseView.row.durableSequences"), t("systemDatabaseView.row.surveysInstalled"),
                t("systemDatabaseView.row.consoleUsers"), t("systemDatabaseView.row.seededAccounts")), column(content, 0));
        assertEquals(List.of("survey.questions_durable_seq exists", "2", "7", t("systemDatabaseView.seededAccounts.none")),
                column(content, 1));
    }

    @Test
    void given_seededAccounts_when_opened_then_contentNamesThem() {
        seeded = List.of("admin", "user");
        init();

        assertEquals(t("systemDatabaseView.seededAccounts.present", "admin, user"),
                column(grid(SystemDatabaseView.CONTENT_GRID_ID), 1).get(3));
    }
}
