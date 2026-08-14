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
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Looks up Elicit roles for a user from {@code survey.user_roles}.
 *
 * <p>The lookup runs inside an explicitly activated CDI request context via
 * {@link ActivateRequestContext}, which only takes effect when this method is
 * invoked through this bean's CDI proxy (e.g. from {@code runBlocking} via an
 * injected reference) -- without it the injected {@link EntityManager} has no
 * active transaction or request context to run in.</p>
 *
 * <p>The raw role(s) found are expanded to their full cumulative set via
 * {@link ElicitRoles#expand} before being granted, matching how OIDC-sourced
 * roles are expanded in {@link RoleSecurityIdentityAugmentor}. Only the raw
 * role is ever stored in {@code survey.user_roles}.</p>
 */
@ApplicationScoped
public class UserRoleDatabaseLookup {

    @Inject
    EntityManager entityManager;

    @ActivateRequestContext
    @SuppressWarnings("unchecked")
    public SecurityIdentity augment(SecurityIdentity identity) {
        String username = identity.getPrincipal().getName();
        Log.debugf("Looking up survey.user_roles for principal: %s", username);
        List<String> dbRoles = entityManager.createNativeQuery(
                        "SELECT ur.role_name FROM survey.user_roles ur " +
                                "JOIN survey.users u ON u.id = ur.user_id " +
                                "WHERE u.username = :username AND u.active = true", String.class)
                .setParameter("username", username)
                .getResultList();
        if (dbRoles.isEmpty()) {
            Log.debugf("No database roles found for principal: %s (no active user match); falling back to OIDC role source", username);
            return RoleSecurityIdentityAugmentor.withRoleSource(identity,
                    RoleSecurityIdentityAugmentor.ROLE_SOURCE_OIDC);
        }
        Set<String> expandedRoles = ElicitRoles.expand(new HashSet<>(dbRoles));
        Log.debugf("Found database roles %s (expanded: %s) for principal: %s", dbRoles, expandedRoles, username);
        return QuarkusSecurityIdentity.builder(identity)
                .addRoles(expandedRoles)
                .addAttribute(RoleSecurityIdentityAugmentor.ROLE_SOURCE_ATTRIBUTE,
                        RoleSecurityIdentityAugmentor.ROLE_SOURCE_DATABASE)
                .build();
    }
}
