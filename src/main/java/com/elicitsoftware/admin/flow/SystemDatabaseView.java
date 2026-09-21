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

import com.elicitsoftware.diagnostics.DatabaseDiagnostics;
import com.elicitsoftware.security.ElicitRoles;
import com.elicitsoftware.service.DefaultAccountCheck;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.theme.lumo.LumoUtility;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;

import java.util.List;

/**
 * Database diagnostics (UC-022): both connections, each module's migration state, the Kimball
 * sequences, and what is stored. Reads only (BR-090).
 */
@Route(value = "system/database", layout = MainLayout.class)
@RolesAllowed(ElicitRoles.ADMIN)
public class SystemDatabaseView extends VerticalLayout implements HasDynamicTitle {

    static final String CONNECTIONS_GRID_ID = "system-db-connections";
    static final String MIGRATIONS_GRID_ID = "system-db-migrations";
    static final String CONTENT_GRID_ID = "system-db-content";

    record Row(String label, String value) {
    }

    @Inject
    DatabaseDiagnostics database;

    @Inject
    DefaultAccountCheck defaultAccounts;

    public SystemDatabaseView() {
        // Size to the content, not the viewport, so the bottom padding follows the last grid.
        setWidthFull();
        setPadding(true);
        addClassName(LumoUtility.Padding.Bottom.XLARGE);
    }

    @PostConstruct
    void init() {
        add(new H3(getTranslation("systemDatabaseView.title")));
        add(new Paragraph(getTranslation("systemDatabaseView.intro")));

        add(new H4(getTranslation("systemDatabaseView.connections")));
        Grid<DatabaseDiagnostics.ConnectionReport> connections = new Grid<>();
        connections.setId(CONNECTIONS_GRID_ID);
        connections.setItems(List.of(database.checkApplicationConnection(), database.checkOwnerConnection()));
        // The report labels the connection "application" or "owner"; the label is the key suffix.
        connections.addColumn(r -> getTranslation("systemDatabaseView.connection." + r.label()))
                .setHeader(getTranslation("systemDatabaseView.grid.connection")).setAutoWidth(true);
        connections.addColumn(DatabaseDiagnostics.ConnectionReport::configuredUser)
                .setHeader(getTranslation("systemDatabaseView.grid.configuredUser")).setAutoWidth(true);
        connections.addComponentColumn(r -> SystemBadges.status(r.result()))
                .setHeader(getTranslation("system.grid.state")).setAutoWidth(true);
        connections.addColumn(r -> r.connectedAs() != null ? r.connectedAs() : "")
                .setHeader(getTranslation("systemDatabaseView.grid.connectedAs")).setAutoWidth(true);
        connections.addColumn(r -> r.result().detail())
                .setHeader(getTranslation("system.grid.detail")).setFlexGrow(1);
        connections.addColumn(r -> SystemBadges.millis(r.result().durationMs()))
                .setHeader(getTranslation("systemDatabaseView.grid.roundTrip")).setAutoWidth(true);
        connections.setAllRowsVisible(true);
        connections.addThemeVariants(GridVariant.LUMO_COMPACT, GridVariant.LUMO_WRAP_CELL_CONTENT);
        add(connections);

        add(new H4(getTranslation("systemDatabaseView.migrations")));
        Grid<DatabaseDiagnostics.MigrationReport> migrations = new Grid<>();
        migrations.setId(MIGRATIONS_GRID_ID);
        migrations.setItems(database.migrationHistory());
        migrations.addColumn(DatabaseDiagnostics.MigrationReport::module)
                .setHeader(getTranslation("systemDatabaseView.grid.module")).setAutoWidth(true);
        migrations.addColumn(DatabaseDiagnostics.MigrationReport::table)
                .setHeader(getTranslation("systemDatabaseView.grid.historyTable")).setAutoWidth(true);
        migrations.addColumn(r -> !r.installed() ? getTranslation("systemDatabaseView.version.notInstalled")
                        : r.version() != null ? r.version() : getTranslation("systemDatabaseView.version.none"))
                .setHeader(getTranslation("systemDatabaseView.grid.latestVersion")).setAutoWidth(true);
        migrations.addColumn(r -> r.installedOn() != null ? r.installedOn() : "")
                .setHeader(getTranslation("systemDatabaseView.grid.applied")).setFlexGrow(1);
        migrations.addComponentColumn(r -> !r.installed()
                        ? SystemBadges.badge(getTranslation("systemDatabaseView.badge.notInstalled"), "contrast")
                        : r.version() == null
                        ? SystemBadges.badge(getTranslation("systemDatabaseView.badge.empty"), "contrast")
                        : r.success() ? SystemBadges.badge(getTranslation("system.badge.ok"), "success")
                        : SystemBadges.badge(getTranslation("system.badge.failed"), "error"))
                .setHeader(getTranslation("system.grid.state")).setAutoWidth(true);
        migrations.setAllRowsVisible(true);
        migrations.addThemeVariants(GridVariant.LUMO_COMPACT, GridVariant.LUMO_WRAP_CELL_CONTENT);
        add(migrations);

        add(new H4(getTranslation("systemDatabaseView.content")));
        List<String> seeded = defaultAccounts.findDefaultAccounts();
        Grid<Row> content = new Grid<>();
        content.setId(CONTENT_GRID_ID);
        content.setItems(List.of(
                new Row(getTranslation("systemDatabaseView.row.durableSequences"), database.checkDurableSequences().detail()),
                new Row(getTranslation("systemDatabaseView.row.surveysInstalled"), Long.toString(database.surveyCount())),
                new Row(getTranslation("systemDatabaseView.row.consoleUsers"), Long.toString(database.userCount())),
                new Row(getTranslation("systemDatabaseView.row.seededAccounts"),
                        seeded.isEmpty() ? getTranslation("systemDatabaseView.seededAccounts.none")
                                : getTranslation("systemDatabaseView.seededAccounts.present", String.join(", ", seeded)))));
        content.addColumn(Row::label).setHeader(getTranslation("system.grid.item")).setAutoWidth(true);
        content.addColumn(Row::value).setHeader(getTranslation("system.grid.value")).setFlexGrow(1);
        content.setAllRowsVisible(true);
        content.addThemeVariants(GridVariant.LUMO_COMPACT, GridVariant.LUMO_WRAP_CELL_CONTENT);
        add(content);
    }

    @Override
    public String getPageTitle() {
        return getTranslation("systemDatabaseView.pageTitle");
    }
}
