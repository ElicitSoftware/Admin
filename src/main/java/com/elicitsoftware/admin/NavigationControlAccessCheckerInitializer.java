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

package com.elicitsoftware.admin;

import com.vaadin.flow.server.ServiceInitEvent;
import com.vaadin.flow.server.VaadinServiceInitListener;
import com.vaadin.flow.server.auth.NavigationAccessControl;
import jakarta.enterprise.event.Observes;

/**
 * Registers a {@link NavigationAccessControl} as a {@code BeforeEnterListener}
 * on every Vaadin UI so that {@code @RolesAllowed}/{@code @PermitAll}/
 * {@code @AnonymousAllowed}/{@code @DenyAll} annotations on {@code @Route}
 * views are actually enforced. Quarkus's Vaadin integration does not wire
 * this up automatically (unlike Spring Boot's VaadinSecurityConfigurer), so
 * it must be done explicitly via a VaadinServiceInitListener.
 */
public class NavigationControlAccessCheckerInitializer implements VaadinServiceInitListener {

    private final NavigationAccessControl accessControl;

    public NavigationControlAccessCheckerInitializer() {
        accessControl = new NavigationAccessControl();
        accessControl.setLoginView("login");
    }

    @Override
    public void serviceInit(@Observes ServiceInitEvent serviceInitEvent) {
        serviceInitEvent.getSource().addUIInitListener(uiInitEvent -> {
            uiInitEvent.getUI().addBeforeEnterListener(accessControl);
        });
    }
}
