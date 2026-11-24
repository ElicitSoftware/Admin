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
import static org.hamcrest.Matchers.equalTo;

/**
 * NFR-026: Forwarding Headers Only From a Trusted Proxy — with the shipped list, a proxy on
 * loopback or a private network decides the address the console builds its redirect URIs and links
 * from, and the client address it logs.
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class TrustedProxyTest {

    @Test
    @TestSecurity(user = "admin", roles = {"elicit_admin"})
    void given_aRequestFromATrustedProxy_when_itForwardsTheSiteAddress_then_theConsoleUsesIt() {
        given()
                .header("X-Forwarded-Proto", "https")
                .header("X-Forwarded-Host", "admin.example.org")
                .header("X-Forwarded-For", "198.51.100.20")
                .when().get("/api/test-only/forwarded")
                .then().statusCode(200)
                .body(equalTo("https://admin.example.org 198.51.100.20"));
    }
}
