package com.elicitsoftware.service;

/*-
 * ***LICENSE_START***
 * Elicit Survey
 * %%
 * Copyright (C) 2025 - 2026 The Regents of the University of Michigan - Rogel Cancer Center
 * %%
 * PolyForm Noncommercial License 1.0.0
 * <https://polyformproject.org/licenses/noncommercial/1.0.0>
 * ***LICENSE_END***
 */

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** UC-002: Search and Monitor Subject Progress — the search query's parameters are never null. */
class StatusQueryTest {

    @Test
    void given_noParameters_when_aQueryIsBuilt_then_itHasAnEmptyParameterMap() {
        StatusQuery query = new StatusQuery("1 = 1", null, null);
        assertEquals(Map.of(), query.params());
        assertNull(query.sort());
    }

    @Test
    void given_parameters_when_aQueryIsBuilt_then_theyAreKept() {
        StatusQuery query = new StatusQuery("departmentId in :d", Map.of("d", 3), null);
        assertEquals(Map.of("d", 3), query.params());
        assertEquals("departmentId in :d", query.whereClause());
    }
}
