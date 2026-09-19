package com.elicitsoftware.test;

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

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;

/** Test profile with no analytics configuration, for the "not configured" flows (UC-020 A1). */
public class AnalyticsDisabledTestProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
                "elicit.analytics.superset-url", "",
                "elicit.analytics.dashboard-id", "",
                "elicit.analytics.guest-token-secret", "");
    }
}
