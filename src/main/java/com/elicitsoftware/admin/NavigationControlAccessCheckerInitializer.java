package com.elicitsoftware.admin;

import com.vaadin.flow.server.ServiceInitEvent;
import com.vaadin.flow.server.VaadinServiceInitListener;
import com.vaadin.flow.server.auth.NavigationAccessControl;
import jakarta.enterprise.event.Observes;

/**
 * Initializer class that configures navigation access control for the Elicit Admin application.
 * 
 * <p>This class implements the Vaadin service initialization pattern to set up security-based
 * navigation control throughout the application. It ensures that protected routes are properly
 * secured and unauthorized users are redirected to the login page when attempting to access
 * restricted views.</p>
 * 
 * <p><strong>Key Responsibilities:</strong></p>
 * <ul>
 *   <li><strong>Access Control Setup:</strong> Configures navigation-based security checks</li>
 *   <li><strong>Login Redirection:</strong> Redirects unauthorized users to the login view</li>
 *   <li><strong>Route Protection:</strong> Enforces security annotations on Vaadin views</li>
 *   <li><strong>Session Management:</strong> Integrates with authentication session state</li>
 * </ul>
 * 
 * <p><strong>Security Integration:</strong></p>
 * <ul>
 *   <li><strong>Role-Based Access:</strong> Works with {@code @RolesAllowed} annotations on views</li>
 *   <li><strong>Authentication Check:</strong> Verifies user authentication status before navigation</li>
 *   <li><strong>OIDC Integration:</strong> Integrates with OpenID Connect authentication</li>
 *   <li><strong>Session Validation:</strong> Ensures valid authentication sessions</li>
 * </ul>
 * 
 * <p><strong>Protected Views in Elicit Admin:</strong></p>
 * <ul>
 *   <li><strong>Administrative Views:</strong> Require "elicit_admin" role</li>
 *   <li><strong>User Management:</strong> UsersView, EditUserView, DepartmentsView</li>
 *   <li><strong>Content Management:</strong> MessageTemplatesView, EditMessageTemplatesView</li>
 *   <li><strong>Data Views:</strong> SearchView, RegisterView</li>
 * </ul>
 * 
 * <p><strong>Navigation Flow:</strong></p>
 * <ol>
 *   <li>User attempts to navigate to a protected view</li>
 *   <li>BeforeEnterListener checks authentication and authorization</li>
 *   <li>If authorized: Navigation proceeds to target view</li>
 *   <li>If unauthorized: User is redirected to "/login" view</li>
 *   <li>After successful login: User is redirected to originally requested view</li>
 * </ol>
 * 
 * <p><strong>Configuration Details:</strong></p>
 * <ul>
 *   <li><strong>Login View:</strong> Set to "login" route for unauthorized access</li>
 *   <li><strong>Access Control:</strong> Applied globally to all UI instances</li>
 *   <li><strong>Listener Registration:</strong> Attached to every UI before enter events</li>
 * </ul>
 * 
 * <p><strong>Integration with Vaadin Security:</strong></p>
 * <ul>
 *   <li>Works seamlessly with Vaadin Flow security annotations</li>
 *   <li>Supports role-based authorization checks</li>
 *   <li>Integrates with external authentication providers (OIDC)</li>
 *   <li>Maintains security context across navigation events</li>
 * </ul>
 * 
 * <p><strong>Automatic Initialization:</strong></p>
 * <p>This class is automatically discovered and initialized by Vaadin during application
 * startup through the {@link VaadinServiceInitListener} interface. No manual configuration
 * or CDI annotations are required.</p>
 * 
 * @author Elicit Software
 * @version 1.0
 * @since 1.0
 * @see VaadinServiceInitListener
 * @see NavigationAccessControl
 * @see com.elicitsoftware.admin.flow.LoginView
 * @see jakarta.annotation.security.RolesAllowed
 */
public class NavigationControlAccessCheckerInitializer implements VaadinServiceInitListener {

    /**
     * Navigation access control instance that handles security checks for route navigation.
     * 
     * <p>This field holds the configured {@link NavigationAccessControl} instance that
     * performs authentication and authorization checks before allowing navigation to
     * protected views. It's initialized during construction with the login view configuration.</p>
     * 
     * <p><strong>Responsibilities:</strong></p>
     * <ul>
     *   <li><strong>Authentication Check:</strong> Verifies user is logged in</li>
     *   <li><strong>Authorization Check:</strong> Validates user has required roles</li>
     *   <li><strong>Login Redirection:</strong> Redirects to login view when unauthorized</li>
     *   <li><strong>Route Protection:</strong> Enforces security on protected routes</li>
     * </ul>
     * 
     * @see NavigationAccessControl
     * @see #serviceInit(ServiceInitEvent)
     */
    private final NavigationAccessControl accessControl;

    /**
     * Constructs and configures the navigation access control initializer.
     * 
     * <p>This constructor creates and configures the {@link NavigationAccessControl}
     * instance with the appropriate login view setting. The access control is configured
     * to redirect unauthorized users to the "login" route.</p>
     * 
     * <p><strong>Configuration Steps:</strong></p>
     * <ol>
     *   <li>Creates new NavigationAccessControl instance</li>
     *   <li>Sets the login view route to "login"</li>
     *   <li>Prepares access control for registration with UI instances</li>
     * </ol>
     * 
     * <p><strong>Login View Configuration:</strong></p>
     * <ul>
     *   <li><strong>Route:</strong> "login" - corresponds to {@link com.elicitsoftware.admin.flow.LoginView}</li>
     *   <li><strong>Purpose:</strong> Target for unauthorized access redirections</li>
     *   <li><strong>Integration:</strong> Works with OIDC authentication flow</li>
     * </ul>
     * 
     * <p>The configured access control will be attached to UI instances during
     * the service initialization phase to provide application-wide security.</p>
     * 
     * @see NavigationAccessControl#setLoginView(String)
     */
    public NavigationControlAccessCheckerInitializer() {
        accessControl = new NavigationAccessControl();
        accessControl.setLoginView("login");
    }

    /**
     * Initializes navigation access control for the Vaadin service.
     * 
     * <p>This method is automatically called by Vaadin during service initialization
     * and sets up security-based navigation control for all UI instances. It registers
     * the access control as a before-enter listener to intercept and validate navigation
     * attempts before they reach protected views.</p>
     * 
     * <p><strong>Initialization Process:</strong></p>
     * <ol>
     *   <li><strong>Service Event Reception:</strong> Observes the Vaadin service initialization event</li>
     *   <li><strong>UI Listener Registration:</strong> Adds a UI initialization listener to the service</li>
     *   <li><strong>Access Control Attachment:</strong> Attaches navigation access control to each UI</li>
     *   <li><strong>Security Activation:</strong> Enables route-based security checks</li>
     * </ol>
     * 
     * <p><strong>Security Enforcement:</strong></p>
     * <ul>
     *   <li><strong>Global Coverage:</strong> Applied to all UI instances in the application</li>
     *   <li><strong>Before-Enter Interception:</strong> Checks security before navigation completes</li>
     *   <li><strong>Role Validation:</strong> Validates user roles against view requirements</li>
     *   <li><strong>Authentication Check:</strong> Ensures user is properly authenticated</li>
     * </ul>
     * 
     * <p><strong>Protected View Integration:</strong></p>
     * <ul>
     *   <li><strong>@RolesAllowed Views:</strong> Enforces role requirements on annotated views</li>
     *   <li><strong>Login Redirection:</strong> Redirects unauthorized access to login view</li>
     *   <li><strong>Session Validation:</strong> Checks authentication session validity</li>
     *   <li><strong>Post-Login Redirect:</strong> Returns to originally requested view after login</li>
     * </ul>
     * 
     * <p><strong>Example Protected Views:</strong></p>
     * <ul>
     *   <li>{@code @RolesAllowed("elicit_admin")} - Administrative views</li>
     *   <li>UsersView, DepartmentsView, MessageTemplatesView, etc.</li>
     * </ul>
     * 
     * <p><strong>Navigation Flow Example:</strong></p>
     * <pre>
     * 1. User navigates to /users
     * 2. BeforeEnterListener (access control) checks authentication
     * 3. If not authenticated: redirect to /login
     * 4. If authenticated but no elicit_admin role: access denied
     * 5. If authorized: navigation proceeds to UsersView
     * </pre>
     * 
     * <p><strong>CDI Integration:</strong></p>
     * <p>The {@code @Observes} annotation enables CDI event observation, allowing this
     * method to automatically receive service initialization events without explicit
     * registration.</p>
     * 
     * @param serviceInitEvent the Vaadin service initialization event containing the service instance
     * @see ServiceInitEvent
     * @see NavigationAccessControl
     * @see com.vaadin.flow.component.UI#addBeforeEnterListener
     */
    @Override
    public void serviceInit(@Observes ServiceInitEvent serviceInitEvent) {
        serviceInitEvent.getSource().addUIInitListener(uiInitEvent -> {
            uiInitEvent.getUI().addBeforeEnterListener(accessControl);
        });
    }
}