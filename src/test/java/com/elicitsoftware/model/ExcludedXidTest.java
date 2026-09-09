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

import java.time.OffsetDateTime;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plain unit tests for {@link ExcludedXid}: constructors, accessors, and ID-based identity.
 *
 * <p>Traceability: UC-003 (Register or Update a Subject) / UC-010 (Register Subjects via
 * Integration API) — both flows check whether an external ID is excluded for a department
 * before registering a subject. These are non-booted tests exercising pure in-memory state;
 * the static finder methods ({@code isExcluded}, {@code findByDepartmentId}, {@code findByXid})
 * are covered separately in {@link ExcludedXidQueryTest} against a real database.</p>
 */
class ExcludedXidTest {

    @Test
    void noArgConstructorLeavesFieldsUnset() {
        ExcludedXid xid = new ExcludedXid();
        assertNull(xid.getXid());
        assertNull(xid.getDepartmentId());
        assertNull(xid.getReason());
        assertNull(xid.getCreatedBy());
        assertNull(xid.getCreatedDt());
    }

    @Test
    void twoArgConstructorSetsXidAndDepartment() {
        ExcludedXid xid = new ExcludedXid("PROB123", 4);
        assertEquals("PROB123", xid.getXid());
        assertEquals(4, xid.getDepartmentId());
        assertNull(xid.getReason());
        assertNull(xid.getCreatedBy());
    }

    @Test
    void fourArgConstructorSetsAllProvidedFields() {
        ExcludedXid xid = new ExcludedXid("PROB456", 7, "Test data", "admin@example.org");
        assertEquals("PROB456", xid.getXid());
        assertEquals(7, xid.getDepartmentId());
        assertEquals("Test data", xid.getReason());
        assertEquals("admin@example.org", xid.getCreatedBy());
    }

    @Test
    void settersUpdateFields() {
        ExcludedXid xid = new ExcludedXid();
        OffsetDateTime now = OffsetDateTime.now();

        xid.setXid("XID-1");
        xid.setDepartmentId(9);
        xid.setReason("Duplicate");
        xid.setCreatedBy("tester");
        xid.setCreatedDt(now);

        assertEquals("XID-1", xid.getXid());
        assertEquals(9, xid.getDepartmentId());
        assertEquals("Duplicate", xid.getReason());
        assertEquals("tester", xid.getCreatedBy());
        assertEquals(now, xid.getCreatedDt());
    }

    @Test
    void exclusionsWithSameIdAreEqual() {
        ExcludedXid a = new ExcludedXid("A", 1);
        a.id = 5;
        ExcludedXid b = new ExcludedXid("B", 2);
        b.id = 5;

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertEquals(a, a, "an ExcludedXid is equal to itself");
        assertNotEquals(a, "not an exclusion", "an ExcludedXid is never equal to a different type");
    }

    @Test
    void exclusionsWithDifferentIdsAreNotEqual() {
        ExcludedXid a = new ExcludedXid("A", 1);
        a.id = 1;
        ExcludedXid b = new ExcludedXid("A", 1);
        b.id = 2;

        assertNotEquals(a, b);
    }

    @Test
    void unpersistedExclusionsWithNullIdDoNotThrow() {
        ExcludedXid a = new ExcludedXid();
        ExcludedXid b = new ExcludedXid();

        assertEquals(a, b, "two unpersisted exclusions both have a null id");
        assertEquals(a.hashCode(), a.hashCode());
    }

    @Test
    void deduplicatesInSetById() {
        ExcludedXid a = new ExcludedXid("A", 1);
        a.id = 1;
        ExcludedXid sameId = new ExcludedXid("DIFFERENT", 99);
        sameId.id = 1;
        ExcludedXid other = new ExcludedXid("A", 1);
        other.id = 2;

        Set<ExcludedXid> set = Set.of(a, other);
        assertEquals(2, set.size());
        assertTrue(set.contains(sameId));
    }
}
