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
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

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
 * <p>{@code @TestSecurity} does not apply any {@code SecurityIdentityAugmentor} by default --
 * {@link RoleSecurityIdentityAugmentor} must be listed explicitly via {@code augmentors} for
 * these tests to exercise it.</p>
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class RoleSecurityIdentityAugmentorOidcModeTest {

    /** UC-001/BR-005: no OIDC role + OIDC mode = no role granted, even with a matching DB grant. */
    @Test
    @TestSecurity(user = "admin", roles = {}, augmentors = RoleSecurityIdentityAugmentor.class)
    void noOidcRoleAndOidcModeGrantsNoRole() {
        given()
            .when().get("/api/secured/roles")
            .then()
            .statusCode(200)
            .body(not(containsString("elicit_admin")))
            .body(not(containsString("elicit_user")))
            .body(not(containsString("elicit_importer")));
    }
}
