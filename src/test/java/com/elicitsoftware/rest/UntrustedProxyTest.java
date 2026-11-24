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
import com.elicitsoftware.test.UntrustedProxyTestProfile;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.security.TestSecurity;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;

/**
 * NFR-026: Forwarding Headers Only From a Trusted Proxy — a client that is not in the trusted list
 * cannot choose the address the console builds its redirect URIs from, nor the client address it
 * logs, by sending forwarding headers of its own.
 */
@QuarkusTest
@TestProfile(UntrustedProxyTestProfile.class)
@QuarkusTestResource(PostgresTestResource.class)
class UntrustedProxyTest {

    @Test
    @TestSecurity(user = "admin", roles = {"elicit_admin"})
    void given_aClientOutsideTheTrustedList_when_itSendsForwardingHeaders_then_theyAreIgnored() {
        given()
                .header("X-Forwarded-Proto", "https")
                .header("X-Forwarded-Host", "attacker.example")
                .header("X-Forwarded-For", "198.51.100.20")
                .header("Forwarded", "for=198.51.100.21;host=attacker.example;proto=https")
                .when().get("/api/test-only/forwarded")
                .then().statusCode(200)
                .body(not(containsString("attacker.example")))
                .body(not(containsString("198.51.100.2")))
                .body(not(containsString("https://")));
    }
}
