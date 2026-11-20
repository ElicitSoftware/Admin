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
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * UC-010 BR-188, UC-003 BR-009 and BR-010: a record that breaks a subject rule is refused naming
 * the field at fault, creates nothing (no subject, no respondent, no access code), and does not
 * stop the other records of a batch or a CSV import (BR-037).
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class SubjectRulesRegistrationTest {

    private static final String CLIENT = "service-account-rules-client";

    private Department own;
    private String token;

    @BeforeEach
    void department() {
        token = Long.toString(System.nanoTime(), 36);
        own = DepartmentFixtures.department(token + "R");
        DepartmentFixtures.importClient(CLIENT, true, own);
    }

    private String subjectJson(String xid, String email) {
        return "{\"surveyId\":1,\"departmentId\":" + own.id + ",\"xid\":\"" + xid + "\","
                + "\"firstName\":\"Ann\",\"lastName\":\"Lee\",\"email\":\"" + email + "\"}";
    }

    private long subjectsWith(String xid) {
        return QuarkusTransaction.requiringNew().call(() -> Subject.count("xid = ?1", xid));
    }

    private long respondents() {
        return QuarkusTransaction.requiringNew().call(() -> Respondent.count());
    }

    @Test
    @TestSecurity(user = CLIENT, roles = {"elicit_importer"})
    void given_aMalformedEmail_when_oneSubjectIsPosted_then_itIsRefusedNamingTheEmailAndNothingIsCreated() {
        String xid = "RULES-" + token + "-1";
        long before = respondents();

        given().contentType(ContentType.JSON).body(subjectJson(xid, "ann.lee"))
                .when().post("/api/secured/add/subject")
                .then().statusCode(200)
                .body("subjects", hasSize(0))
                .body("errors[0]", equalTo("Invalid record " + xid + ": Enter a valid email address"));

        assertEquals(0, subjectsWith(xid));
        assertEquals(before, respondents(), "BR-188: checked before an access code is generated");
    }

    @Test
    @TestSecurity(user = CLIENT, roles = {"elicit_importer"})
    void given_noEmail_when_oneSubjectIsPosted_then_theEmailIsRequired() {
        String xid = "RULES-" + token + "-2";

        given().contentType(ContentType.JSON)
                .body("{\"surveyId\":1,\"departmentId\":" + own.id + ",\"xid\":\"" + xid + "\","
                        + "\"firstName\":\"Ann\",\"lastName\":\"Lee\",\"phone\":\"555-010-0100\"}")
                .when().post("/api/secured/add/subject")
                .then().statusCode(200)
                .body("errors[0]", equalTo("Invalid record " + xid + ": Email is required"));

        assertEquals(0, subjectsWith(xid));
    }

    @Test
    @TestSecurity(user = CLIENT, roles = {"elicit_importer"})
    void given_aBatchWithOneMalformedRecord_when_posted_then_onlyThatOneIsRefused() {
        String bad = "RULES-" + token + "-3";
        String good = "RULES-" + token + "-4";

        given().contentType(ContentType.JSON)
                .body("[" + subjectJson(bad, "ann.lee") + "," + subjectJson(good, "ann." + token + "@example.org") + "]")
                .when().post("/api/secured/add/subjects")
                .then().statusCode(200)
                .body("subjects", hasSize(2))
                .body("subjects[0].importStatus", equalTo("Invalid record " + bad + ": Enter a valid email address"))
                .body("subjects[0].status.accessCode", equalTo(null))
                .body("subjects[1].importStatus", equalTo("New Subject: " + good));

        assertEquals(0, subjectsWith(bad));
        assertEquals(1, subjectsWith(good), "BR-037: one record's refusal does not stop the others");
    }

    @Test
    @TestSecurity(user = CLIENT, roles = {"elicit_importer"})
    void given_aCsvWithAMalformedEmailAndAMalformedPhone_when_uploaded_then_thoseLinesAreRefusedAndTheOtherIsImported() {
        String badEmail = "RULES-" + token + "-5";
        String badPhone = "RULES-" + token + "-6";
        String good = "RULES-" + token + "-7";
        String csv = "1," + own.id + "," + badEmail + ",Bob,Ray,,,bob.ray,555-010-0100\n"
                + "1," + own.id + "," + badPhone + ",Cy,Day,,,cy." + token + "@example.org,5550100101\n"
                + "1," + own.id + "," + good + ",Ann,Lee,,,ann." + token + "@example.org,555-010-0102\n";

        given().multiPart("file", "subjects.csv", csv.getBytes(StandardCharsets.UTF_8))
                .when().post("/api/secured/add/csv")
                .then().statusCode(200)
                .body(containsString("Line 1: Enter a valid email address"))
                .body(containsString("Line 2: Phone must be ###-###-####"));

        assertEquals(0, subjectsWith(badEmail));
        assertEquals(0, subjectsWith(badPhone));
        assertEquals(1, subjectsWith(good), "BR-188: the import's other lines go on");
    }
}
