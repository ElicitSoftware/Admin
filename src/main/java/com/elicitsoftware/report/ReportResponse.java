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

import com.elicitsoftware.report.pdf.PDFDocument;

/**
 * ReportResponse represents the complete response structure returned from a report generation service.
 * <p>
 * This class serves as a comprehensive data transfer object (DTO) that encapsulates all the
 * different representations of a generated report. It supports multiple output formats to
 * accommodate various use cases including web display, PDF download, and data processing.
 * <p>
 * The response contains three main components:
 * - **Title**: A human-readable name or description of the report
 * - **HTML Content**: Web-ready HTML for browser display and interaction
 * - **PDF Document**: Structured PDF data for file generation and download
 * <p>
 * This multi-format approach allows the same report data to be consumed by different
 * client applications and user interfaces. The HTML content is typically used for
 * immediate web display, while the PDF document enables file downloads and printing.
 * <p>
 * Key features:
 * - Multiple output format support (HTML and PDF)
 * - JSON serialization compatible for REST APIs
 * - Flexible field access with public properties
 * - Optional PDF content for text-only reports
 * - Structured data organization for report processing
 * <p>
 * Usage example:
 * <pre>
 * {@code
 * ReportRequest request = new ReportRequest(surveyId);
 * ReportResponse response = reportService.callReport(request);
 * 
 * // Display in web browser
 * webView.setContent(response.innerHTML);
 * 
 * // Generate PDF download
 * if (response.pdf != null) {
 *     StreamResource pdfResource = pdfService.generatePDF(response);
 *     download.setResource(pdfResource);
 * }
 * }
 * </pre>
 * 
 * @see ReportService#callReport(ReportRequest)
 * @see ReportRequest
 * @see PDFDocument
 * @see com.elicitsoftware.report.PDFService
 * @since 1.0.0
 */
public class ReportResponse {
    /**
     * The title or name of the report.
     * <p>
     * This field contains a human-readable title that describes the report content.
     * It is typically displayed as a header in web interfaces and used as the
     * filename base for PDF downloads. The title should be descriptive enough
     * to help users identify the report purpose and content.
     * <p>
     * Examples: "Family History Report", "Survey Results Summary", "Data Analysis Report"
     */
    public String title;
    
    /**
     * HTML content representation of the report for web display.
     * <p>
     * This field contains the complete HTML markup needed to display the report
     * in a web browser. The HTML is typically self-contained and includes all
     * necessary styling and structure for proper rendering. It may include:
     * - Tables with formatted data
     * - Charts and graphs as embedded SVG
     * - Styled text content and headers
     * - Interactive elements for web viewing
     * <p>
     * The HTML content is designed to be embedded directly into web pages
     * or displayed in web view components.
     */
    public String innerHTML;
    
    /**
     * PDF document structure for generating downloadable PDF files.
     * <p>
     * This field contains a structured representation of the report optimized
     * for PDF generation. It includes layout information, content blocks,
     * and formatting details needed by the {@link com.elicitsoftware.report.PDFService}
     * to generate high-quality PDF documents.
     * <p>
     * The PDF document may include:
     * - Text content with proper formatting
     * - SVG graphics and charts
     * - Tables with pagination support
     * - Page layout and styling information
     * <p>
     * This field may be null for reports that are only intended for web display
     * or when PDF generation is not required.
     * 
     * @see PDFDocument
     * @see com.elicitsoftware.report.PDFService#generatePDF(java.util.ArrayList)
     */
    public PDFDocument pdf;

    /**
     * Constructs an empty ReportResponse with all fields set to null.
     * <p>
     * Creates a new ReportResponse instance that can be populated with
     * report data through direct field assignment or using the parameterized
     * constructor. This constructor is useful for gradual construction or
     * when not all fields are immediately available.
     */
    public ReportResponse() {
        super();
    }

    /**
     * Constructs a new ReportResponse with the specified title, HTML content, and PDF document.
     * <p>
     * Creates a fully populated ReportResponse instance with all three main components
     * of a report response. This constructor is useful when all report data is available
     * at construction time and provides a convenient way to create complete response objects.
     * <p>
     * The parameters allow for flexible report creation:
     * - Title can be descriptive text for user display
     * - HTML content enables immediate web rendering
     * - PDF document supports file download functionality
     *
     * @param title The report title or name; may be null but recommended to provide
     * @param innerHTML The HTML content for web display; may be null if only PDF is needed
     * @param pdf The PDF document structure; may be null if only web display is needed
     * @see PDFDocument
     */
    public ReportResponse(String title, String innerHTML, PDFDocument pdf) {
        super();
        this.title = title;
        this.innerHTML = innerHTML;
        this.pdf = pdf;
    }
}

