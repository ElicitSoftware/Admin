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

import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Booted Panache tests for {@link ExcludedXid}'s static finder methods.
 *
 * <p>Traceability: UC-003 (Register or Update a Subject) / UC-010 (Register Subjects via
 * Integration API) — both check {@code ExcludedXid.isExcluded} before creating a subject.
 * Each test runs in a rolled-back transaction so the shared container stays clean.</p>
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class ExcludedXidQueryTest {

    /** {@code created_dt} has no application-side default, so tests must set it explicitly. */
    private static ExcludedXid persisted(String xid, int departmentId) {
        ExcludedXid exclusion = new ExcludedXid(xid, departmentId);
        exclusion.setCreatedDt(OffsetDateTime.now());
        exclusion.persist();
        return exclusion;
    }

    /** UC-003/UC-010: an XID excluded for a department is reported as excluded for that department only. */
    @Test
    @TestTransaction
    void isExcludedMatchesXidAndDepartment() {
        ExcludedXid exclusion = persisted("EXC-1", 101);
        exclusion.setReason("Test data");
        exclusion.setCreatedBy("tester");

        assertTrue(ExcludedXid.isExcluded("EXC-1", 101));
        assertFalse(ExcludedXid.isExcluded("EXC-1", 202), "same xid, different department is not excluded");
        assertFalse(ExcludedXid.isExcluded("NOT-EXCLUDED", 101));
    }

    /** UC-003/UC-010: exclusions can be listed by department id. */
    @Test
    @TestTransaction
    void findByDepartmentIdReturnsOnlyThatDepartmentsExclusions() {
        persisted("EXC-2", 303);
        persisted("EXC-3", 303);
        persisted("EXC-4", 404);

        List<ExcludedXid> found = ExcludedXid.findByDepartmentId(303);

        assertEquals(2, found.size());
        assertTrue(found.stream().allMatch(x -> x.getDepartmentId() == 303));
    }

    /** UC-003/UC-010: exclusions can be listed by xid across departments. */
    @Test
    @TestTransaction
    void findByXidReturnsAllDepartmentsForThatXid() {
        persisted("SHARED-XID", 1);
        persisted("SHARED-XID", 2);
        persisted("OTHER-XID", 1);

        List<ExcludedXid> found = ExcludedXid.findByXid("SHARED-XID");

        assertEquals(2, found.size());
        assertTrue(found.stream().allMatch(x -> "SHARED-XID".equals(x.getXid())));
    }
}
