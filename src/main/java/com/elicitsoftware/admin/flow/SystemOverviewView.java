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

import com.elicitsoftware.diagnostics.BrandDiagnostics;
import com.elicitsoftware.diagnostics.BuildInfo;
import com.elicitsoftware.diagnostics.CheckResult;
import com.elicitsoftware.diagnostics.ConnectionChecks;
import com.elicitsoftware.diagnostics.DatabaseDiagnostics;
import com.elicitsoftware.diagnostics.MailerDiagnostics;
import com.elicitsoftware.diagnostics.RequiredConfigCheck;
import com.elicitsoftware.diagnostics.SetupWarnings;
import com.elicitsoftware.security.ElicitRoles;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.ListItem;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.html.UnorderedList;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouterLink;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;

import java.time.Duration;
import java.util.List;

/**
 * System overview (UC-020): what is running, whether it is healthy, whether every required
 * setting is present, and what setup work remains. Read-only; every summary is re-established
 * when the screen opens (BR-083).
 */
@Route(value = "system", layout = MainLayout.class)
@RolesAllowed(ElicitRoles.ADMIN)
public class SystemOverviewView extends VerticalLayout implements HasDynamicTitle {

    static final String BUILD_GRID_ID = "system-build-grid";
    static final String HEALTH_GRID_ID = "system-health-grid";
    static final String CONFIG_GRID_ID = "system-config-grid";
    static final String WARNINGS_ID = "system-warnings";
    static final String NO_WARNINGS_ID = "system-no-warnings";

    /** One label/value row. */
    record Row(String label, String value) {
    }

    /** One diagnostic area on the overview. */
    record Area(String name, CheckResult result, Class<? extends Component> screen) {
    }

    @Inject
    BuildInfo buildInfo;

    @Inject
    DatabaseDiagnostics database;

    @Inject
    BrandDiagnostics brand;

    @Inject
    MailerDiagnostics mailer;

    @Inject
    ConnectionChecks connections;

    @Inject
    RequiredConfigCheck requiredConfig;

    @Inject
    SetupWarnings setupWarnings;

    public SystemOverviewView() {
        setSizeFull();
    }

    @PostConstruct
    void init() {
        add(new H3("System Overview"));
        add(new Paragraph("What this deployment is running, whether it is healthy, and the setup work "
                + "still outstanding. Settings are shown as present or absent, never by value; they are "
                + "startup configuration and cannot be changed here."));

        add(new H4("Running"));
        add(rows(BUILD_GRID_ID, List.of(
                new Row("Application", buildInfo.applicationName()),
                new Row("Version", buildInfo.version()),
                new Row("Built", buildInfo.buildTimestamp()),
                new Row("Profile", String.join(", ", buildInfo.profiles())),
                new Row("Started", buildInfo.startedAt().toString()),
                new Row("Uptime", formatUptime(buildInfo.uptime())))));

        add(new H4("Health"));
        add(areas(List.of(
                new Area("Application database connection", database.checkApplicationConnection().result(), SystemDatabaseView.class),
                new Area("Owner database connection", database.checkOwnerConnection().result(), SystemDatabaseView.class),
                new Area("Branding", brandSummary(), SystemBrandingView.class),
                new Area("Email", mailerSummary(), SystemEmailView.class),
                new Area("Connections", connectionsSummary(), SystemConnectionsView.class))));

        add(new H4("Required settings"));
        Grid<RequiredConfigCheck.RequiredSetting> config = new Grid<>();
        config.setId(CONFIG_GRID_ID);
        config.setItems(requiredConfig.requiredSettings());
        config.addColumn(RequiredConfigCheck.RequiredSetting::property).setHeader("Setting").setAutoWidth(true);
        config.addColumn(RequiredConfigCheck.RequiredSetting::envVar).setHeader("Supplied by").setAutoWidth(true);
        config.addColumn(RequiredConfigCheck.RequiredSetting::purpose).setHeader("Purpose").setFlexGrow(1);
        config.addComponentColumn(s -> SystemBadges.presence(s.present())).setHeader("State").setAutoWidth(true);
        config.setAllRowsVisible(true);
        config.addThemeVariants(GridVariant.LUMO_COMPACT, GridVariant.LUMO_WRAP_CELL_CONTENT);
        add(config);

        add(new H4("Setup still outstanding"));
        add(renderWarnings(setupWarnings.warnings()));
    }

    Component renderWarnings(List<SetupWarnings.Warning> warnings) {
        if (warnings.isEmpty()) {
            Paragraph done = new Paragraph("Nothing outstanding.");
            done.setId(NO_WARNINGS_ID);
            return done;
        }
        UnorderedList list = new UnorderedList();
        list.setId(WARNINGS_ID);
        for (SetupWarnings.Warning warning : warnings) {
            ListItem item = new ListItem(new Span(warning.text() + " "));
            Component remedy = remedy(warning.remedy());
            if (remedy != null) {
                item.add(remedy);
            }
            list.add(item);
        }
        return list;
    }

    private static Component remedy(SetupWarnings.Remedy remedy) {
        return switch (remedy) {
            case USERS -> new RouterLink("Open Users", UsersView.class);
            case APPLY_SURVEY_DEFINITION -> new RouterLink("Apply a survey definition", SurveyDefinitionApplyView.class);
            case DEPARTMENTS -> new RouterLink("Open Departments", DepartmentsView.class);
            case MESSAGE_TEMPLATES -> new RouterLink("Open Message Templates", MessageTemplatesView.class);
            case NONE -> null;
        };
    }

    private CheckResult brandSummary() {
        try {
            return CheckResult.up("Branding", brand.report().summary(), 0);
        } catch (RuntimeException e) {
            return CheckResult.down("Branding", e.getMessage(), 0);
        }
    }

    private CheckResult mailerSummary() {
        MailerDiagnostics.MailerReport report = mailer.report();
        return report.canSend()
                ? CheckResult.up("Email", report.summary(), 0)
                : CheckResult.down("Email", report.summary(), 0);
    }

    private CheckResult connectionsSummary() {
        try {
            int count = connections.targets().size();
            return CheckResult.unknown("Connections", count + " outbound target" + (count == 1 ? "" : "s")
                    + " configured; open Connections to check them");
        } catch (RuntimeException e) {
            return CheckResult.down("Connections", e.getMessage(), 0);
        }
    }

    private static Grid<Row> rows(String id, List<Row> rows) {
        Grid<Row> grid = new Grid<>();
        grid.setId(id);
        grid.setItems(rows);
        grid.addColumn(Row::label).setHeader("Item").setAutoWidth(true);
        grid.addColumn(Row::value).setHeader("Value").setFlexGrow(1);
        grid.setAllRowsVisible(true);
        grid.addThemeVariants(GridVariant.LUMO_COMPACT, GridVariant.LUMO_WRAP_CELL_CONTENT);
        return grid;
    }

    private static Grid<Area> areas(List<Area> areas) {
        Grid<Area> grid = new Grid<>();
        grid.setId(HEALTH_GRID_ID);
        grid.setItems(areas);
        grid.addComponentColumn(a -> new RouterLink(a.name(), a.screen())).setHeader("Area").setAutoWidth(true);
        grid.addComponentColumn(a -> SystemBadges.status(a.result())).setHeader("State").setAutoWidth(true);
        grid.addColumn(a -> a.result().detail()).setHeader("Detail").setFlexGrow(1);
        grid.setAllRowsVisible(true);
        grid.addThemeVariants(GridVariant.LUMO_COMPACT, GridVariant.LUMO_WRAP_CELL_CONTENT);
        return grid;
    }

    static String formatUptime(Duration uptime) {
        long days = uptime.toDays();
        long hours = uptime.toHoursPart();
        long minutes = uptime.toMinutesPart();
        if (days > 0) {
            return days + " d " + hours + " h " + minutes + " min";
        }
        if (hours > 0) {
            return hours + " h " + minutes + " min";
        }
        return minutes + " min " + uptime.toSecondsPart() + " s";
    }

    @Override
    public String getPageTitle() {
        return "System Overview";
    }
}
