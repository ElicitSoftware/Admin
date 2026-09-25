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

import com.elicitsoftware.admin.i18n.ElicitI18NProvider;
import com.elicitsoftware.admin.i18n.LanguageSwitcher;
import com.elicitsoftware.admin.i18n.LocaleSelection;
import com.elicitsoftware.admin.manual.AdminManual;
import com.elicitsoftware.admin.util.BrandUtil;
import com.elicitsoftware.model.User;
import com.elicitsoftware.security.ElicitRoles;
import com.elicitsoftware.service.DefaultAccountCheck;
import com.elicitsoftware.service.SurveyDefinitionPresenceCheck;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.HasElement;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.AnchorTarget;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.sidenav.SideNav;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.sidenav.SideNavItem;
import com.vaadin.flow.router.AfterNavigationEvent;
import com.vaadin.flow.router.AfterNavigationListener;
import com.vaadin.quarkus.annotation.VaadinServiceEnabled;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;

import java.util.List;

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
     * The brand utility service for managing brand configuration.
     */
    @Inject
    BrandUtil brandUtil;

    /** Remembers the language the administrator picks (UC-026). */
    @Inject
    LocaleSelection localeSelection;

    /** Supplies the languages offered by the switcher. */
    @Inject
    @VaadinServiceEnabled
    ElicitI18NProvider i18nProvider;

    /**
     * Reports whether this deployment has a survey installed (UC-019).
     */
    @Inject
    SurveyDefinitionPresenceCheck surveyPresence;

    /**
     * Answers whether the seeded default accounts still exist, for the console banner (UC-021).
     */
    @Inject
    DefaultAccountCheck defaultAccounts;

    /**
     * The administrator's manual packaged in this image, offered in the header and the drawer
     * when the build carries one (UC-029).
     */
    @Inject
    AdminManual manual;

    /**
     * The current authenticated user.
     */
    User user;

    /** The open no-department dialog, if any, so navigations reuse one overlay (UC-028). */
    private Dialog missingDepartmentDialog;

    /** Where the packaged manual is served (UC-029); outside the Vaadin router, hence router-ignored. */
    private static final String MANUAL_PATH = "/api/manual";

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
            SideNavItem logoutLink = new SideNavItem(getTranslation("mainLayout.nav.logout"), LogoutView.class,
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
        BrandUtil.BrandInfo brandInfo = brandUtil.detectCurrentBrand();

        // Create header container with brand-specific CSS class
        Div headerContainer = new Div();
        headerContainer.addClassName("branded-header");

        // Add brand-specific CSS class if available and not empty
        if (brandInfo != null && brandInfo.getCssClass() != null && !brandInfo.getCssClass().isEmpty()) {
            headerContainer.addClassName(brandInfo.getCssClass());
        }

        // Create drawer toggle
        DrawerToggle toggle = new DrawerToggle();
        headerContainer.add(toggle);

        // Add logo if available.
        // The header uses the icon-only mark, not the full horizontal lockup:
        // the full lockup's wordmark is illegible at header height, and its
        // navy artwork disappears against the navy header background. The
        // full lockup is reserved for surfaces with room to breathe (e.g. a
        // login screen), not the 48-64px nav bar.
        try {
            Image logo = new Image();
            logo.setSrc(brandUtil.getIconResourcePath(brandInfo));
            logo.setAlt(getTranslation("common.logoAlt", brandInfo.getDisplayName(getLocale())));
            logo.addClassName("logo");

            Div logoContainer = new Div(logo);
            logoContainer.addClassName("logo-container");
            headerContainer.add(logoContainer);
        } catch (Exception e) {
            // Logo not available, continue without it
        }

        // Create application title
        String appType = getTranslation("common.appType.admin");
        String appTitle = brandInfo == null || brandInfo.isDefaultBrand()
                ? getTranslation("common.appTitle.default", appType)
                : getTranslation("common.appTitle", brandInfo.getDisplayName(getLocale()), appType);
        Anchor title = new Anchor("/", appTitle);
        title.addClassName("brand-title");
        headerContainer.add(title);

        addManualLink(headerContainer);

        // Language selector (UC-026): every screen offers the shipped and mounted languages.
        headerContainer.add(new LanguageSwitcher(localeSelection, i18nProvider));

        // Add header to navbar
        addToNavbar(headerContainer);
    }

    /**
     * The manual (UC-029): a link in the header opening the packaged PDF in a new tab. A build
     * that carries no manual simply does not offer it (UC-029 A1). Unlike Author's, this link is
     * offered to both console roles, because one manual serves them both and marks the
     * administrator-only procedures where they appear (BR-006).
     *
     * @param headerContainer the branded header being assembled
     */
    private void addManualLink(Div headerContainer) {
        if (!manualAvailable()) {
            return;
        }
        Anchor manualLink = new Anchor(MANUAL_PATH, getTranslation("mainLayout.header.manual"));
        // The Vaadin router intercepts relative hrefs; /api/manual is served outside the router.
        manualLink.setRouterIgnore(true);
        manualLink.setTarget(AnchorTarget.BLANK);
        manualLink.setTitle(getTranslation("mainLayout.header.manualTitle"));
        manualLink.addClassName("header-manual-link");
        manualLink.getElement().insertChild(0, VaadinIcon.FILE_TEXT_O.create().getElement());
        headerContainer.add(manualLink);
    }

    /**
     * The manual's drawer entry (UC-029), placed with the items every signed-in reader sees
     * rather than inside the administrator's sections: both roles may read it (BR-006).
     *
     * @param nav the drawer navigation being assembled
     */
    private void addManualNavItem(SideNav nav) {
        if (!manualAvailable()) {
            return;
        }
        SideNavItem manualItem = new SideNavItem(getTranslation("mainLayout.nav.manual"), MANUAL_PATH,
                VaadinIcon.FILE_TEXT_O.create());
        // As above: without this the router would swallow the /api path and show "page not found".
        manualItem.setRouterIgnore(true);
        manualItem.setOpenInNewBrowserTab(true);
        nav.addItem(manualItem);
    }

    /** Whether this image carries a manual to link to (UC-029 A1). */
    private boolean manualAvailable() {
        return manual != null && manual.isAvailable();
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
     *   <li><strong>Import Respondent:</strong> Import a respondent data file exported from another instance</li>
     *   <li><strong>Apply Survey Definition:</strong> Install or update a survey from an authored definition file</li>
     *   <li><strong>Export Survey Definition:</strong> Download an installed survey's definition file</li>
     * </ul>
     *
     * <h4>System Actions (all users):</h4>
     * <ul>
     *   <li><strong>Manual:</strong> Open the packaged administrator's manual in a new tab,
     *       when the image carries one (UC-029)</li>
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

        SideNavItem searchLink = new SideNavItem(getTranslation("mainLayout.nav.searchSubjects"),
                SearchView.class, VaadinIcon.SEARCH.create());
        SideNavItem registerLink = new SideNavItem(getTranslation("mainLayout.nav.registerSubjects"), RegisterView.class,
                VaadinIcon.USERS.create());
        nav.addItem(searchLink, registerLink);
        // Message Templates Button (Admin only)
        if (identity.hasRole("elicit_admin")) {
            SideNavItem adminSection = new SideNavItem(getTranslation("mainLayout.nav.admin"));
            adminSection.setPrefixComponent(VaadinIcon.COG.create());
            adminSection.addItem(new SideNavItem(getTranslation("mainLayout.nav.departments"), DepartmentsView.class,
                    VaadinIcon.GRID_BEVEL.create()));
            adminSection.addItem(new SideNavItem(getTranslation("mainLayout.nav.messageTemplates"), MessageTemplatesView.class,
                    VaadinIcon.ENVELOPE.create()));
            adminSection.addItem(new SideNavItem(getTranslation("mainLayout.nav.users"), UsersView.class,
                    VaadinIcon.GROUP.create()));
            adminSection.addItem(new SideNavItem(getTranslation("mainLayout.nav.importRespondent"), RespondentImportView.class,
                    VaadinIcon.UPLOAD.create()));
            adminSection.addItem(new SideNavItem(getTranslation("mainLayout.nav.applySurveyDefinition"), SurveyDefinitionApplyView.class,
                    VaadinIcon.FILE_PROCESS.create()));
            adminSection.addItem(new SideNavItem(getTranslation("mainLayout.nav.exportSurveyDefinition"), SurveyDefinitionExportView.class,
                    VaadinIcon.DOWNLOAD.create()));
            nav.addItem(adminSection);
            nav.addItem(createSystemSection());
        }
        addManualNavItem(nav);
        SideNavItem logoutLink = new SideNavItem(getTranslation("mainLayout.nav.logout"), LogoutView.class,
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
    /**
     * The System section: setup and diagnostics for the administrator or operator wiring up a
     * deployment (UC-020 to UC-025, and the OIDC entry for UC-009 / FR-026).
     */
    private SideNavItem createSystemSection() {
        SideNavItem systemSection = new SideNavItem(getTranslation("mainLayout.nav.system"));
        systemSection.setPrefixComponent(VaadinIcon.TOOLS.create());
        systemSection.addItem(new SideNavItem(getTranslation("mainLayout.nav.systemOverview"), SystemOverviewView.class,
                VaadinIcon.DASHBOARD.create()));
        systemSection.addItem(new SideNavItem(getTranslation("mainLayout.nav.systemDatabase"), SystemDatabaseView.class,
                VaadinIcon.DATABASE.create()));
        systemSection.addItem(new SideNavItem(getTranslation("mainLayout.nav.systemBranding"), SystemBrandingView.class,
                VaadinIcon.PAINTBRUSH.create()));
        systemSection.addItem(new SideNavItem(getTranslation("mainLayout.nav.systemEmail"), SystemEmailView.class,
                VaadinIcon.PAPERPLANE.create()));
        systemSection.addItem(new SideNavItem(getTranslation("mainLayout.nav.systemConnections"), SystemConnectionsView.class,
                VaadinIcon.CONNECT.create()));
        systemSection.addItem(new SideNavItem(getTranslation("mainLayout.nav.systemOidc"), DebugView.class,
                VaadinIcon.SHIELD.create()));
        return systemSection;
    }

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

    /**
     * Shows the routed view, preceded by the missing-survey banner when this deployment
     * has no survey installed (UC-019).
     * <p>
     * The check runs on every navigation rather than once, so the banner appears and
     * disappears as definitions are applied or removed without a restart (BR-076). When a
     * survey is installed -- the ordinary case -- the view is shown exactly as it was
     * before this was added, with no wrapper of any kind in the way of its layout.
     *
     * @param content the routed view to display
     */
    @Override
    public void showRouterLayoutContent(HasElement content) {
        gateOnDepartment(content);
        boolean surveyInstalled = surveyPresence.isSurveyInstalled();
        // Only an administrator can rename accounts, so only an administrator is warned (UC-021 A2).
        List<String> seededAccounts = identity.hasRole(ElicitRoles.ADMIN)
                ? defaultAccounts.findDefaultAccounts() : List.of();
        if (surveyInstalled && seededAccounts.isEmpty()) {
            super.showRouterLayoutContent(content);
            return;
        }

        VerticalLayout wrapper = new VerticalLayout();
        wrapper.setSizeFull();
        wrapper.setPadding(false);
        wrapper.setSpacing(false);
        // Inset the banners from the header and the edges (components/console-notice.css); the
        // routed view keeps its own padding.
        wrapper.addClassName("console-notices");
        if (!surveyInstalled) {
            wrapper.add(MissingSurveyNotice.banner(identity.hasRole(ElicitRoles.ADMIN)));
        }
        if (!seededAccounts.isEmpty()) {
            wrapper.add(DefaultAccountNotice.banner(seededAccounts));
        }
        wrapper.add((Component) content);
        setContent(wrapper);
    }

    /**
     * Opens the blocking no-department dialog when the signed-in user has no department, and
     * closes it once they have one (UC-028).
     * <p>
     * Evaluated on every navigation like the banners above, so an assignment made anywhere --
     * by creating a department, by another administrator editing this account -- clears the
     * dialog on the next screen with no restart and no re-login (BR-114). The check is free in
     * the ordinary case: the session record already carries its departments, and the database
     * is re-read only when that record shows none. An administrator on Departments or Edit
     * Department is never blocked, because that is where the remedy lives (BR-111). A principal
     * with no console record at all is left to UC-001's handling: the dialog would only mislead.
     *
     * @param content the routed view about to be shown
     */
    private void gateOnDepartment(HasElement content) {
        boolean administrator = identity.hasRole(ElicitRoles.ADMIN);
        // instanceof, not class equality: CDI hands the router intercepted subclasses of the views.
        boolean onRemedyScreen = administrator && isDepartmentRemedyScreen(content);
        if (onRemedyScreen || !lacksDepartment()) {
            closeMissingDepartmentDialog();
            return;
        }
        if (missingDepartmentDialog == null || !missingDepartmentDialog.isOpened()) {
            missingDepartmentDialog = administrator
                    ? MissingDepartmentDialog.forAdministrator(manualAvailable())
                    : MissingDepartmentDialog.forUser(manualAvailable());
            missingDepartmentDialog.open();
        }
    }

    /**
     * Whether the signed-in user has a console record but no department, re-reading the
     * record when the session copy shows none (UC-028 BR-114).
     */
    private boolean lacksDepartment() {
        User current = uiSessionLogin.getUser();
        if (current == null || current.hasDepartments()) {
            return false;
        }
        // The session copy may be stale: someone may have assigned a department since sign-in.
        uiSessionLogin.refresh();
        current = uiSessionLogin.getUser();
        return current != null && !current.hasDepartments();
    }

    /**
     * The screens an administrator must still reach while blocked for want of a department
     * (UC-028 BR-111): the blocking notice must not block its own remedy.
     */
    private static boolean isDepartmentRemedyScreen(HasElement content) {
        return content instanceof DepartmentsView || content instanceof EditDepartmentView;
    }

    private void closeMissingDepartmentDialog() {
        if (missingDepartmentDialog != null) {
            missingDepartmentDialog.close();
            missingDepartmentDialog = null;
        }
    }
}
