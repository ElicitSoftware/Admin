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

import com.elicitsoftware.admin.util.BrandUtil;
import com.elicitsoftware.diagnostics.BrandDiagnostics;
import com.elicitsoftware.diagnostics.BuildInfo;
import com.elicitsoftware.diagnostics.CheckResult;
import com.elicitsoftware.diagnostics.ConnectionChecks;
import com.elicitsoftware.diagnostics.DatabaseDiagnostics;
import com.elicitsoftware.diagnostics.MailerDiagnostics;
import com.elicitsoftware.diagnostics.RequiredConfigCheck;
import com.elicitsoftware.diagnostics.SetupWarnings;
import com.elicitsoftware.diagnostics.SetupWarnings.Remedy;
import com.elicitsoftware.diagnostics.SetupWarnings.Warning;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.ListItem;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.html.UnorderedList;
import com.vaadin.flow.router.RouterLink;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UC-020: View System Overview — {@link SystemOverviewView} built in a plain browserless UI with
 * stand-ins for every diagnostic it reads. Covers the build facts and uptime wording, the health
 * of each area with its link to the detail screen, required settings shown as present or absent
 * (BR-082), and the outstanding setup items with their remedies (BR-083).
 */
class SystemOverviewViewUnitTest extends PlainSmallViewTestSupport {

    private Duration uptime = Duration.ofMinutes(5).plusSeconds(7);
    private RuntimeException brandFailure;
    private RuntimeException connectionsFailure;
    private List<ConnectionChecks.Target> targets = List.of(
            new ConnectionChecks.Target("Identity provider", "oidc", "http://kc", ConnectionChecks.Kind.OIDC_DISCOVERY),
            new ConnectionChecks.Target("Report service", "r", "http://r", ConnectionChecks.Kind.HTTP));
    private MailerDiagnostics.MailerReport mail = new MailerDiagnostics.MailerReport("ops@example.org", "smtp", 25,
            false, "OPTIONAL", null, false, false, false);
    private List<Warning> warnings = List.of();

    private SystemOverviewView view;

    @BeforeEach
    void build() {
        view = new SystemOverviewView();
        view.buildInfo = new BuildInfo() {
            @Override
            public String applicationName() {
                return "elicit-admin";
            }

            @Override
            public String version() {
                return "3.1.0";
            }

            @Override
            public String buildTimestamp() {
                return "2026-10-01T10:00:00Z";
            }

            @Override
            public List<String> profiles() {
                return List.of("prod", "kc");
            }

            @Override
            public Instant startedAt() {
                return Instant.parse("2026-10-20T08:00:00Z");
            }

            @Override
            public Duration uptime() {
                return uptime;
            }
        };
        view.database = new DatabaseDiagnostics() {
            @Override
            public ConnectionReport checkApplicationConnection() {
                return new ConnectionReport("application", "survey", CheckResult.up("app", "PostgreSQL 17", 3), "survey");
            }

            @Override
            public ConnectionReport checkOwnerConnection() {
                return new ConnectionReport("owner", "owner", CheckResult.down("owner", "auth failed", 4), null);
            }
        };
        view.brand = new BrandDiagnostics() {
            @Override
            public BrandReport report() {
                if (brandFailure != null) {
                    throw brandFailure;
                }
                return new BrandReport("/brand", "brand", false, true, null, null, null, null,
                        new BrandUtil.BrandInfo("default-brand", "Elicit", "/logo.png", "c"), List.of());
            }
        };
        view.mailer = new MailerDiagnostics() {
            @Override
            public MailerReport report() {
                return mail;
            }
        };
        view.connections = new ConnectionChecks() {
            @Override
            public List<Target> targets() {
                if (connectionsFailure != null) {
                    throw connectionsFailure;
                }
                return targets;
            }
        };
        view.requiredConfig = new RequiredConfigCheck() {
            @Override
            public List<RequiredSetting> requiredSettings() {
                return List.of(new RequiredSetting("quarkus.datasource.password", "ELICIT_DB_PASSWORD", "db", true),
                        new RequiredSetting("quarkus.mailer.from", "quarkus.mailer.from", "mail", false));
            }
        };
        view.setupWarnings = new SetupWarnings() {
            @Override
            public List<Warning> warnings() {
                return warnings;
            }
        };
    }

    private SystemOverviewView init() {
        view.init();
        UI.getCurrent().add(view);
        return view;
    }

    @SuppressWarnings("unchecked")
    private <T> Grid<T> grid(String id) {
        return (Grid<T>) byId(view, id, Grid.class);
    }

    @Test
    void given_healthyDeployment_when_opened_then_headingsAndBuildFactsAreShown() {
        init();

        List<Component> parts = view.getChildren().toList();
        assertEquals(t("systemOverviewView.title"), ((H3) parts.get(0)).getText());
        assertEquals(t("systemOverviewView.intro"), ((Paragraph) parts.get(1)).getText());
        assertEquals(t("systemOverviewView.running"), ((H4) parts.get(2)).getText());
        assertEquals(t("systemOverviewView.health"), ((H4) parts.get(4)).getText());
        assertEquals(t("systemOverviewView.requiredSettings"), ((H4) parts.get(6)).getText());
        assertEquals(t("systemOverviewView.outstanding"), ((H4) parts.get(8)).getText());
        assertTrue(view.hasClassName("system-view"));
        assertTrue(view.isPadding());
        assertEquals(t("systemOverviewView.pageTitle"), view.getPageTitle());

        Grid<SystemOverviewView.Row> build = grid(SystemOverviewView.BUILD_GRID_ID);
        assertSummaryGrid(build);
        assertSummaryGrid(grid(SystemOverviewView.HEALTH_GRID_ID));
        assertSummaryGrid(grid(SystemOverviewView.CONFIG_GRID_ID));
        assertEquals(List.of(t("system.grid.item"), t("system.grid.value")), headers(build));
        assertEquals(List.of(t("systemOverviewView.row.application"), t("systemOverviewView.row.version"),
                t("systemOverviewView.row.built"), t("systemOverviewView.row.profile"),
                t("systemOverviewView.row.started"), t("systemOverviewView.row.uptime")), column(build, 0));
        assertEquals(List.of("elicit-admin", "3.1.0", "2026-10-01T10:00:00Z", "prod, kc", "2026-10-20T08:00:00Z",
                t("systemOverviewView.uptime.minutes", "5", "7")), column(build, 1));
    }

    @Test
    void given_mixedHealth_when_opened_then_eachAreaShowsItsStateDetailAndLink() {
        init();

        Grid<SystemOverviewView.Area> health = grid(SystemOverviewView.HEALTH_GRID_ID);
        assertEquals(List.of(t("systemOverviewView.grid.area"), t("system.grid.state"), t("system.grid.detail")),
                headers(health));
        List<Component> links = componentColumn(health, 0);
        assertEquals(List.of(t("systemOverviewView.area.applicationDatabase"), t("systemOverviewView.area.ownerDatabase"),
                        t("systemOverviewView.area.branding"), t("systemOverviewView.area.email"),
                        t("systemOverviewView.area.connections")),
                links.stream().map(l -> ((RouterLink) l).getText()).toList());
        assertEquals(List.of("system/database", "system/database", "system/branding", "system/email",
                "system/connections"), links.stream().map(l -> ((RouterLink) l).getHref()).toList());
        assertEquals(List.of(t("system.badge.ok") + "|success", t("system.badge.failed") + "|error",
                        t("system.badge.ok") + "|success", t("system.badge.ok") + "|success",
                        t("system.badge.notChecked") + "|contrast"),
                componentColumn(health, 1).stream().map(PlainSmallViewTestSupport::badge).toList());
        assertEquals(List.of("PostgreSQL 17", "auth failed", t("systemBrandingView.summary.local", "brand"),
                        t("systemEmailView.summary.from", "ops@example.org", "smtp:25"),
                        t("systemOverviewView.connectionsSummary.many", "2")),
                column(health, 2));
    }

    @Test
    void given_failingBrandAndConnectionsAndNoSender_when_opened_then_thoseAreasAreDown() {
        brandFailure = new IllegalStateException("brand dir unreadable");
        connectionsFailure = new IllegalStateException("no database");
        mail = new MailerDiagnostics.MailerReport(null, "smtp", 25, false, "OPTIONAL", null, false, false, false);
        init();

        Grid<SystemOverviewView.Area> health = grid(SystemOverviewView.HEALTH_GRID_ID);
        List<String> states = componentColumn(health, 1).stream().map(PlainSmallViewTestSupport::badge).toList();
        String failed = t("system.badge.failed") + "|error";
        assertEquals(List.of(failed, failed, failed), states.subList(2, 5));
        assertEquals(List.of("brand dir unreadable", t("systemEmailView.summary.noSender"), "no database"),
                column(health, 2).subList(2, 5));
    }

    @Test
    void given_oneConnectionTarget_when_opened_then_summaryUsesTheSingular() {
        targets = List.of(targets.get(0));
        init();

        assertEquals(t("systemOverviewView.connectionsSummary.one", "1"),
                column(grid(SystemOverviewView.HEALTH_GRID_ID), 2).get(4));
    }

    @Test
    void given_requiredSettings_when_opened_then_presenceIsShownNotTheValue() {
        init();

        Grid<RequiredConfigCheck.RequiredSetting> config = grid(SystemOverviewView.CONFIG_GRID_ID);
        assertEquals(List.of(t("systemOverviewView.grid.setting"), t("systemOverviewView.grid.suppliedBy"),
                t("systemOverviewView.grid.purpose"), t("system.grid.state")), headers(config));
        assertEquals(List.of("quarkus.datasource.password", "quarkus.mailer.from"), column(config, 0));
        assertEquals(List.of("ELICIT_DB_PASSWORD", "quarkus.mailer.from"), column(config, 1));
        assertEquals(List.of("db", "mail"), column(config, 2));
        assertEquals(List.of(t("system.badge.present") + "|success", t("system.badge.absent") + "|error"),
                componentColumn(config, 3).stream().map(PlainSmallViewTestSupport::badge).toList());
    }

    @Test
    void given_nothingOutstanding_when_opened_then_doneParagraphIsShown() {
        init();

        Paragraph done = byId(view, SystemOverviewView.NO_WARNINGS_ID, Paragraph.class);
        assertEquals(t("systemOverviewView.nothingOutstanding"), done.getText());
    }

    @Test
    void given_warnings_when_rendered_then_eachIsListedWithItsRemedyLink() {
        warnings = List.of(new Warning("seeded", Remedy.USERS),
                new Warning("no survey", Remedy.APPLY_SURVEY_DEFINITION),
                new Warning("no department", Remedy.DEPARTMENTS),
                new Warning("no template", Remedy.MESSAGE_TEMPLATES),
                new Warning("legacy", Remedy.NONE));
        init();

        UnorderedList list = byId(view, SystemOverviewView.WARNINGS_ID, UnorderedList.class);
        List<ListItem> items = list.getChildren().map(ListItem.class::cast).toList();
        assertEquals(5, items.size());
        assertEquals(List.of("seeded ", "no survey ", "no department ", "no template ", "legacy "),
                items.stream().map(i -> ((Span) i.getComponentAt(0)).getText()).toList());
        assertEquals(List.of(t("systemOverviewView.remedy.users"), t("systemOverviewView.remedy.applySurveyDefinition"),
                        t("systemOverviewView.remedy.departments"), t("systemOverviewView.remedy.messageTemplates")),
                items.subList(0, 4).stream().map(i -> ((RouterLink) i.getComponentAt(1)).getText()).toList());
        assertEquals(List.of("users", "survey-apply", "departments", "message-templates"),
                items.subList(0, 4).stream().map(i -> ((RouterLink) i.getComponentAt(1)).getHref()).toList());
        assertEquals(1, items.get(4).getComponentCount(), "a warning without a remedy has no link");
    }

    @Test
    void given_uptimes_when_formatted_then_theLargestUnitLeads() {
        assertEquals(t("systemOverviewView.uptime.days", "2", "3", "4"),
                view.formatUptime(Duration.ofDays(2).plusHours(3).plusMinutes(4)));
        assertEquals(t("systemOverviewView.uptime.days", "1", "0", "0"), view.formatUptime(Duration.ofDays(1)));
        assertEquals(t("systemOverviewView.uptime.hours", "1", "2"),
                view.formatUptime(Duration.ofHours(1).plusMinutes(2).plusSeconds(3)));
        assertEquals(t("systemOverviewView.uptime.minutes", "0", "59"), view.formatUptime(Duration.ofSeconds(59)));
    }
}
