package com.elicitsoftware.service;

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
import com.elicitsoftware.model.Status;
import com.elicitsoftware.model.Subject;
import com.elicitsoftware.model.Survey;
import com.elicitsoftware.test.PostgresTestResource;
import io.quarkus.panache.common.Sort;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Booted Panache/DB tests for {@link StatusDataSource} and its parameterized
 * {@link StatusQuery}.
 *
 * <p>Traceability: UC-002 (Search and Monitor Subject Progress). These tests exercise the
 * search data path that backs the console grid — parameterized filtering, database-level
 * paging/counting, and injection safety of the filter values.</p>
 *
 * <p>Regression coverage for code-review finding #1 (HQL injection): a filter value crafted
 * to break out of a string-concatenated {@code LIKE} clause must be treated as a literal and
 * match nothing, rather than altering the query.</p>
 *
 * <p>Each test runs in a rolled-back transaction ({@link TestTransaction}) so the shared
 * container stays clean between tests.</p>
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class StatusDataSourceTest {

    private final StatusDataSource dataSource = new StatusDataSource();

    /**
     * Persists a department + respondent + subject graph and returns the department id, so the
     * caller can build a {@link StatusQuery} scoped to it.
     */
    private long persistSubjectGraph(String token, String firstName, String email) {
        Survey survey = Survey.findById(1L);
        assertNotNull(survey, "test bootstrap should have seeded survey id=1");

        Department department = new Department();
        department.name = "DS Dept " + token;
        department.code = "DS-" + token;
        department.defaultMessageId = "1";
        department.fromEmail = "ds@example.org";
        department.persist();

        Respondent respondent = new Respondent();
        respondent.survey = survey;
        respondent.token = token;
        respondent.active = true;
        respondent.persist();

        Subject subject = new Subject(
                "XID-" + token,
                survey.id.longValue(),
                department.id,
                firstName,
                "Tester",
                null,
                LocalDate.of(1990, 1, 15),
                email,
                null);
        subject.setRespondent(respondent);
        subject.persistAndFlush();
        return department.id;
    }

    /** UC-002: filtering by department id returns the matching status rows. */
    @Test
    @TestTransaction
    void fetchByDepartmentReturnsMatchingRows() {
        long deptId = persistSubjectGraph("DS-TOK-1", "Alice", "alice@example.org");

        StatusQuery query = new StatusQuery(
                Status.PROP_DEPARTMENT_ID + " in :departments",
                Map.of("departments", List.of(deptId)),
                Sort.by(Status.PROP_TOKEN));

        List<Status> rows = dataSource.fetch(query, 0, 10);
        assertEquals(1, rows.size());
        assertEquals("DS-TOK-1", rows.get(0).getToken());
        assertEquals(1, dataSource.count(query));
    }

    /** UC-002: a parameterized LIKE filter matches on partial, case-insensitive first name. */
    @Test
    @TestTransaction
    void fetchWithNameFilterMatches() {
        long deptId = persistSubjectGraph("DS-TOK-2", "Bartholomew", "bart@example.org");

        StatusQuery query = new StatusQuery(
                Status.PROP_DEPARTMENT_ID + " in :departments and lower(" + Status.PROP_FIRST_NAME + ") like :firstName",
                Map.of("departments", List.of(deptId), "firstName", "%barth%"),
                null);

        List<Status> rows = dataSource.fetch(query, 0, 10);
        assertEquals(1, rows.size());
        assertEquals("Bartholomew", rows.get(0).getFirstName());
    }

    /**
     * Regression for finding #1: a classic SQL/HQL injection payload supplied as a filter value
     * is bound as a parameter, so it is matched literally and returns nothing — it cannot widen
     * the result set the way {@code ') OR ('1'='1} would against string concatenation.
     */
    @Test
    @TestTransaction
    void injectionPayloadIsTreatedAsLiteralAndMatchesNothing() {
        long deptId = persistSubjectGraph("DS-TOK-3", "Charlie", "charlie@example.org");

        String malicious = "%') OR ('1'='1";
        StatusQuery query = new StatusQuery(
                Status.PROP_DEPARTMENT_ID + " in :departments and lower(" + Status.PROP_FIRST_NAME + ") like :firstName",
                Map.of("departments", List.of(deptId), "firstName", malicious),
                null);

        // Baseline: the department genuinely has one row.
        StatusQuery baseline = new StatusQuery(
                Status.PROP_DEPARTMENT_ID + " in :departments",
                Map.of("departments", List.of(deptId)),
                null);
        assertEquals(1, dataSource.count(baseline));

        // The injection payload must NOT expand the result set — it matches literally (0 rows).
        assertEquals(0, dataSource.count(query));
        assertTrue(dataSource.fetch(query, 0, 10).isEmpty());
    }

    /** UC-002: database-level paging returns only the requested window. */
    @Test
    @TestTransaction
    void fetchPagesAtDatabaseLevel() {
        long deptId = persistSubjectGraph("DS-PAGE-A", "Dana", "dana@example.org");
        // Reuse the same department for a second subject so both share a filter scope.
        Survey survey = Survey.findById(1L);
        Respondent respondent = new Respondent();
        respondent.survey = survey;
        respondent.token = "DS-PAGE-B";
        respondent.active = true;
        respondent.persist();
        Subject second = new Subject("XID-DS-PAGE-B", survey.id.longValue(), deptId,
                "Evan", "Tester", null, LocalDate.of(1990, 1, 15), "evan@example.org", null);
        second.setRespondent(respondent);
        second.persistAndFlush();

        StatusQuery query = new StatusQuery(
                Status.PROP_DEPARTMENT_ID + " in :departments",
                Map.of("departments", List.of(deptId)),
                Sort.by(Status.PROP_TOKEN));

        assertEquals(2, dataSource.count(query));

        List<Status> firstPage = dataSource.fetch(query, 0, 1);
        assertEquals(1, firstPage.size());

        List<Status> secondPage = dataSource.fetch(query, 1, 1);
        assertEquals(1, secondPage.size());

        assertFalse(firstPage.get(0).getToken().equals(secondPage.get(0).getToken()),
                "consecutive pages should return distinct rows");
    }
}
