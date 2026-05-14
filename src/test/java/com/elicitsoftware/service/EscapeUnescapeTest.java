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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * US4: Verifies that SurveyDefinitionExportService.escapeField() and
 * SurveyDefinitionImportService.parseFields() are perfect inverses for all
 * special characters. No database or Quarkus container required.
 */
class EscapeUnescapeTest {

    // T020: pipe is escaped to \| and restored after parse
    @Test
    void pipeEscapedAndRestored() {
        String escaped = SurveyDefinitionExportService.escapeField("a|b");
        assertEquals("a\\|b", escaped);

        String[] fields = SurveyDefinitionImportService.parseFields("a\\|b");
        assertEquals(1, fields.length);
        assertEquals("a|b", fields[0]);
    }

    // T021: backslash is escaped to \\ and restored after parse
    @Test
    void backslashEscapedAndRestored() {
        String escaped = SurveyDefinitionExportService.escapeField("a\\b");
        assertEquals("a\\\\b", escaped);

        String[] fields = SurveyDefinitionImportService.parseFields("a\\\\b");
        assertEquals(1, fields.length);
        assertEquals("a\\b", fields[0]);
    }

    // T022: null input is escaped to empty string
    @Test
    void nullEscapesToEmptyString() {
        String escaped = SurveyDefinitionExportService.escapeField(null);
        assertEquals("", escaped);
    }

    // T023: newline and carriage-return survive a full round-trip
    @Test
    void newlineAndCarriageReturnPreservedAfterRoundTrip() {
        String original = "line1\nline2\r\nline3";
        String escaped = SurveyDefinitionExportService.escapeField(original);
        String[] fields = SurveyDefinitionImportService.parseFields(escaped);
        assertEquals(1, fields.length);
        assertEquals(original, fields[0]);
    }
}
