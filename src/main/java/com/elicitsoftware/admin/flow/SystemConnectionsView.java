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
import com.elicitsoftware.security.ElicitRoles;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.theme.lumo.LumoUtility;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.List;

/**
 * Connection checks (UC-025): every outbound dependency with a bounded, harmless probe
 * (BR-096, BR-097). Nothing is probed until asked.
 */
@Route(value = "system/connections", layout = MainLayout.class)
@RolesAllowed(ElicitRoles.ADMIN)
public class SystemConnectionsView extends VerticalLayout implements HasDynamicTitle {

    static final String GRID_ID = "system-connections-grid";
    static final String CHECK_ALL_ID = "system-connections-check-all";
    static final String EMPTY_ID = "system-connections-empty";

    /** One target and its latest result, if any. */
    static final class TargetRow {
        final ConnectionChecks.Target target;
        CheckResult result;

        TargetRow(ConnectionChecks.Target target) {
            this.target = target;
        }
    }

    @Inject
    ConnectionChecks connections;

    private final Grid<TargetRow> grid = new Grid<>();
    private final List<TargetRow> rows = new ArrayList<>();

    public SystemConnectionsView() {
        // Size to the content, not the viewport, so the bottom padding follows the last grid.
        setWidthFull();
        setPadding(true);
        addClassName(LumoUtility.Padding.Bottom.XLARGE);
    }

    @PostConstruct
    void init() {
        add(new H3(getTranslation("systemConnectionsView.title")));
        add(new Paragraph(getTranslation("systemConnectionsView.intro",
                Long.toString(ConnectionChecks.TIMEOUT.toSeconds()))));

        for (ConnectionChecks.Target target : connections.targets()) {
            rows.add(new TargetRow(target));
        }
        if (rows.isEmpty()) {
            Paragraph empty = new Paragraph(getTranslation("systemConnectionsView.empty"));
            empty.setId(EMPTY_ID);
            add(empty);
            return;
        }

        Button checkAll = new Button(getTranslation("systemConnectionsView.checkAll"), e -> checkAll());
        checkAll.setId(CHECK_ALL_ID);
        checkAll.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        add(checkAll);

        grid.setId(GRID_ID);
        grid.setItems(rows);
        grid.addColumn(r -> r.target.group())
                .setHeader(getTranslation("systemConnectionsView.grid.dependency")).setAutoWidth(true).setFlexGrow(0);
        grid.addColumn(r -> r.target.name()).setHeader(getTranslation("systemConnectionsView.grid.source")).setFlexGrow(2);
        grid.addColumn(r -> r.target.address()).setHeader(getTranslation("systemConnectionsView.grid.address")).setFlexGrow(3);
        grid.addComponentColumn(this::state).setHeader(getTranslation("system.grid.state")).setAutoWidth(true);
        grid.addColumn(r -> r.result != null ? r.result.detail() : "")
                .setHeader(getTranslation("system.grid.detail")).setFlexGrow(3);
        grid.addColumn(r -> r.result != null ? SystemBadges.millis(r.result.durationMs()) : "")
                .setHeader(getTranslation("systemConnectionsView.grid.time")).setAutoWidth(true);
        grid.addComponentColumn(r -> new Button(getTranslation("systemConnectionsView.check"), e -> check(r)))
                .setHeader("").setAutoWidth(true);
        grid.setAllRowsVisible(true);
        grid.addThemeVariants(GridVariant.LUMO_COMPACT, GridVariant.LUMO_WRAP_CELL_CONTENT);
        add(grid);
    }

    private Component state(TargetRow row) {
        return row.result == null ? new Span("") : SystemBadges.status(row.result);
    }

    void check(TargetRow row) {
        row.result = connections.check(row.target);
        grid.getDataProvider().refreshItem(row);
    }

    void checkAll() {
        for (TargetRow row : rows) {
            row.result = connections.check(row.target);
        }
        grid.getDataProvider().refreshAll();
    }

    List<TargetRow> rows() {
        return rows;
    }

    @Override
    public String getPageTitle() {
        return getTranslation("systemConnectionsView.pageTitle");
    }
}
