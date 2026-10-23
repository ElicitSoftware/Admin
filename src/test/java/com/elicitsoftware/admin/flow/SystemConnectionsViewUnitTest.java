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
import com.elicitsoftware.diagnostics.ConnectionChecks;
import com.elicitsoftware.diagnostics.ConnectionChecks.Kind;
import com.elicitsoftware.diagnostics.ConnectionChecks.Target;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UC-025: Check Connections — {@link SystemConnectionsView} in a plain browserless UI with a
 * stand-in for the connection checks. Covers the targets read from where the runtime reads them
 * (BR-098), the bounded probe named in the intro (BR-097), nothing probed until asked (BR-096),
 * and one row or every row checked on request.
 */
class SystemConnectionsViewUnitTest extends PlainSmallViewTestSupport {

    private static final Target OIDC = new Target("Identity provider", "quarkus.oidc.auth-server-url",
            "http://kc/realms/elicit", Kind.OIDC_DISCOVERY);
    private static final Target REPORT = new Target("Report service", "Pedigree", "http://pedigree:8080", Kind.HTTP);

    private List<Target> targets = List.of(OIDC, REPORT);
    private final List<String> checked = new ArrayList<>();
    private SystemConnectionsView view;

    private SystemConnectionsView init() {
        view = new SystemConnectionsView();
        view.connections = new ConnectionChecks() {
            @Override
            public List<Target> targets() {
                return targets;
            }

            @Override
            public CheckResult check(Target target) {
                checked.add(target.name());
                return target == OIDC ? CheckResult.up("oidc", "discovery served", 17)
                        : CheckResult.down("report", "connection refused", 5000);
            }
        };
        view.init();
        UI.getCurrent().add(view);
        return view;
    }

    @SuppressWarnings("unchecked")
    private Grid<SystemConnectionsView.TargetRow> grid() {
        return byId(view, SystemConnectionsView.GRID_ID, Grid.class);
    }

    @Test
    void given_targets_when_opened_then_eachIsListedUncheckedWithItsOwnCheckButton() {
        init();

        List<Component> parts = view.getChildren().toList();
        assertEquals(t("systemConnectionsView.title"), ((H3) parts.get(0)).getText());
        assertEquals(t("systemConnectionsView.intro", "5"), ((Paragraph) parts.get(1)).getText());
        Button checkAll = (Button) parts.get(2);
        assertEquals(SystemConnectionsView.CHECK_ALL_ID, checkAll.getId().orElseThrow());
        assertEquals(t("systemConnectionsView.checkAll"), checkAll.getText());
        assertTrue(checkAll.getThemeNames().contains(ButtonVariant.LUMO_PRIMARY.getVariantName()));
        assertTrue(view.hasClassName("system-view"));
        assertEquals(t("systemConnectionsView.pageTitle"), view.getPageTitle());
        assertEquals(List.of(), checked, "nothing is probed until asked");

        Grid<SystemConnectionsView.TargetRow> grid = grid();
        assertSummaryGrid(grid);
        assertEquals(List.of(t("systemConnectionsView.grid.dependency"), t("systemConnectionsView.grid.source"),
                t("systemConnectionsView.grid.address"), t("system.grid.state"), t("system.grid.detail"),
                t("systemConnectionsView.grid.time"), ""), headers(grid));
        assertEquals(List.of("Identity provider", "Report service"), column(grid, 0));
        assertEquals(List.of("quarkus.oidc.auth-server-url", "Pedigree"), column(grid, 1));
        assertEquals(List.of("http://kc/realms/elicit", "http://pedigree:8080"), column(grid, 2));
        assertEquals(List.of("", ""), componentColumn(grid, 3).stream().map(c -> ((Span) c).getText()).toList());
        assertEquals(List.of("", ""), column(grid, 4));
        assertEquals(List.of("", ""), column(grid, 5));
        assertEquals(List.of(t("systemConnectionsView.check"), t("systemConnectionsView.check")),
                componentColumn(grid, 6).stream().map(c -> ((Button) c).getText()).toList());
        assertEquals(2, view.rows().size());
        assertNull(view.rows().get(0).result);
    }

    @Test
    void given_noTargets_when_opened_then_emptyNoticeIsShownAndNoGrid() {
        targets = List.of();
        init();

        Paragraph empty = byId(view, SystemConnectionsView.EMPTY_ID, Paragraph.class);
        assertEquals(t("systemConnectionsView.empty"), empty.getText());
        assertNull(byId(view, SystemConnectionsView.CHECK_ALL_ID, Button.class));
        assertEquals(3, view.getComponentCount());
    }

    @Test
    void given_openView_when_oneRowChecked_then_onlyThatRowShowsItsResult() {
        init();
        List<Object> refreshes = refreshesOf(grid());

        test((Button) test(grid()).getCellComponent(1, 6)).click();

        assertEquals(List.of("Pedigree"), checked);
        assertEquals(List.of(view.rows().get(1)), refreshes, "only the checked row is redrawn");
        Grid<SystemConnectionsView.TargetRow> grid = grid();
        assertEquals(List.of("", "connection refused"), column(grid, 4));
        assertEquals(List.of("", t("system.milliseconds", "5000")), column(grid, 5));
        assertEquals(t("system.badge.failed") + "|error", badge(test(grid).getCellComponent(1, 3)));
    }

    @Test
    void given_openView_when_checkAllClicked_then_everyRowShowsItsResult() {
        init();
        List<Object> refreshes = refreshesOf(grid());

        test(byId(view, SystemConnectionsView.CHECK_ALL_ID, Button.class)).click();

        assertEquals(List.of("all"), refreshes, "every row is redrawn");

        assertEquals(List.of("quarkus.oidc.auth-server-url", "Pedigree"), checked);
        Grid<SystemConnectionsView.TargetRow> grid = grid();
        assertEquals(List.of("discovery served", "connection refused"), column(grid, 4));
        assertEquals(List.of(t("system.milliseconds", "17"), t("system.milliseconds", "5000")), column(grid, 5));
        assertEquals(List.of(t("system.badge.ok") + "|success", t("system.badge.failed") + "|error"),
                componentColumn(grid, 3).stream().map(PlainSmallViewTestSupport::badge).toList());
    }
}
