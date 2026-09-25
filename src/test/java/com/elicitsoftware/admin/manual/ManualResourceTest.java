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
import io.quarkus.test.security.TestSecurity;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UC-029 step 2: the manual is served to both console roles, and refused to anyone else.
 *
 * <p>UC-029 BR-006 — one document serves {@code elicit_admin} and {@code elicit_user} alike, so
 * both are asserted here; a signed-in reader holding neither console role is refused as the
 * console's views refuse them (UC-029 A4). The test classpath carries a stand-in PDF at
 * {@code src/test/resources/manual/}, so the suite never needs a LaTeX run.</p>
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class ManualResourceTest {

    private static final String MANUAL_PATH = "/api/manual";

    /** UC-029 BR-006: the administrator role receives the manual. */
    @Test
    @TestSecurity(user = "manual.admin", roles = {"elicit_admin"})
    void anAdministratorGetsTheManual() {
        assertManualIsServed();
    }

    /**
     * UC-029 BR-006: the user role receives the same manual — this is the difference from
     * Author, which publishes its manual to one role only. Rather than a second document, the
     * manual marks the administrator-only procedures where they appear (UC-029 A5).
     */
    @Test
    @TestSecurity(user = "manual.user", roles = {"elicit_user"})
    void theUserRoleGetsTheSameManual() {
        assertManualIsServed();
    }

    /** UC-029 A4: a signed-in reader holding neither console role is refused. */
    @Test
    @TestSecurity(user = "manual.importer", roles = {"elicit_importer"})
    void aReaderWithNoConsoleRoleIsRefused() {
        given().when().get(MANUAL_PATH).then().statusCode(403);
    }

    /**
     * UC-029 A4: an unauthenticated request never reaches the resource. With OIDC hybrid mode
     * the response may be 401 (Bearer challenge) or a 302 redirect to the provider; either
     * proves the manual is not public.
     */
    @Test
    void anAnonymousRequestIsRefused() {
        given().redirects().follow(false)
                .when().get(MANUAL_PATH)
                .then().statusCode(anyOf(is(401), is(302)));
    }

    /** UC-029 steps 2 and A2: a PDF the browser's own viewer opens, named for saving. */
    private void assertManualIsServed() {
        Response response = given().when().get(MANUAL_PATH).then()
                .statusCode(200)
                .extract().response();

        assertEquals("application/pdf", response.getContentType());
        assertTrue(response.getHeader("Content-Disposition").contains(AdminManual.FILE_NAME),
                "the browser should name the saved file " + AdminManual.FILE_NAME);
        // "inline", so the browser's own viewer opens it rather than downloading it silently.
        assertTrue(response.getHeader("Content-Disposition").startsWith("inline"));
        assertTrue(response.asByteArray().length > 0, "the manual has content");
    }
}
