package com.elicitsoftware.security;

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

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Exposes the {@code elicit.authorization.mode} toggle ({@code ELICIT_AUTHORIZATION} env var).
 *
 * <p>This controls two things: whether {@link RoleSecurityIdentityAugmentor} consults the
 * {@code survey.user_roles} database fallback at all, and whether the Edit User admin UI
 * shows its database role-assignment section. It does not affect OIDC-supplied roles, which
 * always take precedence when present, in either mode.</p>
 */
@ApplicationScoped
public class AuthorizationModeConfig {

    /** The two supported authorization sources for Elicit roles. */
    public enum AuthorizationMode {
        /** Elicit roles come only from OIDC (Keycloak); the database fallback is never consulted. */
        OIDC,
        /** Elicit roles fall back to {@code survey.user_roles} when OIDC supplies none. */
        DATABASE
    }

    @ConfigProperty(name = "elicit.authorization.mode", defaultValue = "OIDC")
    AuthorizationMode mode;

    /** Creates the config bean; {@code mode} is populated by CDI config injection. */
    public AuthorizationModeConfig() {
    }

    /** Returns the configured authorization mode. */
    public AuthorizationMode getMode() {
        return mode;
    }

    /** Returns {@code true} when the configured mode is {@link AuthorizationMode#DATABASE}. */
    public boolean isDatabaseMode() {
        return mode == AuthorizationMode.DATABASE;
    }
}
