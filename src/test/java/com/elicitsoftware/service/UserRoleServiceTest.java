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

import com.elicitsoftware.model.User;
import com.elicitsoftware.model.UserRole;
import com.elicitsoftware.test.PostgresTestResource;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Booted persistence tests for {@link UserRoleService} (UC-016: Manage User Role
 * Assignments). Exercises {@code setRole}/{@code clearRole}/{@code findRoleName} against the
 * real schema, including the BR-054 CHECK constraint's rejection of unrecognized role names.
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class UserRoleServiceTest {

    @Inject
    UserRoleService userRoleService;

    private User newUser(String username) {
        User u = new User();
        u.setUsername(username);
        u.setFirstName("F");
        u.setLastName("L");
        u.setActive(true);
        u.persist();
        return u;
    }

    /** UC-016: setRole grants a role that was not previously present. */
    @Test
    @TestTransaction
    void setRoleGrantsRole() {
        User user = newUser("uc016.svc.add@example.org");

        userRoleService.setRole(user.getId(), "elicit_admin");

        assertEquals(Optional.of("elicit_admin"), userRoleService.findRoleName(user.getId()));
    }

    /** UC-016/BR-055: setRole replaces the existing grant rather than adding a second row. */
    @Test
    @TestTransaction
    void setRoleReplacesExistingGrant() {
        User user = newUser("uc016.svc.replace@example.org");
        userRoleService.setRole(user.getId(), "elicit_admin");

        userRoleService.setRole(user.getId(), "elicit_importer");

        assertEquals(Optional.of("elicit_importer"), userRoleService.findRoleName(user.getId()));
        assertEquals(1, UserRole.count("id.userId", user.getId()));
    }

    /** UC-016: clearRole removes the grant entirely. */
    @Test
    @TestTransaction
    void clearRoleRemovesGrant() {
        User user = newUser("uc016.svc.clear@example.org");
        userRoleService.setRole(user.getId(), "elicit_user");

        userRoleService.clearRole(user.getId());

        assertEquals(Optional.empty(), userRoleService.findRoleName(user.getId()));
    }

    /** UC-016/BR-054: an unrecognized role name is rejected before any write. */
    @Test
    @TestTransaction
    void setRoleRejectsUnrecognizedRole() {
        User user = newUser("uc016.svc.invalid@example.org");

        assertThrows(IllegalArgumentException.class,
                () -> userRoleService.setRole(user.getId(), "not_a_role"));
        assertEquals(Optional.empty(), userRoleService.findRoleName(user.getId()));
    }

    /** UC-016/BR-054: the database CHECK constraint independently rejects a bad role name. */
    @Test
    @TestTransaction
    void checkConstraintRejectsInvalidRoleNameAtTheDatabaseLevel() {
        User user = newUser("uc016.svc.dbcheck@example.org");
        UserRole badRole = new UserRole(user.getId(), "not_a_role");

        assertThrows(PersistenceException.class, () -> {
            badRole.persist();
            UserRole.flush();
        });
    }

    /** UC-016: findRoleName returns empty when the user has no grant. */
    @Test
    @TestTransaction
    void findRoleNameReturnsEmptyWhenNoGrant() {
        User user = newUser("uc016.svc.none@example.org");

        assertTrue(userRoleService.findRoleName(user.getId()).isEmpty());
    }
}
