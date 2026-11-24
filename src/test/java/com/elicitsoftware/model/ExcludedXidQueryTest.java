package com.elicitsoftware.model;

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

import com.elicitsoftware.test.PostgresTestResource;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

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

    /** Ids well above anything the table's own sequence hands out. */
    private static int nextId = 930_000;

    /**
     * Inserts an exclusion with an id of the fixture's own. The console only ever reads this
     * table, so its role may not draw from the table's sequence, which {@code persist()} would do.
     */
    private static void excluded(String xid, int departmentId) {
        ExcludedXid.getEntityManager()
                .createNativeQuery("INSERT INTO survey.excluded_xids (id, xid, department, reason, created_dt, created_by) "
                        + "VALUES (?1, ?2, ?3, 'Test data', CURRENT_TIMESTAMP, 'tester')")
                .setParameter(1, ++nextId).setParameter(2, xid).setParameter(3, departmentId)
                .executeUpdate();
    }

    /** UC-003/UC-010: an XID excluded for a department is reported as excluded for that department only. */
    @Test
    @TestTransaction
    void isExcludedMatchesXidAndDepartment() {
        excluded("EXC-1", 101);

        assertTrue(ExcludedXid.isExcluded("EXC-1", 101));
        assertFalse(ExcludedXid.isExcluded("EXC-1", 202), "same xid, different department is not excluded");
        assertFalse(ExcludedXid.isExcluded("NOT-EXCLUDED", 101));
    }

    /** UC-003/UC-010: exclusions can be listed by department id. */
    @Test
    @TestTransaction
    void findByDepartmentIdReturnsOnlyThatDepartmentsExclusions() {
        excluded("EXC-2", 303);
        excluded("EXC-3", 303);
        excluded("EXC-4", 404);

        List<ExcludedXid> found = ExcludedXid.findByDepartmentId(303);

        assertEquals(2, found.size());
        assertTrue(found.stream().allMatch(x -> x.getDepartmentId() == 303));
    }

    /** UC-003/UC-010: exclusions can be listed by xid across departments. */
    @Test
    @TestTransaction
    void findByXidReturnsAllDepartmentsForThatXid() {
        excluded("SHARED-XID", 1);
        excluded("SHARED-XID", 2);
        excluded("OTHER-XID", 1);

        List<ExcludedXid> found = ExcludedXid.findByXid("SHARED-XID");

        assertEquals(2, found.size());
        assertTrue(found.stream().allMatch(x -> "SHARED-XID".equals(x.getXid())));
    }
}
