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

import org.eclipse.microprofile.health.HealthCheckResponse;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * UC-018 BR-162: the console is not ready without a service token — the readiness check named
 * {@code service-token} is down with the reason, and up, saying nothing more, when one is set.
 */
class ServiceTokenHealthCheckTest {

    private static HealthCheckResponse call(String token) {
        ServiceTokenHealthCheck check = new ServiceTokenHealthCheck();
        check.serviceToken = new ServiceToken(Optional.ofNullable(token), Optional.empty(), "http://survey:8080");
        return check.call();
    }

    @Test
    void given_noToken_when_probed_then_notReadyWithTheReason() {
        for (String unset : new String[]{null, "", "  "}) {
            HealthCheckResponse response = call(unset);
            assertEquals("service-token", response.getName());
            assertEquals(HealthCheckResponse.Status.DOWN, response.getStatus());
            assertEquals(Optional.of(Map.<String, Object>of("reason", "ELICIT_SERVICE_TOKEN is not set")), response.getData());
        }
    }

    @Test
    void given_aToken_when_probed_then_readyAndTheValueIsNotReported() {
        String token = "unit-test-service-token-0123456789abcdef";
        HealthCheckResponse response = call(token);
        assertEquals("service-token", response.getName());
        assertEquals(HealthCheckResponse.Status.UP, response.getStatus());
        assertFalse(response.getData().toString().contains(token));
    }
}
