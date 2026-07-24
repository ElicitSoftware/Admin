package com.elicitsoftware.response;

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

import com.elicitsoftware.model.Status;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link AddResponse}.
 *
 * <p>Traceability: UC-010 (Register Subjects via Integration API). Each single,
 * batch, or CSV registration request produces an {@code AddResponse} carrying a
 * per-subject status list and any accumulated errors. These tests pin the DTO's
 * accumulation semantics that the integration API contract relies on.</p>
 */
class AddResponseTest {

    /** UC-010: a fresh response starts with empty (non-null) status and error lists. */
    @Test
    void newResponseHasEmptyCollections() {
        AddResponse response = new AddResponse();
        assertNotNull(response.getSubjects());
        assertNotNull(response.getErrors());
        assertTrue(response.getSubjects().isEmpty());
        assertTrue(response.getErrors().isEmpty());
    }

    /** UC-010: setError accumulates messages rather than replacing them. */
    @Test
    void setErrorAccumulatesMessages() {
        AddResponse response = new AddResponse();
        response.setError("first problem");
        response.setError("second problem");
        assertEquals(2, response.getErrors().size());
        assertEquals("first problem", response.getErrors().get(0));
        assertEquals("second problem", response.getErrors().get(1));
    }

    /** UC-010: each processed subject appends one status to the response. */
    @Test
    void addStatusAppendsPerSubjectStatus() {
        AddResponse response = new AddResponse();
        response.addStatus(new AddResponseStatus(new Status(), "New Subject: EXT001"));
        response.addStatus(new AddResponseStatus(new Status(), "Existing Subject: EXT002"));

        assertEquals(2, response.getSubjects().size());
        assertEquals("New Subject: EXT001", response.getSubjects().get(0).getImportStatus());
        assertEquals("Existing Subject: EXT002", response.getSubjects().get(1).getImportStatus());
    }

    /** UC-010: toString summarises the response for logging/diagnostics. */
    @Test
    void toStringIncludesSubjectSummary() {
        AddResponse response = new AddResponse();
        response.addStatus(new AddResponseStatus(new Status(), "New Subject: EXT001"));
        String rendered = response.toString();
        assertTrue(rendered.contains("AddResponse"));
        assertTrue(rendered.contains("EXT001"));
    }
}
