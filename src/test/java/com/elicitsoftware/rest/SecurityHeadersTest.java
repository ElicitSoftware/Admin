package com.elicitsoftware.rest;

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

import com.elicitsoftware.test.PostgresTestResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.ValidatableResponse;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

/**
 * NFR-020: Browser Security Headers — every response of the console carries them, whether the
 * path is public, part of the API, or refused for want of a session (UC-001).
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class SecurityHeadersTest {

    private static void assertHeaders(ValidatableResponse response) {
        response.header("Strict-Transport-Security", equalTo("max-age=31536000; includeSubDomains"))
                .header("X-Frame-Options", equalTo("DENY"))
                .header("X-Content-Type-Options", equalTo("nosniff"))
                .header("Referrer-Policy", equalTo("same-origin"))
                .header("Content-Security-Policy", equalTo("frame-ancestors 'none'"));
    }

    @Test
    void given_aPublicEndpoint_when_requested_then_theHeadersAreSent() {
        assertHeaders(given().when().get("/q/health/live").then().statusCode(200));
    }

    @Test
    void given_aPublicApiPath_when_requested_then_theHeadersAreSent() {
        assertHeaders(given().when().get("/api/secured/test").then().statusCode(200));
    }

    @Test
    void given_noSession_when_aProtectedPathIsRequested_then_theRefusalCarriesTheHeaders() {
        assertHeaders(given().redirects().follow(false)
                .when().get("/api/secured/roles")
                .then().statusCode(anyOf(is(401), is(302))));
    }
}
