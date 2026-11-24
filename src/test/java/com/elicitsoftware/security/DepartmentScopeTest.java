package com.elicitsoftware.security;


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

    /** UC-010 BR-137: a machine client registers where its import client record (UC-032) is assigned. */
    @Test
    @TestSecurity(user = "service-account-scope-bean", roles = {"elicit_importer"})
    void given_anImportClientAssignedToOneDepartment_when_asked_then_onlyThatDepartmentIsPermitted() {
        Department own = DepartmentFixtures.department(token());
        Department other = DepartmentFixtures.department(token());
        DepartmentFixtures.importClient("service-account-scope-bean", true, own);

        assertDoesNotThrow(() -> scope.requireImportClient());
        assertTrue(scope.permits(own.id));
        assertFalse(scope.permits(other.id));
    }

    /** UC-010 BR-170, UC-032 BR-167: an inactive import client is no import client. */
    @Test
    @TestSecurity(user = "service-account-scope-off", roles = {"elicit_importer"})
    void given_anInactiveImportClient_when_asked_then_itIsRefusedAndNoDepartmentIsPermitted() {
        Department own = DepartmentFixtures.department(token());
        DepartmentFixtures.importClient("service-account-scope-off", false, own);

        assertThrows(ForbiddenException.class, () -> scope.requireImportClient());
        assertFalse(scope.permits(own.id));
    }

    /** UC-010 BR-170: the role without a record is refused. */
    @Test
    @TestSecurity(user = "service-account-scope-none", roles = {"elicit_importer"})
    void given_theImporterRoleAndNoRecord_when_asked_then_itIsNotAnImportClient() {
        DepartmentFixtures.noImportClient("service-account-scope-none");

        assertThrows(ForbiddenException.class, () -> scope.requireImportClient());
    }

    /** UC-010 BR-170: the record without the role, or with a console role beside it, is refused. */
    @Test
    @TestSecurity(user = "service-account-scope-mixed", roles = {"elicit_importer", "elicit_user"})
    void given_anImportClientWhoseIdentityAlsoCarriesAConsoleRole_when_asked_then_itIsRefused() {
        Department own = DepartmentFixtures.department(token());
        DepartmentFixtures.importClient("service-account-scope-mixed", true, own);

        assertThrows(ForbiddenException.class, () -> scope.requireImportClient());
        assertFalse(scope.permits(own.id));
    }

    /**
     * UC-001 BR-171: a principal that matches an import client is that and nothing else. A
     * console user record of the same name, which the screens refuse to create (BR-166), does
     * not give it a user's departments.
     */
    @Test
    @TestSecurity(user = "service-account-scope-both", roles = {"elicit_user"})
    void given_aNameHeldByBothKindsOfRecord_when_askedAsAUser_then_theConsoleRecordIsNotConsulted() {
        Department own = DepartmentFixtures.department(token());
        DepartmentFixtures.consoleRecord("service-account-scope-both", true, own);
        DepartmentFixtures.importClient("service-account-scope-both", true, own);

        assertFalse(scope.permits(own.id));
        assertThrows(ForbiddenException.class, () -> scope.requireImportClient());
    }
}
