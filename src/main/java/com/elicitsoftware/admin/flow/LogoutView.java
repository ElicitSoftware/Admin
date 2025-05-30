package com.elicitsoftware.admin.flow;


import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.VaadinRequest;
import com.vaadin.flow.server.VaadinSession;
import com.vaadin.flow.server.auth.NavigationAccessControl;
import jakarta.annotation.security.PermitAll;

/**
 * This is a dummy view to help in login process.
 * It is actually never shown. Quarkus OIDC integration
 * redirects to OIDC server for actual login, but this
 * view is used to redirect to the original URL after login.
 */
@Route("logout")
@PermitAll
public class LogoutView extends VerticalLayout implements BeforeEnterObserver {

    public LogoutView() {
        add("Admin Logout :-)");
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        VaadinSession.getCurrent().close();
        com.vaadin.flow.component.UI.getCurrent().getPage().setLocation("/logout");
        event.forwardTo("/");
    }
}
