package com.elicitsoftware.admin.flow;

/*-
 * ***LICENSE_START***
 * Elicit Survey
 * %%
 * Copyright (C) 2025 The Regents of the University of Michigan - Rogel Cancer Center
 * %%
 * PolyForm Noncommercial License 1.0.0
 * <https://polyformproject.org/licenses/noncommercial/1.0.0>
 * ***LICENSE_END***
 */

import com.elicitsoftware.analytics.AnalyticsConfig;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.component.dependency.NpmPackage;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.dom.DomEvent;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.theme.lumo.LumoUtility;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;

/**
 * The Analytics screen (UC-020): the Survey Operations dashboard embedded from Apache
 * Superset, plus an "Open in Superset" link to the full tool with Keycloak single sign-on.
 *
 * <p>The embedding runs in the browser: {@code frontend/analytics/superset-embed.js} loads
 * the Superset embedded SDK, fetches a guest token from Admin's
 * {@code /api/secured/analytics/guest-token} endpoint (BR-081), and mounts the dashboard
 * iframe in {@link #mount}. When analytics is not configured for this deployment the view
 * explains that instead (UC-020 A1, BR-082); when the dashboard cannot be loaded it says so
 * and keeps the direct link (A4).</p>
 */
@Route(value = "analytics", layout = MainLayout.class)
@RolesAllowed("elicit_analytics")
@PageTitle("Analytics")
@NpmPackage(value = "@superset-ui/embedded-sdk", version = "0.4.0")
@JsModule("./analytics/superset-embed.js")
public class AnalyticsView extends VerticalLayout {

    /** Where the browser-side script mounts the dashboard iframe. */
    static final String MOUNT_ID = "analytics-dashboard";
    /** Text shown when the deployment has no analytics configuration (UC-020 A1). */
    static final String NOT_CONFIGURED_TEXT = "Analytics is not configured for this deployment.";
    /** Text shown when the dashboard could not be loaded from Superset (UC-020 A4). */
    static final String UNREACHABLE_TEXT = "The analytics service could not be reached. Try again later, or open Superset directly.";

    @Inject
    AnalyticsConfig analyticsConfig;

    private final Div mount = new Div();
    private final Paragraph notice = new Paragraph();
    private final Anchor openInSuperset = new Anchor();

    /** Creates the view; the content is built in {@link #init()} once configuration is injected. */
    public AnalyticsView() {
        setSizeFull();
        setPadding(true);
        setSpacing(false);
    }

    @PostConstruct
    void init() {
        H2 title = new H2("Analytics");
        title.addClassNames(LumoUtility.Margin.NONE);

        openInSuperset.setText("Open in Superset");
        openInSuperset.setTarget("_blank");
        openInSuperset.getElement().setAttribute("rel", "noopener");
        openInSuperset.getElement().setAttribute("router-ignore", true);
        openInSuperset.add(VaadinIcon.EXTERNAL_LINK.create());
        openInSuperset.addClassNames(LumoUtility.Display.INLINE_FLEX, LumoUtility.AlignItems.CENTER, LumoUtility.Gap.XSMALL);

        HorizontalLayout header = new HorizontalLayout(title, openInSuperset);
        header.setWidthFull();
        header.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        header.setAlignItems(FlexComponent.Alignment.BASELINE);

        notice.addClassNames(LumoUtility.TextColor.SECONDARY);
        notice.getElement().setAttribute("role", "status");
        notice.setVisible(false);

        mount.setId(MOUNT_ID);
        mount.setSizeFull();
        mount.getStyle().set("min-height", "70vh");
        mount.getElement().addEventListener("elicit-analytics-error", this::onEmbedError)
                .addEventData("event.detail.message");

        add(header, notice, mount);
        setFlexGrow(1, mount);

        if (!analyticsConfig.isEnabled()) {
            // UC-020 A1: nothing to embed and nowhere to link.
            openInSuperset.setVisible(false);
            mount.setVisible(false);
            notice.setText(NOT_CONFIGURED_TEXT);
            notice.setVisible(true);
            return;
        }
        openInSuperset.setHref(analyticsConfig.getSupersetUrl().orElseThrow() + "/superset/dashboard/survey-operations/");
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        super.onAttach(attachEvent);
        if (!analyticsConfig.isEnabled()) {
            return;
        }
        mount.getElement().executeJs(
                "window.elicitAnalytics.embed($0, {dashboardId: $1, supersetUrl: $2, tokenUrl: $3})",
                mount.getElement(),
                analyticsConfig.getDashboardId().orElseThrow(),
                analyticsConfig.getSupersetUrl().orElseThrow(),
                "api/secured/analytics/guest-token");
    }

    private void onEmbedError(DomEvent event) {
        // UC-020 A4: keep the direct link, explain the failure in place of the dashboard.
        notice.setText(UNREACHABLE_TEXT);
        notice.setVisible(true);
        mount.setVisible(false);
    }

    /** The explanatory notice (visible only in the not-configured and unreachable cases). */
    Paragraph getNotice() {
        return notice;
    }

    /** The "Open in Superset" link. */
    Anchor getOpenInSuperset() {
        return openInSuperset;
    }
}
