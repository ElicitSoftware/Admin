package com.elicitsoftware.service;

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

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

/**
 * Integration smoke test for the {@link TokenService} REST surface.
 *
 * <p>Traceability: UC-001 (Authenticate and Access the Admin Console) and
 * UC-010 (Register Subjects via Integration API). This is a booted
 * {@link QuarkusTest} that exercises the public, permit-all {@code /api/secured/test}
 * probe to confirm the JAX-RS application and security pipeline start correctly.</p>
 *
 * <p><strong>Opt-in:</strong> a full {@code @QuarkusTest} boots the application,
 * which requires a reachable PostgreSQL database (Flyway {@code migrate-at-start})
 * and OIDC provider. Because those are not available in a plain unit build, this
 * test only runs when {@code -Dit.integration=true} is passed. Enable it once the
 * backing services (or Quarkus Dev Services / Testcontainers) are configured for
 * the test profile:</p>
 *
 * <pre>{@code ./mvnw test -Dit.integration=true}</pre>
 */
@QuarkusTest
@EnabledIfSystemProperty(named = "it.integration", matches = "true")
class TokenServiceEndpointIT {

    /** UC-001/UC-010: the secured application root is reachable and the probe responds. */
    @Test
    void testEndpointRespondsWhenPermitted() {
        given()
            .when().get("/api/secured/test")
            .then()
            .statusCode(200)
            .body(containsString("token test"));
    }
}
