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

import com.elicitsoftware.test.PostgresTestResource;
import com.vaadin.browserless.quarkus.QuarkusBrowserlessTest;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.Location;
import com.vaadin.flow.router.NavigationTrigger;
import com.vaadin.flow.server.VaadinRequest;
import com.vaadin.flow.server.VaadinService;
import com.vaadin.flow.server.auth.NavigationAccessControl;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Browserless UI test for {@link LoginView}.
 *
 * <p>Traceability: UC-001 (Authenticate and Access the Admin Console). {@code LoginView} is a
 * dummy intermediary view: its only real behavior is in
 * {@link LoginView#beforeEnter(com.vaadin.flow.router.BeforeEnterEvent)}, which restores the
 * originally requested route saved by {@link NavigationAccessControl} before the OIDC round-trip,
 * or falls back to the application root when no such route was saved.</p>
 *
 * <p>A real {@link BeforeEnterEvent} is constructed directly via its
 * {@code (Router, NavigationTrigger, Location, Class, UI, List)} constructor, and
 * {@code event.forwardTo(String)} is exercised through {@code beforeEnter}. The browserless
 * environment does register this project's real routes, so {@code forwardTo("/departments")} and
 * {@code forwardTo("/")} both resolve to their real view classes ({@code DepartmentsView} and
 * {@code SearchView}, which is mapped to the root route) rather than landing in the "unknown
 * forward" state — the assertions read {@link BeforeEnterEvent#hasForwardTarget()} /
 * {@link BeforeEnterEvent#getForwardTargetType()} accordingly.</p>
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class LoginViewTest extends QuarkusBrowserlessTest {

    private BeforeEnterEvent newEvent() {
        return new BeforeEnterEvent(
                VaadinService.getCurrent().getRouter(),
                NavigationTrigger.PROGRAMMATIC,
                new Location("login"),
                LoginView.class,
                UI.getCurrent(),
                List.of());
    }

    /**
     * UC-001: when {@code NavigationAccessControl} previously stashed the user's originally
     * requested route in the session, returning from OIDC forwards them back to it.
     */
    @Test
    void restoresStoredRedirect() {
        VaadinRequest.getCurrent().getWrappedSession()
                .setAttribute(NavigationAccessControl.SESSION_STORED_REDIRECT, "/departments");

        BeforeEnterEvent event = newEvent();
        new LoginView().beforeEnter(event);

        assertTrue(event.hasForwardTarget(), "expected LoginView to forward to the stored route");
        assertEquals(DepartmentsView.class, event.getForwardTargetType());
    }

    /**
     * UC-001: with no stored redirect (e.g. a user manually navigating to /login while already
     * authenticated), LoginView falls back to the application root.
     */
    @Test
    void fallsBackToRootWhenNoStoredRedirect() {
        BeforeEnterEvent event = newEvent();
        new LoginView().beforeEnter(event);

        assertTrue(event.hasForwardTarget(), "expected LoginView to forward somewhere");
        assertEquals(SearchView.class, event.getForwardTargetType(),
                "the root route \"\" is mapped to SearchView");
    }
}
