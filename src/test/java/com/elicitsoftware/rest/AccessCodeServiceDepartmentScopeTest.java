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
 * UC-010: Register Subjects via Integration API — BR-137, the caller's departments bound the API
 * (NFR-021), on the single, batch and CSV endpoints, with a real database behind them.
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class AccessCodeServiceDepartmentScopeTest {

    private static final String USER = "scope.api.user";
    private static final String INACTIVE = "scope.api.inactive";
    private static final String IMPORTER = "scope.api.importer";
    private static final String MACHINE = "scope.api.machine";

    private Department own;
    private Department other;
    private String token;

    @BeforeEach
    void departmentsAndRecords() {
        token = Long.toString(System.nanoTime(), 36);
        own = DepartmentFixtures.department(token + "A");
        other = DepartmentFixtures.department(token + "B");
        DepartmentFixtures.consoleRecord(USER, true, own);
        DepartmentFixtures.consoleRecord(INACTIVE, false, own);
        DepartmentFixtures.consoleRecord(IMPORTER, true, own);
        DepartmentFixtures.noConsoleRecord(MACHINE);
    }

    private String subjectJson(long departmentId, String xid) {
        return "{\"surveyId\":1,\"departmentId\":" + departmentId + ",\"xid\":\"" + xid + "\","
                + "\"firstName\":\"Ann\",\"lastName\":\"Lee\",\"email\":\"ann." + token + "@example.org\"}";
    }

    private long subjectsWith(String xid) {
        return QuarkusTransaction.requiringNew().call(() -> Subject.count("xid = ?1", xid));
    }

    @Test
    @TestSecurity(user = USER, roles = {"elicit_user"})
    void given_aDepartmentTheCallerIsNotAssignedTo_when_oneSubjectIsPosted_then_itIsForbiddenAndNothingIsStored() {
        String xid = "SCOPE-" + token + "-1";

        given().contentType(ContentType.JSON).body(subjectJson(other.id, xid))
                .when().post("/api/secured/add/subject")
                .then().statusCode(403)
                .body(not(containsString(xid)));

        assertEquals(0, subjectsWith(xid));
    }

    @Test
    @TestSecurity(user = USER, roles = {"elicit_user"})
    void given_theCallersOwnDepartment_when_oneSubjectIsPosted_then_itIsRegisteredWithItsAccessCode() {
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
    @TestSecurity(user = USER, roles = {"elicit_user"})
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
    @TestSecurity(user = INACTIVE, roles = {"elicit_user"})
    void given_anInactiveConsoleRecord_when_oneSubjectIsPosted_then_itIsForbidden() {
        String xid = "SCOPE-" + token + "-5";

        given().contentType(ContentType.JSON).body(subjectJson(own.id, xid))
                .when().post("/api/secured/add/subject")
                .then().statusCode(403);

        assertEquals(0, subjectsWith(xid));
    }

    @Test
    @TestSecurity(user = IMPORTER, roles = {"elicit_importer"})
    void given_aCsvAcrossDepartments_when_uploaded_then_theForeignLineIsReportedAndNotStored() {
        String foreign = "SCOPE-" + token + "-6";
        String mine = "SCOPE-" + token + "-7";
        String csv = other.id + ",Bob,Ray,,,bob." + token + "@example.org,555-010-0100," + foreign + "\n"
                + own.id + ",Ann,Lee,,,ann." + token + "@example.org,555-010-0101," + mine + "\n";

        given().multiPart("file", "subjects.csv", csv.getBytes(StandardCharsets.UTF_8))
                .when().post("/api/secured/add/csv")
                .then().statusCode(200)
                .body(containsString("Line 1: You are not assigned to department " + other.id));

        assertEquals(0, subjectsWith(foreign));
        assertEquals(1, subjectsWith(mine));
    }

    /** BR-137: a role without a console record registers nowhere, in a single request. */
    @Test
    @TestSecurity(user = MACHINE, roles = {"elicit_importer"})
    void given_aCallerWithNoConsoleRecord_when_oneSubjectIsPosted_then_itIsForbiddenAndNothingIsStored() {
        String xid = "SCOPE-" + token + "-8";

        given().contentType(ContentType.JSON).body(subjectJson(other.id, xid))
                .when().post("/api/secured/add/subject")
                .then().statusCode(403)
                .body(not(containsString(xid)));

        assertEquals(0, subjectsWith(xid));
    }

    /** BR-137, A6: the same caller's batch is answered record by record, and its CSV line by line. */
    @Test
    @TestSecurity(user = MACHINE, roles = {"elicit_importer"})
    void given_aCallerWithNoConsoleRecord_when_aBatchOrACsvIsPosted_then_noRecordIsPermitted() {
        String batched = "SCOPE-" + token + "-9";
        String uploaded = "SCOPE-" + token + "-10";

        given().contentType(ContentType.JSON).body("[" + subjectJson(own.id, batched) + "]")
                .when().post("/api/secured/add/subjects")
                .then().statusCode(200)
                .body("subjects[0].importStatus", equalTo("Not permitted: " + batched));

        String csv = own.id + ",Ann,Lee,,,ann." + token + "@example.org,555-010-0102," + uploaded + "\n";
        given().multiPart("file", "subjects.csv", csv.getBytes(StandardCharsets.UTF_8))
                .when().post("/api/secured/add/csv")
                .then().statusCode(200)
                .body(containsString("Line 1: You are not assigned to department " + own.id));

        assertEquals(0, subjectsWith(batched));
        assertEquals(0, subjectsWith(uploaded));
    }

    /**
     * BR-164: the answer for a subject that already exists carries the outcome, the external ID
     * and the department, and nothing stored; the access code came back once, when it was created.
     */
    @Test
    @TestSecurity(user = USER, roles = {"elicit_user"})
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
