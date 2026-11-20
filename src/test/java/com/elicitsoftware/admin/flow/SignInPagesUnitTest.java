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

import com.elicitsoftware.model.User;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.server.VaadinRequest;
import com.vaadin.flow.server.VaadinSession;
import com.vaadin.flow.server.auth.NavigationAccessControl;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UC-001: Authenticate and Access the Admin Console — the pages around signing in, in a plain
 * browserless UI: {@link UnauthorizedView} and its 403 error variant {@link AccessDeniedErrorView}
 * (an authenticated user without an Elicit role, BR-001), the {@link LoginView} return to the
 * originally requested route, the {@link LogoutView} redirect, and the session-held user that
 * {@link UiSessionLogin} serves (BR-003).
 */
class SignInPagesUnitTest extends PlainSmallViewTestSupport {

    private static void assertUnauthorizedLayout(UnauthorizedView view) {
        assertEquals(FlexComponent.Alignment.CENTER, view.getAlignItems());
        assertEquals(FlexComponent.JustifyContentMode.CENTER, view.getJustifyContentMode());
        assertEquals("100%", view.getWidth());
        assertEquals("100%", view.getHeight());
        List<Component> top = view.getChildren().toList();
        assertEquals(1, top.size());
        Div card = (Div) top.get(0);
        assertTrue(card.hasClassName("unauthorized-card"));
        assertEquals("400px", card.getWidth());
        List<Component> parts = card.getChildren().toList();
        assertEquals(3, parts.size());
        assertEquals(t("unauthorizedView.title"), ((H1) parts.get(0)).getText());
        assertEquals(t("unauthorizedView.message"), ((Paragraph) parts.get(1)).getText());
        Button logout = (Button) parts.get(2);
        assertEquals(t("unauthorizedView.logout"), logout.getText());
        assertTrue(logout.getThemeNames().contains(ButtonVariant.LUMO_PRIMARY.getVariantName()));
    }

    private static Button logoutButton(UnauthorizedView view) {
        Div card = (Div) view.getChildren().findFirst().orElseThrow();
        return (Button) card.getChildren().toList().get(2);
    }

    private static String pendingJs() {
        StringBuilder sb = new StringBuilder();
        UI.getCurrent().getInternals().dumpPendingJavaScriptInvocations().forEach(i ->
                sb.append(i.getInvocation().getExpression()).append(' ')
                        .append(i.getInvocation().getParameters()).append('\n'));
        return sb.toString();
    }

    @Test
    void given_userWithoutRole_when_unauthorizedViewBuilt_then_cardExplainsAndOffersLogout() {
        assertUnauthorizedLayout(new UnauthorizedView());
    }

    @Test
    void given_unauthorizedViewAttached_when_logoutClicked_then_browserIsSentToLogout() {
        UnauthorizedView view = new UnauthorizedView();
        UI.getCurrent().add(view);
        pendingJs();

        test(logoutButton(view)).click();

        assertTrue(pendingJs().contains("/logout"), "the page location must be set to /logout");
    }

    @Test
    void given_accessDenied_when_errorViewBuilt_then_itRendersTheUnauthorizedPageWith403() {
        AccessDeniedErrorView view = new AccessDeniedErrorView();

        assertUnauthorizedLayout(view);
        assertEquals(403, view.setErrorParameter(null, null));
    }

    @Test
    void given_storedRedirect_when_loginEntered_then_forwardsToTheStoredRoute() {
        VaadinRequest.getCurrent().getWrappedSession()
                .setAttribute(NavigationAccessControl.SESSION_STORED_REDIRECT, "users");
        LoginView view = new LoginView();
        BeforeEnterEvent event = beforeEnter(LoginView.class, Map.of());

        view.beforeEnter(event);

        assertEquals("users", event.getForwardUrl());
        assertEquals(t("loginView.redirecting"), view.getElement().getText());
    }

    @Test
    void given_noStoredRedirect_when_loginEntered_then_forwardsToTheRoot() {
        LoginView view = new LoginView();
        BeforeEnterEvent event = beforeEnter(LoginView.class, Map.of());

        view.beforeEnter(event);

        assertEquals("", event.getForwardUrl());
    }

    @Test
    void given_blankStoredRedirect_when_loginEntered_then_forwardsToTheRoot() {
        VaadinRequest.getCurrent().getWrappedSession()
                .setAttribute(NavigationAccessControl.SESSION_STORED_REDIRECT, "  ");
        LoginView view = new LoginView();
        BeforeEnterEvent event = beforeEnter(LoginView.class, Map.of());

        view.beforeEnter(event);

        assertEquals("", event.getForwardUrl());
    }

    @Test
    void given_signedInSession_when_logoutEntered_then_sessionClosesAndBrowserLeavesForLogout() {
        LogoutView view = new LogoutView();
        VaadinSession signedIn = VaadinSession.getCurrent();
        BeforeEnterEvent event = beforeEnter(LogoutView.class, Map.of());

        view.beforeEnter(event);

        assertEquals(t("logoutView.signingOut"), view.getElement().getText());
        assertNotSame(signedIn, VaadinSession.getCurrent(), "the signed-in session must be closed");
        assertTrue(pendingJs().contains("/logout"), "the browser is sent to the OIDC logout path");
    }

    @Test
    void given_sessionUser_when_getUserAsked_then_theSessionAttributeIsReturned() {
        UiSessionLogin login = new UiSessionLogin();
        assertNull(login.getUser());
        User user = new User();
        user.setUsername("ada");
        VaadinSession.getCurrent().setAttribute("user", user);

        assertSame(user, login.getUser());
    }
}
