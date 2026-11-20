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

import com.elicitsoftware.model.Department;
import com.elicitsoftware.model.Respondent;
import com.elicitsoftware.model.Subject;
import com.elicitsoftware.model.Survey;
import com.elicitsoftware.test.DepartmentFixtures;
import com.elicitsoftware.test.PostgresTestResource;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import java.time.LocalDate;

import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
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

    // ---- UC-011 BR-185, A4: only within the administrator's departments ----------------------

    private static final String SCOPED_ADMIN = "export.scope.admin";

    /** A respondent of the seeded survey, with a subject in the department when one is given. */
    private static int respondent(String accessCode, Department department) {
        return QuarkusTransaction.requiringNew().call(() -> {
            Respondent respondent = new Respondent();
            respondent.survey = Survey.findById(1L);
            respondent.accessCode = accessCode;
            respondent.active = true;
            respondent.persist();
            if (department != null) {
                Subject subject = new Subject("XID-" + accessCode, 1L, department.id, "Pat", "Tester", null,
                        LocalDate.of(1990, 1, 15), "pat." + accessCode.toLowerCase() + "@example.org", null);
                subject.setRespondent(respondent);
                subject.persistAndFlush();
            }
            return respondent.id;
        });
    }

    private static String token() {
        return Long.toString(System.nanoTime(), 36).toUpperCase();
    }

    /** BR-185: a respondent whose subject is in the administrator's department is exported. */
    @Test
    @TestSecurity(user = SCOPED_ADMIN, roles = {"elicit_admin"})
    void aRespondentInTheAdministratorsDepartmentIsExported() {
        String token = token();
        Department own = DepartmentFixtures.department(token + "O");
        DepartmentFixtures.consoleRecord(SCOPED_ADMIN, true, own);
        int id = respondent("EXO" + token, own);

        given().queryParam("id", id)
            .when().get(EXPORT_PATH)
            .then()
            .statusCode(200)
            .body(containsString("XID-EXO" + token));
    }

    /** A4: one outside the administrator's departments reads exactly as one that does not exist. */
    @Test
    @TestSecurity(user = SCOPED_ADMIN, roles = {"elicit_admin"})
    void aRespondentInAnotherDepartmentIsNotFound() {
        String token = token();
        Department own = DepartmentFixtures.department(token + "O");
        Department other = DepartmentFixtures.department(token + "X");
        DepartmentFixtures.consoleRecord(SCOPED_ADMIN, true, own);
        int id = respondent("EXX" + token, other);

        given().queryParam("id", id)
            .when().get(EXPORT_PATH)
            .then()
            .statusCode(404)
            .body(equalTo("Respondent not found: " + id));
    }

    /** BR-185 with UC-002 BR-181: an auto-registered respondent has no department and is exported. */
    @Test
    @TestSecurity(user = SCOPED_ADMIN, roles = {"elicit_admin"})
    void anAutoRegisteredRespondentIsExported() {
        String token = token();
        Department own = DepartmentFixtures.department(token + "O");
        DepartmentFixtures.consoleRecord(SCOPED_ADMIN, true, own);
        int id = respondent("EXA" + token, null);

        given().queryParam("id", id)
            .when().get(EXPORT_PATH)
            .then()
            .statusCode(200)
            .body(containsString("access_code: EXA" + token));
    }

    /** BR-185: an administrator with no console record exports no respondent with a subject. */
    @Test
    @TestSecurity(user = "export.scope.norecord", roles = {"elicit_admin"})
    void anAdministratorWithNoDepartmentExportsNoSubject() {
        String token = token();
        DepartmentFixtures.noConsoleRecord("export.scope.norecord");
        int id = respondent("EXN" + token, DepartmentFixtures.department(token + "N"));

        given().queryParam("id", id)
            .when().get(EXPORT_PATH)
            .then()
            .statusCode(404);
    }
}
