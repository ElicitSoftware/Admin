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

import com.elicitsoftware.service.DefaultAccountCheck;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.router.RouterLink;

import java.util.List;

/**
 * The console-wide warning shown to administrators while the seeded default accounts still
 * exist (UC-021 step 4). Same shape as {@link MissingSurveyNotice}: informational, persistent,
 * never blocking (BR-087).
 */
final class DefaultAccountNotice {

    static final String BANNER_ID = "default-account-banner";

    private DefaultAccountNotice() {
        // Static factory only
    }

    static Component banner(List<String> accounts) {
        Div banner = new Div();
        banner.setId(BANNER_ID);
        banner.setWidthFull();
        banner.addClassName("console-notice");
        banner.getElement().setAttribute("role", "status");

        banner.add(VaadinIcon.WARNING.create());
        banner.add(new Span(DefaultAccountCheck.instruction(accounts) + " "));
        RouterLink remedy = new RouterLink("Open Users", UsersView.class);
        banner.add(remedy);
        return banner;
    }
}
