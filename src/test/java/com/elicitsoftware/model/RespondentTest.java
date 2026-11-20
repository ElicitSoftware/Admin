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

import java.util.Date;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plain unit tests for {@link Respondent}: field access, elapsed-time calculation, and ID-based
 * identity.
 *
 * <p>Traceability: UC-002 (Search and Monitor Subject Progress) — the console surfaces how long
 * a respondent took to complete their survey via {@link Respondent#getElapsedTime()}. These are
 * non-booted tests; the static access-code lookup finders are covered against a real database in
 * {@link StatusViewQueryTest}-style tests elsewhere and are not re-tested here.</p>
 */
class RespondentTest {

    @Test
    void elapsedTimeIsNotCalculatedWhenEitherTimestampIsMissing() {
        Respondent noAccess = new Respondent();
        assertEquals("Not calculated", noAccess.getElapsedTime());

        Respondent notFinished = new Respondent();
        notFinished.firstAccessDt = new Date();
        assertEquals("Not calculated", notFinished.getElapsedTime());
    }

    @Test
    void elapsedTimeIsFormattedAsHoursMinutesSeconds() {
        Respondent respondent = new Respondent();
        long start = 1_700_000_000_000L;
        respondent.firstAccessDt = new Date(start);
        // 1 hour, 2 minutes, 3 seconds later
        respondent.finalizedDt = new Date(start + (((1 * 60 + 2) * 60 + 3) * 1000L));

        assertEquals("01:02:03", respondent.getElapsedTime());
    }

    @Test
    void settersUpdateFields() {
        Respondent respondent = new Respondent();
        Date created = new Date();
        Date firstAccess = new Date();
        Date finalized = new Date();
        Survey survey = new Survey();

        respondent.id = 5;
        respondent.createdDt = created;
        respondent.firstAccessDt = firstAccess;
        respondent.finalizedDt = finalized;
        respondent.active = true;
        respondent.logins = 3;
        respondent.survey = survey;
        respondent.accessCode = "tok-123";

        assertEquals(5, respondent.id);
        assertEquals(created, respondent.createdDt);
        assertEquals(firstAccess, respondent.firstAccessDt);
        assertEquals(finalized, respondent.finalizedDt);
        assertTrue(respondent.active);
        assertEquals(3, respondent.logins);
        assertEquals(survey, respondent.survey);
        assertEquals("tok-123", respondent.accessCode);
    }

    @Test
    void respondentsWithSameIdAreEqual() {
        Respondent a = new Respondent();
        a.id = 9;
        Respondent b = new Respondent();
        b.id = 9;
        b.accessCode = "different-code-same-id";

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, "not a respondent", "a Respondent is never equal to a different type");
    }

    @Test
    void respondentsWithNullIdDoNotThrow() {
        Respondent a = new Respondent();
        Respondent b = new Respondent();

        assertEquals(a, b, "two unpersisted respondents both have a null id");
    }

    @Test
    void deduplicatesInSetById() {
        Respondent a = new Respondent();
        a.id = 1;
        Respondent sameId = new Respondent();
        sameId.id = 1;
        Respondent other = new Respondent();
        other.id = 2;

        Set<Respondent> set = Set.of(a, other);
        assertEquals(2, set.size());
        assertTrue(set.contains(sameId));
        assertNotEquals(a, other);
    }
}
