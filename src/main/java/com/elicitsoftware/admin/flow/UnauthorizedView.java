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

package com.elicitsoftware.admin.flow;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;

@Route("unauthorized")
@PermitAll
public class UnauthorizedView extends VerticalLayout {

    public UnauthorizedView() {
        setAlignItems(Alignment.CENTER);
        setJustifyContentMode(JustifyContentMode.CENTER);
        setSizeFull();
        
        Div container = new Div();
        container.addClassNames("lumo-utility-background-color-contrast-5", 
                               "lumo-utility-border-radius-m",
                               "lumo-utility-padding-l");
        container.setWidth("400px");
        
        H1 title = new H1("Access Restricted");
        title.addClassName("lumo-utility-text-color-error");
        
        Paragraph message = new Paragraph(
            "You are authenticated but do not have the required permissions to access this application. " +
            "Please contact your administrator to request access with the 'elicit_user' or 'elicit_admin' role."
        );
        
        Button logoutButton = new Button("Logout", event -> {
            getUI().ifPresent(ui -> ui.getPage().setLocation("/logout"));
        });
        logoutButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        logoutButton.getStyle().set("margin-top", "1rem");
        
        container.add(title, message, logoutButton);
        add(container);
    }
}
