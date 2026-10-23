package com.elicitsoftware.security;

/*-
 * ***LICENSE_START***
 * Elicit Survey
 * %%
 * Copyright (C) 2025 - 2026 The Regents of the University of Michigan - Rogel Cancer Center
 * %%
 * PolyForm Noncommercial License 1.0.0
 * <https://polyformproject.org/licenses/noncommercial/1.0.0>
 * ***LICENSE_END***
 */

import com.elicitsoftware.security.AuthorizationModeConfig.AuthorizationMode;
import com.elicitsoftware.test.ScriptedEntityManager;
import io.quarkus.security.identity.AuthenticationRequestContext;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.runtime.QuarkusPrincipal;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UC-001: Authenticate and Access the Admin Console and UC-016: Manage User Role Assignments —
 * how an identity's Elicit roles are resolved, without Quarkus.
 * <p>
 * Covers BR-001 (recognized roles), BR-002 (OIDC is the primary authority), BR-003 (only active
 * users are looked up), BR-005 (the database fallback is gated by the authorization mode) and
 * BR-006 (roles are cumulative).
 */
class RoleSecurityIdentityAugmentorUnitTest {

    private static final AuthenticationRequestContext BLOCKING = supplier -> Uni.createFrom().item(supplier);

    private int lookups;

    private RoleSecurityIdentityAugmentor augmentor(AuthorizationMode mode, Set<String> databaseRoles) {
        RoleSecurityIdentityAugmentor augmentor = new RoleSecurityIdentityAugmentor();
        augmentor.authorizationModeConfig = new AuthorizationModeConfig();
        augmentor.authorizationModeConfig.mode = mode;
        augmentor.userRoleDatabaseLookup = new UserRoleDatabaseLookup() {
            @Override
            public SecurityIdentity augment(SecurityIdentity identity) {
                lookups++;
                return QuarkusSecurityIdentity.builder(identity).addRoles(databaseRoles)
                        .addAttribute(RoleSecurityIdentityAugmentor.ROLE_SOURCE_ATTRIBUTE,
                                RoleSecurityIdentityAugmentor.ROLE_SOURCE_DATABASE).build();
            }
        };
        return augmentor;
    }

    private static SecurityIdentity identity(String... roles) {
        return QuarkusSecurityIdentity.builder().setPrincipal(new QuarkusPrincipal("alice")).addRoles(Set.of(roles)).build();
    }

    private static SecurityIdentity augment(RoleSecurityIdentityAugmentor augmentor, SecurityIdentity identity) {
        return augmentor.augment(identity, BLOCKING).await().indefinitely();
    }

    @Test
    void given_anAnonymousIdentity_when_augmented_then_itIsReturnedUntouched() {
        SecurityIdentity anonymous = QuarkusSecurityIdentity.builder().setAnonymous(true).build();
        assertSame(anonymous, augment(augmentor(AuthorizationMode.DATABASE, Set.of("elicit_admin")), anonymous));
        assertEquals(0, lookups);
    }

    @Test
    void given_anOidcElicitRole_when_augmented_then_itIsExpandedAndStampedOidc() {
        SecurityIdentity result = augment(augmentor(AuthorizationMode.DATABASE, Set.of()), identity("elicit_user", "offline_access"));

        assertEquals(Set.of("elicit_user", "elicit_importer", "offline_access"), result.getRoles());
        assertEquals("oidc", result.getAttribute("roleSource"));
        assertEquals("alice", result.getPrincipal().getName());
        assertEquals(0, lookups, "OIDC roles win: the database is not consulted");
    }

    @Test
    void given_noElicitRoleInOidcMode_when_augmented_then_noRoleIsAddedAndTheDatabaseIsNotAsked() {
        SecurityIdentity result = augment(augmentor(AuthorizationMode.OIDC, Set.of("elicit_admin")), identity("offline_access"));

        assertEquals(Set.of("offline_access"), result.getRoles());
        assertEquals("oidc", result.getAttribute("roleSource"));
        assertEquals(0, lookups);
    }

    @Test
    void given_noElicitRoleInDatabaseMode_when_augmented_then_theDatabaseSuppliesTheRoles() {
        SecurityIdentity result = augment(augmentor(AuthorizationMode.DATABASE, Set.of("elicit_importer")), identity("offline_access"));

        assertEquals(1, lookups);
        assertTrue(result.getRoles().contains("elicit_importer"));
        assertEquals("database", result.getAttribute("roleSource"));
    }

    @Test
    void given_anAuthorizationModeConfig_when_read_then_onlyDatabaseIsDatabaseMode() {
        AuthorizationModeConfig config = new AuthorizationModeConfig();
        config.mode = AuthorizationMode.DATABASE;
        assertTrue(config.isDatabaseMode());
        assertEquals(AuthorizationMode.DATABASE, config.getMode());
        config.mode = AuthorizationMode.OIDC;
        assertFalse(config.isDatabaseMode());
        assertEquals(AuthorizationMode.OIDC, config.getMode());
    }

    @Test
    void given_aUserWithADatabaseRole_when_lookedUp_then_theRoleIsExpandedAndStampedDatabase() {
        ScriptedEntityManager db = new ScriptedEntityManager(call -> List.of("elicit_admin"));
        UserRoleDatabaseLookup lookup = new UserRoleDatabaseLookup();
        lookup.entityManager = db.entityManager();

        SecurityIdentity result = lookup.augment(identity("offline_access"));

        assertEquals(Set.of("elicit_admin", "elicit_user", "elicit_importer", "offline_access"), result.getRoles());
        assertEquals("database", result.getAttribute("roleSource"));
        assertEquals("alice", db.calls().get(0).param("username"));
        assertTrue(db.calls().get(0).is("u.active = true"), "only active users are looked up");
    }

    @Test
    void given_aUserWithoutADatabaseRole_when_lookedUp_then_noRoleIsAddedAndTheSourceIsOidc() {
        UserRoleDatabaseLookup lookup = new UserRoleDatabaseLookup();
        lookup.entityManager = new ScriptedEntityManager(call -> List.of()).entityManager();

        SecurityIdentity result = lookup.augment(identity("offline_access"));

        assertEquals(Set.of("offline_access"), result.getRoles());
        assertEquals("oidc", result.getAttribute("roleSource"));
    }
}
