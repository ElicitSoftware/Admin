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

import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plain unit tests for {@link User} accessors not already covered elsewhere, including the
 * positional department-access helpers.
 *
 * <p>Traceability: UC-008 (Manage Users). ID-based equality/hashCode and the core
 * username/name/active setters are already exercised by {@code EntityIdentityTest} and
 * {@code UserServiceTest} respectively.</p>
 */
class UserTest {

    @Test
    void surveysAndDepartmentsCanBeSetAndRetrieved() {
        User user = new User();
        Survey survey = new Survey();
        survey.id = 1;
        Set<Survey> surveys = Set.of(survey);
        Department department = new Department();
        department.id = 2;
        Set<Department> departments = Set.of(department);

        user.setSurveys(surveys);
        user.setDepartments(departments);

        assertEquals(surveys, user.getSurveys());
        assertEquals(departments, user.getDepartments());
    }

    /** UC-028: a user with no department is recognised as such; there is no silent fallback. */
    @Test
    void hasDepartmentsIsFalseWhenNoneAssigned() {
        User user = new User();
        assertFalse(user.hasDepartments(), "a transient user has no departments");
        user.setDepartments(Set.of());
        assertFalse(user.hasDepartments());
    }

    @Test
    void getDepartmentDefaultsToNullWhenNoDepartmentsAssigned() {
        User user = new User();
        user.setDepartments(Set.of());

        assertNull(user.getDepartment(1));
    }

    /** UC-028: an assigned department is reported, and the first one is reachable positionally. */
    @Test
    void getDepartmentReturnsFirstAssignedDepartment() {
        User user = new User();
        Department department = new Department();
        department.id = 42;
        Set<Department> departments = new LinkedHashSet<>();
        departments.add(department);
        user.setDepartments(departments);

        assertTrue(user.hasDepartments());
        assertEquals(department, user.getDepartment(1));
    }

    @Test
    void coreFieldGettersReflectSetValues() {
        User user = new User();
        user.setUsername("pat@example.org");
        user.setFirstName("Pat");
        user.setLastName("Tester");
        user.setActive(true);

        assertEquals("pat@example.org", user.getUsername());
        assertEquals("Pat", user.getFirstName());
        assertEquals("Tester", user.getLastName());
        assertTrue(user.isActive());
    }
}
