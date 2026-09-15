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
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.is;

/**
 * Booted HTTP-layer tests for {@link SurveyDefinitionUpdateResource}.
 *
 * <p>Traceability: UC-001 (Authenticate and Access the Admin Console) and UC-017 (Update Survey
 * Definition). This resource had zero test coverage before this class.</p>
 *
 * <p>Tests needing a real target survey create one through the live Import endpoint first
 * (a genuine, committed HTTP request, not a rolled-back {@code @TestTransaction}) and then read
 * its id/survey_key back directly — the same reason
 * {@code SurveyDefinitionImportResourceTest} avoids seeding a full definition tree through the
 * test method's own connection. Deeper reconciliation coverage across the full definition tree
 * lives in {@code SurveyDefinitionUpdateServiceTest}, which calls the service directly.</p>
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class SurveyDefinitionUpdateResourceTest {

    private static final String IMPORT_PATH = "/api/secured/survey/import";
    private static final String UPDATE_PATH = "/api/secured/survey/update";

    @Inject
    EntityManager em;

    private byte[] bytes(String content) {
        return content.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Creates a brand-new survey through a real, committed Import HTTP call (not a rolled-back
     * {@code @TestTransaction}), then reads its assigned id back directly. Must be called from
     * inside a {@code @TestSecurity(roles = {"elicit_admin"})} test method — it's a plain method
     * call on the same thread, so the ambient test security context carries over to the POST
     * this helper makes.
     */
    private long createTargetSurveyViaImport(String uniqueName) {
        String content = "# ELICIT_SURVEY_EXPORT_V1\n\nsurveys: 1||" + uniqueName + "|1|Title|||||\n";
        given()
                .multiPart("file", "survey.elicit", bytes(content), "application/octet-stream")
                .when().post(IMPORT_PATH)
                .then()
                .statusCode(200);
        return readSurveyId(uniqueName);
    }

    private long readSurveyId(String name) {
        return QuarkusTransaction.requiringNew().call(() -> {
            Number id = (Number) em.createNativeQuery(
                            "SELECT id FROM survey.surveys WHERE name = ?1 ORDER BY id DESC LIMIT 1")
                    .setParameter(1, name)
                    .getSingleResult();
            return id.longValue();
        });
    }

    private String surveyKeyOf(long surveyId) {
        return QuarkusTransaction.requiringNew().call(() ->
                (String) em.createNativeQuery("SELECT survey_key::text FROM survey.surveys WHERE id = ?1")
                        .setParameter(1, surveyId)
                        .getSingleResult());
    }

    /** UC-001/UC-008: an elicit_admin reaches the resource (a 400 on this malformed request, not 401/403). */
    @Test
    @TestSecurity(user = "admin", roles = {"elicit_admin"})
    void adminRoleIsAuthorized() {
        given()
                .multiPart("file", "survey.elicit", bytes("not a valid export file"), "application/octet-stream")
                .multiPart("surveyId", "1")
                .when().post(UPDATE_PATH)
                .then()
                .statusCode(400);
    }

    /** UC-001/UC-008: an authenticated non-admin is forbidden (403). */
    @Test
    @TestSecurity(user = "bob", roles = {"elicit_user"})
    void nonAdminRoleIsForbidden() {
        given()
                .multiPart("file", "survey.elicit", bytes("not a valid export file"), "application/octet-stream")
                .multiPart("surveyId", "1")
                .when().post(UPDATE_PATH)
                .then()
                .statusCode(403);
    }

    /** UC-001: an unauthenticated request is rejected, proving the endpoint is not public. */
    @Test
    void anonymousIsRejected() {
        given()
                .redirects().follow(false)
                .multiPart("file", "survey.elicit", bytes("not a valid export file"), "application/octet-stream")
                .multiPart("surveyId", "1")
                .when().post(UPDATE_PATH)
                .then()
                .statusCode(anyOf(is(401), is(302)));
    }

    /** A1: a request with no "file" part is rejected before the service is even called. */
    @Test
    @TestSecurity(user = "admin", roles = {"elicit_admin"})
    void missingFilePartReturnsBadRequest() {
        given()
                .multiPart("surveyId", "1")
                .when().post(UPDATE_PATH)
                .then()
                .statusCode(400)
                .body("message", is("No file provided in the 'file' field"));
    }

    /** A1: a request with no "surveyId" part is rejected before the service is even called. */
    @Test
    @TestSecurity(user = "admin", roles = {"elicit_admin"})
    void missingSurveyIdReturnsBadRequest() {
        given()
                .multiPart("file", "survey.elicit", bytes("not a valid export file"), "application/octet-stream")
                .when().post(UPDATE_PATH)
                .then()
                .statusCode(400)
                .body("message", is("No target survey selected in the 'surveyId' field"));
    }

    /** No survey exists with the given target id. */
    @Test
    @TestSecurity(user = "admin", roles = {"elicit_admin"})
    void unknownTargetSurveyReturnsNotFound() {
        String content = "# ELICIT_SURVEY_EXPORT_V1\n\nsurveys: 1|" + java.util.UUID.randomUUID() + "|Name|1|Title|||||\n";

        given()
                .multiPart("file", "survey.elicit", bytes(content), "application/octet-stream")
                .multiPart("surveyId", "987654321")
                .when().post(UPDATE_PATH)
                .then()
                .statusCode(404)
                .body("success", is(false));
    }

    /** A2: a file that never validates its header returns a structured failure, not a 500. */
    @Test
    @TestSecurity(user = "admin", roles = {"elicit_admin"})
    void malformedFileReturnsBadRequestWithoutServerError() {
        long targetId = createTargetSurveyViaImport("UpdRest-BadHeader-" + System.nanoTime());

        given()
                .multiPart("file", "survey.elicit", bytes("surveys: 1|Name|1|Title|||\n"), "application/octet-stream")
                .multiPart("surveyId", String.valueOf(targetId))
                .when().post(UPDATE_PATH)
                .then()
                .statusCode(400)
                .body("success", is(false));
    }

    /**
     * UC-017: a well-formed file whose survey_key matches the target updates it successfully
     * end-to-end through the real multipart POST.
     */
    @Test
    @TestSecurity(user = "admin", roles = {"elicit_admin"})
    void wellFormedUpdateSucceeds() {
        long targetId = createTargetSurveyViaImport("UpdRest-Ok-" + System.nanoTime());
        String key = surveyKeyOf(targetId);

        String content = "# ELICIT_SURVEY_EXPORT_V1\n\nsurveys: 1|" + key + "|Renamed|1|New Title|||||\n";

        given()
                .multiPart("file", "survey.elicit", bytes(content), "application/octet-stream")
                .multiPart("surveyId", String.valueOf(targetId))
                .when().post(UPDATE_PATH)
                .then()
                .statusCode(200)
                .body("success", is(true))
                .body("counts.surveys.versioned", is(1));
    }

    /** BR-063: a mismatched survey_key is rejected without applying any changes. */
    @Test
    @TestSecurity(user = "admin", roles = {"elicit_admin"})
    void mismatchedSurveyKeyReturnsBadRequest() {
        long targetId = createTargetSurveyViaImport("UpdRest-Mismatch-" + System.nanoTime());
        String content = "# ELICIT_SURVEY_EXPORT_V1\n\nsurveys: 1|" + java.util.UUID.randomUUID() + "|Other|1|Title|||||\n";

        given()
                .multiPart("file", "survey.elicit", bytes(content), "application/octet-stream")
                .multiPart("surveyId", String.valueOf(targetId))
                .when().post(UPDATE_PATH)
                .then()
                .statusCode(400)
                .body("success", is(false));
    }
}
