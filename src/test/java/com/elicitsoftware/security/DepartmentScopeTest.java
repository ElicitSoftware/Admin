package com.elicitsoftware.security;


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
import com.elicitsoftware.test.DepartmentFixtures;
import com.elicitsoftware.test.PostgresTestResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import jakarta.ws.rs.ForbiddenException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * NFR-021: Department Isolation — the rule every path asks (UC-010 BR-137, UC-003 BR-138): the
 * departments of the principal's console record, read from the database at the moment of asking.
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class DepartmentScopeTest {

    @Inject
    DepartmentScope scope;

    private static String token() {
        return Long.toString(System.nanoTime(), 36);
    }

    @Test
    @TestSecurity(user = "scope.bean.assigned", roles = {"elicit_user"})
    void given_aConsoleRecordAssignedToOneDepartment_when_asked_then_onlyThatDepartmentIsPermitted() {
        Department own = DepartmentFixtures.department(token());
        Department other = DepartmentFixtures.department(token());
        DepartmentFixtures.consoleRecord("scope.bean.assigned", true, own);

        assertTrue(scope.permits(own.id));
        assertFalse(scope.permits(other.id));
        assertDoesNotThrow(() -> scope.require(own.id));
        assertThrows(ForbiddenException.class, () -> scope.require(other.id));
    }

    @Test
    @TestSecurity(user = "scope.bean.changing", roles = {"elicit_user"})
    void given_aDepartmentRemovedAfterTheFirstQuestion_when_askedAgain_then_theAnswerFollowsTheDatabase() {
        Department first = DepartmentFixtures.department(token());
        Department second = DepartmentFixtures.department(token());
        DepartmentFixtures.consoleRecord("scope.bean.changing", true, first, second);
        assertTrue(scope.permits(second.id));

        DepartmentFixtures.consoleRecord("scope.bean.changing", true, first);

        assertFalse(scope.permits(second.id), "the record is read each time, not remembered");
        assertTrue(scope.permits(first.id));
    }

    @Test
    @TestSecurity(user = "scope.bean.inactive", roles = {"elicit_user"})
    void given_anInactiveConsoleRecord_when_asked_then_noDepartmentIsPermitted() {
        Department own = DepartmentFixtures.department(token());
        DepartmentFixtures.consoleRecord("scope.bean.inactive", false, own);

        assertFalse(scope.permits(own.id));
    }

    @Test
    @TestSecurity(user = "scope.bean.unassigned", roles = {"elicit_user"})
    void given_aConsoleRecordWithNoDepartment_when_asked_then_noDepartmentIsPermitted() {
        Department any = DepartmentFixtures.department(token());
        DepartmentFixtures.consoleRecord("scope.bean.unassigned", true);

        assertFalse(scope.permits(any.id));
    }

    /** UC-010 BR-137: a principal with no console record is permitted no department, whatever its role. */
    @Test
    @TestSecurity(user = "scope.bean.norecord", roles = {"elicit_importer"})
    void given_noConsoleRecord_when_asked_then_noDepartmentIsPermitted() {
        Department any = DepartmentFixtures.department(token());
        DepartmentFixtures.noConsoleRecord("scope.bean.norecord");

        assertFalse(scope.permits(any.id));
        assertThrows(ForbiddenException.class, () -> scope.require(any.id));
    }
}
