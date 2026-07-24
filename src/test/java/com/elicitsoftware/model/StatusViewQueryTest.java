package com.elicitsoftware.model;

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
import io.quarkus.test.TestTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Booted Panache/DB test for the {@code survey.status} view.
 *
 * <p>Traceability: UC-002 (Search and Monitor Subject Progress). The console
 * lists subjects with their derived progress status (Not Started / In Progress /
 * Finished). That status comes from the {@code survey.status} database view,
 * which joins respondents ⋈ subjects ⋈ departments and derives the label from
 * the respondent's first-access and finalized timestamps. This test persists a
 * full subject graph through Panache and asserts the view computes the expected
 * status, exercising the real schema (Flyway-migrated on a throwaway PostgreSQL
 * container) end-to-end.</p>
 *
 * <p>Each test runs in a rolled-back transaction ({@link TestTransaction}) so the
 * shared container stays clean between tests.</p>
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class StatusViewQueryTest {

    /**
     * Creates and persists a Department, a Respondent (linked to the seeded
     * survey id=1), and a Subject wired to that respondent. Returns the token
     * used, so the caller can look the row up through the status view.
     */
    private String persistSubjectGraph(String token, Date firstAccess, Date finalized) {
        Survey survey = Survey.findById(1L);
        assertNotNull(survey, "test bootstrap should have seeded survey id=1");

        Department department = new Department();
        department.name = "UC-002 Dept " + token;
        department.code = "UC002-" + token;
        department.defaultMessageId = "1";
        department.fromEmail = "uc002@example.org";
        department.persist();

        Respondent respondent = new Respondent();
        respondent.survey = survey;
        respondent.token = token;
        respondent.active = true;
        respondent.firstAccessDt = firstAccess;
        respondent.finalizedDt = finalized;
        respondent.persist();

        Subject subject = new Subject(
                "XID-" + token,               // xid
                survey.id.longValue(),        // surveyId
                department.id,                // departmentId
                "Pat",                        // firstName
                "Tester",                     // lastName
                null,                         // middleName
                LocalDate.of(1990, 1, 15),    // dob (must be @Past)
                "pat.tester@example.org",     // email (@Email @NotBlank)
                null);                        // phone (optional; @Pattern when present)
        subject.setRespondent(respondent);
        subject.persistAndFlush();
        return token;
    }

    /** UC-002: a respondent that has neither accessed nor finished shows "Not Started". */
    @Test
    @TestTransaction
    void notStartedStatusIsDerived() {
        String token = "TOK-NS";
        persistSubjectGraph(token, null, null);

        Status status = Status.find("token", token).firstResult();
        assertNotNull(status, "status view should return a row for the persisted subject");
        assertEquals("Not Started", status.getStatus());
        assertEquals(token, status.getToken());
        assertEquals(1L, status.getSurveyId());
        assertNotNull(status.getDepartmentName());
    }

    /** UC-002: a respondent that has accessed but not finished shows "In Progress". */
    @Test
    @TestTransaction
    void inProgressStatusIsDerived() {
        String token = "TOK-IP";
        persistSubjectGraph(token, new Date(), null);

        Status status = Status.find("token", token).firstResult();
        assertNotNull(status);
        assertEquals("In Progress", status.getStatus());
    }

    /** UC-002: a respondent with a finalized timestamp shows "Finished". */
    @Test
    @TestTransaction
    void finishedStatusIsDerived() {
        String token = "TOK-DONE";
        persistSubjectGraph(token, new Date(), new Date());

        Status status = Status.find("token", token).firstResult();
        assertNotNull(status);
        assertEquals("Finished", status.getStatus());
    }
}
