package com.elicitsoftware.service;

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

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

/**
 * Tests for BrandResourceHandler, covering the embedded-resource fallback path,
 * content-type detection for all supported extensions, and the 404 path when
 * neither a filesystem file nor an embedded resource exists.
 *
 * <p>Embedded test resources live under
 * {@code src/test/resources/META-INF/brand/brand-test.*} and are loaded by
 * {@code BrandResourceHandler.readEmbeddedResource()} when no file exists at the
 * configured {@code brand.file.system.path} (default {@code /brand}).</p>
 *
 * <p>Path-traversal protection cannot be verified via HTTP because Vert.x
 * normalises {@code ..} segments before routing; the guard in
 * {@code getBrandFile()} therefore applies only to programmatic callers.</p>
 */
@QuarkusTest
@QuarkusTestResource(PostgreSQLTestResource.class)
class BrandResourceHandlerTest {

    // -----------------------------------------------------------------------
    // 404 – no file on filesystem, no embedded resource
    // -----------------------------------------------------------------------

    @Test
    void unknownFile_returnsNotFound() {
        given()
            .when().get("/api/brand/does-not-exist-anywhere.xyz")
            .then().statusCode(404);
    }

    // -----------------------------------------------------------------------
    // 200 – embedded-resource fallback, content-type per extension
    // -----------------------------------------------------------------------

    @Test
    void embeddedCss_returns200WithCssType() {
        given()
            .when().get("/api/brand/brand-test.css")
            .then()
            .statusCode(200)
            .contentType("text/css");
    }

    @Test
    void embeddedJs_returns200WithJsType() {
        given()
            .when().get("/api/brand/brand-test.js")
            .then()
            .statusCode(200)
            .contentType("application/javascript");
    }

    @Test
    void embeddedPng_returns200WithPngType() {
        given()
            .when().get("/api/brand/brand-test.png")
            .then()
            .statusCode(200)
            .contentType("image/png");
    }

    @Test
    void embeddedJpg_returns200WithJpegType() {
        given()
            .when().get("/api/brand/brand-test.jpg")
            .then()
            .statusCode(200)
            .contentType("image/jpeg");
    }

    @Test
    void embeddedJpeg_returns200WithJpegType() {
        given()
            .when().get("/api/brand/brand-test.jpeg")
            .then()
            .statusCode(200)
            .contentType("image/jpeg");
    }

    @Test
    void embeddedSvg_returns200WithSvgType() {
        given()
            .when().get("/api/brand/brand-test.svg")
            .then()
            .statusCode(200)
            .contentType("image/svg+xml");
    }

    @Test
    void embeddedIco_returns200WithIcoType() {
        given()
            .when().get("/api/brand/brand-test.ico")
            .then()
            .statusCode(200)
            .contentType("image/x-icon");
    }

    @Test
    void embeddedWoff_returns200WithWoffType() {
        given()
            .when().get("/api/brand/brand-test.woff")
            .then()
            .statusCode(200)
            .contentType("font/woff");
    }

    @Test
    void embeddedWoff2_returns200WithWoff2Type() {
        given()
            .when().get("/api/brand/brand-test.woff2")
            .then()
            .statusCode(200)
            .contentType("font/woff2");
    }

    @Test
    void embeddedTtf_returns200WithTtfType() {
        given()
            .when().get("/api/brand/brand-test.ttf")
            .then()
            .statusCode(200)
            .contentType("font/ttf");
    }

    @Test
    void embeddedOtf_returns200WithOtfType() {
        given()
            .when().get("/api/brand/brand-test.otf")
            .then()
            .statusCode(200)
            .contentType("font/otf");
    }

    @Test
    void embeddedJson_returns200WithJsonType() {
        given()
            .when().get("/api/brand/brand-test.json")
            .then()
            .statusCode(200)
            .contentType("application/json");
    }

    @Test
    void embeddedUnknownExtension_returns200WithOctetStream() {
        given()
            .when().get("/api/brand/brand-test.bin")
            .then()
            .statusCode(200)
            .contentType("application/octet-stream");
    }

    // -----------------------------------------------------------------------
    // Cache-Control header is present on 200 responses
    // -----------------------------------------------------------------------

    @Test
    void embeddedResource_hasCacheControlHeader() {
        given()
            .when().get("/api/brand/brand-test.css")
            .then()
            .statusCode(200)
            .header("Cache-Control", notNullValue());
    }
}
