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

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;

/**
 * REST resource for serving generated PDFs from a stable application URL.
 * <p>
 * PDFs are cached under a 256-bit random key and served via the /api/pdf/download endpoint
 * with a query parameter. The endpoint is intentionally left unauthenticated
 * ({@code application.properties} permits {@code /api/pdf/*}) so the browser can open the
 * report in a new tab without triggering an OIDC redirect; the key's entropy plus single-use
 * removal are what keep an unguessed/leaked link from being replayed. Cache entries expire
 * after 10 minutes and are then removed whether or not they were fetched
 * ({@link PDFDownloadCachePurge}); at most {@value #MAX_ENTRIES} wait at once (UC-005 BR-021).
 */
@Path("/pdf/download")
public class PDFDownloadResource {

    /**
     * Default constructor.
     */
    public PDFDownloadResource() {
    }

    private static final ConcurrentHashMap<String, PDFCacheEntry> PDF_CACHE = new ConcurrentHashMap<>();
    private static final long CACHE_EXPIRY_MS = 10 * 60 * 1000;
    /** How many generated reports may wait to be fetched at once (UC-005 BR-021). */
    static final int MAX_ENTRIES = 200;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private static class PDFCacheEntry {
        byte[] content;
        long timestamp;
        final String fileName;

        PDFCacheEntry(byte[] content, String fileName) {
            this.content = content;
            this.fileName = fileName;
            this.timestamp = System.currentTimeMillis();
        }

        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > CACHE_EXPIRY_MS;
        }
    }

    /**
     * Cache PDF bytes and return the unique key used by the download endpoint.
     *
     * @param pdfContent the generated PDF bytes
     * @return a unique key for the cached PDF
     */
    public static String cachePDF(byte[] pdfContent) {
        return cachePDF(pdfContent, null);
    }

    /**
     * Caches PDF bytes under the name of the survey they were generated for (UC-005 BR-187) and
     * returns the unique key used by the download endpoint.
     *
     * @param pdfContent the generated PDF bytes
     * @param surveyName the survey's name; null offers the PDF as {@code report.pdf}
     * @return a unique key for the cached PDF
     */
    public static String cachePDF(byte[] pdfContent, String surveyName) {
        byte[] randomBytes = new byte[32];
        SECURE_RANDOM.nextBytes(randomBytes);
        String key = "pdf_" + Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        purgeExpired();
        PDF_CACHE.put(key, new PDFCacheEntry(pdfContent, fileName(surveyName)));
        dropOldestBeyondLimit();
        return key;
    }

    /**
     * The name a survey's PDF is offered under (UC-005 BR-187): {@code <name>-report.pdf}, the name
     * in lower case with every run of characters other than {@code a}-{@code z} and the digits made
     * one hyphen and none at either end.
     *
     * @param surveyName the survey's name; may be null
     * @return the file name; {@code report.pdf} when the name leaves nothing
     */
    static String fileName(String surveyName) {
        String slug = surveyName == null ? ""
                : surveyName.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        return slug.isEmpty() ? "report.pdf" : slug + "-report.pdf";
    }

    /**
     * Removes every cached PDF that has expired (UC-005 BR-021), so a report that was never
     * fetched does not stay in memory.
     *
     * @return how many entries were removed
     */
    static int purgeExpired() {
        int before = PDF_CACHE.size();
        PDF_CACHE.entrySet().removeIf(entry -> entry.getValue().isExpired());
        return Math.max(0, before - PDF_CACHE.size());
    }

    /** Drops the oldest waiting PDFs until no more than {@link #MAX_ENTRIES} remain. */
    private static void dropOldestBeyondLimit() {
        while (PDF_CACHE.size() > MAX_ENTRIES) {
            PDF_CACHE.entrySet().stream()
                    .min(Comparator.comparingLong((Map.Entry<String, PDFCacheEntry> entry) -> entry.getValue().timestamp))
                    .ifPresent(oldest -> PDF_CACHE.remove(oldest.getKey(), oldest.getValue()));
        }
    }

    /**
     * Download a cached PDF by key.
     * 
     * @param key the unique cache key for the PDF
     * @return the PDF content with appropriate headers if found and not expired, or 404 if missing/expired
     */
    @GET
    @Produces("application/pdf")
    public Response downloadPDF(@QueryParam("key") String key) {
        if (key == null || key.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Missing key parameter")
                    .build();
        }

        PDFCacheEntry entry = PDF_CACHE.remove(key);
        if (entry == null || entry.isExpired()) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity("PDF not found or expired")
                    .build();
        }

        return Response.ok(entry.content)
                .header("Content-Type", "application/pdf")
                .header("Content-Disposition", "inline; filename=\"" + entry.fileName + "\"")
                .header("Cache-Control", "no-cache, no-store, must-revalidate")
                .header("Pragma", "no-cache")
                .header("Expires", "0")
                .build();
    }
}