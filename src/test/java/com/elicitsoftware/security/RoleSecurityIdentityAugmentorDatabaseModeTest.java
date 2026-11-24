package com.elicitsoftware.security;

/*-
 * ***LICENSE_START***
 * Elicit Admin
 * %%
 * Copyright (C) 2025 - 2026 Elicit Software LLC
 * %%
 * PolyForm Noncommercial License 1.0.0
 * <https://polyformproject.org/licenses/noncommercial/1.0.0>
 * ***LICENSE_END***
 */

import com.elicitsoftware.test.DatabaseAuthorizationTestProfile;
import com.elicitsoftware.test.PostgresTestResource;
import io.quarkus.security.identity.AuthenticationRequestContext;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.runtime.QuarkusPrincipal;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Confirms {@link RoleSecurityIdentityAugmentor}'s database fallback runs, and expands the
 * raw grant to its full cumulative set (UC-001 BR-006), when
 * {@code elicit.authorization.mode=DATABASE}.
 *
 * <p>Traceability: UC-001 A3/BR-005/BR-006. The seeded {@code admin}/{@code user} users
 * (V0.0.3) have raw grants in {@code survey.user_roles} (V0.0.11); with no OIDC-supplied role
 * and database mode active, the effective identity should carry the full expanded role set.</p>
 *
 * <p><strong>2026-09 audit finding #6:</strong> this test previously exercised the augmentor
 * indirectly through the (formerly public) {@code GET /api/secured/roles} diagnostic endpoint.
 * That endpoint was first restricted to {@code elicit_admin} and has since been removed; the
 * OIDC diagnostics view (UC-009) shows the same identity to an administrator. The augmentor is
 * invoked directly here instead - a cleaner test that doesn't couple identity augmentation to an
 * unrelated REST resource's authorization policy.</p>
 */
@QuarkusTest
@TestProfile(DatabaseAuthorizationTestProfile.class)
@QuarkusTestResource(PostgresTestResource.class)
class RoleSecurityIdentityAugmentorDatabaseModeTest {

    /** Runs the supplier synchronously and wraps the result, mirroring how the real
     * {@code AuthenticationRequestContext} would execute the blocking database lookup. */
    private static final AuthenticationRequestContext SYNC_CONTEXT =
            supplier -> Uni.createFrom().item(supplier.get());

    @Inject
    RoleSecurityIdentityAugmentor augmentor;

    /** UC-001/A3/BR-006: a raw elicit_admin database grant expands to all three roles. */
    @Test
    void databaseGrantExpandsToFullRoleSet() {
        SecurityIdentity noRoleIdentity = QuarkusSecurityIdentity.builder()
                .setPrincipal(new QuarkusPrincipal("admin"))
                .build();

        SecurityIdentity augmented = augmentor.augment(noRoleIdentity, SYNC_CONTEXT)
                .await().indefinitely();

        assertTrue(augmented.getRoles().contains("elicit_admin"));
        assertTrue(augmented.getRoles().contains("elicit_user"));
        assertFalse(augmented.getRoles().contains("elicit_importer"), "BR-006: no user role implies elicit_importer");
    }

    /** UC-001/A3/BR-006: a raw elicit_user database grant stays elicit_user only. */
    @Test
    void userGrantExpandsToUserOnly() {
        SecurityIdentity noRoleIdentity = QuarkusSecurityIdentity.builder()
                .setPrincipal(new QuarkusPrincipal("user"))
                .build();

        SecurityIdentity augmented = augmentor.augment(noRoleIdentity, SYNC_CONTEXT)
                .await().indefinitely();

        assertTrue(augmented.getRoles().contains("elicit_user"));
        assertFalse(augmented.getRoles().contains("elicit_admin"));
        assertFalse(augmented.getRoles().contains("elicit_importer"));
    }
}
