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

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UC-020: View the System Overview, step 2 and A3 — what is running: the version and build time
 * as Maven filtered them, "unknown" when the build did not, the active profiles and the uptime.
 * The configuration fields are set directly, as CDI would inject them.
 */
class BuildInfoValuesTest {

    private static BuildInfo info(Optional<String> version, Optional<String> timestamp) {
        BuildInfo info = new BuildInfo();
        info.applicationName = "elicit-admin";
        info.version = version;
        info.buildTimestamp = timestamp;
        return info;
    }

    @Test
    void given_filteredValues_when_read_then_theyAreReported() {
        BuildInfo info = info(Optional.of("3.1.0"), Optional.of("2026-10-01T12:00:00Z"));

        assertEquals("elicit-admin", info.applicationName());
        assertEquals("3.1.0", info.version());
        assertEquals("2026-10-01T12:00:00Z", info.buildTimestamp());
    }

    @Test
    void given_unfilteredOrBlankValues_when_read_then_unknown() {
        for (String value : List.of("@project.version@", "", "   ")) {
            BuildInfo info = info(Optional.of(value), Optional.of(value));

            assertEquals("unknown", info.version(), value);
            assertEquals("unknown", info.buildTimestamp(), value);
        }
        BuildInfo none = info(Optional.empty(), Optional.empty());
        assertEquals("unknown", none.version());
        assertEquals("unknown", none.buildTimestamp());
    }

    @Test
    void given_aValueWithAnAtSignInside_when_read_then_itIsKept() {
        BuildInfo info = info(Optional.of("3.1.0@abc"), Optional.of("x@y"));

        assertEquals("3.1.0@abc", info.version());
        assertEquals("x@y", info.buildTimestamp());
    }

    @Test
    void given_theBeanStarted_when_uptime_then_measuredFromTheStart() {
        BuildInfo info = info(Optional.empty(), Optional.empty());
        Instant before = Instant.now();

        info.recordStart();

        Instant started = info.startedAt();
        assertFalse(started.isBefore(before));
        assertFalse(started.isAfter(Instant.now()));
        Duration uptime = info.uptime();
        assertFalse(uptime.isNegative());
        assertTrue(uptime.compareTo(Duration.ofMinutes(1)) < 0, uptime.toString());
    }

    @Test
    void given_anActiveProfile_when_profiles_then_itIsListed() {
        List<String> profiles = IsolatedConfig.with(Map.of(), "staging",
                () -> info(Optional.empty(), Optional.empty()).profiles());

        assertEquals(List.of("staging"), profiles);
    }
}
