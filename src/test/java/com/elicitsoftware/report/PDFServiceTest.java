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
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Booted smoke test for {@link PDFService#generatePDF(ArrayList)} covering the full rendering
 * pipeline (text, table, and SVG content) end to end.
 *
 * <p>Traceability: UC-005 (Generate and Download Survey Report). This closes the coverage gap
 * flagged in the 2026-09-03 best-practices audit: {@code PDFService}'s full rendering flow had
 * zero test coverage beyond the XXE guard tested in {@link PDFServiceXxeTest}.</p>
 */
@QuarkusTest
class PDFServiceTest {

    @Inject
    PDFService pdfService;

    /**
     * Builds one {@link ReportResponse} whose PDF document contains a text block, a small table,
     * and a legitimate (non-malicious) SVG image, exercising every branch of
     * {@link PDFService#generatePDF(ArrayList)}'s content dispatch.
     */
    private ArrayList<ReportResponse> buildResponses() {
        Content textContent = new Content();
        textContent.text = "This is a short paragraph of survey report text used to smoke-test "
                + "PDFService's text rendering and line-wrapping path end to end.";

        Content tableContent = new Content();
        Table table = new Table();
        table.headers = new String[]{"Person", "Result"};
        table.widths = new float[]{120f, 120f};
        table.body = new String[][]{
                {"Alice", "Positive"},
                {"Bob", "Negative"}
        };
        tableContent.table = table;

        Content svgContent = new Content();
        svgContent.svg = "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"10\" height=\"10\">"
                + "<rect width=\"10\" height=\"10\"/></svg>";

        PDFDocument pdf = new PDFDocument();
        pdf.title = "UC-005 Smoke Test Report";
        pdf.content = new Content[]{textContent, tableContent, svgContent};

        ReportResponse response = new ReportResponse("UC-005 Smoke Test Report", null, pdf);

        ArrayList<ReportResponse> responses = new ArrayList<>();
        responses.add(response);
        return responses;
    }

    /**
     * UC-005: generating a PDF from a response with text, table, and SVG content produces a
     * well-formed, non-empty PDF byte stream that PDFBox can re-parse.
     */
    @Test
    void generatePDFProducesWellFormedPdfFromTextTableAndSvgContent() throws Exception {
        ArrayList<ReportResponse> responses = buildResponses();

        byte[] pdfBytes = runWithActiveRequestContext(() -> pdfService.generatePDF(responses, "http://localhost:8080"));

        assertNotNull(pdfBytes, "generatePDF must return a byte array");
        assertTrue(pdfBytes.length > 0, "generatePDF must return non-empty PDF content");

        String magic = new String(pdfBytes, 0, 4, StandardCharsets.US_ASCII);
        assertEquals("%PDF", magic, "generated content must start with the PDF magic bytes");

        try (PDDocument reparsed = Loader.loadPDF(pdfBytes)) {
            assertTrue(reparsed.getNumberOfPages() >= 1, "the generated PDF must have at least one page");
        }
    }

    /**
     * UC-005: generating the PDF does not throw, even though it exercises the SVG rendering path
     * (Batik parsing + PdfBoxGraphics2D) alongside plain text and table content.
     */
    @Test
    void generatePDFDoesNotThrow() {
        ArrayList<ReportResponse> responses = buildResponses();
        assertDoesNotThrow(() -> runWithActiveRequestContext(() -> pdfService.generatePDF(responses, "http://localhost:8080")));
    }

    /**
     * Runs the given action with the CDI request context active, since {@link PDFService} is
     * {@code @RequestScoped} and its client proxy cannot dispatch to the underlying bean outside
     * of an active request scope.
     */
    private byte[] runWithActiveRequestContext(java.util.function.Supplier<byte[]> action) {
        io.quarkus.arc.ManagedContext requestContext = io.quarkus.arc.Arc.container().requestContext();
        boolean activatedHere = !requestContext.isActive();
        if (activatedHere) {
            requestContext.activate();
        }
        try {
            return action.get();
        } finally {
            if (activatedHere) {
                requestContext.terminate();
            }
        }
    }
}
