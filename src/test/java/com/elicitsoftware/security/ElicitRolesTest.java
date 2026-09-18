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

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plain unit tests for {@link ElicitRoles#expand}.
 *
 * <p>Traceability: UC-001 BR-006 (roles are cumulative) and UC-020 BR-080 (analytics is an
 * orthogonal grant). No Quarkus context is needed -- {@code expand} is a pure function.</p>
 */
class ElicitRolesTest {

    /** UC-001/BR-006: elicit_admin implies elicit_user and elicit_importer. */
    @Test
    void adminExpandsToAllThreeRoles() {
        assertEquals(Set.of(ElicitRoles.ADMIN, ElicitRoles.USER, ElicitRoles.IMPORTER),
                ElicitRoles.expand(Set.of(ElicitRoles.ADMIN)));
    }

    /** UC-001/BR-006: elicit_user implies elicit_importer but not elicit_admin. */
    @Test
    void userExpandsToUserAndImporter() {
        assertEquals(Set.of(ElicitRoles.USER, ElicitRoles.IMPORTER),
                ElicitRoles.expand(Set.of(ElicitRoles.USER)));
    }

    /** UC-001/BR-006: elicit_importer implies only itself. */
    @Test
    void importerExpandsToItselfOnly() {
        assertEquals(Set.of(ElicitRoles.IMPORTER), ElicitRoles.expand(Set.of(ElicitRoles.IMPORTER)));
    }

    /** UC-020/BR-080: elicit_analytics expands to itself only. */
    @Test
    void analyticsExpandsToItselfOnly() {
        assertEquals(Set.of(ElicitRoles.ANALYTICS), ElicitRoles.expand(Set.of(ElicitRoles.ANALYTICS)));
    }

    /** UC-020/BR-080: elicit_admin does not imply elicit_analytics. */
    @Test
    void adminDoesNotImplyAnalytics() {
        assertFalse(ElicitRoles.expand(Set.of(ElicitRoles.ADMIN)).contains(ElicitRoles.ANALYTICS));
    }

    /** UC-001/BR-006 + UC-020/BR-080: a ladder role and analytics expand independently. */
    @Test
    void userPlusAnalyticsExpandsToBoth() {
        assertEquals(Set.of(ElicitRoles.USER, ElicitRoles.IMPORTER, ElicitRoles.ANALYTICS),
                ElicitRoles.expand(Set.of(ElicitRoles.USER, ElicitRoles.ANALYTICS)));
    }

    /** UC-001/BR-001: ALL recognizes the three ladder roles plus analytics; LADDER excludes analytics. */
    @Test
    void allContainsAnalyticsAndLadderDoesNot() {
        assertTrue(ElicitRoles.ALL.contains(ElicitRoles.ANALYTICS));
        assertFalse(ElicitRoles.LADDER.contains(ElicitRoles.ANALYTICS));
        assertEquals(3, ElicitRoles.LADDER.size());
    }

    /** An empty input set expands to an empty set. */
    @Test
    void emptySetExpandsToEmptySet() {
        assertEquals(Set.of(), ElicitRoles.expand(Set.of()));
    }
}
