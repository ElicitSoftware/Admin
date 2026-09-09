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

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Date;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plain unit tests for {@link Subject}: constructors, accessors, and ID-based identity.
 *
 * <p>Traceability: UC-003 (Register or Update a Subject). A Subject is the administrative
 * record created when a participant is registered; these tests exercise its in-memory state
 * directly without booting the application, since none of this behavior touches the database.</p>
 */
class SubjectTest {

    @Test
    void allArgConstructorSetsProvidedFields() {
        LocalDate dob = LocalDate.of(1990, 1, 15);
        Subject subject = new Subject("XID-1", 10L, 20L, "Pat", "Tester", "M", dob, "pat@example.org", "555-123-4567");

        assertEquals("XID-1", subject.getXid());
        assertEquals(10L, subject.getSurveyId());
        assertEquals(20L, subject.getDepartmentId());
        assertEquals("Pat", subject.getFirstName());
        assertEquals("Tester", subject.getLastName());
        assertEquals("M", subject.getMiddleName());
        assertEquals(dob, subject.getDob());
        assertEquals("pat@example.org", subject.getEmail());
        assertEquals("555-123-4567", subject.getPhone());
    }

    @Test
    void noArgConstructorLeavesFieldsUnset() {
        Subject subject = new Subject();
        assertNull(subject.getXid());
        assertNull(subject.getFirstName());
        assertNull(subject.getLastName());
        assertNull(subject.getRespondent());
    }

    @Test
    void settersUpdateFields() {
        Subject subject = new Subject();
        Respondent respondent = new Respondent();
        Date createdDt = new Date();

        subject.setId(42L);
        subject.setXid("XID-2");
        subject.setRespondent(respondent);
        subject.setDepartmentId(5L);
        subject.setSurveyId(6L);
        subject.setFirstName("Jane");
        subject.setLastName("Doe");
        subject.setMiddleName("Q");
        subject.setDob(LocalDate.of(2000, 6, 1));
        subject.setEmail("jane@example.org");
        subject.setPhone("111-222-3333");
        subject.setCreatedDt(createdDt);

        assertEquals(42L, subject.getId());
        assertEquals("XID-2", subject.getXid());
        assertEquals(respondent, subject.getRespondent());
        assertEquals(5L, subject.getDepartmentId());
        assertEquals(6L, subject.getSurveyId());
        assertEquals("Jane", subject.getFirstName());
        assertEquals("Doe", subject.getLastName());
        assertEquals("Q", subject.getMiddleName());
        assertEquals(LocalDate.of(2000, 6, 1), subject.getDob());
        assertEquals("jane@example.org", subject.getEmail());
        assertEquals("111-222-3333", subject.getPhone());
        assertEquals(createdDt, subject.getCreatedDt());
    }

    @Test
    void subjectsWithSameIdAreEqual() {
        Subject a = new Subject();
        a.setId(7L);
        Subject b = new Subject();
        b.setId(7L);
        b.setFirstName("Different name, same id");

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, "not a subject", "a Subject is never equal to a different type");
    }

    @Test
    void subjectsWithDifferentIdsAreNotEqual() {
        Subject a = new Subject();
        a.setId(1L);
        Subject b = new Subject();
        b.setId(2L);

        assertNotEquals(a, b);
    }

    @Test
    void deduplicatesInSetById() {
        Subject a = new Subject();
        a.setId(100L);
        Subject sameId = new Subject();
        sameId.setId(100L);
        Subject other = new Subject();
        other.setId(101L);

        Set<Subject> set = Set.of(a, other);
        assertEquals(2, set.size());
        assertTrue(set.contains(sameId));
    }
}
