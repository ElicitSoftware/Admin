package com.elicitsoftware.admin.flow;

/*-
 * ***LICENSE_START***
 * Elicit Survey
 * %%
 * Copyright (C) 2025 - 2026 The Regents of the University of Michigan - Rogel Cancer Center
 * %%
 * PolyForm Noncommercial License 1.0.0
 * <https://polyformproject.org/licenses/noncommercial/1.0.0>
 * ***LICENSE_END***
 */

import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * UC-023: Diagnose Branding (BR-091) — the brand assets the pages link to are served from the
 * mounted brand directory, then from the embedded default, with their media type and a cache
 * header, and nothing outside the brand directory is served. The mounted directory is a
 * temporary directory of the test's making.
 */
class BrandResourceHandlerUnitTest {

    @TempDir
    Path mount;

    private BrandResourceHandler handler;

    @BeforeEach
    void setUp() {
        handler = new BrandResourceHandler();
        handler.brandFileSystemPath = mount.toString();
    }

    private void write(String path, String content) throws IOException {
        Path file = mount.resolve(path);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    @Test
    void given_aMountedFile_when_requested_then_servedWithItsTypeAndCached() throws IOException {
        write("colors/brand-colors.css", ":root{--x:1}");

        Response response = handler.getBrandFile("colors/brand-colors.css");

        assertEquals(200, response.getStatus());
        assertArrayEquals(":root{--x:1}".getBytes(), (byte[]) response.getEntity());
        assertEquals("text/css", response.getMediaType().toString());
        assertEquals("public, max-age=3600", response.getHeaderString("Cache-Control"));
    }

    @Test
    void given_aFileOnlyEmbedded_when_requested_then_theEmbeddedCopy() throws IOException {
        Response response = handler.getBrandFile("images/favicon.ico");

        byte[] embedded;
        try (InputStream in = getClass().getResourceAsStream("/META-INF/brand/images/favicon.ico")) {
            embedded = in.readAllBytes();
        }
        assertEquals(200, response.getStatus());
        assertArrayEquals(embedded, (byte[]) response.getEntity());
        assertEquals("image/x-icon", response.getMediaType().toString());
        assertEquals("public, max-age=3600", response.getHeaderString("Cache-Control"));
    }

    @Test
    void given_aFileNowhere_when_requested_then_notFound() {
        assertEquals(404, handler.getBrandFile("images/missing.png").getStatus());
    }

    @Test
    void given_aPathLeavingTheBrandDirectory_when_requested_then_forbidden() throws IOException {
        Files.writeString(mount.resolveSibling("secret.txt"), "secret");

        assertEquals(403, handler.getBrandFile("../secret.txt").getStatus());
        assertEquals(403, handler.getBrandFile("images/../../secret.txt").getStatus());
    }

    @Test
    void given_aMountedFileThatCannotBeRead_when_requested_then_serverError() throws IOException {
        write("theme.css", "x");
        boolean locked = mount.resolve("theme.css").toFile().setReadable(false);
        Assumptions.assumeTrue(locked && !Files.isReadable(mount.resolve("theme.css")), "the file system honors permissions");
        try {
            assertEquals(500, handler.getBrandFile("theme.css").getStatus());
        } finally {
            mount.resolve("theme.css").toFile().setReadable(true);
        }
    }

    @Test
    void given_eachExtension_when_served_then_itsMediaType() throws IOException {
        Map<String, String> types = Map.ofEntries(
                Map.entry("a.css", "text/css"), Map.entry("a.js", "application/javascript"),
                Map.entry("a.png", "image/png"), Map.entry("A.PNG", "image/png"), Map.entry("a.jpg", "image/jpeg"),
                Map.entry("a.jpeg", "image/jpeg"), Map.entry("a.svg", "image/svg+xml"), Map.entry("a.ico", "image/x-icon"),
                Map.entry("a.woff", "font/woff"), Map.entry("a.woff2", "font/woff2"), Map.entry("a.ttf", "font/ttf"),
                Map.entry("a.otf", "font/otf"), Map.entry("a.json", "application/json"),
                Map.entry("a.txt", "application/octet-stream"), Map.entry("noextension", "application/octet-stream"),
                Map.entry(".hidden", "application/octet-stream"), Map.entry("trailing.", "application/octet-stream"));
        for (Map.Entry<String, String> type : types.entrySet()) {
            write("files/" + type.getKey(), "x");

            Response response = handler.getBrandFile("files/" + type.getKey());

            assertEquals(200, response.getStatus(), type.getKey());
            assertEquals(type.getValue(), response.getMediaType().toString(), type.getKey());
        }
    }

    @Test
    void given_aDotFileAtTheTop_when_served_then_itHasNoExtension() throws IOException {
        write(".css", "x");

        assertEquals("application/octet-stream", handler.getBrandFile(".css").getMediaType().toString());
    }
}
