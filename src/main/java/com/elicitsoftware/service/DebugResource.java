package com.elicitsoftware.service;

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

import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * Debug REST resource for testing and troubleshooting OIDC authentication and authorization.
 *
 * <p>This resource provides debugging endpoints to inspect the current security context,
 * user identity, and role assignments in the Elicit Admin application. It's primarily
 * used during development and troubleshooting to verify that OpenID Connect (OIDC)
 * authentication is working correctly and that users have the expected roles.</p>
 *
 * <p><strong>Primary Use Cases:</strong></p>
 * <ul>
 *   <li><strong>OIDC Testing:</strong> Verify authentication integration is working</li>
 *   <li><strong>Role Verification:</strong> Check that users have correct role assignments</li>
 *   <li><strong>Security Debugging:</strong> Troubleshoot authorization issues</li>
 *   <li><strong>Development Support:</strong> Assist developers in understanding security context</li>
 * </ul>
 *
 * <p><strong>Security Information Provided:</strong></p>
 * <ul>
 *   <li><strong>User Principal:</strong> The authenticated user's name/identifier</li>
 *   <li><strong>Assigned Roles:</strong> All roles granted to the current user</li>
 *   <li><strong>Security Context:</strong> Current authentication state</li>
 * </ul>
 *
 * <p><strong>Expected Roles in Elicit Admin:</strong></p>
 * <ul>
 *   <li><strong>elicit_admin:</strong> Administrative users with full system access</li>
 *   <li><strong>elicit_user:</strong> Regular users with limited access</li>
 * </ul>
 *
 * <p><strong>Usage During Development:</strong></p>
 * <ol>
 *   <li>Ensure user is authenticated through OIDC provider</li>
 *   <li>Access the debug endpoint to view security information</li>
 *   <li>Verify the user has expected roles assigned</li>
 *   <li>Troubleshoot any role or authentication issues</li>
 * </ol>
 *
 * <p><strong>Security Considerations:</strong></p>
 * <ul>
 *   <li><strong>Debug Only:</strong> Should be disabled or secured in production</li>
 *   <li><strong>Information Exposure:</strong> Reveals user and role information</li>
 *   <li><strong>Access Control:</strong> Consider restricting to admin users only</li>
 * </ul>
 *
 * <p><strong>Deployment Notes:</strong></p>
 * <p>⚠️ <strong>Production Warning:</strong> This debug resource should be disabled
 * or properly secured in production environments to prevent information disclosure.
 * Consider using Quarkus profiles to enable only in development/test environments.</p>
 *
 * <p><strong>Example Output:</strong></p>
 * <pre>
 * User: john.doe@company.com
 * Roles: [elicit_admin, elicit_user]
 * </pre>
 *
 * @author Elicit Software
 * @version 1.0
 * @see SecurityIdentity
 * @see io.quarkus.oidc.OidcConfigurationMetadata
 * @since 1.0
 */
// Uncomment out this next line for testing of the OIDC roles
@Path("/debug")
@Produces(MediaType.TEXT_PLAIN)
public class DebugResource {

    /**
     * Injected security identity for accessing current user authentication information.
     *
     * <p>This field provides access to the current security context including the
     * authenticated user's principal and assigned roles. The SecurityIdentity is
     * populated by Quarkus OIDC extension based on the authentication token.</p>
     *
     * <p><strong>Information Available:</strong></p>
     * <ul>
     *   <li><strong>Principal:</strong> User identifier (typically email or username)</li>
     *   <li><strong>Roles:</strong> Set of roles assigned to the user</li>
     *   <li><strong>Attributes:</strong> Additional claims from the OIDC token</li>
     *   <li><strong>Anonymous Status:</strong> Whether the user is authenticated</li>
     * </ul>
     *
     * @see SecurityIdentity#getPrincipal()
     * @see SecurityIdentity#getRoles()
     */
    @Inject
    SecurityIdentity identity;

    /**
     * Default constructor for DebugResource.
     *
     * <p>Creates a new DebugResource instance for handling debug endpoints.
     * This constructor is automatically called by the JAX-RS framework when
     * creating instances to handle incoming REST requests.</p>
     *
     * <p><strong>Framework Integration:</strong></p>
     * <ul>
     *   <li><strong>JAX-RS:</strong> Instantiated by JAX-RS for request handling</li>
     *   <li><strong>CDI:</strong> Supports dependency injection of SecurityIdentity</li>
     *   <li><strong>Quarkus:</strong> Managed by Quarkus container lifecycle</li>
     * </ul>
     *
     * <p><strong>Instance Lifecycle:</strong></p>
     * <ul>
     *   <li>Created per request or as singleton depending on scope</li>
     *   <li>SecurityIdentity is injected after construction</li>
     *   <li>Available for handling authenticated requests</li>
     * </ul>
     *
     * <p><strong>Usage Context:</strong></p>
     * <p>This constructor is called automatically when clients access the
     * {@code /debug} endpoint. No manual instantiation is required.</p>
     */
    public DebugResource() {
        // Default constructor for JAX-RS resource instantiation
    }

    /**
     * Debug endpoint that returns the current user's identity and role information.
     *
     * <p>This GET endpoint provides a simple text response containing the authenticated
     * user's principal name and all assigned roles. It's designed for quick verification
     * of OIDC authentication status and role assignments during development and testing.</p>
     *
     * <p><strong>Response Format:</strong></p>
     * <pre>
     * User: {principal_name}
     * Roles: {comma_separated_roles}
     * </pre>
     *
     * <p><strong>Example Responses:</strong></p>
     * <pre>
     * // Successful authentication with admin role:
     * User: admin@company.com
     * Roles: [elicit_admin]
     *
     * // User with multiple roles:
     * User: john.doe@company.com
     * Roles: [elicit_user, elicit_admin]
     *
     * // User with no specific roles:
     * User: guest@company.com
     * Roles: []
     * </pre>
     *
     * <p><strong>Authentication Requirements:</strong></p>
     * <ul>
     *   <li><strong>OIDC Token:</strong> Valid authentication token required</li>
     *   <li><strong>Active Session:</strong> User must have active authentication session</li>
     *   <li><strong>Token Validity:</strong> Token must not be expired</li>
     * </ul>
     *
     * <p><strong>Troubleshooting Use Cases:</strong></p>
     * <ul>
     *   <li><strong>Role Issues:</strong> Verify user has expected roles (elicit_admin, elicit_user)</li>
     *   <li><strong>Authentication Problems:</strong> Confirm user identity is correct</li>
     *   <li><strong>Authorization Failures:</strong> Check role assignments for access control</li>
     *   <li><strong>OIDC Configuration:</strong> Validate provider integration is working</li>
     * </ul>
     *
     * <p><strong>Access Information:</strong></p>
     * <ul>
     *   <li><strong>URL:</strong> {@code GET /debug}</li>
     *   <li><strong>Content-Type:</strong> {@code text/plain}</li>
     *   <li><strong>Authentication:</strong> Required (OIDC token)</li>
     *   <li><strong>Authorization:</strong> No specific roles required</li>
     * </ul>
     *
     * <p><strong>Security Notes:</strong></p>
     * <ul>
     *   <li><strong>Information Disclosure:</strong> Reveals user identity and roles</li>
     *   <li><strong>Production Risk:</strong> Should be disabled in production environments</li>
     *   <li><strong>Monitoring:</strong> Access should be logged for security auditing</li>
     * </ul>
     *
     * @return a plain text string containing user principal name and roles
     * @throws SecurityException if user is not authenticated (handled by Quarkus)
     */
    @GET
    public String showIdentity() {
        StringBuffer sb = new StringBuffer("User: " + identity.getPrincipal().getName() +
                "\nRoles: " + identity.getRoles().toString() );
        return sb.toString();

    }
}
