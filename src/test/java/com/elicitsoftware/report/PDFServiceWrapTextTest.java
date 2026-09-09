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

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plain-JUnit tests for {@link PDFService#wrapText(String, org.apache.pdfbox.pdmodel.font.PDFont, float, float)}.
 *
 * <p>Traceability: UC-005 (Generate and Download Survey Report). {@code wrapText} is a public
 * static method with no CDI dependencies, so it is tested directly against a real
 * {@link org.apache.pdfbox.pdmodel.font.PDFont} instance ({@link PDFService#TEXT_FONT}, reused
 * here since this test class shares the {@code com.elicitsoftware.report} package) without
 * booting a {@code @QuarkusTest}.</p>
 */
class PDFServiceWrapTextTest {

    /** UC-005: a short string that fits within the max width returns a single, unmodified line. */
    @Test
    void shortTextFitsOnOneLine() throws Exception {
        String text = "Short line";
        List<String> lines = PDFService.wrapText(text, PDFService.TEXT_FONT, PDFService.FONT_SIZE, 400f);

        assertEquals(1, lines.size());
        assertEquals(text, lines.get(0));
    }

    /** UC-005: a long string wraps into multiple lines, each within the max width, without losing words. */
    @Test
    void longTextWrapsIntoMultipleLinesPreservingAllWords() throws Exception {
        String text = "This is a considerably long sentence that must wrap across several lines "
                + "when rendered with a narrow maximum width so the word-wrapping logic is exercised.";
        float fontSize = PDFService.FONT_SIZE;
        float maxWidth = 100f;

        List<String> lines = PDFService.wrapText(text, PDFService.TEXT_FONT, fontSize, maxWidth);

        assertTrue(lines.size() > 1, "a long string at a narrow width must wrap into multiple lines");

        for (String line : lines) {
            float width = PDFService.TEXT_FONT.getStringWidth(line) / 1000 * fontSize;
            assertTrue(width <= maxWidth, "line '" + line + "' (" + width + "pt) exceeds maxWidth " + maxWidth);
        }

        String reconstructed = String.join(" ", lines);
        assertEquals(text, reconstructed, "concatenating the wrapped lines with spaces must reproduce the original text");
    }
}
