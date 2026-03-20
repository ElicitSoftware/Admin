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

import java.util.concurrent.ConcurrentHashMap;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;

/**
 * REST resource for serving generated PDFs from a stable application URL.
 * 
 * PDFs are cached with unique keys and served via the /api/pdf/download endpoint with a query parameter.
 * Cache entries expire after 10 minutes.
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

    private static class PDFCacheEntry {
        byte[] content;
        long timestamp;

        PDFCacheEntry(byte[] content) {
            this.content = content;
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
        String key = "pdf_" + System.currentTimeMillis() + "_" + System.nanoTime();
        PDF_CACHE.put(key, new PDFCacheEntry(pdfContent));
        return key;
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

        PDFCacheEntry entry = PDF_CACHE.get(key);
        if (entry == null || entry.isExpired()) {
            PDF_CACHE.remove(key);
            return Response.status(Response.Status.NOT_FOUND)
                    .entity("PDF not found or expired")
                    .build();
        }

        return Response.ok(entry.content)
                .header("Content-Type", "application/pdf")
                .header("Content-Disposition", "inline; filename=\"family_history_report.pdf\"")
                .header("Cache-Control", "no-cache, no-store, must-revalidate")
                .header("Pragma", "no-cache")
                .header("Expires", "0")
                .build();
    }
}