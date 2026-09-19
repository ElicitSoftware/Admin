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

import com.elicitsoftware.test.AnalyticsDisabledTestProfile;
import com.elicitsoftware.test.PostgresTestResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.security.TestSecurity;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;

/** UC-020/A1: with no analytics configuration the endpoint reports the feature absent. */
@QuarkusTest
@TestProfile(AnalyticsDisabledTestProfile.class)
@QuarkusTestResource(PostgresTestResource.class)
class AnalyticsResourceDisabledTest {

    /** UC-020/A1: even an analyst gets 404 when analytics is not configured. */
    @Test
    @TestSecurity(user = "uc020.analyst", roles = {"elicit_analytics"})
    void notConfiguredIs404() {
        given()
            .when().get("/api/secured/analytics/guest-token")
            .then()
            .statusCode(404);
    }
}
