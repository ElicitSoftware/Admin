package com.elicitsoftware.admin.flow;


import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.VaadinSession;
import jakarta.annotation.security.PermitAll;

/**
 * A specialized logout view that facilitates OIDC-based authentication logout flow.
 * This view serves as an intermediary component in the Quarkus OIDC integration
 * and is not directly displayed to users during normal operation.
 * 
 * <p>The primary purpose of this view is to handle user logout by properly
 * terminating both the Vaadin session and the OIDC authentication session.
 * When users navigate to this view, it automatically initiates the logout
 * process and redirects them appropriately.</p>
 * 
 * <p>Key characteristics:</p>
 * <ul>
 *   <li>Acts as a dummy view that is never actually shown to users</li>
 *   <li>Handles session termination for both Vaadin and OIDC</li>
 *   <li>Coordinates logout with the OIDC provider</li>
 *   <li>Accessible to all users (no authentication required)</li>
 * </ul>
 * 
 * <p>The logout process follows these steps:</p>
 * <ol>
 *   <li>User navigates to the logout URL or clicks a logout link</li>
 *   <li>This view closes the Vaadin session</li>
 *   <li>Browser is redirected to the OIDC logout endpoint</li>
 *   <li>OIDC provider handles session termination and may redirect back</li>
 *   <li>User is ultimately forwarded to the application root</li>
 * </ol>
 * 
 * @author Elicit Software
 * @version 1.0
 * @since 1.0
 * @see VaadinSession
 */
@Route("logout")
@PermitAll
public class LogoutView extends VerticalLayout implements BeforeEnterObserver {

    /**
     * Constructs a new LogoutView.
     * 
     * <p>Initializes the view with a simple logout message. However, this content
     * is typically never displayed to users as the view immediately processes
     * the logout sequence in the {@link #beforeEnter(BeforeEnterEvent)} method.</p>
     * 
     * <p>The message "Admin Logout :-)" serves as a placeholder and debugging aid
     * to identify this view during development, though users will not see it
     * due to immediate redirects.</p>
     */
    public LogoutView() {
        add("Admin Logout :-)");
    }

    /**
     * Handles the logout process by terminating sessions and redirecting the user.
     * 
     * <p>This method is called before the user enters this view and serves as the core
     * functionality for the logout process. It performs the following sequence:</p>
     * 
     * <ol>
     *   <li><strong>Close Vaadin Session:</strong> Terminates the current Vaadin session,
     *       which invalidates server-side state and removes user data</li>
     *   <li><strong>OIDC Logout Redirect:</strong> Redirects the browser to the "/logout"
     *       endpoint, which is handled by the Quarkus OIDC extension to properly
     *       terminate the OIDC authentication session</li>
     *   <li><strong>Fallback Navigation:</strong> Forwards to the application root "/"
     *       as a fallback destination</li>
     * </ol>
     * 
     * <p>The combination of closing the Vaadin session and redirecting to the OIDC
     * logout endpoint ensures that both client-side and server-side authentication
     * state is properly cleaned up. This prevents security issues and ensures that
     * subsequent access attempts require fresh authentication.</p>
     * 
     * <p><strong>Note:</strong> The order of operations is important - the Vaadin session
     * is closed first to ensure proper cleanup, then the OIDC logout is initiated
     * through the browser redirect.</p>
     * 
     * @param event the BeforeEnterEvent containing navigation information
     * @see BeforeEnterObserver#beforeEnter(BeforeEnterEvent)
     * @see VaadinSession#close()
     */
    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        VaadinSession.getCurrent().close();
        com.vaadin.flow.component.UI.getCurrent().getPage().setLocation("/logout");
        event.forwardTo("/");
    }
}
