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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Booted Panache/DB test for the {@code survey.status} view.
 *
 * <p>Traceability: UC-002 (Search and Monitor Subject Progress). The console
 * lists subjects with their derived progress status (Not Started / In Progress /
 * Finished). That status comes from the {@code survey.status} database view,
 * which left-joins each respondent to its subject and department and derives the label from
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
     * survey id=1), and a Subject wired to that respondent. Returns the access code
     * used, so the caller can look the row up through the status view.
     */
    private String persistSubjectGraph(String accessCode, Date firstAccess, Date finalized) {
        Survey survey = Survey.findById(1L);
        assertNotNull(survey, "test bootstrap should have seeded survey id=1");

        Department department = new Department();
        department.name = "UC-002 Dept " + accessCode;
        department.code = "UC002-" + accessCode;
        department.defaultMessageId = "1";
        department.fromEmail = "uc002@example.org";
        department.persist();

        Respondent respondent = new Respondent();
        respondent.survey = survey;
        respondent.accessCode = accessCode;
        respondent.active = true;
        respondent.firstAccessDt = firstAccess;
        respondent.finalizedDt = finalized;
        respondent.persist();

        Subject subject = new Subject(
                "XID-" + accessCode,           // xid
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
        return accessCode;
    }

    /** UC-002: a respondent that has neither accessed nor finished shows "Not Started". */
    @Test
    @TestTransaction
    void notStartedStatusIsDerived() {
        String accessCode = "TOK-NS";
        persistSubjectGraph(accessCode, null, null);

        Status status = Status.find("accessCode", accessCode).firstResult();
        assertNotNull(status, "status view should return a row for the persisted subject");
        assertEquals("Not Started", status.getStatus());
        assertEquals(accessCode, status.getAccessCode());
        assertEquals(1L, status.getSurveyId());
        assertNotNull(status.getDepartmentName());
    }

    /** UC-002: a respondent that has accessed but not finished shows "In Progress". */
    @Test
    @TestTransaction
    void inProgressStatusIsDerived() {
        String accessCode = "TOK-IP";
        persistSubjectGraph(accessCode, new Date(), null);

        Status status = Status.find("accessCode", accessCode).firstResult();
        assertNotNull(status);
        assertEquals("In Progress", status.getStatus());
    }

    /** UC-002: a respondent with a finalized timestamp shows "Finished". */
    @Test
    @TestTransaction
    void finishedStatusIsDerived() {
        String accessCode = "TOK-DONE";
        persistSubjectGraph(accessCode, new Date(), new Date());

        Status status = Status.find("accessCode", accessCode).firstResult();
        assertNotNull(status);
        assertEquals("Finished", status.getStatus());
    }

    /**
     * UC-002 BR-181: a respondent Survey auto-registered has no subject; the view still lists it,
     * with no department, its id negated and its own survey and creation date, and the department
     * filter's "Auto-registered" query finds it.
     */
    @Test
    @TestTransaction
    void autoRegisteredRespondentIsListedWithNoDepartment() {
        Respondent respondent = new Respondent();
        respondent.survey = Survey.findById(1L);
        respondent.accessCode = "SELF-REG1";
        respondent.active = true;
        respondent.firstAccessDt = new Date();
        respondent.persistAndFlush();

        Status status = Status.find("accessCode", "SELF-REG1").firstResult();
        assertNotNull(status, "a respondent with no subject is in the view");
        assertEquals(-respondent.id, status.getId());
        assertTrue(status.isAutoRegistered());
        assertEquals((long) respondent.id, status.getRespondentId());
        assertEquals(1L, status.getSurveyId());
        assertNull(status.getDepartmentId());
        assertNull(status.getDepartmentName());
        assertNull(status.getFirstName());
        assertNotNull(status.getCreatedDt(), "the respondent's creation date stands in");
        assertEquals("In Progress", status.getStatus());
        assertEquals(1L, Status.count("accessCode = ?1 and (departmentId in ?2 or departmentId is null)",
                "SELF-REG1", java.util.List.of(-99L)));
    }
}
