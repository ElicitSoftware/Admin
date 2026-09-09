package com.elicitsoftware.rest;

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
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.is;

/**
 * Role-based access tests for {@link TokenService#roles()}.
 *
 * <p>Traceability: UC-001 (Authenticate and Access the Admin Console), UC-015 (Security
 * Diagnostics). 2026-09 audit finding #6: {@code /api/secured/roles} was previously
 * {@code @PermitAll} and publicly whitelisted, echoing back a caller's full JWT claim set
 * and validation-failure detail for any bearer token presented. It is now restricted to
 * {@code elicit_admin} and removed from the public permission list.</p>
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class TokenServiceRolesSecurityTest {

    private static final String ROLES_PATH = "/api/secured/roles";

    /** #6: an elicit_admin can reach the diagnostic endpoint. */
    @Test
    @TestSecurity(user = "admin", roles = {"elicit_admin"})
    void adminRoleIsAuthorized() {
        given()
            .when().get(ROLES_PATH)
            .then()
            .statusCode(200);
    }

    /** #6: an authenticated non-admin is forbidden. */
    @Test
    @TestSecurity(user = "bob", roles = {"elicit_user"})
    void nonAdminRoleIsForbidden() {
        given()
            .when().get(ROLES_PATH)
            .then()
            .statusCode(403);
    }

    /**
     * #6: an unauthenticated request is rejected rather than served. With OIDC hybrid mode
     * the response may be 401 (Bearer challenge) or a 302 redirect to the provider; either
     * proves the endpoint is no longer public.
     */
    @Test
    void anonymousIsRejected() {
        given()
            .redirects().follow(false)
            .when().get(ROLES_PATH)
            .then()
            .statusCode(anyOf(is(401), is(302)));
    }
}
