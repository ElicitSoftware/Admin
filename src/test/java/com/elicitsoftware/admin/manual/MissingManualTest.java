package com.elicitsoftware.admin.manual;

/*-
 * ***LICENSE_START***
 * Elicit Admin
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
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * UC-029 A1: an image built without the manual simply does not offer it. The console is
 * otherwise unaffected — its on-screen help texts and the System overview remain.
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
@TestProfile(MissingManualTest.NoManualProfile.class)
class MissingManualTest {

    /** Points the manual at a classpath resource no build produces (UC-029 A1). */
    public static class NoManualProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("admin.manual.resource", "manual/no-such-manual.pdf");
        }
    }

    @Inject
    AdminManual manual;

    /** UC-029 A1: the bean reports the manual missing, so the navigation omits the entry. */
    @Test
    void aBuildWithoutTheManualReportsItMissing() {
        assertFalse(manual.isAvailable(), "a build that produced no manual should not offer one");
        assertNull(manual.open(), "isAvailable() and open() must agree");
    }

    /** UC-029 A1: a reader who reaches the path anyway is told there is nothing to open. */
    @Test
    @TestSecurity(user = "manual.admin", roles = {"elicit_admin"})
    void theEndpointAnswersNotFound() {
        given().when().get("/api/manual").then().statusCode(404);
    }
}
