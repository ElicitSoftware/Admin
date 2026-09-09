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
import com.vaadin.flow.server.VaadinService;
import com.vaadin.flow.server.VaadinSession;
import com.vaadin.flow.server.VaadinSessionState;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Browserless UI test for {@link LogoutView}.
 *
 * <p>Traceability: UC-001 (Authenticate and Access the Admin Console). {@code LogoutView} is a
 * dummy intermediary view: its only real behavior is in
 * {@link LogoutView#beforeEnter(com.vaadin.flow.router.BeforeEnterEvent)}, which closes the
 * current {@link VaadinSession} and then redirects the browser to the OIDC "/logout" endpoint.</p>
 *
 * <p>Vaadin defers the actual teardown of a closed session to the end of the request, so the
 * session object itself is still reachable immediately after {@code close()} returns. This test
 * therefore asserts on {@link VaadinSession#getState()} having transitioned away from
 * {@link VaadinSessionState#OPEN} rather than asserting the session is gone.</p>
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class LogoutViewTest extends QuarkusBrowserlessTest {

    /** UC-001: entering /logout closes the Vaadin session. */
    @Test
    void closesVaadinSession() {
        BeforeEnterEvent event = new BeforeEnterEvent(
                VaadinService.getCurrent().getRouter(),
                NavigationTrigger.PROGRAMMATIC,
                new Location("logout"),
                LogoutView.class,
                UI.getCurrent(),
                List.of());

        // Capture the session before beforeEnter runs: the browserless mock installs a new
        // "current" session once setLocation() triggers navigation, so VaadinSession.getCurrent()
        // called afterward would inspect a different (fresh, still-OPEN) session, not the one
        // LogoutView actually closed.
        VaadinSession session = VaadinSession.getCurrent();
        new LogoutView().beforeEnter(event);

        assertNotEquals(VaadinSessionState.OPEN, session.getState(),
                "LogoutView should close the Vaadin session, moving it out of the OPEN state");
    }
}
