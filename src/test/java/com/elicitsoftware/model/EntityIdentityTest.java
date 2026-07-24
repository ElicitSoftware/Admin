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

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the ID-based {@code equals}/{@code hashCode} added to the entities.
 *
 * <p>Traceability: UC-002/UC-009/UC-010. Code-review finding #3 required stable, ID-based
 * identity on {@link Status}, {@link User}, and {@link Department} so Vaadin {@code Grid} and
 * {@code MultiSelectComboBox} selection tracking behaves correctly. These are plain
 * (non-booted) unit tests — identity is pure in-memory logic — asserting equality by id,
 * hash-code consistency, set de-duplication, and that the {@code Department} "All Departments"
 * sentinel ({@code id == -1}) has a distinct identity from real departments.</p>
 */
class EntityIdentityTest {

    /** #3: two Departments with the same id are equal and share a hash code. */
    @Test
    void departmentsWithSameIdAreEqual() {
        Department a = new Department();
        a.id = 7;
        a.name = "Cardiology";
        Department b = new Department();
        b.id = 7;
        b.name = "Different name, same id";

        assertEquals(a, b, "same id should mean equal");
        assertEquals(a.hashCode(), b.hashCode(), "equal objects must share a hash code");
    }

    /** #3: Departments with different ids are not equal. */
    @Test
    void departmentsWithDifferentIdsAreNotEqual() {
        Department a = new Department();
        a.id = 1;
        Department b = new Department();
        b.id = 2;
        assertNotEquals(a, b);
    }

    /** #3: the "All Departments" sentinel (id -1) is distinct from real departments. */
    @Test
    void allDepartmentsSentinelHasDistinctIdentity() {
        Department all = new Department();
        all.id = -1;
        all.name = "All Departments";
        Department real = new Department();
        real.id = 5;

        assertNotEquals(all, real);

        // A Set must keep both, and contains() must resolve by id.
        Set<Department> selection = Set.of(all, real);
        assertEquals(2, selection.size());
        Department sameAsAll = new Department();
        sameAsAll.id = -1;
        assertTrue(selection.contains(sameAsAll), "contains() should match the sentinel by id");
    }

    /** #3: Users with the same id are equal (identity independent of mutable fields). */
    @Test
    void usersWithSameIdAreEqual() {
        User a = new User();
        a.setId(42);
        a.setUsername("a@example.org");
        User b = new User();
        b.setId(42);
        b.setUsername("b@example.org");

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    /** #3: Statuses with the same id are equal and de-duplicate in a Set. */
    @Test
    void statusesWithSameIdDeduplicateInSet() {
        Status a = new Status();
        a.setId(100);
        Status b = new Status();
        b.setId(100);
        Status other = new Status();
        other.setId(101);

        assertEquals(a, b);
        Set<Status> set = Set.of(a, other);
        assertEquals(2, set.size());
        assertTrue(set.contains(b), "a status with the same id should be found in the set");
    }
}
