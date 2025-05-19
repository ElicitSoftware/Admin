package com.elicitsoftware.admin;

import com.vaadin.flow.server.ServiceInitEvent;
import com.vaadin.flow.server.VaadinServiceInitListener;
import com.vaadin.flow.server.auth.NavigationAccessControl;
import jakarta.enterprise.event.Observes;

public class NavigationControlAccessCheckerInitializer implements VaadinServiceInitListener {

    private NavigationAccessControl accessControl;

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