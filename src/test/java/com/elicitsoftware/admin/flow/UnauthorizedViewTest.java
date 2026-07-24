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

import com.elicitsoftware.test.PostgresTestResource;
import com.vaadin.browserless.quarkus.QuarkusBrowserlessTest;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Paragraph;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Browserless UI test for {@link UnauthorizedView}.
 *
 * <p>Traceability: UC-001 (Authenticate and Access the Admin Console), alternative
 * flow A1 — an authenticated user without an Elicit role is sent to the
 * "unauthorized" view. This test builds the view in the browserless Vaadin
 * environment and asserts, from the user's perspective, that they see the
 * "Access Restricted" heading, guidance to request the {@code elicit_user} /
 * {@code elicit_admin} role, and a Logout button.</p>
 *
 * <p>The view is instantiated and attached to the test {@link UI} rather than
 * reached via {@code navigate(...)}: under {@code @QuarkusTest} the Vaadin route
 * registry is not populated by the browserless route scanner, so navigation by
 * route is unavailable. Attaching the component still runs it through the real
 * Vaadin server-side lifecycle and lets us query its rendered components with the
 * framework's {@code find(...)} API. {@code @QuarkusTest} boots the app (using the
 * shared PostgreSQL container via {@link PostgresTestResource}), and
 * {@link QuarkusBrowserlessTest} sets up the Vaadin session/UI in the same JVM.</p>
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class UnauthorizedViewTest extends QuarkusBrowserlessTest {

    private UnauthorizedView attachView() {
        UnauthorizedView view = new UnauthorizedView();
        UI.getCurrent().add(view);
        return view;
    }

    /** UC-001 A1: the view renders the "Access Restricted" heading. */
    @Test
    void showsAccessRestrictedHeading() {
        UnauthorizedView view = attachView();
        H1 heading = find(H1.class, view).single();
        assertEquals("Access Restricted", heading.getText());
    }

    /** UC-001 A1: the message tells the user which roles to request. */
    @Test
    void explainsRequiredRoles() {
        UnauthorizedView view = attachView();
        Paragraph message = find(Paragraph.class, view).single();
        assertTrue(message.getText().contains("elicit_user"));
        assertTrue(message.getText().contains("elicit_admin"));
    }

    /** UC-001 A1: a Logout action is offered so the user can sign out and retry. */
    @Test
    void offersLogoutButton() {
        UnauthorizedView view = attachView();
        Button logout = find(Button.class, view).single();
        assertEquals("Logout", logout.getText());
    }
}
