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

import io.quarkus.logging.Log;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Removes expired PDFs from the download cache once a minute (UC-005 BR-021), so a generated
 * report nobody fetched leaves memory when its link expires rather than when the process ends.
 */
@ApplicationScoped
public class PDFDownloadCachePurge {

    /** Creates the purge job; the scheduler calls {@link #purge()}. */
    public PDFDownloadCachePurge() {
    }

    @Scheduled(every = "1m")
    void purge() {
        int removed = PDFDownloadResource.purgeExpired();
        if (removed > 0) {
            Log.debugf("Removed %d expired report PDF(s) from the download cache", removed);
        }
    }
}
