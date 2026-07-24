package com.elicitsoftware.service;

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
 * Role-based access tests for {@link RespondentExportResource}.
 *
 * <p>Traceability: UC-001 (Authenticate and Access the Admin Console) and
 * UC-011/UC-008 — the respondent export endpoint is restricted to the
 * {@code elicit_admin} role. These tests use annotation-based OIDC mocking
 * ({@link TestSecurity}) to assert the security pipeline enforces that role
 * without a live identity provider.</p>
 *
 * <p>The endpoint {@code GET /api/secured/respondent/export} returns 404 for an
 * id that does not exist. A 404 therefore confirms that <em>authorization
 * passed</em> (the request reached the resource); a 401/403 would mean it was
 * blocked. We assert on that distinction rather than on the export payload.</p>
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class RespondentExportSecurityTest {

    private static final String EXPORT_PATH = "/api/secured/respondent/export";
    private static final int NONEXISTENT_ID = 999999;

    /** UC-001/UC-008: an elicit_admin reaches the resource (404 for a missing id, not 401/403). */
    @Test
    @TestSecurity(user = "admin", roles = {"elicit_admin"})
    void adminRoleIsAuthorized() {
        given()
            .queryParam("id", NONEXISTENT_ID)
            .when().get(EXPORT_PATH)
            .then()
            .statusCode(404);
    }

    /** UC-001/UC-008: an authenticated non-admin is forbidden (403). */
    @Test
    @TestSecurity(user = "bob", roles = {"elicit_user"})
    void nonAdminRoleIsForbidden() {
        given()
            .queryParam("id", NONEXISTENT_ID)
            .when().get(EXPORT_PATH)
            .then()
            .statusCode(403);
    }

    /**
     * UC-001: an unauthenticated request is rejected. With OIDC hybrid mode the
     * response may be 401 (Bearer challenge) or a 302 redirect to the provider;
     * either proves the pipeline is active and the endpoint is not public.
     */
    @Test
    void anonymousIsRejected() {
        given()
            .redirects().follow(false)
            .queryParam("id", NONEXISTENT_ID)
            .when().get(EXPORT_PATH)
            .then()
            .statusCode(anyOf(is(401), is(302)));
    }
}
