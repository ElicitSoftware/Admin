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
import com.vaadin.flow.theme.lumo.LumoUtility;
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
        // Size to the content, not the viewport, so the bottom padding follows the last grid.
        setWidthFull();
        setPadding(true);
        addClassName(LumoUtility.Padding.Bottom.XLARGE);
    }

    @PostConstruct
    void init() {
        add(new H3(getTranslation("systemOverviewView.title")));
        add(new Paragraph(getTranslation("systemOverviewView.intro")));

        add(new H4(getTranslation("systemOverviewView.running")));
        add(rows(BUILD_GRID_ID, List.of(
                new Row(getTranslation("systemOverviewView.row.application"), buildInfo.applicationName()),
                new Row(getTranslation("systemOverviewView.row.version"), buildInfo.version()),
                new Row(getTranslation("systemOverviewView.row.built"), buildInfo.buildTimestamp()),
                new Row(getTranslation("systemOverviewView.row.profile"), String.join(", ", buildInfo.profiles())),
                new Row(getTranslation("systemOverviewView.row.started"), buildInfo.startedAt().toString()),
                new Row(getTranslation("systemOverviewView.row.uptime"), formatUptime(buildInfo.uptime())))));

        add(new H4(getTranslation("systemOverviewView.health")));
        add(areas(List.of(
                new Area(getTranslation("systemOverviewView.area.applicationDatabase"),
                        database.checkApplicationConnection().result(), SystemDatabaseView.class),
                new Area(getTranslation("systemOverviewView.area.ownerDatabase"),
                        database.checkOwnerConnection().result(), SystemDatabaseView.class),
                new Area(getTranslation("systemOverviewView.area.branding"), brandSummary(), SystemBrandingView.class),
                new Area(getTranslation("systemOverviewView.area.email"), mailerSummary(), SystemEmailView.class),
                new Area(getTranslation("systemOverviewView.area.connections"), connectionsSummary(),
                        SystemConnectionsView.class))));

        add(new H4(getTranslation("systemOverviewView.requiredSettings")));
        Grid<RequiredConfigCheck.RequiredSetting> config = new Grid<>();
        config.setId(CONFIG_GRID_ID);
        config.setItems(requiredConfig.requiredSettings());
        config.addColumn(RequiredConfigCheck.RequiredSetting::property)
                .setHeader(getTranslation("systemOverviewView.grid.setting")).setAutoWidth(true);
        config.addColumn(RequiredConfigCheck.RequiredSetting::envVar)
                .setHeader(getTranslation("systemOverviewView.grid.suppliedBy")).setAutoWidth(true);
        config.addColumn(RequiredConfigCheck.RequiredSetting::purpose)
                .setHeader(getTranslation("systemOverviewView.grid.purpose")).setFlexGrow(1);
        config.addComponentColumn(s -> SystemBadges.presence(s.present()))
                .setHeader(getTranslation("system.grid.state")).setAutoWidth(true);
        config.setAllRowsVisible(true);
        config.addThemeVariants(GridVariant.LUMO_COMPACT, GridVariant.LUMO_WRAP_CELL_CONTENT);
        add(config);

        add(new H4(getTranslation("systemOverviewView.outstanding")));
        add(renderWarnings(setupWarnings.warnings()));
    }

    Component renderWarnings(List<SetupWarnings.Warning> warnings) {
        if (warnings.isEmpty()) {
            Paragraph done = new Paragraph(getTranslation("systemOverviewView.nothingOutstanding"));
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

    private Component remedy(SetupWarnings.Remedy remedy) {
        return switch (remedy) {
            case USERS -> new RouterLink(getTranslation("systemOverviewView.remedy.users"), UsersView.class);
            case APPLY_SURVEY_DEFINITION -> new RouterLink(getTranslation("systemOverviewView.remedy.applySurveyDefinition"),
                    SurveyDefinitionApplyView.class);
            case DEPARTMENTS -> new RouterLink(getTranslation("systemOverviewView.remedy.departments"), DepartmentsView.class);
            case MESSAGE_TEMPLATES -> new RouterLink(getTranslation("systemOverviewView.remedy.messageTemplates"),
                    MessageTemplatesView.class);
            case NONE -> null;
        };
    }

    private CheckResult brandSummary() {
        try {
            return CheckResult.up("Branding", brand.report().summary(), 0); // i18n:ignore
        } catch (RuntimeException e) {
            return CheckResult.down("Branding", e.getMessage(), 0); // i18n:ignore
        }
    }

    private CheckResult mailerSummary() {
        MailerDiagnostics.MailerReport report = mailer.report();
        return report.canSend()
                ? CheckResult.up("Email", report.summary(), 0) // i18n:ignore
                : CheckResult.down("Email", report.summary(), 0); // i18n:ignore
    }

    private CheckResult connectionsSummary() {
        try {
            int count = connections.targets().size();
            return CheckResult.unknown("Connections", getTranslation(count == 1 // i18n:ignore
                    ? "systemOverviewView.connectionsSummary.one" : "systemOverviewView.connectionsSummary.many",
                    Integer.toString(count)));
        } catch (RuntimeException e) {
            return CheckResult.down("Connections", e.getMessage(), 0); // i18n:ignore
        }
    }

    private Grid<Row> rows(String id, List<Row> rows) {
        Grid<Row> grid = new Grid<>();
        grid.setId(id);
        grid.setItems(rows);
        grid.addColumn(Row::label).setHeader(getTranslation("system.grid.item")).setAutoWidth(true);
        grid.addColumn(Row::value).setHeader(getTranslation("system.grid.value")).setFlexGrow(1);
        grid.setAllRowsVisible(true);
        grid.addThemeVariants(GridVariant.LUMO_COMPACT, GridVariant.LUMO_WRAP_CELL_CONTENT);
        return grid;
    }

    private Grid<Area> areas(List<Area> areas) {
        Grid<Area> grid = new Grid<>();
        grid.setId(HEALTH_GRID_ID);
        grid.setItems(areas);
        grid.addComponentColumn(a -> new RouterLink(a.name(), a.screen()))
                .setHeader(getTranslation("systemOverviewView.grid.area")).setAutoWidth(true);
        grid.addComponentColumn(a -> SystemBadges.status(a.result()))
                .setHeader(getTranslation("system.grid.state")).setAutoWidth(true);
        grid.addColumn(a -> a.result().detail()).setHeader(getTranslation("system.grid.detail")).setFlexGrow(1);
        grid.setAllRowsVisible(true);
        grid.addThemeVariants(GridVariant.LUMO_COMPACT, GridVariant.LUMO_WRAP_CELL_CONTENT);
        return grid;
    }

    String formatUptime(Duration uptime) {
        String days = Long.toString(uptime.toDays());
        String hours = Long.toString(uptime.toHoursPart());
        String minutes = Long.toString(uptime.toMinutesPart());
        if (uptime.toDays() > 0) {
            return getTranslation("systemOverviewView.uptime.days", days, hours, minutes);
        }
        if (uptime.toHoursPart() > 0) {
            return getTranslation("systemOverviewView.uptime.hours", hours, minutes);
        }
        return getTranslation("systemOverviewView.uptime.minutes", minutes, Integer.toString(uptime.toSecondsPart()));
    }

    @Override
    public String getPageTitle() {
        return getTranslation("systemOverviewView.pageTitle");
    }
}
