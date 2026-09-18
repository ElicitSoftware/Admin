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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Booted persistence tests for {@link UserRoleService} (UC-016: Manage User Role
 * Assignments). Exercises the ladder grant ({@code setLadderRole}/{@code clearLadderRole}/
 * {@code findLadderRole}) and the independent analytics grant ({@code setAnalytics}/
 * {@code hasAnalytics}, UC-020) against the real schema, including the BR-054 CHECK
 * constraint's rejection of unrecognized role names.
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

    /** UC-016: setLadderRole grants a role that was not previously present. */
    @Test
    @TestTransaction
    void setLadderRoleGrantsRole() {
        User user = newUser("uc016.svc.add@example.org");

        userRoleService.setLadderRole(user.getId(), "elicit_admin");

        assertEquals(Optional.of("elicit_admin"), userRoleService.findLadderRole(user.getId()));
    }

    /** UC-016/BR-055: setLadderRole replaces the existing grant rather than adding a second row. */
    @Test
    @TestTransaction
    void setLadderRoleReplacesExistingGrant() {
        User user = newUser("uc016.svc.replace@example.org");
        userRoleService.setLadderRole(user.getId(), "elicit_admin");

        userRoleService.setLadderRole(user.getId(), "elicit_importer");

        assertEquals(Optional.of("elicit_importer"), userRoleService.findLadderRole(user.getId()));
        assertEquals(1, UserRole.count("id.userId", user.getId()));
    }

    /** UC-016: clearLadderRole removes the grant entirely. */
    @Test
    @TestTransaction
    void clearLadderRoleRemovesGrant() {
        User user = newUser("uc016.svc.clear@example.org");
        userRoleService.setLadderRole(user.getId(), "elicit_user");

        userRoleService.clearLadderRole(user.getId());

        assertEquals(Optional.empty(), userRoleService.findLadderRole(user.getId()));
    }

    /** UC-016/BR-054: an unrecognized role name is rejected before any write. */
    @Test
    @TestTransaction
    void setLadderRoleRejectsUnrecognizedRole() {
        User user = newUser("uc016.svc.invalid@example.org");

        assertThrows(IllegalArgumentException.class,
                () -> userRoleService.setLadderRole(user.getId(), "not_a_role"));
        assertEquals(Optional.empty(), userRoleService.findLadderRole(user.getId()));
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

    /** UC-016/BR-054: setLadderRole rejects elicit_analytics; it is not a ladder role. */
    @Test
    @TestTransaction
    void setLadderRoleRejectsAnalytics() {
        User user = newUser("uc016.svc.ladder-analytics@example.org");

        assertThrows(IllegalArgumentException.class,
                () -> userRoleService.setLadderRole(user.getId(), "elicit_analytics"));
    }

    /** UC-020: setAnalytics(true) adds an analytics row; hasAnalytics reports it. */
    @Test
    @TestTransaction
    void setAnalyticsAddsRow() {
        User user = newUser("uc016.svc.analytics@example.org");

        userRoleService.setAnalytics(user.getId(), true);

        assertTrue(userRoleService.hasAnalytics(user.getId()));
        assertEquals(1, UserRole.count("id.userId", user.getId()));
        assertEquals(Optional.empty(), userRoleService.findLadderRole(user.getId()));
    }

    /** UC-020: setAnalytics(true) twice leaves a single row. */
    @Test
    @TestTransaction
    void setAnalyticsIsIdempotent() {
        User user = newUser("uc016.svc.analytics-twice@example.org");

        userRoleService.setAnalytics(user.getId(), true);
        userRoleService.setAnalytics(user.getId(), true);

        assertEquals(1, UserRole.count("id.userId", user.getId()));
    }

    /** UC-016/BR-055: replacing the ladder grant preserves the analytics grant. */
    @Test
    @TestTransaction
    void settingLadderRolePreservesAnalytics() {
        User user = newUser("uc016.svc.keep-analytics@example.org");
        userRoleService.setAnalytics(user.getId(), true);
        userRoleService.setLadderRole(user.getId(), "elicit_admin");

        userRoleService.setLadderRole(user.getId(), "elicit_user");

        assertEquals(Optional.of("elicit_user"), userRoleService.findLadderRole(user.getId()));
        assertTrue(userRoleService.hasAnalytics(user.getId()));
        assertEquals(2, UserRole.count("id.userId", user.getId()));
    }

    /** UC-016/BR-055: clearing the ladder grant preserves the analytics grant. */
    @Test
    @TestTransaction
    void clearingLadderRolePreservesAnalytics() {
        User user = newUser("uc016.svc.clear-keep-analytics@example.org");
        userRoleService.setLadderRole(user.getId(), "elicit_user");
        userRoleService.setAnalytics(user.getId(), true);

        userRoleService.clearLadderRole(user.getId());

        assertEquals(Optional.empty(), userRoleService.findLadderRole(user.getId()));
        assertTrue(userRoleService.hasAnalytics(user.getId()));
    }

    /** UC-016/BR-055: revoking analytics removes only the analytics row. */
    @Test
    @TestTransaction
    void clearingAnalyticsKeepsLadder() {
        User user = newUser("uc016.svc.revoke-analytics@example.org");
        userRoleService.setLadderRole(user.getId(), "elicit_importer");
        userRoleService.setAnalytics(user.getId(), true);

        userRoleService.setAnalytics(user.getId(), false);

        assertFalse(userRoleService.hasAnalytics(user.getId()));
        assertEquals(Optional.of("elicit_importer"), userRoleService.findLadderRole(user.getId()));
    }

    /** UC-016/BR-054: the database CHECK constraint accepts elicit_analytics (V0.0.19). */
    @Test
    @TestTransaction
    void checkConstraintAcceptsAnalytics() {
        User user = newUser("uc016.svc.dbcheck-analytics@example.org");

        new UserRole(user.getId(), "elicit_analytics").persist();
        UserRole.flush();

        assertEquals(1, UserRole.count("id.userId", user.getId()));
    }

    /** UC-016: findLadderRole returns empty when the user has no grant. */
    @Test
    @TestTransaction
    void findLadderRoleReturnsEmptyWhenNoGrant() {
        User user = newUser("uc016.svc.none@example.org");

        assertTrue(userRoleService.findLadderRole(user.getId()).isEmpty());
    }
}
