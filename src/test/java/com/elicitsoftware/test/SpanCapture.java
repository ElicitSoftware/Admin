package com.elicitsoftware.test;

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

import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

/**
 * Keeps the spans the booted tests record in memory, so a test can read what a traced call
 * wrote down (NFR-024: never the service token).
 */
@ApplicationScoped
public class SpanCapture {

    @Produces
    @Singleton
    InMemorySpanExporter spans() {
        return InMemorySpanExporter.create();
    }
}
