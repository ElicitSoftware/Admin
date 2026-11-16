package com.elicitsoftware.security;

/*-
 * ***LICENSE_START***
 * Elicit Admin
 * %%
 * Copyright (C) 2025 - 2026 The Regents of the University of Michigan - Rogel Cancer Center
 * %%
 * PolyForm Noncommercial License 1.0.0
 * <https://polyformproject.org/licenses/noncommercial/1.0.0>
 * ***LICENSE_END***
 */

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.HealthCheckResponseBuilder;
import org.eclipse.microprofile.health.Readiness;

/**
 * The console is not ready without a service token (UC-018 BR-162): the readiness probe carries
 * a check named {@code service-token}, down with the reason while {@code ELICIT_SERVICE_TOKEN}
 * is unset. It reports presence only (NFR-011).
 */
@Readiness
@ApplicationScoped
public class ServiceTokenHealthCheck implements HealthCheck {

    static final String NAME = "service-token";
    static final String REASON = ServiceToken.VARIABLE + " is not set";

    @Inject
    ServiceToken serviceToken;

    @Override
    public HealthCheckResponse call() {
        HealthCheckResponseBuilder response = HealthCheckResponse.named(NAME);
        return serviceToken.isConfigured()
                ? response.up().build()
                : response.down().withData("reason", REASON).build();
    }
}
