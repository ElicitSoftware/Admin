package com.elicitsoftware.analytics;

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
import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guest tokens minted for the embedded dashboard (UC-020 BR-081, NFR-011), checked against the
 * {@code %test} analytics configuration: signature with the shared secret, Superset's claim
 * shape, dashboard scope, and lifetime.
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class SupersetGuestTokenServiceTest {

    private static final String SECRET = "test-guest-token-secret-at-least-32-chars";
    private static final String DASHBOARD = "11111111-2222-4333-8444-555555555555";

    @Inject
    SupersetGuestTokenService service;

    @Inject
    AnalyticsConfig config;

    private static JsonObject payload(String jwt) {
        String[] parts = jwt.split("\\.");
        assertEquals(3, parts.length, "compact JWS has three parts");
        String json = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        return Json.createReader(new StringReader(json)).readObject();
    }

    private static byte[] hmac(String signingInput) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8));
    }

    /** UC-020/BR-081: the token verifies with the secret shared with Superset (HS256). */
    @Test
    void tokenIsSignedWithSharedSecret() throws Exception {
        String jwt = service.mint("uc020.analyst");
        String[] parts = jwt.split("\\.");
        JsonObject header = Json.createReader(new StringReader(
                new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8))).readObject();

        assertEquals("HS256", header.getString("alg"));
        assertArrayEquals(hmac(parts[0] + "." + parts[1]), Base64.getUrlDecoder().decode(parts[2]));
    }

    /** UC-020/BR-081: the claims follow Superset's guest-token shape and name only the configured dashboard. */
    @Test
    void claimsMatchSupersetGuestTokenShape() {
        JsonObject claims = payload(service.mint("uc020.analyst"));

        assertEquals("guest", claims.getString("type"));
        assertEquals("uc020.analyst", claims.getJsonObject("user").getString("username"));
        assertEquals(1, claims.getJsonArray("resources").size());
        assertEquals("dashboard", claims.getJsonArray("resources").getJsonObject(0).getString("type"));
        assertEquals(DASHBOARD, claims.getJsonArray("resources").getJsonObject(0).getString("id"));
        assertTrue(claims.getJsonArray("rls_rules").isEmpty(), "no row-level security rules this round");
        assertFalse(claims.containsKey("aud"), "no audience unless configured");
    }

    /** NFR-011: the token expires after the configured lifetime, at most five minutes. */
    @Test
    void tokenExpiresAfterConfiguredTtl() {
        JsonObject claims = payload(service.mint("uc020.analyst"));

        long lifetime = claims.getJsonNumber("exp").longValue() - claims.getJsonNumber("iat").longValue();
        assertEquals(config.getGuestTokenTtlSeconds(), lifetime);
        assertTrue(lifetime <= 300, "guest tokens live at most five minutes");
    }
}
