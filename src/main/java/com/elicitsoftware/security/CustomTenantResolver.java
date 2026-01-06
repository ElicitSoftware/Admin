package com.elicitsoftware.security;

/*-
 * ***LICENSE_START***
 * Elicit Admin
 * %%
 * Copyright (C) 2025 The Regents of the University of Michigan - Rogel Cancer Center
 * %%
 * PolyForm Noncommercial License 1.0.0
 * <https://polyformproject.org/licenses/noncommercial/1.0.0>
 * ***LICENSE_END***
 */

import io.quarkus.oidc.TenantResolver;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Custom OIDC tenant resolver that selects the appropriate authentication provider
 * based on the request path.
 * 
 * <p>This resolver supports multi-tenant authentication:
 * <ul>
 *   <li><strong>Azure tenant:</strong> For API paths (/api/*) using Azure AD tokens</li>
 *   <li><strong>Default tenant (Keycloak):</strong> For UI paths using browser-based authentication</li>
 * </ul>
 */
@ApplicationScoped
public class CustomTenantResolver implements TenantResolver {

    @Override
    public String resolve(RoutingContext context) {
        String path = context.request().path();
        
        // Use Azure AD tenant for API endpoints
        if (path != null && path.startsWith("/api/")) {
            return "azure";
        }
        
        // Use default Keycloak tenant for all other paths
        return "default";
    }
}
