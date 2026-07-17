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

import io.quarkus.security.identity.AuthenticationRequestContext;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.SecurityIdentityAugmentor;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Augments the OIDC-derived {@link SecurityIdentity} with roles from the
 * {@code survey.user_roles} table when the identity carries none of the
 * application's Elicit roles.
 *
 * <p>OIDC (Keycloak) remains the primary source of authorization. This
 * augmentor only consults the database when the identity has none of
 * {@code elicit_admin}, {@code elicit_user}, or {@code elicit_importer} --
 * every other Keycloak-provided role (e.g. {@code offline_access} from the
 * realm's default composite role) is ignored for this check.</p>
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

    private static final Set<String> ELICIT_ROLES = Set.of("elicit_admin", "elicit_user", "elicit_importer");

    @Inject
    EntityManager entityManager;

    @Override
    public Uni<SecurityIdentity> augment(SecurityIdentity identity, AuthenticationRequestContext context) {
        if (identity.isAnonymous()) {
            return Uni.createFrom().item(identity);
        }
        boolean hasElicitRole = identity.getRoles().stream().anyMatch(ELICIT_ROLES::contains);
        if (hasElicitRole) {
            return Uni.createFrom().item(withRoleSource(identity, ROLE_SOURCE_OIDC));
        }
        return context.runBlocking(() -> augmentFromDatabase(identity));
    }

    @SuppressWarnings("unchecked")
    private SecurityIdentity augmentFromDatabase(SecurityIdentity identity) {
        String username = identity.getPrincipal().getName();
        List<String> dbRoles = entityManager.createNativeQuery(
                        "SELECT ur.role_name FROM survey.user_roles ur " +
                                "JOIN survey.users u ON u.id = ur.user_id " +
                                "WHERE u.username = :username AND u.active = true", String.class)
                .setParameter("username", username)
                .getResultList();
        if (dbRoles.isEmpty()) {
            return withRoleSource(identity, ROLE_SOURCE_OIDC);
        }
        return QuarkusSecurityIdentity.builder(identity)
                .addRoles(new HashSet<>(dbRoles))
                .addAttribute(ROLE_SOURCE_ATTRIBUTE, ROLE_SOURCE_DATABASE)
                .build();
    }

    private SecurityIdentity withRoleSource(SecurityIdentity identity, String source) {
        return QuarkusSecurityIdentity.builder(identity)
                .addAttribute(ROLE_SOURCE_ATTRIBUTE, source)
                .build();
    }
}
