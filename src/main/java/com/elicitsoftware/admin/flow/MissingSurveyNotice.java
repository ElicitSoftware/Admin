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

import com.elicitsoftware.admin.i18n.Translations;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.flow.theme.lumo.LumoUtility;

/**
 * On-screen notices for a deployment that has no survey installed (UC-019).
 * <p>
 * Two shapes of the same message: a {@link #banner(boolean)} the layout carries on
 * every screen, and an {@link #emptyState(boolean)} a view puts where its own content
 * would otherwise be. Both are built here so the wording and the decision about who
 * gets a route to the remedy stay in one place.
 * <p>
 * The {@code canApplyDefinition} flag implements UC-019 BR-078: the link to Apply
 * Survey Definition is offered only to an administrator who holds the role that
 * screen requires. Everyone else gets the explanation without a route that would
 * fail on arrival.
 */
final class MissingSurveyNotice {

    /**
     * Element id of the layout-wide banner, for tests and styling.
     */
    static final String BANNER_ID = "missing-survey-banner";

    /**
     * Element id of the per-view empty state, for tests and styling.
     */
    static final String EMPTY_STATE_ID = "missing-survey-empty-state";

    private MissingSurveyNotice() {
        // Static factory only
    }

    /**
     * Builds the banner shown above every console screen while no survey is installed.
     *
     * @param canApplyDefinition whether the current user may reach Apply Survey Definition
     * @return the banner component
     */
    static Component banner(boolean canApplyDefinition) {
        Div banner = new Div();
        banner.setId(BANNER_ID);
        banner.setWidthFull();
        banner.addClassNames(LumoUtility.Background.CONTRAST_5, LumoUtility.Padding.MEDIUM,
                LumoUtility.Display.FLEX, LumoUtility.AlignItems.CENTER, LumoUtility.Gap.SMALL);
        // role=status rather than alert: the condition is informational and persistent, so a
        // screen reader should announce it without interrupting what the user is doing.
        banner.getElement().setAttribute("role", "status");

        banner.add(VaadinIcon.WARNING.create());
        banner.add(new Span(Translations.get("missingSurveyNotice.headline")));
        banner.add(remedy(canApplyDefinition));
        return banner;
    }

    /**
     * Builds the message a view shows in place of content that needs an installed survey.
     *
     * @param canApplyDefinition whether the current user may reach Apply Survey Definition
     * @return the empty-state component
     */
    static Component emptyState(boolean canApplyDefinition) {
        VerticalLayout emptyState = new VerticalLayout();
        emptyState.setId(EMPTY_STATE_ID);
        emptyState.setPadding(true);
        emptyState.setSpacing(false);
        emptyState.getElement().setAttribute("role", "status");

        Span headline = new Span(Translations.get("missingSurveyNotice.headline"));
        headline.addClassName(LumoUtility.FontWeight.SEMIBOLD);
        emptyState.add(headline);
        emptyState.add(remedy(canApplyDefinition));
        return emptyState;
    }

    /**
     * The remedy half of the message: a link for those who can act on it, plain text
     * for everyone else (UC-019 BR-078).
     */
    private static Component remedy(boolean canApplyDefinition) {
        if (canApplyDefinition) {
            return new RouterLink(Translations.get("missingSurveyNotice.adminRemedy"), SurveyDefinitionApplyView.class);
        }
        return new Span(Translations.get("missingSurveyNotice.nonAdminRemedy"));
    }
}
