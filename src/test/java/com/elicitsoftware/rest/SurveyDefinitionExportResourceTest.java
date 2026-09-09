package com.elicitsoftware.rest;

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

import com.elicitsoftware.test.PostgresTestResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

/**
 * Booted HTTP-layer tests for {@link SurveyDefinitionExportResource}.
 *
 * <p>Traceability: UC-001 (Authenticate and Access the Admin Console) and UC-013 (Export Survey
 * Definition). This resource had zero test coverage before this class. The success case exports
 * the bootstrap-seeded survey id=1 ({@code V0.0.0.1__TEST_BOOTSTRAP.sql}) rather than a
 * test-method-local fixture, since a GET request is handled on a separate connection from the
 * test method's own (default-rolled-back) transaction and would not see uncommitted writes.
 * Deeper round-trip coverage across the full definition tree lives in
 * {@code SurveyDefinitionImportServiceTest}, which calls the services directly.</p>
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class SurveyDefinitionExportResourceTest {

    private static final String EXPORT_PATH = "/api/secured/survey/export";
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

    /** UC-001: an unauthenticated request is rejected, proving the endpoint is not public. */
    @Test
    void anonymousIsRejected() {
        given()
                .redirects().follow(false)
                .queryParam("id", NONEXISTENT_ID)
                .when().get(EXPORT_PATH)
                .then()
                .statusCode(anyOf(is(401), is(302)));
    }

    /** UC-013: a request with no id query parameter is rejected before the service is called. */
    @Test
    @TestSecurity(user = "admin", roles = {"elicit_admin"})
    void missingIdReturnsBadRequest() {
        given()
                .when().get(EXPORT_PATH)
                .then()
                .statusCode(400)
                .body(is("Missing required query parameter: id"));
    }

    /** UC-013: a nonexistent survey id returns 404 with the service's exception message. */
    @Test
    @TestSecurity(user = "admin", roles = {"elicit_admin"})
    void nonexistentSurveyReturnsNotFound() {
        given()
                .queryParam("id", NONEXISTENT_ID)
                .when().get(EXPORT_PATH)
                .then()
                .statusCode(404)
                .body(containsString("Survey not found"));
    }

    /** UC-013: exporting the bootstrap-seeded survey succeeds with the expected file headers. */
    @Test
    @TestSecurity(user = "admin", roles = {"elicit_admin"})
    void existingSurveyExportsSuccessfully() {
        given()
                .queryParam("id", 1)
                .when().get(EXPORT_PATH)
                .then()
                .statusCode(200)
                .header("Content-Disposition", containsString("survey_1_definition.elicit"))
                .body(containsString("# ELICIT_SURVEY_EXPORT_V1"));
    }
}
