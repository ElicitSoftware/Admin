package com.elicitsoftware.report;

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

import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
        assertEquals("inline; filename=\"report.pdf\"", response.getHeaderString("Content-Disposition"));
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

    @Test
    void given_anExpiredPdfNobodyFetched_when_purged_then_itLeavesTheCacheAndAFreshOneStays() throws Exception {
        String fresh = PDFDownloadResource.cachePDF(new byte[]{1});
        String old = PDFDownloadResource.cachePDF(new byte[]{2});
        age(old, 10 * 60 * 1000 + 5_000);

        assertTrue(PDFDownloadResource.purgeExpired() >= 1);

        assertTrue(cache().containsKey(fresh), "BR-021: a report inside its ten minutes survives the purge");
        assertFalse(cache().containsKey(old), "BR-021: an expired report is removed without a fetch");
        assertEquals(200, resource.downloadPDF(fresh).getStatus());
        assertEquals(404, resource.downloadPDF(fresh).getStatus(), "and it is still good for one download only");
    }

    @Test
    void given_anExpiredPdf_when_anotherIsCached_then_theExpiredOneIsRemoved() throws Exception {
        String old = PDFDownloadResource.cachePDF(new byte[]{2});
        age(old, 10 * 60 * 1000 + 5_000);

        PDFDownloadResource.cachePDF(new byte[]{3});

        assertFalse(cache().containsKey(old));
    }

    @Test
    void given_theCacheIsFull_when_anotherIsCached_then_theOldestIsDropped() throws Exception {
        cache().clear();
        String oldest = PDFDownloadResource.cachePDF(new byte[]{0});
        age(oldest, 60_000);
        for (int i = 1; i < PDFDownloadResource.MAX_ENTRIES; i++) {
            PDFDownloadResource.cachePDF(new byte[]{1});
        }
        assertEquals(PDFDownloadResource.MAX_ENTRIES, cache().size());

        String newest = PDFDownloadResource.cachePDF(new byte[]{2});

        assertEquals(PDFDownloadResource.MAX_ENTRIES, cache().size(), "BR-021: at most this many wait at once");
        assertFalse(cache().containsKey(oldest));
        assertTrue(cache().containsKey(newest));
        cache().clear();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> cache() throws Exception {
        Field cacheField = PDFDownloadResource.class.getDeclaredField("PDF_CACHE");
        cacheField.setAccessible(true);
        return (Map<String, Object>) cacheField.get(null);
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

    /** UC-005 BR-187: the PDF is offered under the survey's name. */
    @Test
    void given_aPdfOfASurvey_when_downloaded_then_itIsNamedAfterTheSurvey() {
        String key = PDFDownloadResource.cachePDF(new byte[]{'%', 'P'}, "Family History Survey");

        assertEquals("inline; filename=\"family-history-survey-report.pdf\"",
                resource.downloadPDF(key).getHeaderString("Content-Disposition"));
    }

    /** UC-005 BR-187: only a-z and digits are kept, as one hyphen per run of anything else. */
    @Test
    void given_surveyNames_when_namingThePdf_then_onlyLettersAndDigitsAreKept() {
        assertEquals("family-history-survey-report.pdf", PDFDownloadResource.fileName("Family History Survey"));
        assertEquals("premm5-v2-report.pdf", PDFDownloadResource.fileName("  PREMM5 (v2) "));
        assertEquals("report.pdf", PDFDownloadResource.fileName("استبيان"));
        assertEquals("report.pdf", PDFDownloadResource.fileName(""));
        assertEquals("report.pdf", PDFDownloadResource.fileName(null));
    }
}
