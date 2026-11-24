package com.elicitsoftware.rest;

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
import com.elicitsoftware.test.PostgresTestResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

/**
 * NFR-003: Authentication Enforcement and NFR-011: Diagnostics Never Leak Secrets — the two
 * troubleshooting endpoints the registration API used to carry are gone. {@code /api/secured/test}
 * answered anyone, and {@code /api/secured/roles} listed every claim of the caller's token; the
 * OIDC diagnostics view (UC-009) is where an administrator reads the signed-in identity.
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class RemovedDebugEndpointsTest {

    @Test
    void given_noSession_when_theFormerProbeIsRequested_then_itIsRefused() {
        given().redirects().follow(false)
                .when().get("/api/secured/test")
                .then().statusCode(anyOf(is(401), is(302)));
    }

    /** No resource answers either path; what does is the console's page, which says nothing of either. */
    @Test
    @TestSecurity(user = "admin", roles = {"elicit_admin"})
    void given_anAdministrator_when_eitherEndpointIsRequested_then_neitherAnswers() {
        given().when().get("/api/secured/test")
                .then().body(not(containsString("access code service test")));
        given().when().get("/api/secured/roles")
                .then().body(not(containsString("Principal Name")))
                .body(not(containsString("JWT")));
    }
}
