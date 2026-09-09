package com.elicitsoftware.report.pdfbox;

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

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link PDFTableGenerator}.
 *
 * <p>Traceability: UC-005 (Generate and Download a Subject Report) — this is the table-drawing
 * primitive report generation would use to lay out tabular content on a PDF page. These are
 * plain (non-booted) unit tests: {@link PDFTableGenerator} has no CDI dependencies of its own,
 * only Apache PDFBox, so it can be exercised directly against a real in-memory {@link PDDocument}.</p>
 *
 * <p>Note: {@link PDFTableGenerator#drawTable} draws onto the document's current last page and
 * does not call {@code document.addPage(...)} itself (that call is commented out pending
 * multi-page support), so callers must pre-add one page per expected iteration of the
 * pagination loop.</p>
 */
class PDFTableGeneratorTest {

    private final PDFTableGenerator generator = new PDFTableGenerator();
    private final PDFont font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

    private Table buildTable(String[][] content, int numberOfRows, float height, float rowHeight, boolean landscape) {
        List<Column> columns = List.of(new Column("Name", 200f), new Column("Value", 100f));
        return new TableBuilder()
                .setColumns(columns)
                .setContent(content)
                .setNumberOfRows(numberOfRows)
                .setHeight(height)
                .setRowHeight(rowHeight)
                .setMargin(40f)
                .setCellMargin(2f)
                .setPageSize(PDRectangle.LETTER)
                .setLandscape(landscape)
                .setTextFont(font)
                .setFontSize(10f)
                .build();
    }

    /** UC-005: a table whose rows all fit within one page's height renders onto a single page. */
    @Test
    void singlePageTableRendersAllRows() throws IOException {
        String[][] content = {
                {"Question 1", "Yes"},
                {"Question 2", "No"},
        };
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage(PDRectangle.LETTER));
            Table table = buildTable(content, content.length, 600f, 20f, false);

            generator.generatePDF(document, table);

            assertEquals(1, document.getNumberOfPages());
            String text = new PDFTextStripper().getText(document);
            assertTrue(text.contains("Name"), "column header should be rendered");
            assertTrue(text.contains("Question 1"), "row content should be rendered");
            assertTrue(text.contains("Yes"), "row content should be rendered");
        }
    }

    /** UC-005: a landscape table applies the rotation transform and still renders without error. */
    @Test
    void landscapeTableRendersWithoutError() throws IOException {
        String[][] content = {{"Wide Question", "Answer"}};
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage(PDRectangle.LETTER));
            Table table = buildTable(content, content.length, 400f, 20f, true);

            assertDoesNotThrow(() -> generator.generatePDF(document, table));
            assertEquals(1, document.getNumberOfPages());
        }
    }

    /** UC-005: pagination math splits rows exceeding one page's capacity across multiple pages. */
    @Test
    void multiPageTableIteratesOncePerCalculatedPage() throws IOException {
        // height=100, rowHeight=20 -> rowsPerPage = floor(100/20) - 1 = 4
        // 10 rows -> numberOfPages = ceil(10/4) = 3 (last page holds the remaining 2, exercising
        // the endRange-clamp branch in getContentForCurrentPage)
        String[][] content = new String[10][];
        for (int i = 0; i < content.length; i++) {
            content[i] = new String[]{"Row " + i, String.valueOf(i)};
        }
        try (PDDocument document = new PDDocument()) {
            for (int i = 0; i < 3; i++) {
                document.addPage(new PDPage(PDRectangle.LETTER));
            }
            Table table = buildTable(content, content.length, 100f, 20f, false);

            assertDoesNotThrow(() -> generator.drawTable(document, table));
            assertEquals(3, document.getNumberOfPages());
        }
    }

    /** UC-005: Column's name/width can be changed after construction. */
    @Test
    void columnSettersUpdateFields() {
        Column column = new Column("Original", 100f);
        column.setName("Updated");
        column.setWidth(150f);

        assertEquals("Updated", column.getName());
        assertEquals(150f, column.getWidth());
    }

    /** UC-005: PDFPage tracks a mutable cursor position on top of the standard PDPage. */
    @Test
    void pdfPageTracksCursorPosition() {
        PDFPage page = new PDFPage();
        assertEquals(0f, page.cursorY);

        page.cursorY = 750f;

        assertEquals(750f, page.cursorY);
    }

    /** UC-005: null cell values are rendered as empty text rather than throwing. */
    @Test
    void nullCellValueIsRenderedAsEmptyString() throws IOException {
        String[][] content = {{"Question with no answer", null}};
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage(PDRectangle.LETTER));
            Table table = buildTable(content, content.length, 600f, 20f, false);

            assertDoesNotThrow(() -> generator.generatePDF(document, table));

            String text = new PDFTextStripper().getText(document);
            assertTrue(text.contains("Question with no answer"));
        }
    }
}
