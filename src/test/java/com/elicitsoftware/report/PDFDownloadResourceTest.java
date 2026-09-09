package com.elicitsoftware.report;

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

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link PDFDownloadResource}.
 *
 * <p>Traceability: UC-005 (Generate and Download Survey Report). 2026-09 audit finding #2:
 * {@code cachePDF()} previously derived its cache key from
 * {@code System.currentTimeMillis() + "_" + System.nanoTime()}, a low-entropy, guessable
 * value served by an intentionally unauthenticated endpoint
 * ({@code quarkus.http.auth.permission.pdf.paths=/api/pdf/*}). The key is now a 256-bit
 * {@code SecureRandom} value, and a successful download removes the cache entry so a
 * leaked/guessed key cannot be replayed.</p>
 */
@QuarkusTest
class PDFDownloadResourceTest {

    private static final String DOWNLOAD_PATH = "/api/pdf/download";

    /** #2: cache keys are not derived from a small, guessable value space. */
    @Test
    void cacheKeysAreHighEntropyAndUnique() {
        Set<String> keys = new HashSet<>();
        for (int i = 0; i < 50; i++) {
            String key = PDFDownloadResource.cachePDF("test".getBytes(StandardCharsets.UTF_8));
            assertTrue(key.startsWith("pdf_"));
            // "pdf_" + 32 raw bytes, base64url-encoded without padding, is well over 40 chars -
            // far too long to be a timestamp/nanoTime-derived value.
            assertTrue(key.length() > 40, "key should be long enough to reflect 256 bits of entropy: " + key);
            keys.add(key);
        }
        assertEquals(50, keys.size(), "every generated key should be unique");
    }

    /** #2: a cached PDF can be downloaded once via its key. */
    @Test
    void downloadSucceedsForAValidKey() {
        byte[] content = "%PDF-1.4 test content".getBytes(StandardCharsets.UTF_8);
        String key = PDFDownloadResource.cachePDF(content);

        given()
            .queryParam("key", key)
            .when().get(DOWNLOAD_PATH)
            .then()
            .statusCode(200)
            .contentType("application/pdf");
    }

    /** #2: a second download with the same key fails - the cache entry is single-use. */
    @Test
    void keyCannotBeReplayedAfterASuccessfulDownload() {
        byte[] content = "%PDF-1.4 test content".getBytes(StandardCharsets.UTF_8);
        String key = PDFDownloadResource.cachePDF(content);

        given().queryParam("key", key).when().get(DOWNLOAD_PATH).then().statusCode(200);

        given()
            .queryParam("key", key)
            .when().get(DOWNLOAD_PATH)
            .then()
            .statusCode(404);
    }

    /** An unknown key is rejected. */
    @Test
    void downloadFailsForAnUnknownKey() {
        given()
            .queryParam("key", "pdf_does-not-exist")
            .when().get(DOWNLOAD_PATH)
            .then()
            .statusCode(404);
    }

    /** A missing key parameter is rejected with a client error, not a server error. */
    @Test
    void downloadFailsWithoutAKey() {
        given()
            .when().get(DOWNLOAD_PATH)
            .then()
            .statusCode(400);
    }
}
