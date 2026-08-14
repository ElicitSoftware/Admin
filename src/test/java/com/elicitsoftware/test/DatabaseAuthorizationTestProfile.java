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

/**
 * Overrides {@code elicit.authorization.mode} to {@code DATABASE} for tests that need the
 * Edit User role-assignment section to be visible/functional, or the
 * {@link com.elicitsoftware.security.RoleSecurityIdentityAugmentor} database fallback to run
 * (UC-016). Tests without this profile run against the default {@code OIDC} mode.
 */
public class DatabaseAuthorizationTestProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of("elicit.authorization.mode", "DATABASE");
    }
}
