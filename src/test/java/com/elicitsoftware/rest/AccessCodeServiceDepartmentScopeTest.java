package com.elicitsoftware.rest;


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

import com.elicitsoftware.model.Department;
import com.elicitsoftware.model.Subject;
import com.elicitsoftware.test.DepartmentFixtures;
import com.elicitsoftware.test.PostgresTestResource;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * UC-010: Register Subjects via Integration API — the API is for import clients only (BR-034,
 * BR-170) and an import client's departments bound it (BR-137, NFR-021), on the single, batch
 * and CSV endpoints, with a real database behind them.
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class AccessCodeServiceDepartmentScopeTest {

    private static final String CLIENT = "service-account-scope-client";
    private static final String INACTIVE = "service-account-scope-inactive";
    private static final String MACHINE = "service-account-scope-unrecorded";
    private static final String USER = "scope.api.user";

    private Department own;
    private Department other;
    private String token;

    @BeforeEach
    void departmentsAndRecords() {
        token = Long.toString(System.nanoTime(), 36);
        own = DepartmentFixtures.department(token + "A");
        other = DepartmentFixtures.department(token + "B");
        DepartmentFixtures.importClient(CLIENT, true, own);
        DepartmentFixtures.importClient(INACTIVE, false, own);
        DepartmentFixtures.noImportClient(MACHINE);
        DepartmentFixtures.noConsoleRecord(MACHINE);
        // A person: a console user record and no import client record.
        DepartmentFixtures.noImportClient(USER);
        DepartmentFixtures.consoleRecord(USER, true, own);
    }

    private String subjectJson(long departmentId, String xid) {
        return "{\"surveyId\":1,\"departmentId\":" + departmentId + ",\"xid\":\"" + xid + "\","
                + "\"firstName\":\"Ann\",\"lastName\":\"Lee\",\"email\":\"ann." + token + "@example.org\"}";
    }

    private long subjectsWith(String xid) {
        return QuarkusTransaction.requiringNew().call(() -> Subject.count("xid = ?1", xid));
    }

    /** Posts one subject for the caller's own department to each of the three endpoints and expects 403. */
    private void assertAllThreeEndpointsForbidden(String suffix) {
        String single = "SCOPE-" + token + "-" + suffix + "s";
        String batched = "SCOPE-" + token + "-" + suffix + "b";
        String uploaded = "SCOPE-" + token + "-" + suffix + "c";

        given().contentType(ContentType.JSON).body(subjectJson(own.id, single))
                .when().post("/api/secured/add/subject")
                .then().statusCode(403)
                .body(not(containsString(single)));
        given().contentType(ContentType.JSON).body("[" + subjectJson(own.id, batched) + "]")
                .when().post("/api/secured/add/subjects")
                .then().statusCode(403)
                .body(not(containsString(batched)));
        String csv = "1," + own.id + "," + uploaded + ",Ann,Lee,,,ann." + token + "@example.org,555-010-0102\n";
        given().multiPart("file", "subjects.csv", csv.getBytes(StandardCharsets.UTF_8))
                .when().post("/api/secured/add/csv")
                .then().statusCode(403)
                .body(not(containsString("Line 1")));

        assertEquals(0, subjectsWith(single));
        assertEquals(0, subjectsWith(batched));
        assertEquals(0, subjectsWith(uploaded));
    }

    /** UC-010 BR-177, A8: the client's own department, but a survey that department does not have. */
    @Test
    @TestSecurity(user = CLIENT, roles = {"elicit_importer"})
    void given_aSurveyTheDepartmentDoesNotHave_when_subjectsArePosted_then_eachFormRefusesIt() {
        QuarkusTransaction.requiringNew().run(() -> Department.getEntityManager()
                .createNativeQuery("DELETE FROM survey.department_surveys WHERE department_id = ?1")
                .setParameter(1, own.id).executeUpdate());
        String single = "SURV-" + token + "-1";
        String batched = "SURV-" + token + "-2";
        String uploaded = "SURV-" + token + "-3";

        given().contentType(ContentType.JSON).body(subjectJson(own.id, single))
                .when().post("/api/secured/add/subject")
                .then().statusCode(403);
        given().contentType(ContentType.JSON).body("[" + subjectJson(own.id, batched) + "]")
                .when().post("/api/secured/add/subjects")
                .then().statusCode(200)
                .body(containsString("Not permitted: " + batched));
        String csv = "1," + own.id + "," + uploaded + ",Ann,Lee,,,ann." + token + "@example.org,555-010-0103\n";
        given().multiPart("file", "subjects.csv", csv.getBytes(StandardCharsets.UTF_8))
                .when().post("/api/secured/add/csv")
                .then().statusCode(200)
                .body(containsString("Line 1: Department " + own.id + " does not register subjects onto survey 1"));

        assertEquals(0, subjectsWith(single));
        assertEquals(0, subjectsWith(batched));
        assertEquals(0, subjectsWith(uploaded));
    }

    /** UC-010 BR-178, A9: a CSV row without its survey rejects the whole upload. */
    @Test
    @TestSecurity(user = CLIENT, roles = {"elicit_importer"})
    void given_aCsvRowWithoutItsSurvey_when_uploaded_then_theWholeFileIsRejected() {
        String named = "NOSURV-" + token + "-1";
        String unnamed = "NOSURV-" + token + "-2";
        String csv = "1," + own.id + "," + named + ",Ann,Lee,,,ann." + token + "@example.org,555-010-0104\n"
                + "," + own.id + "," + unnamed + ",Bob,Ray,,,bob." + token + "@example.org,555-010-0105\n";

        given().multiPart("file", "subjects.csv", csv.getBytes(StandardCharsets.UTF_8))
                .when().post("/api/secured/add/csv")
                .then().statusCode(200)
                .body(containsString("Lines without it: 2"));

        assertEquals(0, subjectsWith(named), "nothing is imported from a rejected file");
        assertEquals(0, subjectsWith(unnamed));
    }

    @Test
    @TestSecurity(user = CLIENT, roles = {"elicit_importer"})
    void given_aDepartmentTheClientIsNotAssignedTo_when_oneSubjectIsPosted_then_itIsForbiddenAndNothingIsStored() {
        String xid = "SCOPE-" + token + "-1";

        given().contentType(ContentType.JSON).body(subjectJson(other.id, xid))
                .when().post("/api/secured/add/subject")
                .then().statusCode(403)
                .body(not(containsString(xid)));

        assertEquals(0, subjectsWith(xid));
    }

    /** UC-010 step 6, A1: an excluded subject is answered "Excluded Subject" and nothing is stored. */
    @Test
    @TestSecurity(user = CLIENT, roles = {"elicit_importer"})
    void given_anExcludedXid_when_oneSubjectIsPosted_then_theOutcomeIsExcludedSubject() {
        String xid = "SCOPE-" + token + "-x";
        // An id of the test's own: the console's role may not draw from the table's sequence.
        int id = 1_000_000_000 + (int) (System.nanoTime() % 1_000_000_000);
        QuarkusTransaction.requiringNew().run(() -> Department.getEntityManager()
                .createNativeQuery("INSERT INTO survey.excluded_xids (id, xid, department, reason, created_dt, created_by) "
                        + "VALUES (?1, ?2, ?3, 'Test data', CURRENT_TIMESTAMP, 'tester')")
                .setParameter(1, id).setParameter(2, xid).setParameter(3, (int) own.id)
                .executeUpdate());

        given().contentType(ContentType.JSON).body(subjectJson(own.id, xid))
                .when().post("/api/secured/add/subject")
                .then().statusCode(200)
                .body("subjects[0].importStatus", equalTo("Excluded Subject"));

        assertEquals(0, subjectsWith(xid));
    }

    @Test
    @TestSecurity(user = CLIENT, roles = {"elicit_importer"})
    void given_theClientsOwnDepartment_when_oneSubjectIsPosted_then_itIsRegisteredWithItsAccessCode() {
        String xid = "SCOPE-" + token + "-2";

        given().contentType(ContentType.JSON).body(subjectJson(own.id, xid))
                .when().post("/api/secured/add/subject")
                .then().statusCode(200)
                .body("subjects", hasSize(1))
                .body("subjects[0].importStatus", equalTo("New Subject"))
                .body("subjects[0].status.accessCode", notNullValue());

        assertEquals(1, subjectsWith(xid));
    }

    @Test
    @TestSecurity(user = CLIENT, roles = {"elicit_importer"})
    void given_aBatchAcrossDepartments_when_posted_then_theForeignRecordIsNotPermittedAndTheOthersGoOn() {
        String foreign = "SCOPE-" + token + "-3";
        String mine = "SCOPE-" + token + "-4";

        given().contentType(ContentType.JSON)
                .body("[" + subjectJson(other.id, foreign) + "," + subjectJson(own.id, mine) + "]")
                .when().post("/api/secured/add/subjects")
                .then().statusCode(200)
                .body("subjects", hasSize(2))
                .body("subjects[0].importStatus", equalTo("Not permitted: " + foreign))
                .body("subjects[0].status.accessCode", equalTo(null))
                .body("subjects[1].importStatus", equalTo("New Subject: " + mine));

        assertEquals(0, subjectsWith(foreign));
        assertEquals(1, subjectsWith(mine), "BR-037: one record's refusal does not stop the others");
    }

    @Test
    @TestSecurity(user = CLIENT, roles = {"elicit_importer"})
    void given_aCsvAcrossDepartments_when_uploaded_then_theForeignLineIsReportedAndNotStored() {
        String foreign = "SCOPE-" + token + "-6";
        String mine = "SCOPE-" + token + "-7";
        String csv = "1," + other.id + "," + foreign + ",Bob,Ray,,,bob." + token + "@example.org,555-010-0100\n"
                + "1," + own.id + "," + mine + ",Ann,Lee,,,ann." + token + "@example.org,555-010-0101\n";

        given().multiPart("file", "subjects.csv", csv.getBytes(StandardCharsets.UTF_8))
                .when().post("/api/secured/add/csv")
                .then().statusCode(200)
                .body(containsString("Line 1: You are not assigned to department " + other.id));

        assertEquals(0, subjectsWith(foreign));
        assertEquals(1, subjectsWith(mine));
    }

    /** BR-170, UC-032 BR-167: an inactive import client is refused as if it had no record. */
    @Test
    @TestSecurity(user = INACTIVE, roles = {"elicit_importer"})
    void given_anInactiveImportClient_when_anyEndpointIsCalled_then_theWholeRequestIsForbidden() {
        assertAllThreeEndpointsForbidden("inactive");
    }

    /** BR-170, A7: the role without an import client record is refused, whole request, all three forms. */
    @Test
    @TestSecurity(user = MACHINE, roles = {"elicit_importer"})
    void given_theImporterRoleAndNoImportClientRecord_when_anyEndpointIsCalled_then_theWholeRequestIsForbidden() {
        assertAllThreeEndpointsForbidden("unrecorded");
    }

    /**
     * BR-170: a console user record does not stand in for an import client record. This is the
     * arrangement the Users screen offered before UC-032.
     */
    @Test
    @TestSecurity(user = USER, roles = {"elicit_importer"})
    void given_theImporterRoleAndOnlyAConsoleUserRecord_when_anyEndpointIsCalled_then_theWholeRequestIsForbidden() {
        assertAllThreeEndpointsForbidden("consoleonly");
    }

    /** BR-034: the API accepts elicit_importer and no other role; a user registers on the Register page. */
    @Test
    @TestSecurity(user = USER, roles = {"elicit_user"})
    void given_aUser_when_anyEndpointIsCalled_then_itIsForbidden() {
        assertAllThreeEndpointsForbidden("user");
    }

    /** BR-034, UC-001 BR-006: elicit_admin does not imply elicit_importer. */
    @Test
    @TestSecurity(user = USER, roles = {"elicit_admin"})
    void given_anAdministrator_when_anyEndpointIsCalled_then_itIsForbidden() {
        assertAllThreeEndpointsForbidden("admin");
    }

    /** BR-170: an import client's token that carries a console role beside elicit_importer is refused. */
    @Test
    @TestSecurity(user = CLIENT, roles = {"elicit_importer", "elicit_user"})
    void given_anImportClientWhoseTokenAlsoCarriesElicitUser_when_anyEndpointIsCalled_then_itIsForbidden() {
        assertAllThreeEndpointsForbidden("mixeduser");
    }

    /** BR-170: the same with elicit_admin. */
    @Test
    @TestSecurity(user = CLIENT, roles = {"elicit_importer", "elicit_admin"})
    void given_anImportClientWhoseTokenAlsoCarriesElicitAdmin_when_anyEndpointIsCalled_then_itIsForbidden() {
        assertAllThreeEndpointsForbidden("mixedadmin");
    }

    /**
     * BR-164: the answer for a subject that already exists carries the outcome, the external ID
     * and the department, and nothing stored; the access code came back once, when it was created.
     */
    @Test
    @TestSecurity(user = CLIENT, roles = {"elicit_importer"})
    void given_anExistingSubject_when_postedAgain_then_theAnswerCarriesTheOutcomeAndNoStoredField() {
        String xid = "SCOPE-" + token + "-11";
        String accessCode = given().contentType(ContentType.JSON).body(subjectJson(own.id, xid))
                .when().post("/api/secured/add/subject")
                .then().statusCode(200)
                .body("subjects[0].importStatus", equalTo("New Subject"))
                .extract().path("subjects[0].status.accessCode");

        String single = given().contentType(ContentType.JSON).body(subjectJson(own.id, xid))
                .when().post("/api/secured/add/subject")
                .then().statusCode(200)
                .body("subjects[0].importStatus", equalTo("Existing Subject"))
                .body("subjects[0].status.xid", equalTo(xid))
                .body("subjects[0].status.departmentId", equalTo((int) own.id))
                .body("subjects[0].status.accessCode", equalTo(null))
                .body("subjects[0].status.firstName", equalTo(null))
                .body("subjects[0].status.lastName", equalTo(null))
                .body("subjects[0].status.email", equalTo(null))
                .extract().asString();
        String batch = given().contentType(ContentType.JSON).body("[" + subjectJson(own.id, xid) + "]")
                .when().post("/api/secured/add/subjects")
                .then().statusCode(200)
                .body("subjects[0].importStatus", equalTo("Existing Subject: " + xid))
                .body("subjects[0].status.accessCode", equalTo(null))
                .extract().asString();

        for (String answer : new String[]{single, batch}) {
            assertFalse(answer.contains(accessCode), "the access code of a subject the call did not create");
            assertFalse(answer.contains("Ann"), "the stored first name");
            assertFalse(answer.contains("example.org"), "the stored email");
        }
        assertEquals(1, subjectsWith(xid));
    }
}
