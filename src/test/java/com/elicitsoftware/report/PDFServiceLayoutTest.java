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

import com.elicitsoftware.report.pdf.Content;
import com.elicitsoftware.report.pdf.PDFDocument;
import com.elicitsoftware.report.pdf.Table;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UC-005: Generate and Download a Subject Report — how {@link PDFService} lays the report
 * services' parts out on pages: page breaks, overflow onto a fresh page, tables that span pages,
 * diagrams on a landscape page, and the header and footer stamped on every page.
 */
class PDFServiceLayoutTest {

    private static final String URL = "http://example.org/admin";
    private static final String SVG = "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"200\" height=\"%d\">"
            + "<rect x=\"10\" y=\"10\" width=\"100\" height=\"50\" fill=\"#336699\"/></svg>";

    /** One piece of text and where it was drawn, measured from the page's top-left corner. */
    record Placed(String text, float x, float yFromTop) {
    }

    private static Content text(String value) {
        Content c = new Content();
        c.text = value;
        return c;
    }

    private static Content svg(int height, boolean inline) {
        Content c = new Content();
        c.svg = SVG.formatted(height);
        c.inline = inline;
        return c;
    }

    private static Content table(int rows) {
        Table t = new Table();
        t.headers = new String[]{"Person", "Condition", "Age"};
        t.widths = new float[]{0, 200, 80};
        t.body = new String[rows][];
        for (int r = 0; r < rows; r++) {
            t.body[r] = new String[]{"Relative " + r, "Condition " + r, String.valueOf(40 + r)};
        }
        Content c = new Content();
        c.table = t;
        return c;
    }

    private static ReportResponse part(String title, boolean pageBreak, Content... content) {
        PDFDocument pdf = new PDFDocument();
        pdf.title = title;
        pdf.pageBreak = pageBreak;
        pdf.content = content;
        ReportResponse response = new ReportResponse();
        response.pdf = pdf;
        return response;
    }

    private static byte[] generate(ReportResponse... parts) {
        return new PDFService().generatePDF(new ArrayList<>(List.of(parts)), URL, "Census");
    }

    private static List<String> pageTexts(PDDocument doc) throws IOException {
        List<String> texts = new ArrayList<>();
        for (int i = 1; i <= doc.getNumberOfPages(); i++) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setStartPage(i);
            stripper.setEndPage(i);
            texts.add(stripper.getText(doc));
        }
        return texts;
    }

    /** The strings drawn on one page (1-based), in drawing order, with their positions. */
    private static Map<String, Placed> placed(PDDocument doc, int page) throws IOException {
        Map<String, Placed> found = new LinkedHashMap<>();
        PDFTextStripper stripper = new PDFTextStripper() {
            @Override
            protected void writeString(String text, List<TextPosition> positions) throws IOException {
                TextPosition first = positions.get(0);
                found.putIfAbsent(text, new Placed(text, first.getXDirAdj(), first.getYDirAdj()));
                super.writeString(text, positions);
            }
        };
        stripper.setSortByPosition(false);
        stripper.setStartPage(page);
        stripper.setEndPage(page);
        stripper.getText(doc);
        return found;
    }

    private static boolean hasForm(PDPage page) {
        return page.getResources() != null && page.getResources().getXObjectNames().iterator().hasNext();
    }

    @Test
    void given_theShortGenerateCall_when_aReportIsBuilt_then_theDefaultHeaderAndTheFooterAreStamped() throws IOException {
        byte[] pdf = new PDFService().generatePDF(new ArrayList<>(List.of(part("Pedigree", false, text("Hello")))), URL);
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            assertEquals(1, doc.getNumberOfPages());
            String page = pageTexts(doc).get(0);
            assertTrue(page.contains("Family Health History Survey - Page 1 of 1"), page);
            assertTrue(page.contains(URL), page);
            assertTrue(page.contains(LocalDate.now().format(DateTimeFormatter.ofPattern("MM/dd/yyyy"))), page);
            assertTrue(page.contains("Pedigree"));
            assertTrue(page.contains("Hello"));

            Map<String, Placed> where = placed(doc, 1);
            Placed header = where.get("Family Health History Survey - Page 1 of 1");
            assertNotNull(header, where.keySet().toString());
            assertEquals(190f, header.x(), 0.5f, "a portrait header sits right of the logo");
            Placed footerPage = where.get("Page 1 of 1");
            assertNotNull(footerPage, where.keySet().toString());
            float width = PDFService.TEXT_FONT.getStringWidth("Page 1 of 1") / 1000 * 10;
            assertEquals(612f - 40f - width, footerPage.x(), 0.5f, "the page number is right-aligned");
            float urlWidth = PDFService.TEXT_FONT.getStringWidth(URL) / 1000 * 10;
            assertEquals((612f - urlWidth) / 2, where.get(URL).x(), 0.5f, "the address is centred");
            assertEquals(30f, header.yFromTop(), 0.5f, "the header sits in the top margin");
            String date = LocalDate.now().format(DateTimeFormatter.ofPattern("MM/dd/yyyy"));
            for (String footer : List.of(date, URL, "Page 1 of 1")) {
                assertEquals(782f, where.get(footer).yFromTop(), 0.5f, footer + " sits in the bottom margin");
            }
            assertEquals(40f, where.get(date).x(), 0.5f);
            assertEquals(60f, where.get("Pedigree").yFromTop(), 0.5f, "the first title starts below the top margin");
            assertEquals(40f, where.get("Pedigree").x(), 0.5f);
            assertEquals(70f, where.get("Hello").yFromTop(), 0.5f, "text follows its title");
        }
    }

    @Test
    void given_aPartThatAsksForAPageBreak_when_built_then_itStartsAFreshPage() throws IOException {
        ReportResponse noPdf = new ReportResponse();
        noPdf.title = "Ignored";
        byte[] pdf = generate(part("First", false, text("one"), null), noPdf, part("Second", true, text("two")));
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            assertEquals(2, doc.getNumberOfPages());
            List<String> pages = pageTexts(doc);
            assertTrue(pages.get(0).contains("First") && pages.get(0).contains("one"));
            assertFalse(pages.get(0).contains("Second"));
            assertTrue(pages.get(1).contains("Second") && pages.get(1).contains("two"));
            assertTrue(pages.get(0).contains("Census - Page 1 of 2"));
            assertTrue(pages.get(1).contains("Census - Page 2 of 2"));
            assertFalse(String.join("", pages).contains("Ignored"));
            Placed second = placed(doc, 2).get("Second");
            assertTrue(second.yFromTop() > 40 && second.yFromTop() < 80, "starts at the top: " + second);
        }
    }

    @Test
    void given_moreTextThanAPageHolds_when_built_then_theRestContinuesAtTheTopOfANewPage() throws IOException {
        Content[] blocks = new Content[60];
        for (int i = 0; i < blocks.length; i++) {
            blocks[i] = text("Block " + i);
        }
        try (PDDocument doc = Loader.loadPDF(generate(part("Notes", false, blocks)))) {
            assertEquals(2, doc.getNumberOfPages());
            List<String> pages = pageTexts(doc);
            assertTrue(pages.get(0).contains("Block 0\n"));
            assertTrue(pages.get(1).contains("Block 59"));
            assertFalse(pages.get(0).contains("Block 59"));
            for (int i = 0; i < blocks.length; i++) {
                String block = "Block " + i + "\n";
                assertTrue(pages.get(0).contains(block) ^ pages.get(1).contains(block), block);
            }
            Placed firstOnPage2 = placed(doc, 2).values().stream().filter(p -> p.text().startsWith("Block "))
                    .findFirst().orElseThrow();
            assertTrue(firstOnPage2.yFromTop() > 30 && firstOnPage2.yFromTop() < 60, firstOnPage2.toString());
            placed(doc, 1).values().stream().filter(p -> p.text().startsWith("Block "))
                    .forEach(p -> assertTrue(p.yFromTop() < 792 - 40, "above the footer zone: " + p));
        }
    }

    @Test
    void given_moreTitlesThanAPageHolds_when_built_then_theTitlesContinueOnANewPage() throws IOException {
        ReportResponse[] parts = new ReportResponse[30];
        for (int i = 0; i < parts.length; i++) {
            parts[i] = part("Title " + i, false);
        }
        try (PDDocument doc = Loader.loadPDF(generate(parts))) {
            assertEquals(2, doc.getNumberOfPages());
            List<String> pages = pageTexts(doc);
            assertTrue(pages.get(1).contains("Title 29"));
            assertFalse(pages.get(0).contains("Title 29"));
            Placed first = placed(doc, 2).values().stream().filter(p -> p.text().startsWith("Title ")).findFirst().orElseThrow();
            assertTrue(first.yFromTop() > 30 && first.yFromTop() < 60, first.toString());
            placed(doc, 1).values().stream().filter(p -> p.text().startsWith("Title "))
                    .forEach(p -> assertTrue(p.yFromTop() < 792 - 40, "above the footer zone: " + p));
        }
    }

    @Test
    void given_anInlineChartThatDoesNotFit_when_built_then_itMovesToAFreshPortraitPage() throws IOException {
        Content[] content = new Content[22];
        for (int i = 0; i < 21; i++) {
            content[i] = text("Line " + i);
        }
        content[21] = svg(400, true);
        try (PDDocument doc = Loader.loadPDF(generate(part("Charts", false, content)))) {
            assertEquals(2, doc.getNumberOfPages());
            assertFalse(hasForm(doc.getPage(0)));
            assertTrue(hasForm(doc.getPage(1)));
            doc.getPages().forEach(p -> assertTrue(p.getMediaBox().getHeight() > p.getMediaBox().getWidth()));
        }
    }

    @Test
    void given_aDiagram_when_built_then_itFillsALandscapePageWithACentredHeader() throws IOException {
        try (PDDocument doc = Loader.loadPDF(generate(part("Pedigree", false), part("Diagram", true, svg(300, false))))) {
            assertEquals(2, doc.getNumberOfPages());
            PDRectangle first = doc.getPage(0).getMediaBox();
            PDRectangle landscape = doc.getPage(1).getMediaBox();
            assertTrue(first.getHeight() > first.getWidth());
            assertEquals(792f, landscape.getWidth(), 0.01f);
            assertEquals(612f, landscape.getHeight(), 0.01f);
            assertTrue(hasForm(doc.getPage(1)));

            String header = "Census - Page 2 of 2";
            Placed placedHeader = placed(doc, 2).get(header);
            assertNotNull(placedHeader);
            float width = PDFService.TEXT_FONT.getStringWidth(header) / 1000 * 10;
            assertEquals((792f - width) / 2, placedHeader.x(), 0.5f);
            assertEquals(190f, placed(doc, 1).get("Census - Page 1 of 2").x(), 0.5f);
        }
    }

    @Test
    void given_aPartWhoseOnlyContentIsADiagram_when_built_then_itsTitleIsKeptAboveTheDrawing() throws IOException {
        // UC-005 (#132): the drawing used to reopen the page without appending, erasing the title.
        try (PDDocument doc = Loader.loadPDF(generate(part("DIAGRAMTITLE", false, svg(300, false))))) {
            assertEquals(1, doc.getNumberOfPages());
            assertTrue(pageTexts(doc).get(0).contains("DIAGRAMTITLE"), pageTexts(doc).get(0));
            assertTrue(hasForm(doc.getPage(0)));
        }
    }

    @Test
    void given_textAfterADiagram_when_built_then_itIsDrawnBelowItOnTheSameLandscapePage() throws IOException {
        // UC-005 (#132): the legend that follows the pedigree used to go to a closed stream.
        try (PDDocument doc = Loader.loadPDF(generate(part("Pedigree", false, svg(300, false),
                text("red fill = family member with cancer"),
                text("* Indicates multiple diagnoses of the same cancer type."))))) {
            assertEquals(1, doc.getNumberOfPages(), "the legend fits under the drawing");
            PDRectangle box = doc.getPage(0).getMediaBox();
            assertTrue(box.getWidth() > box.getHeight(), "the drawing's page is landscape");
            Map<String, Placed> drawn = placed(doc, 1);
            Placed title = drawn.get("Pedigree");
            Placed legend = drawn.get("red fill = family member with cancer");
            assertNotNull(title, drawn.keySet().toString());
            assertNotNull(legend, drawn.keySet().toString());
            assertNotNull(drawn.get("* Indicates multiple diagnoses of the same cancer type."), drawn.keySet().toString());
            assertTrue(legend.yFromTop() - title.yFromTop() > 100, "the drawing sits between the title and the legend");
        }
    }

    @Test
    void given_aPartAfterADiagram_when_built_then_itIsDrawnOnAPortraitPage() throws IOException {
        // UC-005 (#132): a later part without a page break used to be dropped as well.
        try (PDDocument doc = Loader.loadPDF(generate(part("Pedigree", false, svg(300, false)),
                part("Summary", false, text("After the drawing"))))) {
            assertEquals(2, doc.getNumberOfPages());
            PDRectangle second = doc.getPage(1).getMediaBox();
            assertTrue(second.getHeight() > second.getWidth(), "a part without a drawing returns to portrait");
            assertTrue(pageTexts(doc).get(1).contains("Summary"), pageTexts(doc).get(1));
            assertTrue(pageTexts(doc).get(1).contains("After the drawing"), pageTexts(doc).get(1));
        }
    }

    @Test
    void given_thePedigreeAfterAPortraitPart_when_built_then_itHasALandscapePageOfItsOwn() throws IOException {
        // UC-005 (#132): the pedigree as FHHS sends it: page break, landscape, drawing, legend.
        ReportResponse pedigree = part("Pedigree", true, svg(300, false), text("red fill = family member with cancer"));
        pedigree.pdf.landscape = true;
        try (PDDocument doc = Loader.loadPDF(generate(part("Summary", false, text("Before the drawing")), pedigree))) {
            assertEquals(2, doc.getNumberOfPages());
            PDRectangle first = doc.getPage(0).getMediaBox();
            PDRectangle second = doc.getPage(1).getMediaBox();
            assertTrue(first.getHeight() > first.getWidth(), "the summary stays portrait");
            assertTrue(second.getWidth() > second.getHeight(), "the pedigree page is landscape");
            assertTrue(pageTexts(doc).get(0).contains("Before the drawing"));
            assertTrue(pageTexts(doc).get(1).contains("Pedigree"), pageTexts(doc).get(1));
            assertTrue(pageTexts(doc).get(1).contains("red fill = family member with cancer"), pageTexts(doc).get(1));
        }
    }

    @Test
    void given_oneTextBlockLongerThanAPage_when_built_then_itContinuesOnNewPagesAndNothingRunsOffThePage() throws IOException {
        // UC-005 (#133): the page check ran once per block, so a long block was drawn below the
        // bottom margin and off the page; each continuation page starts at its top.
        StringBuilder words = new StringBuilder();
        for (int i = 1; i <= 2500; i++) {
            words.append(String.format("w%04d ", i));
        }
        try (PDDocument doc = Loader.loadPDF(generate(part("Long", false, text(words.toString().trim()))))) {
            assertTrue(doc.getNumberOfPages() >= 2, "pages: " + doc.getNumberOfPages());
            String all = String.join(" ", pageTexts(doc));
            for (int i = 1; i <= 2500; i++) {
                String word = String.format("w%04d", i);
                assertEquals(1, all.split("\\b" + word + "\\b", -1).length - 1, word + " appears exactly once");
            }
            for (int p = 1; p <= doc.getNumberOfPages(); p++) {
                float lowestAllowed = doc.getPage(p - 1).getMediaBox().getHeight() - (PDFService.PADDING + PDFService.FONT_SIZE);
                for (Placed line : placed(doc, p).values()) {
                    if (line.text().startsWith("w")) {
                        assertTrue(line.yFromTop() <= lowestAllowed + 0.5f,
                                "page " + p + ": line at " + line.yFromTop() + " is below the text area: " + line.text());
                    }
                }
            }
        }
    }

    @Test
    void given_aDiagramWithADoctype_when_built_then_theReportFails() {
        Content malicious = new Content();
        malicious.svg = "<!DOCTYPE svg [<!ENTITY x SYSTEM \"file:///etc/passwd\">]><svg>&x;</svg>";
        RuntimeException e = assertThrows(RuntimeException.class, () -> generate(part("Diagram", false, malicious)));
        IOException cause = assertInstanceOf(IOException.class, e.getCause());
        assertEquals("Rejected SVG content containing a DOCTYPE/ENTITY declaration", cause.getMessage());
    }

    @Test
    void given_aTableLongerThanAPage_when_built_then_itContinuesOnNewPagesWithItsHeader() throws IOException {
        try (PDDocument doc = Loader.loadPDF(generate(part("Relatives", false, table(80))))) {
            assertTrue(doc.getNumberOfPages() >= 2, "pages: " + doc.getNumberOfPages());
            List<String> pages = pageTexts(doc);
            String all = String.join("", pages);
            for (int r = 0; r < 80; r++) {
                assertTrue(all.contains("Relative " + r + " "), "row " + r);
            }
            assertTrue(pages.get(1).contains("Person"), "the header row repeats");
            Map<String, Placed> second = placed(doc, 2);
            Placed header = second.values().stream().filter(p -> p.text().startsWith("Person")).findFirst().orElseThrow();
            assertTrue(header.yFromTop() > 40 && header.yFromTop() < 60, header.toString());
            for (int page = 1; page <= doc.getNumberOfPages(); page++) {
                placed(doc, page).values().stream().filter(p -> p.text().startsWith("Relative"))
                        .forEach(p -> assertTrue(p.yFromTop() < 792 - 40, "above the footer zone: " + p));
            }
            doc.getPages().forEach(p -> assertTrue(p.getMediaBox().getHeight() > p.getMediaBox().getWidth()));
        }
    }

    @Test
    void given_aLongParagraph_when_built_then_itWrapsWithinThePageMargins() throws IOException {
        StringBuilder words = new StringBuilder();
        for (int i = 0; i < 60; i++) {
            words.append("word").append(i).append(' ');
        }
        try (PDDocument doc = Loader.loadPDF(generate(part("Notes", false, text(words.toString().trim()))))) {
            List<Placed> lines = placed(doc, 1).values().stream().filter(p -> p.text().startsWith("word")).toList();
            assertTrue(lines.size() >= 2, lines.toString());
            for (Placed line : lines) {
                float width = PDFService.TEXT_FONT.getStringWidth(line.text()) / 1000 * 10;
                assertTrue(width <= 612 - 80, "fits between the margins: " + line);
                assertEquals(40f, line.x(), 0.5f);
            }
            assertEquals(12f, lines.get(1).yFromTop() - lines.get(0).yFromTop(), 0.5f, "lines are 12 pt apart");
            String all = String.join(" ", lines.stream().map(Placed::text).toList());
            assertEquals(words.toString().trim(), all);
        }
    }

    @Test
    void given_aTable_when_built_then_cellsAreLaidOutInColumnsAndLongCellsWrap() throws IOException {
        Table t = new Table();
        t.headers = new String[]{"Person", "Condition", "Age"};
        t.widths = new float[]{0, 120, 60};
        String longCondition = "Hereditary nonpolyposis colorectal cancer";
        t.body = new String[][]{{"Mother", longCondition, "61"}, {"Maternal grandfather", "None", "88"}};
        Content content = new Content();
        content.table = t;
        try (PDDocument doc = Loader.loadPDF(generate(part("Relatives", false, content)))) {
            Map<String, Placed> where = placed(doc, 1);
            float personWidth = PDFService.TEXT_FONT.getStringWidth("Maternal grandfather") / 1000 * 10 + 4 + 10;
            Placed person = where.get("Person");
            assertNotNull(person, where.keySet().toString());
            assertEquals(42f, person.x(), 0.5f, "text starts one cell margin inside the table margin");
            assertEquals(42f + personWidth, where.get("Condition").x(), 0.5f, "the first column fits its widest cell");
            assertEquals(42f + personWidth + 120f, where.get("Age").x(), 0.5f);

            Placed mother = where.get("Mother");
            assertEquals(42f, mother.x(), 0.5f);
            assertEquals(15f, mother.yFromTop() - person.yFromTop(), 0.5f, "the first row follows the header row");
            List<Placed> conditionLines = where.values().stream()
                    .filter(p -> longCondition.contains(p.text()) && p.text().length() > 3).toList();
            assertEquals(2, conditionLines.size(), where.keySet().toString());
            assertEquals(mother.yFromTop(), conditionLines.get(0).yFromTop(), 0.5f);
            assertEquals(15f, conditionLines.get(1).yFromTop() - conditionLines.get(0).yFromTop(), 0.5f, "a wrapped cell continues below");
            assertEquals(longCondition, conditionLines.get(0).text() + " " + conditionLines.get(1).text());
            for (Placed line : conditionLines) {
                assertEquals(42f + personWidth, line.x(), 0.5f);
                assertTrue(PDFService.TEXT_FONT.getStringWidth(line.text()) / 1000 * 10 <= 120 - 4, line.toString());
            }
            Placed grandfather = where.get("Maternal grandfather");
            assertEquals(30f, grandfather.yFromTop() - mother.yFromTop(), 0.5f, "a two-line row is twice as tall");
            assertEquals(42f, grandfather.x(), 0.5f);
            assertEquals(42f + personWidth + 120f, where.get("88").x(), 0.5f);
        }
    }

    @Test
    void given_aDocument_when_theDefaultHeaderIsStamped_then_everyPageCarriesIt() throws IOException {
        PDFService service = new PDFService();
        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new PDPage(PDRectangle.LETTER));
            doc.addPage(new PDPage(PDRectangle.LETTER));
            service.document = doc;
            service.addHeadersAndFooters(URL);
            List<String> pages = pageTexts(doc);
            assertTrue(pages.get(0).contains("Family Health History Survey - Page 1 of 2"));
            assertTrue(pages.get(1).contains("Family Health History Survey - Page 2 of 2"));
            assertTrue(pages.get(1).contains("Page 2 of 2"));
        }
    }
}
