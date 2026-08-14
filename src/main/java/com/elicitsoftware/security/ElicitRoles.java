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

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * The three roles Elicit Admin recognizes, from any source (OIDC or database), and the
 * cumulative hierarchy between them: {@code elicit_admin} implies {@code elicit_user} and
 * {@code elicit_importer}; {@code elicit_user} implies {@code elicit_importer};
 * {@code elicit_importer} implies only itself.
 *
 * <p>Keycloak declares all three client roles {@code composite: false} today, so this
 * hierarchy is not expressed on the identity-provider side -- {@link #expand} computes it
 * application-side, uniformly regardless of whether a raw role came from OIDC or the
 * {@code survey.user_roles} database fallback. Only the raw (unexpanded) role is ever stored
 * in Keycloak or in {@code survey.user_roles}.</p>
 */
public final class ElicitRoles {

    public static final String ADMIN = "elicit_admin";
    public static final String USER = "elicit_user";
    public static final String IMPORTER = "elicit_importer";

    public static final Set<String> ALL = Set.of(ADMIN, USER, IMPORTER);

    private static final Map<String, Set<String>> IMPLIED = Map.of(
            ADMIN, Set.of(ADMIN, USER, IMPORTER),
            USER, Set.of(USER, IMPORTER),
            IMPORTER, Set.of(IMPORTER)
    );

    private ElicitRoles() {
    }

    /**
     * Expands a set of raw roles to the full set of roles they imply under the
     * admin/user/importer hierarchy. Roles outside {@link #ALL} pass through unchanged.
     */
    public static Set<String> expand(Set<String> rawRoles) {
        Set<String> expanded = new HashSet<>();
        for (String role : rawRoles) {
            expanded.addAll(IMPLIED.getOrDefault(role, Set.of(role)));
        }
        return expanded;
    }
}
