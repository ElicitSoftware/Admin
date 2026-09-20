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

import com.elicitsoftware.security.RoleSecurityIdentityAugmentor;
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
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.jwt.JsonWebToken;

/**
 * Debug view for displaying authentication and security information.
 * <p>
 * This view provides detailed information about the current user's authentication
 * status, roles, and tokens for debugging purposes. Restricted to {@code elicit_admin}
 * since it can reveal token material; raw tokens are masked unless
 * {@code elicit.debug.reveal-tokens} is explicitly enabled (e.g. in {@code %dev}).
 * </p>
 *
 * @author Elicit Software
 * @version 1.0
 * @since 1.0
 */
@Route(value = "debug", layout = MainLayout.class)
@RolesAllowed("elicit_admin")
public class DebugView extends VerticalLayout implements HasDynamicTitle {

    /**
     * Default constructor for DebugView.
     * <p>
     * Creates a new DebugView instance. Dependencies are injected by CDI,
     * and initialization occurs in the {@link #init()} method.
     * </p>
     */
    public DebugView() {
        // Default constructor
    }

    /**
     * The security identity of the current user.
     */
    @Inject
    SecurityIdentity identity;

    /**
     * Instance holder for the ID token containing user identity information.
     */
    @Inject
    @IdToken
    Instance<JsonWebToken> idTokenInstance;

    /**
     * Instance holder for the access token credential.
     */
    @Inject
    Instance<AccessTokenCredential> accessTokenInstance;

    /**
     * Whether raw token values should be rendered in full. Defaults to {@code false}
     * so tokens are masked everywhere except environments (e.g. {@code %dev}) that
     * explicitly opt in via {@code ELICIT_DEBUG_REVEAL_TOKENS}.
     */
    @ConfigProperty(name = "elicit.debug.reveal-tokens", defaultValue = "false")
    boolean revealTokens;

    /**
     * Initializes the debug view with authentication information.
     */
    @PostConstruct
    public void init() {
        add(new H1(getTranslation("debugView.title")));

        StringBuilder sb = new StringBuilder();

        // Basic identity information
        sb.append(getTranslation("debugView.user", identity.getPrincipal().getName())).append("\n");
        sb.append(getTranslation("debugView.isAnonymous", identity.isAnonymous())).append("\n");
        sb.append(getTranslation("debugView.roles", identity.getRoles())).append("\n");
        String roleSource = identity.getAttribute(RoleSecurityIdentityAugmentor.ROLE_SOURCE_ATTRIBUTE);
        sb.append(getTranslation("debugView.roleSource", roleSource)).append("\n");
        sb.append(getTranslation("debugView.hasAdminRole", identity.hasRole("elicit_admin"))).append("\n");
        sb.append(getTranslation("debugView.hasUserRole", identity.hasRole("elicit_user"))).append("\n");
        sb.append("\n");

        // ID Token information
        try {
            if (idTokenInstance.isResolvable()) {
                JsonWebToken idToken = idTokenInstance.get();
                sb.append(getTranslation("debugView.idToken", maskToken(idToken.getRawToken(), revealTokens))).append("\n\n");
            } else {
                sb.append(getTranslation("debugView.idTokenUnavailable")).append("\n\n");
            }
        } catch (Exception e) {
            sb.append(getTranslation("debugView.idTokenError", e.getMessage())).append("\n\n");
        }

        // Access Token information
        try {
            if (accessTokenInstance.isResolvable()) {
                AccessTokenCredential accessToken = accessTokenInstance.get();
                sb.append(getTranslation("debugView.accessToken", maskToken(accessToken.getToken(), revealTokens))).append("\n\n");
            } else {
                sb.append(getTranslation("debugView.accessTokenUnavailable")).append("\n\n");
            }
        } catch (Exception e) {
            sb.append(getTranslation("debugView.accessTokenError", e.getMessage())).append("\n\n");
        }

        Pre debugInfo = new Pre(sb.toString());
        debugInfo.addClassName("debug-info");

        add(debugInfo);
    }

    /**
     * Masks a raw token to its last 4 characters unless {@code reveal} is {@code true}.
     *
     * @param rawToken the raw token value
     * @param reveal whether to return the token unmasked
     * @return the full token if {@code reveal} is {@code true}, otherwise a masked form
     */
    static String maskToken(String rawToken, boolean reveal) {
        if (reveal || rawToken == null) {
            return rawToken;
        }
        int visible = Math.min(4, rawToken.length());
        return "*".repeat(Math.max(0, rawToken.length() - visible)) + rawToken.substring(rawToken.length() - visible);
    }

    @Override
    public String getPageTitle() {
        return getTranslation("debugView.pageTitle");
    }
}