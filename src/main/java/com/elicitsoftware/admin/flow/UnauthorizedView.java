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

/**
 * View displayed when a user is authenticated but lacks required permissions.
 * <p>
 * This view informs users that they need the 'elicit_user' or 'elicit_admin' role
 * to access the application and provides a logout option.
 * </p>
 */
@Route("unauthorized")
@PermitAll
public class UnauthorizedView extends VerticalLayout {

    /**
     * Constructs the unauthorized view with informational message and logout button.
     */
    public UnauthorizedView() {
        setAlignItems(Alignment.CENTER);
        setJustifyContentMode(JustifyContentMode.CENTER);
        setSizeFull();
        
        // The card, its heading color and the button's gap come from
        // components/unauthorized-view.css.
        Div container = new Div();
        container.addClassName("unauthorized-card");
        container.setWidth("400px");

        H1 title = new H1(getTranslation("unauthorizedView.title"));
        
        Paragraph message = new Paragraph(getTranslation("unauthorizedView.message"));
        
        Button logoutButton = new Button(getTranslation("unauthorizedView.logout"), event -> {
            getUI().ifPresent(ui -> ui.getPage().setLocation("/logout"));
        });
        logoutButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        
        container.add(title, message, logoutButton);
        add(container);
    }
}
