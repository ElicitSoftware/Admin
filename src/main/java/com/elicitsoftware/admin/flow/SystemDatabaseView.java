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
        add(new H3("Database"));
        add(new Paragraph("The application connection serves the console; the owner connection runs the "
                + "migrations. Readiness probes only the first, so a wrong owner password shows up here "
                + "and nowhere else."));

        add(new H4("Connections"));
        Grid<DatabaseDiagnostics.ConnectionReport> connections = new Grid<>();
        connections.setId(CONNECTIONS_GRID_ID);
        connections.setItems(List.of(database.checkApplicationConnection(), database.checkOwnerConnection()));
        connections.addColumn(DatabaseDiagnostics.ConnectionReport::label).setHeader("Connection").setAutoWidth(true);
        connections.addColumn(DatabaseDiagnostics.ConnectionReport::configuredUser).setHeader("Configured user").setAutoWidth(true);
        connections.addComponentColumn(r -> SystemBadges.status(r.result())).setHeader("State").setAutoWidth(true);
        connections.addColumn(r -> r.connectedAs() != null ? r.connectedAs() : "").setHeader("Connected as").setAutoWidth(true);
        connections.addColumn(r -> r.result().detail()).setHeader("Detail").setFlexGrow(1);
        connections.addColumn(r -> r.result().durationMs() + " ms").setHeader("Round trip").setAutoWidth(true);
        connections.setAllRowsVisible(true);
        connections.addThemeVariants(GridVariant.LUMO_COMPACT, GridVariant.LUMO_WRAP_CELL_CONTENT);
        add(connections);

        add(new H4("Migrations"));
        Grid<DatabaseDiagnostics.MigrationReport> migrations = new Grid<>();
        migrations.setId(MIGRATIONS_GRID_ID);
        migrations.setItems(database.migrationHistory());
        migrations.addColumn(DatabaseDiagnostics.MigrationReport::module).setHeader("Module").setAutoWidth(true);
        migrations.addColumn(DatabaseDiagnostics.MigrationReport::table).setHeader("History table").setAutoWidth(true);
        migrations.addColumn(r -> !r.installed() ? "not installed" : r.version() != null ? r.version() : "none")
                .setHeader("Latest version").setAutoWidth(true);
        migrations.addColumn(r -> r.installedOn() != null ? r.installedOn() : "").setHeader("Applied").setFlexGrow(1);
        migrations.addComponentColumn(r -> !r.installed() ? SystemBadges.badge("Not installed", "badge contrast")
                : r.version() == null ? SystemBadges.badge("Empty", "badge contrast")
                : r.success() ? SystemBadges.badge("OK", "badge success") : SystemBadges.badge("Failed", "badge error"))
                .setHeader("State").setAutoWidth(true);
        migrations.setAllRowsVisible(true);
        migrations.addThemeVariants(GridVariant.LUMO_COMPACT, GridVariant.LUMO_WRAP_CELL_CONTENT);
        add(migrations);

        add(new H4("Content"));
        List<String> seeded = defaultAccounts.findDefaultAccounts();
        Grid<Row> content = new Grid<>();
        content.setId(CONTENT_GRID_ID);
        content.setItems(List.of(
                new Row("Kimball durable-key sequences", database.checkDurableSequences().detail()),
                new Row("Surveys installed", Long.toString(database.surveyCount())),
                new Row("Console users", Long.toString(database.userCount())),
                new Row("Seeded default accounts still present",
                        seeded.isEmpty() ? "none" : String.join(", ", seeded) + " (rename them, see Users)")));
        content.addColumn(Row::label).setHeader("Item").setAutoWidth(true);
        content.addColumn(Row::value).setHeader("Value").setFlexGrow(1);
        content.setAllRowsVisible(true);
        content.addThemeVariants(GridVariant.LUMO_COMPACT, GridVariant.LUMO_WRAP_CELL_CONTENT);
        add(content);
    }

    @Override
    public String getPageTitle() {
        return "System Database";
    }
}
