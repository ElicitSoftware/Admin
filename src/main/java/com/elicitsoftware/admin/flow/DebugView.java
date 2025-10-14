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

import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Pre;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.Route;
import io.quarkus.oidc.AccessTokenCredential;
import io.quarkus.oidc.IdToken;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.eclipse.microprofile.jwt.JsonWebToken;

/**
 * Debug view for displaying authentication and security information.
 * <p>
 * This view provides detailed information about the current user's authentication
 * status, roles, and tokens for debugging purposes.
 * </p>
 *
 * @author Elicit Software
 * @version 1.0
 * @since 1.0
 */
@Route(value = "debug", layout = MainLayout.class)
@RolesAllowed({"elicit_admin", "elicit_user"})
public class DebugView extends VerticalLayout implements HasDynamicTitle {

    @Inject
    SecurityIdentity identity;

    @Inject
    @IdToken
    Instance<JsonWebToken> idTokenInstance;

    @Inject
    Instance<AccessTokenCredential> accessTokenInstance;

    /**
     * Initializes the debug view with authentication information.
     */
    @PostConstruct
    public void init() {
        add(new H1("Debug Information"));
        
        StringBuilder sb = new StringBuilder();
        
        // Basic identity information
        sb.append("User: ").append(identity.getPrincipal().getName()).append("\n");
        sb.append("Is Anonymous: ").append(identity.isAnonymous()).append("\n");
        sb.append("Roles: ").append(identity.getRoles()).append("\n");
        sb.append("Has elicit_admin: ").append(identity.hasRole("elicit_admin")).append("\n");
        sb.append("Has elicit_user: ").append(identity.hasRole("elicit_user")).append("\n");
        sb.append("\n");

        // ID Token information
        try {
            if (idTokenInstance.isResolvable()) {
                JsonWebToken idToken = idTokenInstance.get();
                sb.append("ID Token: ").append(idToken.getRawToken()).append("\n\n");
            } else {
                sb.append("ID Token: Not available or resolvable\n\n");
            }
        } catch (Exception e) {
            sb.append("ID Token Error: ").append(e.getMessage()).append("\n\n");
        }

        // Access Token information
        try {
            if (accessTokenInstance.isResolvable()) {
                AccessTokenCredential accessToken = accessTokenInstance.get();
                sb.append("Access Token: ").append(accessToken.getToken()).append("\n\n");
            } else {
                sb.append("Access Token: Not available or resolvable\n\n");
            }
        } catch (Exception e) {
            sb.append("Access Token Error: ").append(e.getMessage()).append("\n\n");
        }

        Pre debugInfo = new Pre(sb.toString());
        debugInfo.getStyle().set("background", "#f5f5f5");
        debugInfo.getStyle().set("padding", "1em");
        debugInfo.getStyle().set("border", "1px solid #ddd");
        debugInfo.getStyle().set("border-radius", "4px");
        debugInfo.getStyle().set("font-family", "monospace");
        debugInfo.getStyle().set("white-space", "pre-wrap");
        debugInfo.getStyle().set("overflow-x", "auto");
        
        add(debugInfo);
    }

    @Override
    public String getPageTitle() {
        return "Debug - Elicit Admin";
    }
}