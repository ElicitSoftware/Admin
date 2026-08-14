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

import io.quarkus.logging.Log;
import io.quarkus.security.identity.AuthenticationRequestContext;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.SecurityIdentityAugmentor;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.HashSet;
import java.util.Set;

/**
 * Augments the OIDC-derived {@link SecurityIdentity} with roles from the
 * {@code survey.user_roles} table when the identity carries none of the
 * application's Elicit roles, and expands whichever raw role(s) the identity
 * ends up with to their full cumulative set (see {@link ElicitRoles#expand}).
 *
 * <p>OIDC (Keycloak) remains the primary source of authorization. This
 * augmentor only consults the database when {@link AuthorizationModeConfig}
 * is in {@code DATABASE} mode <em>and</em> the identity has none of
 * {@code elicit_admin}, {@code elicit_user}, or {@code elicit_importer} --
 * every other Keycloak-provided role (e.g. {@code offline_access} from the
 * realm's default composite role) is ignored for this check. In the default
 * {@code OIDC} mode, an identity with no OIDC-supplied Elicit role is granted
 * no role at all -- the database is never consulted.</p>
 *
 * <p>Every authenticated identity is stamped with a {@value #ROLE_SOURCE_ATTRIBUTE}
 * attribute ({@value #ROLE_SOURCE_OIDC} or {@value #ROLE_SOURCE_DATABASE}) so
 * callers can tell which system provided the roles currently in effect.</p>
 */
@ApplicationScoped
public class RoleSecurityIdentityAugmentor implements SecurityIdentityAugmentor {

    public static final String ROLE_SOURCE_ATTRIBUTE = "roleSource";
    public static final String ROLE_SOURCE_OIDC = "oidc";
    public static final String ROLE_SOURCE_DATABASE = "database";

    @Inject
    UserRoleDatabaseLookup userRoleDatabaseLookup;

    @Inject
    AuthorizationModeConfig authorizationModeConfig;

    @Override
    public Uni<SecurityIdentity> augment(SecurityIdentity identity, AuthenticationRequestContext context) {
        if (identity.isAnonymous()) {
            Log.debug("Skipping role augmentation for anonymous identity");
            return Uni.createFrom().item(identity);
        }
        String principalName = identity.getPrincipal().getName();
        boolean hasElicitRole = identity.getRoles().stream().anyMatch(ElicitRoles.ALL::contains);
        if (hasElicitRole) {
            Log.debugf("Principal %s already has an Elicit role from OIDC (roles: %s)", principalName, identity.getRoles());
            return Uni.createFrom().item(withExpandedRoles(identity, ROLE_SOURCE_OIDC));
        }
        if (!authorizationModeConfig.isDatabaseMode()) {
            Log.debugf("Principal %s has no Elicit role from OIDC (roles: %s); authorization mode is OIDC, skipping database lookup",
                    principalName, identity.getRoles());
            return Uni.createFrom().item(withRoleSource(identity, ROLE_SOURCE_OIDC));
        }
        Log.debugf("Principal %s has no Elicit role from OIDC (roles: %s); checking database", principalName, identity.getRoles());
        return context.runBlocking(() -> userRoleDatabaseLookup.augment(identity));
    }

    static SecurityIdentity withRoleSource(SecurityIdentity identity, String source) {
        return QuarkusSecurityIdentity.builder(identity)
                .addAttribute(ROLE_SOURCE_ATTRIBUTE, source)
                .build();
    }

    /**
     * Expands the identity's current Elicit roles to their full cumulative set
     * (see {@link ElicitRoles#expand}) and stamps the given role source.
     */
    static SecurityIdentity withExpandedRoles(SecurityIdentity identity, String source) {
        Set<String> rawElicitRoles = new HashSet<>(identity.getRoles());
        rawElicitRoles.retainAll(ElicitRoles.ALL);
        Set<String> expanded = ElicitRoles.expand(rawElicitRoles);
        return QuarkusSecurityIdentity.builder(identity)
                .addRoles(expanded)
                .addAttribute(ROLE_SOURCE_ATTRIBUTE, source)
                .build();
    }
}
