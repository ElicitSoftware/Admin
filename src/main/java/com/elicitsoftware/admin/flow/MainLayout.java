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

import com.elicitsoftware.model.User;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.AfterNavigationEvent;
import com.vaadin.flow.router.AfterNavigationListener;
import com.vaadin.flow.server.VaadinSession;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import org.eclipse.microprofile.jwt.JsonWebToken;

/**
 * The MainLayout class serves as the primary layout structure for the application,
 * managing the organization of headers, navigation bars, and main content.
 * <p>
 * This class extends {@code AppLayout} and implements {@code AfterNavigationListener}
 * to provide a container framework for the application's UI and handle navigation events
 * for improved user experience.
 */

public class MainLayout extends AppLayout implements AfterNavigationListener {

    @Inject
    JsonWebToken accessToken;

    // Add configurable name for the main header.
    // Add configurable icon for the tab.

    VaadinSession session = VaadinSession.getCurrent();

    @Inject
    UiSessionLogin uiSessionLogin;
    User user;

    /**
     * Initializes the main layout components after the construction of the class.
     * <p>
     * This method is annotated with {@code @PostConstruct}, ensuring it is called
     * automatically after the dependency injection is completed. It performs the following actions:
     * - Creates and configures the header section of the layout by calling {@code createHeader()}.
     * - Creates and adds a navigation bar to the drawer section of the layout using {@code createNavBar()}.
     * - Configures and sets the main content area using the {@code mainView} field.
     */
    @PostConstruct
    public void init() {
        user = uiSessionLogin.getUser();
        createHeader();
        createNavBar();
    }

    /**
     * Creates and adds a header section in the layout.
     * <p>
     * This method initializes a drawer toggle and a title anchor that links
     * to the application's root path ("/") and displays the software name.
     * The title is styled with a larger font size and zero-margin spacing.
     * Both the toggle and title are added to the navigation bar.
     */
    private void createHeader() {
        DrawerToggle toggle = new DrawerToggle();
        Anchor title = new Anchor("/", getTranslation("software.name"));
        title.getStyle().set("font-size", "var(--lumo-font-size-l)")
                .set("margin", "20");
        addToNavbar(toggle, title);
    }

    /**
     * Creates and adds a navigation bar to the drawer section of the layout.
     * <p>
     * This method initializes a {@code SideNav} component using the {@code getsideNav} method,
     * wraps it with a {@code Scroller} for scrollable functionality, and applies a small padding
     * style using {@code LumoUtility.Padding.SMALL}. The resulting scroller is then added to the
     * layout drawer.
     */
    private void createNavBar() {
        // Create a layout to hold the buttons
        VerticalLayout buttonLayout = new VerticalLayout();
        buttonLayout.setPadding(false);
        buttonLayout.setSpacing(true);

        // Search Subjects Button
        Button searchButton = new Button(
                "Search Subjects",
                VaadinIcon.SEARCH.create()
        );
        searchButton.addClickListener(e -> {
            VaadinSession.getCurrent().close();
            com.vaadin.flow.component.UI.getCurrent().navigate(SearchView.class);
        });
        searchButton.setWidthFull();
        buttonLayout.add(searchButton);

        // Register Subject Button
        Button registerButton = new Button(
                "Register Subject",
                VaadinIcon.USERS.create()
        );
        registerButton.addClickListener(e -> {
            VaadinSession.getCurrent().close();
            com.vaadin.flow.component.UI.getCurrent().navigate(RegisterView.class);
        });
        registerButton.setWidthFull();
        buttonLayout.add(registerButton);

        // Message Templates Button (Admin only)
        if (uiSessionLogin.hasRole("admin")) {
            Button messageTemplatesButton = new Button(
                    "Message Templates",
                    VaadinIcon.ENVELOPE_OPEN.create()
            );
            messageTemplatesButton.addClickListener(e -> {
                com.vaadin.flow.component.UI.getCurrent().navigate(MessageTemplatesView.class);
            });
            messageTemplatesButton.setWidthFull();
            buttonLayout.add(messageTemplatesButton);
        }

        // Logout Button
        Button logoutButton = new Button(
                getTranslation("sideNav.logout"),
                VaadinIcon.UNLINK.create()
        );
        logoutButton.addClickListener(e -> {
            VaadinSession.getCurrent().close();
            com.vaadin.flow.component.UI.getCurrent().getPage().setLocation("/logout");
        });
        logoutButton.setWidthFull();
        buttonLayout.add(logoutButton);

        addToDrawer(buttonLayout);
    }

    //These two functions were added to make sure the top of the page is in view after navigation
    @Override
    protected void onAttach(AttachEvent attachEvent) {
        getUI().ifPresent(ui -> ui.addAfterNavigationListener(this));
    }

    @Override
    public void afterNavigation(AfterNavigationEvent event) {
        getContent().scrollIntoView();
    }
}
