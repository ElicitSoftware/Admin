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

import com.vaadin.flow.router.BeforeEnterEvent;
import io.quarkus.security.identity.SecurityIdentity;

/**
 * Utility class providing role-based authorization checks for Vaadin views.
 * This class helps implement consistent authorization behavior across all views
 * in the application.
 *
 * @author Elicit Software
 * @version 1.0
 * @since 1.0
 */
public class RoleAuthorizationHelper {

    /**
     * Checks if the current user has the required roles for the application.
     * If the user is authenticated but lacks required roles, redirects to unauthorized page.
     *
     * @param event the BeforeEnterEvent containing navigation information
     * @param identity the SecurityIdentity of the current user
     * @return true if authorization check passes, false if redirected
     */
    public static boolean checkAuthorization(BeforeEnterEvent event, SecurityIdentity identity) {
        // Check if user is authenticated and has required roles
        if (identity != null && !identity.isAnonymous()) {
            // User is authenticated, check if they have required roles
            String[] requiredRoles = {"elicit_user", "elicit_admin", "SPI-Elicit-Users", "SPI-Elicit-Admins"};
            boolean hasRequiredRole = false;
            
            for (String role : requiredRoles) {
                if (identity.hasRole(role)) {
                    hasRequiredRole = true;
                    break;
                }
            }
            
            if (!hasRequiredRole) {
                // User is authenticated but lacks required roles - redirect to unauthorized view
                event.forwardTo("unauthorized");
                return false;
            }
        }
        
        return true;
    }
}
