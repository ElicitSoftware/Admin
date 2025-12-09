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
import com.vaadin.flow.server.VaadinSession;
import com.vaadin.quarkus.annotation.UIScoped;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import jakarta.persistence.Transient;

import java.io.Serializable;
import java.util.List;
import java.util.Set;

/**
 * A UI-scoped service that manages user authentication and session state within Vaadin applications.
 * This service bridges the gap between Quarkus security and Vaadin session management, providing
 * a convenient interface for accessing user information and role-based authorization.
 *
 * <p>The service is scoped to the UI session (browser tab/window), meaning a new instance is created
 * for each browser tab that accesses the application. This ensures proper isolation of user sessions
 * and maintains security boundaries between different browser sessions.</p>
 *
 * <p>Key responsibilities include:</p>
 * <ul>
 *   <li><strong>User Authentication:</strong> Validates user identity against the database</li>
 *   <li><strong>Session Management:</strong> Stores user information in the Vaadin session</li>
 *   <li><strong>Role Authorization:</strong> Provides role-based access control utilities</li>
 *   <li><strong>Security Integration:</strong> Bridges Quarkus security with Vaadin UI components</li>
 * </ul>
 *
 * <p>The service automatically initializes when the UI is created, performing the following operations:</p>
 * <ol>
 *   <li>Extracts the principal name from the Quarkus security context</li>
 *   <li>Looks up the corresponding user in the database</li>
 *   <li>Validates that the user is active</li>
 *   <li>Stores user information and roles in the Vaadin session</li>
 * </ol>
 *
 * <p>If the user is not found or is inactive, the service sets up an empty session that will
 * trigger appropriate error handling in the UI components.</p>
 *
 * <p><strong>Usage Example:</strong></p>
 * <pre>{@code
 * @Inject
 * UiSessionLogin sessionLogin;
 *
 * User currentUser = sessionLogin.getUser();
 * if (sessionLogin.hasRole("elicit_admin")) {
 *     // Show admin features
 * }
 * }</pre>
 *
 * @author Elicit Software
 * @version 1.0
 * @since 1.0
 * @see User
 * @see SecurityIdentity
 * @see VaadinSession
 */
@UIScoped
public class UiSessionLogin implements Serializable {

    /** Injected Quarkus security identity for accessing authentication information. */
    @Inject
    SecurityIdentity identity;

    /**
     * Default constructor for CDI and Vaadin session management.
     * <p>
     * Creates a new UiSessionLogin instance for UI scoped beans.
     * This constructor is used by CDI for session-scoped bean
     * instantiation.
     */
    public UiSessionLogin() {
        // Default constructor for CDI UI scope
    }

    /**
     * Initializes the user session after dependency injection is complete.
     *
     * <p>This method is automatically called once per UI session (browser tab/window)
     * and performs the following authentication and session setup operations:</p>
     *
     * <ol>
     *   <li><strong>Principal Extraction:</strong> Gets the username from the security context</li>
     *   <li><strong>User Lookup:</strong> Searches for an active user with the given username</li>
     *   <li><strong>Session Setup:</strong> Configures the Vaadin session based on lookup results</li>
     * </ol>
     *
     * <h4>Successful Authentication:</h4>
     * <p>When a valid, active user is found:</p>
     * <ul>
     *   <li>User roles are extracted from the security identity</li>
     *   <li>User object is stored in the Vaadin session</li>
     *   <li>Session is ready for normal application use</li>
     * </ul>
     *
     * <h4>Failed Authentication:</h4>
     * <p>When the user is not found or inactive:</p>
     * <ul>
     *   <li>Roles are set to an empty set</li>
     *   <li>User attribute is set to null in the session</li>
     *   <li>UI components will handle the error state appropriately</li>
     * </ul>
     *
     * <p>Debug information is logged to the console showing the principal name
     * being processed for troubleshooting authentication issues.</p>
     */
    @PostConstruct
    public void init() {
        String principalName = identity.getPrincipal().getName();
        System.out.println("Initializing UI for principal: " + principalName);
        System.out.println("Available roles: " + identity.getRoles());
        
        // This runs once per UI session (browser tab/window)
        User user = User.find("username = ?1 and active = true", principalName).firstResult();

        if (user != null) {
            System.out.println("Found user: " + user.getUsername());
            VaadinSession.getCurrent().setAttribute("user", user);
        } else {
            // User not found or inactive - immediately redirect without loading UI
            System.out.println("WARNING: User not found in database for principal: " + principalName);
            System.out.println("Attempting to find similar usernames in database...");
            List<User> allUsers = User.find("active = true").list();
            System.out.println("Active users in database: " + allUsers.stream().map(User::getUsername).toList());
            VaadinSession.getCurrent().setAttribute("user", null);
        }
    }

    /**
     * Retrieves the current user from the Vaadin session.
     *
     * <p>This method provides access to the user information that was stored during
     * the session initialization process. The user object contains all relevant
     * information including personal details, department assignments, and permissions.</p>
     *
     * <p>The method is marked as {@code @Transient} to indicate that this property
     * should not be included in JPA persistence operations, as it represents
     * session state rather than persistent entity data.</p>
     *
     * @return the current User object if authentication was successful, or null
     *         if the user was not found, is inactive, or authentication failed
     * @see User
     */
    @Transient
    public User getUser() {
        return (User) VaadinSession.getCurrent().getAttribute("user");
    }

}
