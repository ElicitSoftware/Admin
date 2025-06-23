package com.elicitsoftware.admin.flow;


import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.VaadinRequest;
import com.vaadin.flow.server.auth.NavigationAccessControl;
import jakarta.annotation.security.PermitAll;

/**
 * A specialized login view that facilitates OIDC-based authentication flow.
 * This view serves as an intermediary component in the Quarkus OIDC integration
 * and is not directly displayed to users during normal operation.
 * 
 * <p>The primary purpose of this view is to handle post-authentication navigation.
 * When users are redirected back from the OIDC server after successful authentication,
 * this view processes the return and forwards them to their originally requested
 * destination or to the application home page.</p>
 * 
 * <p>Key characteristics:</p>
 * <ul>
 *   <li>Acts as a dummy view that is never actually shown to users</li>
 *   <li>Handles redirect logic after OIDC authentication</li>
 *   <li>Restores the user's original navigation intent</li>
 *   <li>Accessible to all users (no authentication required)</li>
 * </ul>
 * 
 * <p>The view works in conjunction with Quarkus OIDC integration where:</p>
 * <ol>
 *   <li>User attempts to access a protected resource</li>
 *   <li>Quarkus redirects to OIDC server for authentication</li>
 *   <li>After authentication, user is returned to this login view</li>
 *   <li>This view forwards user to their original destination</li>
 * </ol>
 * 
 * @author Elicit Software
 * @version 1.0
 * @since 1.0
 * @see NavigationAccessControl
 */
@Route("login")
@PermitAll
public class LoginView extends VerticalLayout implements BeforeEnterObserver  {

    /**
     * Constructs a new LoginView.
     * 
     * <p>Initializes the view with a simple welcome message. However, this content
     * is typically never displayed to users as the view immediately processes
     * post-authentication navigation in the {@link #beforeEnter(BeforeEnterEvent)}
     * method.</p>
     * 
     * <p>The message "FHHS Admin :-)" serves as a placeholder and debugging aid
     * to identify this view during development.</p>
     */
    public LoginView() {
        add("FHHS Admin :-)");
    }

    /**
     * Handles post-authentication navigation by redirecting users to their intended destination.
     * 
     * <p>This method is called before the user enters this view and serves as the core
     * functionality for post-OIDC authentication processing. It performs the following logic:</p>
     * 
     * <ol>
     *   <li>Retrieves the current HTTP session from the Vaadin request</li>
     *   <li>Checks for a stored redirect URL that was saved by the ViewAccessChecker
     *       when the user was initially redirected for authentication</li>
     *   <li>If a stored redirect exists, forwards the user to that original destination</li>
     *   <li>If no stored redirect exists, forwards the user to the application root "/"</li>
     * </ol>
     * 
     * <p>The absence of a stored redirect typically occurs when:</p>
     * <ul>
     *   <li>A user manually navigates to the login URL while already authenticated</li>
     *   <li>There was an issue with session state during the authentication flow</li>
     * </ul>
     * 
     * <p>This implementation ensures that users are always redirected somewhere
     * appropriate, maintaining a smooth user experience even in edge cases.</p>
     * 
     * @param event the BeforeEnterEvent containing navigation information
     * @see BeforeEnterObserver#beforeEnter(BeforeEnterEvent)
     * @see NavigationAccessControl#SESSION_STORED_REDIRECT
     */
    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        var session = VaadinRequest.getCurrent().getWrappedSession();
        // ViewAccessChecker saves the original route to session
        // restore that when we are returned form OIDC server
        Object origView = session.getAttribute(NavigationAccessControl.SESSION_STORED_REDIRECT);
        if (origView != null) {
            event.forwardTo(origView.toString());
        } else {
            // This should never happen :-)
            // But happens if you manually enter login while already
            // logged in.
            event.forwardTo("/");
        }
    }
}
