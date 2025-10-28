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

import com.elicitsoftware.admin.util.BrandUtil;
import com.elicitsoftware.model.User;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.sidenav.SideNav;
import com.vaadin.flow.component.sidenav.SideNavItem;
import com.vaadin.flow.router.AfterNavigationEvent;
import com.vaadin.flow.router.AfterNavigationListener;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;

/**
 * The main layout component that provides the structural foundation for the entire application.
 * This layout serves as the container for all views and manages the application's navigation system.
 *
 * <p>This class extends {@link AppLayout} to provide a responsive application shell with:</p>
 * <ul>
 *   <li>A collapsible navigation drawer with role-based menu items</li>
 *   <li>A header section with application branding and drawer toggle</li>
 *   <li>Main content area where individual views are displayed</li>
 *   <li>Automatic scroll-to-top functionality after navigation</li>
 * </ul>
 *
 * <p>The layout implements {@link AfterNavigationListener} to enhance user experience
 * by automatically scrolling to the top of the page after each navigation event.</p>
 *
 * <p>Key features:</p>
 * <ul>
 *   <li><strong>Role-based navigation:</strong> Admin users see additional menu items</li>
 *   <li><strong>Responsive design:</strong> Adapts to different screen sizes</li>
 *   <li><strong>User context awareness:</strong> Navigation adapts based on user authentication</li>
 *   <li><strong>Automatic logout handling:</strong> Provides logout functionality for all users</li>
 * </ul>
 *
 * @author Elicit Software
 * @version 1.0
 * @see AppLayout
 * @see AfterNavigationListener
 * @see UiSessionLogin
 * @since 1.0
 */
@PermitAll
public class MainLayout extends AppLayout implements AfterNavigationListener {

    /**
     * Injected service for handling user session and authentication.
     */
    @Inject
    UiSessionLogin uiSessionLogin;

    /**
     * Security identity for user authentication and role checking.
     */
    @Inject
    SecurityIdentity identity;

    /**
     * The current authenticated user.
     */
    User user;

    /**
     * Default constructor for Vaadin layout component instantiation.
     * <p>
     * Creates a new MainLayout instance for the Vaadin framework.
     * This constructor is called by Vaadin during application layout
     * initialization.
     */
    public MainLayout() {
        // Default constructor for Vaadin
    }

    /**
     * Initializes the main layout components after dependency injection is complete.
     *
     * <p>This method is automatically called after construction and dependency injection.
     * It sets up the complete layout structure based on the user's authentication status
     * and role permissions:</p>
     *
     * <ol>
     *   <li>Retrieves the current user from the session</li>
     *   <li>Creates the header section with branding and navigation toggle</li>
     *   <li>Creates appropriate navigation menu based on user authentication:
     *       <ul>
     *         <li>If user is authenticated: Creates full navigation with role-based items</li>
     *         <li>If user is not authenticated: Creates minimal navigation with logout only</li>
     *       </ul>
     *   </li>
     * </ol>
     *
     * <p>The navigation structure adapts dynamically to the user's permissions,
     * ensuring that only appropriate menu items are displayed.</p>
     */
    @PostConstruct
    public void init() {
        user = uiSessionLogin.getUser();
        createHeader();
        if (user != null) {
            createNavBar();
        } else {
            SideNav nav = new SideNav();
            SideNavItem logoutLink = new SideNavItem("Logout", LogoutView.class,
                    VaadinIcon.LOCK.create());
            nav.addItem(logoutLink);
            addToDrawer(nav);
        }
    }

    /**
     * Creates and configures the application header section with brand-aware styling.
     *
     * <p>This method creates a branded header that adapts to the current brand configuration,
     * including appropriate logos, colors, and styling. The header contains:</p>
     * <ul>
     *   <li><strong>Drawer toggle:</strong> Button to open/close the navigation drawer</li>
     *   <li><strong>Brand logo:</strong> Organization logo (if available)</li>
     *   <li><strong>Application title:</strong> Clickable link that navigates to the home page</li>
     * </ul>
     *
     * <p>The header adapts to different brands (um-brand, test-brand, default-brand)
     * with appropriate styling and branding elements.</p>
     */
    private void createHeader() {
        // Detect current brand
        BrandUtil.BrandInfo brandInfo = BrandUtil.detectCurrentBrand();
        
        // Create header container with brand-specific CSS class
        Div headerContainer = new Div();
        headerContainer.addClassNames("branded-header", brandInfo.getCssClass());
        
        // Create drawer toggle
        DrawerToggle toggle = new DrawerToggle();
        headerContainer.add(toggle);
        
        // Add logo if available
        try {
            Image logo = new Image();
            logo.setSrc(BrandUtil.getLogoResourcePath(brandInfo));
            logo.setAlt(brandInfo.getDisplayName() + " Logo");
            logo.addClassName("logo");
            
            Div logoContainer = new Div(logo);
            logoContainer.addClassName("logo-container");
            headerContainer.add(logoContainer);
        } catch (Exception e) {
            // Logo not available, continue without it
        }
        
        // Create application title
        String appTitle = BrandUtil.getApplicationTitle(brandInfo, "Admin");
        Anchor title = new Anchor("/", appTitle);
        title.addClassName("brand-title");
        headerContainer.add(title);
        
        // Add header to navbar
        addToNavbar(headerContainer);
    }

    /**
     * Creates and configures the navigation sidebar for authenticated users.
     *
     * <p>This method builds a comprehensive navigation menu with the following structure:</p>
     *
     * <h4>Standard Navigation Items (all authenticated users):</h4>
     * <ul>
     *   <li><strong>Search Subjects:</strong> Navigate to subject search functionality</li>
     *   <li><strong>Register Subjects:</strong> Navigate to subject registration</li>
     * </ul>
     *
     * <h4>Admin Section (admin users only):</h4>
     * <ul>
     *   <li><strong>Departments:</strong> Manage department information</li>
     *   <li><strong>Message Templates:</strong> Manage communication templates</li>
     *   <li><strong>Users:</strong> Manage user accounts and permissions</li>
     * </ul>
     *
     * <h4>System Actions (all users):</h4>
     * <ul>
     *   <li><strong>Logout:</strong> Terminate the current session</li>
     * </ul>
     *
     * <p>The admin section is conditionally displayed based on the user's role permissions.
     * Each navigation item is configured with appropriate icons from the Vaadin icon set
     * for improved visual recognition and user experience.</p>
     *
     * <p>The navigation is implemented using {@link SideNav} and {@link SideNavItem}
     * components, providing a hierarchical menu structure with proper routing integration.</p>
     */
    private void createNavBar() {
        SideNav nav = new SideNav();

        SideNavItem searchLink = new SideNavItem("Search Subjects",
                SearchView.class, VaadinIcon.SEARCH.create());
        SideNavItem registerLink = new SideNavItem("Register Subjects", RegisterView.class,
                VaadinIcon.USERS.create());
        nav.addItem(searchLink, registerLink);
        // Message Templates Button (Admin only)
        // TODO this is a hack! Restore the if statement after the OIDC is fixed.
        if (identity.hasRole("elicit_admin")) {
            SideNavItem adminSection = new SideNavItem("Admin");
            adminSection.setPrefixComponent(VaadinIcon.COG.create());
            adminSection.addItem(new SideNavItem("Departments", DepartmentsView.class,
                    VaadinIcon.GRID_BEVEL.create()));
            adminSection.addItem(new SideNavItem("Message Templates", MessageTemplatesView.class,
                    VaadinIcon.ENVELOPE.create()));
            adminSection.addItem(new SideNavItem("Users", UsersView.class,
                    VaadinIcon.GROUP.create()));
            nav.addItem(adminSection);
        }
        SideNavItem logoutLink = new SideNavItem("Logout", LogoutView.class,
                VaadinIcon.LOCK.create());
        nav.addItem(logoutLink);
        addToDrawer(nav);
    }

    /**
     * Registers this layout as a navigation listener when attached to the UI.
     *
     * <p>This method is part of the component lifecycle and ensures that the layout
     * can respond to navigation events. It adds this instance as an after-navigation
     * listener to the current UI, enabling the scroll-to-top functionality.</p>
     *
     * @param attachEvent the event fired when this component is attached to the UI
     * @see AfterNavigationListener
     */
    @Override
    protected void onAttach(AttachEvent attachEvent) {
        getUI().ifPresent(ui -> ui.addAfterNavigationListener(this));
    }

    /**
     * Handles post-navigation actions to improve user experience.
     *
     * <p>This method is automatically called after each navigation event and ensures
     * that the main content area is scrolled to the top. This provides a consistent
     * user experience by preventing users from being left at an arbitrary scroll
     * position when navigating between views.</p>
     *
     * <p>This is particularly important in applications with long content pages where
     * users might scroll down before navigating to a new view. Without this functionality,
     * the new view would appear at the same scroll position, potentially showing
     * the middle or bottom of the new content rather than the top.</p>
     *
     * @param event the navigation event containing information about the completed navigation
     * @see AfterNavigationListener#afterNavigation(AfterNavigationEvent)
     */
    @Override
    public void afterNavigation(AfterNavigationEvent event) {
        getContent().scrollIntoView();
    }
}
