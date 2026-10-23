package com.elicitsoftware.report;

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
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UC-005: Generate and Download a Subject Report — the one-time download of a generated PDF:
 * an unguessable key, a single use, and a ten-minute expiry.
 */
class PDFDownloadCacheTest {

    private final PDFDownloadResource resource = new PDFDownloadResource();

    @Test
    void given_aCachedPdf_when_downloaded_then_itIsServedInlineOnceWithoutCaching() {
        byte[] pdf = {'%', 'P', 'D', 'F'};
        String key = PDFDownloadResource.cachePDF(pdf);
        assertTrue(key.matches("pdf_[A-Za-z0-9_-]{43}"), key);

        Response response = resource.downloadPDF(key);
        assertEquals(200, response.getStatus());
        assertArrayEquals(pdf, (byte[]) response.getEntity());
        assertEquals("application/pdf", response.getHeaderString("Content-Type"));
        assertEquals("inline; filename=\"family_history_report.pdf\"", response.getHeaderString("Content-Disposition"));
        assertEquals("no-cache, no-store, must-revalidate", response.getHeaderString("Cache-Control"));
        assertEquals("no-cache", response.getHeaderString("Pragma"));
        assertEquals("0", response.getHeaderString("Expires"));

        Response again = resource.downloadPDF(key);
        assertEquals(404, again.getStatus(), "a key is good for one download");
        assertEquals("PDF not found or expired", again.getEntity());
    }

    @Test
    void given_twoPdfs_when_cached_then_theirKeysDiffer() {
        assertNotEquals(PDFDownloadResource.cachePDF(new byte[0]), PDFDownloadResource.cachePDF(new byte[0]));
    }

    @Test
    void given_noKey_when_downloaded_then_theRequestIsBad() {
        for (String key : new String[]{null, ""}) {
            Response response = resource.downloadPDF(key);
            assertEquals(400, response.getStatus());
            assertEquals("Missing key parameter", response.getEntity());
        }
    }

    @Test
    void given_anUnknownKey_when_downloaded_then_itIsNotFound() {
        assertEquals(404, resource.downloadPDF("pdf_nope").getStatus());
    }

    @Test
    void given_aPdfOlderThanTenMinutes_when_downloaded_then_itIsExpired() throws Exception {
        String fresh = PDFDownloadResource.cachePDF(new byte[]{1});
        String old = PDFDownloadResource.cachePDF(new byte[]{2});
        age(fresh, 10 * 60 * 1000 - 5_000);
        age(old, 10 * 60 * 1000 + 5_000);

        assertEquals(200, resource.downloadPDF(fresh).getStatus(), "inside ten minutes it is still served");
        assertEquals(404, resource.downloadPDF(old).getStatus());
    }

    /** Moves a cached entry's timestamp back by the given age. */
    private static void age(String key, long millis) throws Exception {
        Field cacheField = PDFDownloadResource.class.getDeclaredField("PDF_CACHE");
        cacheField.setAccessible(true);
        Object entry = ((Map<?, ?>) cacheField.get(null)).get(key);
        Field timestamp = entry.getClass().getDeclaredField("timestamp");
        timestamp.setAccessible(true);
        timestamp.setLong(entry, System.currentTimeMillis() - millis);
    }
}
