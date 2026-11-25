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

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Plain unit tests for {@link ElicitRoles#expand}.
 *
 * <p>Traceability: UC-001 BR-006 (roles are cumulative). No Quarkus context is needed --
 * {@code expand} is a pure function.</p>
 */
class ElicitRolesTest {

    /** UC-001/BR-006: elicit_admin implies elicit_user, and not elicit_importer. */
    @Test
    void adminExpandsToAdminAndUser() {
        assertEquals(Set.of(ElicitRoles.ADMIN, ElicitRoles.USER),
                ElicitRoles.expand(Set.of(ElicitRoles.ADMIN)));
    }

    /** UC-001/BR-006: elicit_user implies only itself; no user role implies elicit_importer. */
    @Test
    void userExpandsToItselfOnly() {
        assertEquals(Set.of(ElicitRoles.USER), ElicitRoles.expand(Set.of(ElicitRoles.USER)));
    }

    /** UC-016/BR-054: the roles a user can hold leave elicit_importer to import clients. */
    @Test
    void userRolesAreAdminAndUser() {
        assertEquals(Set.of(ElicitRoles.ADMIN, ElicitRoles.USER), ElicitRoles.USER_ROLES);
    }

    /** UC-001/BR-006: elicit_importer implies only itself. */
    @Test
    void importerExpandsToItselfOnly() {
        assertEquals(Set.of(ElicitRoles.IMPORTER), ElicitRoles.expand(Set.of(ElicitRoles.IMPORTER)));
    }

    /** An empty input set expands to an empty set. */
    @Test
    void emptySetExpandsToEmptySet() {
        assertEquals(Set.of(), ElicitRoles.expand(Set.of()));
    }
}
