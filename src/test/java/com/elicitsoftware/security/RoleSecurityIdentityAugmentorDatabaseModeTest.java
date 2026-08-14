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

import com.elicitsoftware.test.DatabaseAuthorizationTestProfile;
import com.elicitsoftware.test.PostgresTestResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.security.TestSecurity;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

/**
 * Confirms {@link RoleSecurityIdentityAugmentor}'s database fallback runs, and expands the
 * raw grant to its full cumulative set (UC-001 BR-006), when
 * {@code elicit.authorization.mode=DATABASE}.
 *
 * <p>Traceability: UC-001 A3/BR-005/BR-006. The seeded {@code admin} user (V0.0.3) has a raw
 * {@code elicit_admin} grant in {@code survey.user_roles} (V0.0.11); with no OIDC-supplied role
 * and database mode active, the effective identity should carry all three roles.</p>
 *
 * <p>{@code @TestSecurity} does not apply any {@code SecurityIdentityAugmentor} by default --
 * {@link RoleSecurityIdentityAugmentor} must be listed explicitly via {@code augmentors} for
 * these tests to exercise it.</p>
 */
@QuarkusTest
@TestProfile(DatabaseAuthorizationTestProfile.class)
@QuarkusTestResource(PostgresTestResource.class)
class RoleSecurityIdentityAugmentorDatabaseModeTest {

    /** UC-001/A3/BR-006: a raw elicit_admin database grant expands to all three roles. */
    @Test
    @TestSecurity(user = "admin", roles = {}, augmentors = RoleSecurityIdentityAugmentor.class)
    void databaseGrantExpandsToFullRoleSet() {
        given()
            .when().get("/api/secured/roles")
            .then()
            .statusCode(200)
            .body(containsString("elicit_admin"))
            .body(containsString("elicit_user"))
            .body(containsString("elicit_importer"));
    }

    /** UC-001/A3/BR-006: a raw elicit_user database grant expands to user + importer only. */
    @Test
    @TestSecurity(user = "user", roles = {}, augmentors = RoleSecurityIdentityAugmentor.class)
    void userGrantExpandsToUserAndImporterOnly() {
        given()
            .when().get("/api/secured/roles")
            .then()
            .statusCode(200)
            .body(containsString("elicit_user"))
            .body(containsString("elicit_importer"));
    }
}
