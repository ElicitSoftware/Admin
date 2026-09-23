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
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

/**
 * Booted HTTP-layer tests for {@link RespondentImportResource}.
 *
 * <p>Traceability: UC-001 (Authenticate and Access the Admin Console) and UC-012 (Import
 * Respondent Data). {@link RespondentImportResource} had zero test coverage before this class -
 * unlike {@link RespondentExportResource} (see {@link RespondentExportSecurityTest}), which was
 * already covered for role gating.</p>
 *
 * <p>These tests deliberately avoid pre-seeding complex fixture data through Panache: a write
 * made by the test method under the default transaction is not visible to the separate
 * connection/thread the multipart POST is handled on, so any success-path assertion here uses
 * only {@code survey.surveys(id=1)}, which the test bootstrap seeds and Flyway commits at
 * container startup - not something this test's own transaction created. Deeper round-trip
 * coverage across all six record types lives in {@code RespondentImportServiceTest}, which calls
 * the service directly and can safely rely on {@code @TestTransaction}-scoped fixtures.</p>
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class RespondentImportResourceTest {

    private static final String IMPORT_PATH = "/api/secured/respondent/import";

    private byte[] bytes(String content) {
        return content.getBytes(StandardCharsets.UTF_8);
    }

    /** UC-001/UC-008: an elicit_admin reaches the resource (a 400 on this near-empty file, not 401/403). */
    @Test
    @TestSecurity(user = "admin", roles = {"elicit_admin"})
    void adminRoleIsAuthorized() {
        given()
                .multiPart("file", "export.elicit", bytes("not a valid export file"), "application/octet-stream")
                .when().post(IMPORT_PATH)
                .then()
                .statusCode(400);
    }

    /** UC-001/UC-008: an authenticated non-admin is forbidden (403). */
    @Test
    @TestSecurity(user = "bob", roles = {"elicit_user"})
    void nonAdminRoleIsForbidden() {
        given()
                .multiPart("file", "export.elicit", bytes("not a valid export file"), "application/octet-stream")
                .when().post(IMPORT_PATH)
                .then()
                .statusCode(403);
    }

    /** UC-001: an unauthenticated request is rejected, proving the endpoint is not public. */
    @Test
    void anonymousIsRejected() {
        given()
                .redirects().follow(false)
                .multiPart("file", "export.elicit", bytes("not a valid export file"), "application/octet-stream")
                .when().post(IMPORT_PATH)
                .then()
                .statusCode(anyOf(is(401), is(302)));
    }

    /** UC-012: a request with no "file" part is rejected before the service is even called. */
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

    /** UC-012: a file that never validates its header returns a structured failure, not a 500. */
    @Test
    @TestSecurity(user = "admin", roles = {"elicit_admin"})
    void malformedFileReturnsBadRequestWithoutServerError() {
        given()
                .multiPart("file", "export.elicit", bytes("respondents: 1|tok|0||\n"), "application/octet-stream")
                .when().post(IMPORT_PATH)
                .then()
                .statusCode(400)
                .body("success", is(false));
    }

    /**
     * UC-012: a well-formed file for the one record type that needs no other fixture data
     * (a respondent, which only FKs to the bootstrap-seeded survey id=1) imports successfully
     * end-to-end through the real multipart POST.
     */
    @Test
    @TestSecurity(user = "admin", roles = {"elicit_admin"})
    void wellFormedRespondentOnlyFileImportsSuccessfully() {
        String content = "# ELICIT_EXPORT_V2\n\n"
                + "respondents: 00000000-0000-0000-0000-000000000001|REST-IMPORT-TOK|true|0|2026-01-01T00:00:00-05:00||\n";

        given()
                .multiPart("file", "export.elicit", bytes(content), "application/octet-stream")
                .when().post(IMPORT_PATH)
                .then()
                .statusCode(200)
                .body("success", is(true))
                .body("recordsImported", is(1))
                .body("counts.respondents", is(1));
    }

    /**
     * UC-012 A4: a file whose survey key is unknown here is a validation failure - 400 with the
     * administrator-facing message, not the generic 500 with a correlation id.
     */
    @Test
    @TestSecurity(user = "admin", roles = {"elicit_admin"})
    void unresolvableReferenceReturnsBadRequestWithMessage() {
        String content = "# ELICIT_EXPORT_V2\n\n"
                + "respondents: 00000000-0000-0000-0000-0000000000ee|REST-NOSURVEY|true|0|2026-01-01T00:00:00-05:00||\n";

        given()
                .multiPart("file", "export.elicit", bytes(content), "application/octet-stream")
                .when().post(IMPORT_PATH)
                .then()
                .statusCode(400)
                .body("success", is(false))
                .body("message", containsString("No survey with survey_key 00000000-0000-0000-0000-0000000000ee"));
    }

    /** UC-012 A8: a legacy V1 file is refused as a structured 400 carrying the re-export instruction. */
    @Test
    @TestSecurity(user = "admin", roles = {"elicit_admin"})
    void legacyV1FileReturnsBadRequestWithReExportInstruction() {
        String content = "# ELICIT_EXPORT_V1\n\nrespondents: 1|REST-V1|0|2026-01-01T00:00:00-05:00|\n";

        given()
                .multiPart("file", "export.elicit", bytes(content), "application/octet-stream")
                .when().post(IMPORT_PATH)
                .then()
                .statusCode(400)
                .body("success", is(false))
                .body("message", containsString("Re-export the respondent from the source instance"));
    }
}
