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
import io.quarkus.test.security.TestSecurity;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

/**
 * Access rules of the guest-token endpoint the embedded Analytics view calls (UC-020: analysts
 * only; A2 for other roles; UC-001 for anonymous callers).
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class AnalyticsResourceTest {

    private static final String PATH = "/api/secured/analytics/guest-token";

    /** UC-020: an analyst receives a token and its lifetime. */
    @Test
    @TestSecurity(user = "uc020.analyst", roles = {"elicit_analytics"})
    void analystGetsToken() {
        given()
            .when().get(PATH)
            .then()
            .statusCode(200)
            .header("Cache-Control", "no-store")
            .body("token", notNullValue())
            .body("expiresInSeconds", is(300));
    }

    /** UC-020/A2: an authenticated user without the analytics role is forbidden. */
    @Test
    @TestSecurity(user = "uc020.user", roles = {"elicit_user", "elicit_admin"})
    void ladderRolesAloneAreForbidden() {
        given()
            .when().get(PATH)
            .then()
            .statusCode(403);
    }

    /** UC-001: an unauthenticated request is rejected (401 challenge or 302 to the provider). */
    @Test
    void anonymousIsRejected() {
        given()
            .redirects().follow(false)
            .when().get(PATH)
            .then()
            .statusCode(anyOf(is(401), is(302)));
    }
}
