package com.elicitsoftware.admin.flow;

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

import jakarta.annotation.security.RolesAllowed;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Tests for {@link DebugView}.
 *
 * <p>Traceability: UC-009 (View Security Diagnostics). 2026-09 audit finding #1:
 * {@code DebugView} previously rendered the raw OIDC ID token and access token (full JWTs)
 * for any {@code elicit_admin} or {@code elicit_user}, in every environment. It is now
 * restricted to {@code elicit_admin} only, and token values are masked to their last 4
 * characters unless {@code elicit.debug.reveal-tokens} is explicitly enabled (e.g. in
 * {@code %dev}). These are plain unit tests of the masking helper and the security
 * annotation - no Vaadin/CDI boot required.</p>
 */
class DebugViewTest {

    /** #1: masking is on by default - only the last 4 characters remain visible. */
    @Test
    void tokenIsMaskedByDefault() {
        String token = "abcdefghijklmnop";
        String masked = DebugView.maskToken(token, false);
        assertEquals("************mnop", masked);
        assertFalse(masked.contains("abcd"), "the token's prefix must not survive masking");
    }

    /** #1: when explicitly enabled (e.g. %dev), the raw token is returned unmasked. */
    @Test
    void tokenIsUnmaskedWhenRevealIsEnabled() {
        String token = "abcdefghijklmnop";
        assertEquals(token, DebugView.maskToken(token, true));
    }

    /** A null token is passed through rather than throwing. */
    @Test
    void nullTokenIsPassedThrough() {
        assertEquals(null, DebugView.maskToken(null, false));
    }

    /** A token shorter than the visible window is masked as fully as possible, not thrown. */
    @Test
    void shortTokenDoesNotThrow() {
        assertEquals("ab", DebugView.maskToken("ab", false));
    }

    /** #1: only elicit_admin may view this diagnostic page - elicit_user was removed. */
    @Test
    void routeIsRestrictedToAdminOnly() {
        RolesAllowed rolesAllowed = DebugView.class.getAnnotation(RolesAllowed.class);
        assertArrayEquals(new String[] {"elicit_admin"}, rolesAllowed.value());
    }
}
