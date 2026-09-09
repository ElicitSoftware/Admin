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

import java.nio.charset.StandardCharsets;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.is;

/**
 * Booted HTTP-layer tests for {@link SurveyDefinitionImportResource}.
 *
 * <p>Traceability: UC-001 (Authenticate and Access the Admin Console) and UC-014 (Import Survey
 * Definition). This resource had zero test coverage before this class.</p>
 *
 * <p>The success case imports a survey-only file: {@code surveys:} is the one record type with
 * no FK dependency on any other table, so it needs no pre-seeded fixture data to succeed - unlike
 * a full definition tree, which would require data committed on a separate connection from this
 * test method's own (default-rolled-back) transaction. Deeper round-trip coverage across the full
 * definition tree lives in {@code SurveyDefinitionImportServiceTest}, which calls the services
 * directly.</p>
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class SurveyDefinitionImportResourceTest {

    private static final String IMPORT_PATH = "/api/secured/survey/import";

    private byte[] bytes(String content) {
        return content.getBytes(StandardCharsets.UTF_8);
    }

    /** UC-001/UC-008: an elicit_admin reaches the resource (a 400 on this malformed file, not 401/403). */
    @Test
    @TestSecurity(user = "admin", roles = {"elicit_admin"})
    void adminRoleIsAuthorized() {
        given()
                .multiPart("file", "survey.elicit", bytes("not a valid export file"), "application/octet-stream")
                .when().post(IMPORT_PATH)
                .then()
                .statusCode(400);
    }

    /** UC-001/UC-008: an authenticated non-admin is forbidden (403). */
    @Test
    @TestSecurity(user = "bob", roles = {"elicit_user"})
    void nonAdminRoleIsForbidden() {
        given()
                .multiPart("file", "survey.elicit", bytes("not a valid export file"), "application/octet-stream")
                .when().post(IMPORT_PATH)
                .then()
                .statusCode(403);
    }

    /** UC-001: an unauthenticated request is rejected, proving the endpoint is not public. */
    @Test
    void anonymousIsRejected() {
        given()
                .redirects().follow(false)
                .multiPart("file", "survey.elicit", bytes("not a valid export file"), "application/octet-stream")
                .when().post(IMPORT_PATH)
                .then()
                .statusCode(anyOf(is(401), is(302)));
    }

    /** UC-014: a request with no "file" part is rejected before the service is even called. */
    @Test
    @TestSecurity(user = "admin", roles = {"elicit_admin"})
    void missingFilePartReturnsBadRequest() {
        given()
                .multiPart("username", "admin")
                .when().post(IMPORT_PATH)
                .then()
                .statusCode(400)
                .body("message", is("No file provided in the 'file' field"));
    }

    /** UC-014: a file that never validates its header returns a structured failure, not a 500. */
    @Test
    @TestSecurity(user = "admin", roles = {"elicit_admin"})
    void malformedFileReturnsBadRequestWithoutServerError() {
        given()
                .multiPart("file", "survey.elicit", bytes("surveys: 1|Name|1|Title|||\n"), "application/octet-stream")
                .when().post(IMPORT_PATH)
                .then()
                .statusCode(400)
                .body("success", is(false));
    }

    /**
     * UC-014: a well-formed file for the one record type with no FK dependency on any other
     * table (a survey, which is the root of the tree) imports successfully end-to-end through
     * the real multipart POST.
     */
    @Test
    @TestSecurity(user = "admin", roles = {"elicit_admin"})
    void wellFormedSurveyOnlyFileImportsSuccessfully() {
        String content = "# ELICIT_SURVEY_EXPORT_V1\n\nsurveys: 1|REST Import Survey|1|Title|||\n";

        given()
                .multiPart("file", "survey.elicit", bytes(content), "application/octet-stream")
                .when().post(IMPORT_PATH)
                .then()
                .statusCode(200)
                .body("success", is(true))
                .body("recordsImported", is(1))
                .body("counts.surveys", is(1));
    }
}
