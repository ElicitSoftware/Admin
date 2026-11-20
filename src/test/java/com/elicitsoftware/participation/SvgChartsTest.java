package com.elicitsoftware.participation;

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

import com.elicitsoftware.report.PDFService;
import com.elicitsoftware.report.ReportResponse;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** UC-031 BR-129: the charts are well-formed SVG that never draws a suppressed value. */
class SvgChartsTest {

    private final SmallCell cells = new SmallCell(5, Locale.ENGLISH);

    private static Document parse(String svg) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory.newDocumentBuilder().parse(new ByteArrayInputStream(svg.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void barsDrawNothingForASuppressedCountAndLabelIt() throws Exception {
        String svg = SvgCharts.bars("Funnel", List.of(
                new SvgCharts.Bar("Registered", 80, "80"),
                new SvgCharts.Bar("Started", 3, "< 5"),
                new SvgCharts.Bar("Finished <\"&>", 40, "40")), cells);
        Document doc = parse(svg);
        assertEquals("img", doc.getDocumentElement().getAttribute("role"));
        assertEquals("Funnel", doc.getDocumentElement().getAttribute("aria-label"));
        NodeList paths = doc.getElementsByTagNameNS("http://www.w3.org/2000/svg", "path");
        assertEquals(2, paths.getLength(), "the suppressed bar is not drawn");
        assertTrue(svg.contains(">&lt; 5</text>"), "the suppressed value is labeled");
        assertTrue(svg.contains("Finished &lt;&quot;&amp;&gt;"), "labels are escaped");
        // The longest bar spans the plot; the other is in proportion to it.
        assertTrue(svg.contains("h382 ") || svg.contains("h382,"), svg);
    }

    @Test
    void columnsCarryALegendAndSkipSuppressedValues() throws Exception {
        List<SvgCharts.Group> weeks = new ArrayList<>();
        weeks.add(new SvgCharts.Group("5 Jan", new long[]{20, 12, 0}, new String[]{"20", "12", "0"}));
        weeks.add(new SvgCharts.Group("12 Jan", new long[]{8, 2, 6}, new String[]{"8", "< 5", "6"}));
        String svg = SvgCharts.columns("Weeks", List.of("Registered", "Started", "Finished"), weeks, cells);
        Document doc = parse(svg);
        assertEquals(3, doc.getElementsByTagNameNS("http://www.w3.org/2000/svg", "rect").getLength(), "one legend swatch per series");
        assertEquals(4, doc.getElementsByTagNameNS("http://www.w3.org/2000/svg", "path").getLength(),
                "four drawn columns: the zero and the suppressed value draw nothing");
        assertTrue(svg.contains("12 Jan, Started: &lt; 5"));
    }

    @Test
    void niceCeilingRoundsUpToOneTwoOrFive() {
        assertEquals(0, SvgCharts.niceCeiling(0));
        assertEquals(1, SvgCharts.niceCeiling(1));
        assertEquals(20, SvgCharts.niceCeiling(12));
        assertEquals(50, SvgCharts.niceCeiling(38));
        assertEquals(100, SvgCharts.niceCeiling(80));
    }

    /** BR-129: the PDF service draws the inline charts the export hands it, in a valid document. */
    @Test
    void pdfServiceDrawsTheInlineCharts() throws Exception {
        Participation p = new Participation(new Participation.Counts(40, 10, 18, 12, 8), List.of(),
                List.of(new Participation.Week(LocalDate.of(2026, 1, 5), 40, 30, 12)), List.of(),
                new Participation.Progress(List.of(new Participation.StepProgress(1, "Household", 12, 6, Participation.Durations.NONE)),
                        0, 0, Participation.Durations.NONE, 0), 14);
        ReportResponse response = ParticipationExport.pdf(p, cells,
                new ParticipationExport.Heading("Census", "All departments", null, null));
        ArrayList<ReportResponse> parts = new ArrayList<>(List.of(response));
        byte[] pdf = new PDFService().generatePDF(parts, "http://localhost", "Census");
        assertTrue(pdf.length > 1000);
        try (org.apache.pdfbox.pdmodel.PDDocument doc = org.apache.pdfbox.Loader.loadPDF(pdf)) {
            assertTrue(doc.getNumberOfPages() >= 1);
            // Inline charts stay on portrait pages; only a report service's diagram turns a page.
            doc.getPages().forEach(page -> assertTrue(page.getMediaBox().getHeight() > page.getMediaBox().getWidth()));
        }
    }
}
