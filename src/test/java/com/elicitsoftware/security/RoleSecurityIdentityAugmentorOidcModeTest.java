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

import com.elicitsoftware.test.PostgresTestResource;
import io.quarkus.security.identity.AuthenticationRequestContext;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.runtime.QuarkusPrincipal;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Confirms {@link RoleSecurityIdentityAugmentor}'s database fallback is skipped in the
 * default {@code elicit.authorization.mode=OIDC} (no {@link com.elicitsoftware.test.DatabaseAuthorizationTestProfile}
 * applied, so this class boots against the default profile).
 *
 * <p>Traceability: UC-001 BR-005. The seeded {@code admin} user (V0.0.3) has an
 * {@code elicit_admin} row in {@code survey.user_roles} (V0.0.11), but with no OIDC-supplied
 * role and OIDC mode active, no database consultation should occur and no role should be
 * granted.</p>
 *
 * <p><strong>2026-09 audit finding #6:</strong> this test previously exercised the augmentor
 * indirectly through the (formerly public) {@code GET /api/secured/roles} diagnostic endpoint.
 * That endpoint is now restricted to {@code elicit_admin}, so a role-less identity correctly
 * gets 403 before ever reaching the augmented-roles check the original assertions relied on.
 * The augmentor is invoked directly here instead - a cleaner test that doesn't couple identity
 * augmentation to an unrelated REST resource's authorization policy.</p>
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class RoleSecurityIdentityAugmentorOidcModeTest {

    /** Runs the supplier synchronously and wraps the result - {@code context.runBlocking()} is
     * only exercised in DATABASE mode, but a real implementation is still supplied for clarity. */
    private static final AuthenticationRequestContext SYNC_CONTEXT =
            supplier -> Uni.createFrom().item(supplier.get());

    @Inject
    RoleSecurityIdentityAugmentor augmentor;

    /** UC-001/BR-005: no OIDC role + OIDC mode = no role granted, even with a matching DB grant. */
    @Test
    void noOidcRoleAndOidcModeGrantsNoRole() {
        SecurityIdentity noRoleIdentity = QuarkusSecurityIdentity.builder()
                .setPrincipal(new QuarkusPrincipal("admin"))
                .build();

        SecurityIdentity augmented = augmentor.augment(noRoleIdentity, SYNC_CONTEXT)
                .await().indefinitely();

        assertFalse(augmented.getRoles().contains("elicit_admin"));
        assertFalse(augmented.getRoles().contains("elicit_user"));
        assertFalse(augmented.getRoles().contains("elicit_importer"));
    }

    /**
     * UC-001/BR-001 + UC-020/BR-080: an OIDC identity carrying only elicit_analytics counts as
     * role-bearing, so the database is not consulted (the seeded admin's elicit_admin row must
     * not leak in) and analytics passes through unexpanded.
     */
    @Test
    void analyticsOnlyOidcIdentityIsNotSentToDatabase() {
        SecurityIdentity analyticsOnly = QuarkusSecurityIdentity.builder()
                .setPrincipal(new QuarkusPrincipal("admin"))
                .addRole("elicit_analytics")
                .build();

        SecurityIdentity augmented = augmentor.augment(analyticsOnly, SYNC_CONTEXT)
                .await().indefinitely();

        assertTrue(augmented.getRoles().contains("elicit_analytics"));
        assertFalse(augmented.getRoles().contains("elicit_admin"));
        assertFalse(augmented.getRoles().contains("elicit_user"));
        assertEquals(RoleSecurityIdentityAugmentor.ROLE_SOURCE_OIDC,
                augmented.getAttribute(RoleSecurityIdentityAugmentor.ROLE_SOURCE_ATTRIBUTE));
    }
}
