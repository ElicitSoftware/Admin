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
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.router.AccessDeniedException;
import com.vaadin.flow.router.ErrorParameter;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.annotation.security.PermitAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Browserless UI test for {@link AccessDeniedErrorView}.
 *
 * <p>Traceability: UC-001 (Authenticate and Access the Admin Console), alternative flow A1.
 * This view overrides Vaadin's built-in access-denied error handler so a
 * {@code NavigationAccessControl} rejection renders the existing "Access Restricted" UI
 * ({@link UnauthorizedView}) with a 403 status, rather than a generic Vaadin error page.</p>
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class AccessDeniedErrorViewTest extends QuarkusBrowserlessTest {

    /** UC-001 A1: setErrorParameter reports a 403 status code. */
    @Test
    void reportsHttpStatus403() {
        AccessDeniedErrorView view = new AccessDeniedErrorView();
        int status = view.setErrorParameter(null, new ErrorParameter<>(AccessDeniedException.class, new AccessDeniedException()));
        assertEquals(403, status);
    }

    /** UC-001 A1: the view inherits UnauthorizedView's "Access Restricted" UI. */
    @Test
    void rendersUnauthorizedViewLayout() {
        AccessDeniedErrorView view = new AccessDeniedErrorView();
        UI.getCurrent().add(view);

        H1 heading = find(H1.class, view).single();
        assertEquals(view.getTranslation("unauthorizedView.title"), heading.getText());
    }

    /** The view remains reachable independent of any @RolesAllowed annotation. */
    @Test
    void isPermitAll() {
        assertNotNull(AccessDeniedErrorView.class.getAnnotation(PermitAll.class));
    }
}
