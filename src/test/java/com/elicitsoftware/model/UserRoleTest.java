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

import com.elicitsoftware.model.UserRole.UserRoleId;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plain unit tests for {@link UserRole} and its composite key {@link UserRoleId}.
 *
 * <p>Traceability: UC-016 (Manage User Role Assignments) — direct database role grants in
 * {@code survey.user_roles}, managed by the admin UI when
 * {@code elicit.authorization.mode=DATABASE}.</p>
 */
class UserRoleTest {

    @Test
    void constructorBuildsCompositeKey() {
        UserRole role = new UserRole(42L, "elicit_admin");

        assertEquals(42L, role.getId().getUserId());
        assertEquals("elicit_admin", role.getId().getRoleName());
    }

    @Test
    void noArgConstructorLeavesIdUnset() {
        UserRole role = new UserRole();
        assertNull(role.getId());
    }

    @Test
    void setIdReplacesCompositeKey() {
        UserRole role = new UserRole();
        UserRoleId id = new UserRoleId(1L, "elicit_user");

        role.setId(id);

        assertEquals(id, role.getId());
    }

    @Test
    void userRolesWithSameCompositeKeyAreEqual() {
        UserRole a = new UserRole(1L, "elicit_admin");
        UserRole b = new UserRole(1L, "elicit_admin");

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertEquals(a, a, "a UserRole is equal to itself");
        assertNotEquals(a, "not a user role", "a UserRole is never equal to a different type");
    }

    @Test
    void userRolesWithDifferentRoleNamesAreNotEqual() {
        UserRole a = new UserRole(1L, "elicit_admin");
        UserRole b = new UserRole(1L, "elicit_user");

        assertNotEquals(a, b);
    }

    @Test
    void userRolesWithDifferentUserIdsAreNotEqual() {
        UserRole a = new UserRole(1L, "elicit_admin");
        UserRole b = new UserRole(2L, "elicit_admin");

        assertNotEquals(a, b);
    }

    @Test
    void userRoleIdSettersUpdateFields() {
        UserRoleId id = new UserRoleId();
        id.setUserId(5L);
        id.setRoleName("elicit_importer");

        assertEquals(5L, id.getUserId());
        assertEquals("elicit_importer", id.getRoleName());
    }

    @Test
    void userRoleIdEqualityIsValueBased() {
        UserRoleId a = new UserRoleId(1L, "elicit_admin");
        UserRoleId b = new UserRoleId(1L, "elicit_admin");
        UserRoleId different = new UserRoleId(1L, "elicit_user");

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, different);
        assertEquals(a, a, "a UserRoleId is equal to itself");
        assertNotEquals(a, "not a user role id", "a UserRoleId is never equal to a different type");
    }

    @Test
    void deduplicatesInSetByCompositeKey() {
        UserRole a = new UserRole(1L, "elicit_admin");
        UserRole sameKey = new UserRole(1L, "elicit_admin");
        UserRole other = new UserRole(1L, "elicit_user");

        Set<UserRole> set = Set.of(a, other);
        assertEquals(2, set.size());
        assertTrue(set.contains(sameKey));
    }
}
