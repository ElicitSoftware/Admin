package com.elicitsoftware.diagnostics;

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

import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * C-007: the application carries no identity-provider realm definition; the evaluation realm
 * lives in the umbrella repository, beside the compose file that imports it.
 */
class PackagedResourcesTest {

    @Test
    void given_theApplicationResources_when_aRealmDefinitionIsLookedFor_then_thereIsNone() {
        assertNull(getClass().getResource("/elicit-realm.json"));
    }
}
