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

import com.elicitsoftware.report.pdf.Content;
import com.elicitsoftware.report.pdfbox.Column;
import com.elicitsoftware.report.pdfbox.Table;
import com.elicitsoftware.report.pdfbox.TableBuilder;
import com.vaadin.flow.server.StreamResource;
import de.rototor.pdfbox.graphics2d.PdfBoxGraphics2D;
import de.rototor.pdfbox.graphics2d.PdfBoxGraphics2DFontTextDrawer;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.batik.anim.dom.SAXSVGDocumentFactory;
import org.apache.batik.bridge.BridgeContext;
import org.apache.batik.bridge.GVTBuilder;
import org.apache.batik.bridge.UserAgentAdapter;
import org.apache.batik.gvt.GraphicsNode;
import org.apache.batik.util.XMLResourceDescriptor;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.util.Matrix;
import org.w3c.dom.svg.SVGDocument;

import java.awt.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * PDFService is a request-scoped service responsible for generating PDF documents from report data.
 * <p>
 * This service provides comprehensive PDF generation capabilities including:
 * - Text content rendering with automatic line wrapping
 * - SVG graphics embedding with proper scaling and transformation
 * - Table generation with automatic pagination
 * - Headers and footers management
 * - Multi-page document support with automatic page breaks
 * <p>
 * The service uses Apache PDFBox library for PDF manipulation and supports various content types
 * including plain text, SVG graphics, and tabular data. It automatically handles page layout,
 * font management, and content positioning.
 * <p>
 * Key features:
 * - Automatic page breaks when content exceeds page boundaries
 * - Consistent formatting with predefined margins and fonts
 * - SVG rendering with landscape orientation support
 * - Table pagination with proper column alignment
 * - Stream resource generation for download functionality
 * <p>
 * Usage example:
 * <pre>
 * {@code
 * @Inject
 * PDFService pdfService;
 * 
 * ArrayList<ReportResponse> responses = getReportData();
 * StreamResource pdfResource = pdfService.generatePDF(responses);
 * }
 * </pre>
 * 
 * @see ReportResponse
 * @see Content
 * @see StreamResource
 * @since 1.0.0
 */
@RequestScoped
public class PDFService {

    /**
     * Standard page size used for all generated PDF documents.
     */
    private static final PDRectangle PAGE_SIZE = PDRectangle.LETTER;
    
    /**
     * Top margin used for headers, in points.
     */
    static final float HEADER_MARGIN = 20f;
    
    /**
     * Standard text margin from page edges, in points.
     */
    static final float TEXT_MARGIN = 40f;
    
    /**
     * Padding used for content positioning, in points.
     */
    static final float PADDING = 40f;
    
    /**
     * Default font used for all text content in the PDF.
     */
    static final PDFont TEXT_FONT = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    
    /**
     * Standard font size for text content, in points.
     */
    static final float FONT_SIZE = 10f;
    
    /**
     * Line spacing (leading) between text lines, in points.
     */
    static final float LEADING = FONT_SIZE;

    /**
     * Height of each table row, in points.
     */
    private static final float ROW_HEIGHT = 15;
    
    /**
     * Margin within table cells, in points.
     */
    private static final float CELL_MARGIN = 2;

    /**
     * The current PDF document being generated.
     */
    PDDocument document;
    
    /**
     * The current page being written to.
     */
    PDPage page;
    
    /**
     * The current content stream for writing to the page.
     */
    PDPageContentStream contentStream;
    
    /**
     * Current Y position on the page for content placement.
     */
    float yPosition;
    
    /**
     * Height of the current page, in points.
     */
    float pageHeight;
    
    /**
     * Width of the current page, in points.
     */
    float pageWidth;

    /**
     * Injected HTTP servlet request for context information.
     */
    @Inject
    HttpServletRequest request;

    /**
     * Generates a PDF document from a list of report responses and returns it as a StreamResource.
     * <p>
     * This method processes each report response sequentially, handling page breaks, titles,
     * and various content types (text, SVG, tables). It automatically manages page layout,
     * font settings, and content positioning throughout the document generation process.
     * <p>
     * The generated PDF includes:
     * - Proper page margins and formatting
     * - Automatic page breaks when content overflows
     * - Headers and footers on all pages
     * - Consistent font and styling throughout
     * <p>
     * Process flow:
     * 1. Initialize new PDF document with standard page size
     * 2. Process each report response in sequence
     * 3. Handle page breaks as specified in report data
     * 4. Add title blocks, text content, SVG graphics, and tables
     * 5. Add headers and footers to all pages
     * 6. Save document to stream and return as downloadable resource
     *
     * @param reportResponses List of report responses containing the content to be rendered
     * @return StreamResource containing the generated PDF, named "family_history_report.pdf"
     * @throws RuntimeException if an IOException occurs during PDF generation
     * @see ReportResponse
     * @see Content
     * @see StreamResource
     */

    /**
     * Default constructor for PDFService.
     * <p>
     * Creates a new PDFService instance with default values.
     * This constructor is used by frameworks and for general instantiation.
     */
    public PDFService() {
        // Default constructor
    }

    public StreamResource generatePDF(ArrayList<ReportResponse> reportResponses) {
        try {
            // Create a new document
            document = new PDDocument();
            page = new PDPage(PDRectangle.LETTER);
            document.addPage(page);
            contentStream = new PDPageContentStream(document, page);
            contentStream.setFont(TEXT_FONT, FONT_SIZE);

            pageHeight = PDRectangle.LETTER.getHeight();
            pageWidth = PDRectangle.LETTER.getWidth();
            yPosition = pageHeight - TEXT_MARGIN;

            for (ReportResponse response : reportResponses) {
                // Close the current content stream if a new page is needed
                if (response.pdf.pageBreak) {
                    contentStream.close();
                    page = new PDPage(PDRectangle.LETTER);
                    document.addPage(page);
                    contentStream = new PDPageContentStream(document, page);
                    contentStream.setFont(TEXT_FONT, FONT_SIZE);
                    yPosition = pageHeight - TEXT_MARGIN;
                }

                addTitleBlock(response.pdf.title);

                for (Content content : response.pdf.content) {
                    if (content == null) {
                        System.out.println("Content is null");
                        continue;
                    }
                    // Close the current content stream if a new page is needed
                    if (yPosition < TEXT_MARGIN + FONT_SIZE) {
                        contentStream.close();
                        page = new PDPage(PDRectangle.LETTER);
                        document.addPage(page);
                        contentStream = new PDPageContentStream(document, page);
                        contentStream.setFont(TEXT_FONT, FONT_SIZE);
                        yPosition = pageHeight - TEXT_MARGIN;
                    }

                    // Add the content
                    if (content.svg != null) {
                        addSVG(content);
                    } else if (content.table != null) {
                        drawTable(createContent(content));
                    } else {
                        addTextBlock(content.text);
                    }
                }
            }

            addHeadersAndFooters();

            if (contentStream != null) {
                contentStream.close();
            }

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            document.save(outputStream);
            ByteArrayInputStream inputStream = new ByteArrayInputStream(outputStream.toByteArray());
            return new StreamResource("family_history_report.pdf", () -> inputStream);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Adds a title block to the current page with proper spacing and formatting.
     * <p>
     * This method renders a title with appropriate margins and handles page breaks
     * if necessary. The title is positioned with extra spacing above it to separate
     * it from previous content sections.
     * <p>
     * Behavior:
     * - Adds header margin spacing above the title
     * - Creates a new page if insufficient space remains
     * - Positions title at standard padding distance from left margin
     * - Updates the current Y position for subsequent content
     * <p>
     * If the title is null or empty, this method performs no action.
     *
     * @param title The title text to be added; ignored if null or empty
     * @throws IOException if an error occurs while writing to the PDF content stream
     */
    public void addTitleBlock(String title) throws IOException {
        if (title != null && !title.isEmpty()) {
            //Add some space between the last item and the new title.
            yPosition -= HEADER_MARGIN;
            // Check if we need a new page
            if (yPosition < FONT_SIZE + PADDING) {
                page = new PDPage(PDRectangle.LETTER);
                document.addPage(page);
                contentStream.close();
                contentStream = new PDPageContentStream(document, page);
                contentStream.setFont(TEXT_FONT, FONT_SIZE); // Ensure font is set
                yPosition = pageHeight - TEXT_MARGIN; // Reset yPosition for new page
            }

            // Begin text
            contentStream.beginText();
            contentStream.setFont(TEXT_FONT, FONT_SIZE); // Ensure font is set before writing text
            contentStream.newLineAtOffset(PADDING, yPosition);
            contentStream.showText(title);
            contentStream.endText();
            // Update Y position
            yPosition -= LEADING;
        }
    }

    /**
     * Adds a text block to the current page with automatic line wrapping and page break handling.
     * <p>
     * This method processes text content by:
     * - Checking for sufficient space on the current page
     * - Creating a new page if needed
     * - Wrapping text to fit within page margins
     * - Rendering each line with consistent spacing
     * - Updating the Y position for subsequent content
     * <p>
     * The text is automatically wrapped to fit within the available page width,
     * accounting for left and right padding. Each line is rendered with additional
     * line spacing for improved readability.
     * <p>
     * Page break logic:
     * - Creates a new page if remaining space is insufficient
     * - Resets font settings on new pages
     * - Maintains consistent margin settings
     *
     * @param text The text content to be added to the PDF
     * @throws IOException if an error occurs while writing to the PDF content stream
     * @see #wrapText(String, PDFont, float, float)
     */
    public void addTextBlock(String text) throws IOException {
        // Check if we need a new page
        if (yPosition < PADDING + FONT_SIZE) {
            page = new PDPage(PDRectangle.LETTER);
            document.addPage(page);
            contentStream.close();
            contentStream = new PDPageContentStream(document, page);
            contentStream.setFont(TEXT_FONT, FONT_SIZE); // Ensure font is set
        }
        PDRectangle mediaBox = page.getMediaBox();
        float width = mediaBox.getWidth() - 2 * PADDING;

        List<String> lines = wrapText(text, TEXT_FONT, FONT_SIZE, width);

        // Begin text
        for (String line : lines) {
            contentStream.beginText();
            contentStream.setFont(TEXT_FONT, FONT_SIZE); // Ensure font is set before writing text
            contentStream.newLineAtOffset(PADDING, yPosition);
            contentStream.showText(line);
            contentStream.endText();
            yPosition -= FONT_SIZE + 2; // Add some line spacing
        }
        // Update Y position
        yPosition -= LEADING;
    }

    /**
     * Adds an SVG graphic to the PDF document in landscape orientation.
     * <p>
     * This method handles SVG content by:
     * - Creating a new landscape-oriented page
     * - Parsing the SVG content using Apache Batik
     * - Rendering the SVG using PdfBoxGraphics2D
     * - Applying proper scaling and positioning
     * - Closing the content stream after rendering
     * <p>
     * The SVG is rendered on a dedicated landscape page to accommodate
     * wider graphics that might not fit in portrait orientation. The method
     * uses Apache Batik for SVG parsing and PdfBoxGraphics2D for rendering
     * the vector graphics directly into the PDF.
     * <p>
     * Process flow:
     * 1. Close current content stream
     * 2. Switch current page to landscape orientation
     * 3. Create new content stream for the landscape page
     * 4. Parse SVG content using Batik SAXSVGDocumentFactory
     * 5. Build graphics tree and render to PDF
     * 6. Apply coordinate transformation for proper positioning
     * 7. Close content stream
     * <p>
     * Note: This method assumes the content object contains valid SVG data
     * in its svg field.
     *
     * @param content The content object containing SVG data to be rendered
     * @throws IOException if an error occurs during SVG processing or PDF writing
     * @see Content
     */
    void addSVG(Content content) throws IOException {
        PDRectangle landscape = new PDRectangle(PDRectangle.LETTER.getHeight(), PDRectangle.LETTER.getWidth());

        //Create a new page and open a new content stream.
        contentStream.close();
        page = document.getPage(document.getNumberOfPages() - 1);
        page.setMediaBox(landscape);
        contentStream = new PDPageContentStream(document, page);

        float pageWidth = landscape.getWidth();
        float pageHeight = landscape.getHeight();

        try {
            PdfBoxGraphics2D graphics2D = new PdfBoxGraphics2D(document, (int) pageWidth, (int) pageHeight);
            graphics2D.setFontTextDrawer(new PdfBoxGraphics2DFontTextDrawer());

            // Parse the SVG
            String parser = XMLResourceDescriptor.getXMLParserClassName();
            SAXSVGDocumentFactory factory = new SAXSVGDocumentFactory(parser);
            SVGDocument svgDocument = factory.createSVGDocument(null, new StringReader(content.svg));

            // Build GVT
            GVTBuilder builder = new GVTBuilder();
            BridgeContext ctx = new BridgeContext(new UserAgentAdapter());
            GraphicsNode graphicsNode = builder.build(ctx, svgDocument);

            // Use actual computed bounds that include all rendered content
            Rectangle bounds = graphicsNode.getBounds().getBounds();
            Rectangle primitiveBounds = graphicsNode.getPrimitiveBounds().getBounds();
            Rectangle actualBounds = bounds.union(primitiveBounds);

            // Add explicit padding to ensure content that extends beyond computed bounds is captured
            int padding = 30; // Add 30 pixels padding on all sides
            Rectangle expandedBounds = new Rectangle(
                actualBounds.x - padding,
                actualBounds.y - padding,
                actualBounds.width + (2 * padding),
                actualBounds.height + (2 * padding)
            );

            // Calculate scale to fit using expanded bounds with margins
            double availableWidth = pageWidth - (2 * TEXT_MARGIN);
            double availableHeight = pageHeight - (2 * TEXT_MARGIN);

            double scaleX = availableWidth / expandedBounds.getWidth();
            double scaleY = availableHeight / expandedBounds.getHeight();
            double scale = Math.min(scaleX, scaleY); // Preserve aspect ratio

            graphics2D.scale(scale, scale);

            // Center the image using expanded bounds within available space
            double translateX = (availableWidth / scale - expandedBounds.getWidth()) / 2.0 + TEXT_MARGIN / scale;
            double translateY = (availableHeight / scale - expandedBounds.getHeight()) / 2.0 + TEXT_MARGIN / scale;
            graphics2D.translate(translateX - expandedBounds.getX(), translateY - expandedBounds.getY());

            graphicsNode.paint(graphics2D);
            graphics2D.dispose();

            contentStream.drawForm(graphics2D.getXFormObject());

            // Reset color to black for subsequent content
            contentStream.setNonStrokingColor(0f, 0f, 0f);

        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            // Ensure the content stream is closed after drawing
            if (contentStream != null) {
                contentStream.close();
            }
        }
    }

    /**
     * Creates a Table object from the provided Content, configuring columns and data for PDF rendering.
     * <p>
     * This method processes table content by:
     * - Creating column definitions with appropriate widths
     * - Setting the first column to auto-width based on content
     * - Distributing remaining width evenly among other columns
     * - Configuring table properties for PDF rendering
     * <p>
     * Column width calculation:
     * - First column (typically "person"): Auto-sized based on longest content
     * - Remaining columns: Equal distribution of remaining available width
     * - All calculations account for cell margins and font metrics
     * <p>
     * The table is configured with standard settings including row height,
     * cell margins, font properties, and page layout parameters suitable
     * for PDF generation.
     *
     * @param content The Content object containing table headers, widths, and body data
     * @return A configured Table object ready for PDF rendering
     * @see Table
     * @see Column
     * @see Content
     */
    private static Table createContent(Content content) {

        // Total size of columns must not be greater than table width.
        List<Column> columns = new ArrayList<>();
        int i = 0;
        for (String header : content.table.headers) {
            float columnWidth;

            // Set the first column (person) to auto width based on content
            if (i == 0) {
                // Calculate max width needed for the "person" column
                float maxWidth = 0;
                try {
                    // Check header width
                    float headerWidth = TEXT_FONT.getStringWidth(header) / 1000 * FONT_SIZE + (CELL_MARGIN * 2);
                    maxWidth = Math.max(maxWidth, headerWidth);

                    // Check all data in this column
                    for (String[] row : content.table.body) {
                        if (row.length > i && row[i] != null) {
                            float cellWidth = TEXT_FONT.getStringWidth(row[i]) / 1000 * FONT_SIZE + (CELL_MARGIN * 2);
                            maxWidth = Math.max(maxWidth, cellWidth);
                        }
                    }
                    columnWidth = maxWidth + 10; // Add some padding
                } catch (IOException e) {
                    // Fallback to a reasonable width if font width calculation fails
                    columnWidth = 120f;
                }
            } else {
                columnWidth = Float.valueOf(content.table.widths[i]);
            }

            columns.add(new Column(header, columnWidth));
            i++;
        }

        String[][] tableContent = content.table.body;

        float tableHeight = PAGE_SIZE.getHeight() - TEXT_MARGIN;

        Table table = new TableBuilder()
                .setCellMargin(CELL_MARGIN)
                .setColumns(columns)
                .setContent(tableContent)
                .setHeight(tableHeight)
                .setNumberOfRows(tableContent.length)
                .setRowHeight(ROW_HEIGHT)
                .setMargin(PADDING)
                .setPageSize(PAGE_SIZE)
                .setLandscape(false)
                .setTextFont(TEXT_FONT)
                .setFontSize(FONT_SIZE)
                .build();
        return table;
    }

    /**
     * Adds headers and footers to all pages in the current PDF document.
     * <p>
     * This method iterates through all pages in the document and adds:
     * - Current date in the top left corner
     * - Base URL (constructed from HTTP request) centered at the bottom
     * - Page numbers in the bottom right corner
     * <p>
     * The header and footer content is consistently positioned using standard
     * margins and font settings. The base URL is dynamically constructed from
     * the current HTTP request context including scheme, server name, port,
     * and context path.
     * <p>
     * Layout:
     * - Header: Current date (top left)
     * - Footer: Base URL (center) and "Page X of Y" (right)
     * <p>
     * All text uses the standard font and 10-point size for consistency.
     *
     * @throws RuntimeException if an IOException occurs during content stream operations
     */
    void addHeadersAndFooters() {
        // Step 2: Add header and footer to each page
        int totalPages = document.getNumberOfPages();
        for (int i = 0; i < totalPages; i++) {
            PDPage page = document.getPage(i);
            try (PDPageContentStream contentStream = new PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
                PDRectangle mediaBox = page.getMediaBox();
                float yTop = mediaBox.getHeight() - HEADER_MARGIN;
                float yBottom = HEADER_MARGIN;

                // Check if this is a landscape page
                boolean isLandscape = mediaBox.getWidth() > mediaBox.getHeight();

                // Header text - adjust positioning based on orientation
                String headerText = "Family Health History Survey - Page " + (i + 1) + " of " + totalPages;
                float headerTextWidth = TEXT_FONT.getStringWidth(headerText) / 1000 * 10;
                float headerTextX;

                if (isLandscape) {
                    // For landscape, center the text considering the wider page
                    headerTextX = (mediaBox.getWidth() - headerTextWidth) / 2;
                } else {
                    // For portrait, keep original positioning (to the right of image)
                    headerTextX = PADDING + 150;
                }

                contentStream.beginText();
                contentStream.setFont(TEXT_FONT, 10);
                contentStream.newLineAtOffset(headerTextX, yTop - 10);
                contentStream.showText(headerText);
                contentStream.endText();

                // Footer
                // Date in the form of MM/DD/YYYY (far left)
                String currentDate = LocalDate.now().format(DateTimeFormatter.ofPattern("MM/dd/yyyy"));
                contentStream.beginText();
                contentStream.setFont(TEXT_FONT, 10);
                contentStream.newLineAtOffset(PADDING, yBottom - 10);
                contentStream.showText(currentDate);
                contentStream.endText();

                // Base URL (center) - constructed from request
                String baseUrl = request.getScheme() + "://" + request.getServerName() +
                               (request.getServerPort() != 80 && request.getServerPort() != 443 ?
                                ":" + request.getServerPort() : "") + request.getContextPath();
                float baseUrlWidth = TEXT_FONT.getStringWidth(baseUrl) / 1000 * 10;
                float centerX = (mediaBox.getWidth() - baseUrlWidth) / 2;
                contentStream.beginText();
                contentStream.setFont(TEXT_FONT, 10);
                contentStream.newLineAtOffset(centerX, yBottom - 10);
                contentStream.showText(baseUrl);
                contentStream.endText();

                // Page numbers (far right)
                String pageText = "Page " + (i + 1) + " of " + totalPages;
                float pageTextWidth = TEXT_FONT.getStringWidth(pageText) / 1000 * 10;
                contentStream.beginText();
                contentStream.setFont(TEXT_FONT, 10);
                contentStream.newLineAtOffset(mediaBox.getWidth() - PADDING - pageTextWidth, yBottom - 10);
                contentStream.showText(pageText);
                contentStream.endText();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * Draws a table across multiple pages with proper pagination and formatting.
     * <p>
     * This method handles table rendering by:
     * - Calculating rows per page based on available height
     * - Determining total number of pages needed
     * - Creating content streams for each page
     * - Drawing table content with proper grid and formatting
     * <p>
     * The table is automatically paginated when content exceeds the available
     * page height. Each page maintains consistent column widths and formatting.
     * <p>
     * Process for each page:
     * 1. Calculate content for current page
     * 2. Generate appropriate content stream
     * 3. Draw table grid and content
     * 4. Handle page-specific formatting
     *
     * @param table The Table object containing data, columns, and formatting information
     * @throws IOException if an error occurs during PDF content generation
     * @see Table
     * @see #generateContentStream(Table)
     * @see #getContentForCurrentPage(Table, Integer, int)
     * @see #drawCurrentPage(Table, String[][])
     */
    public void drawTable(Table table) throws IOException {
        // Calculate pagination
        Integer rowsPerPage = (int) Math.floor(table.getHeight() / table.getRowHeight()) - 1;
        Integer numberOfPages = (int) Math.ceil(table.getNumberOfRows().floatValue() / rowsPerPage);

        // Generate each page, get the content and draw it
        for (int pageCount = 0; pageCount < numberOfPages; pageCount++) {
            PDPageContentStream contentStream = null;
            try {
                contentStream = generateContentStream(table);
                String[][] currentPageContent = getContentForCurrentPage(table, rowsPerPage, pageCount);
                drawCurrentPage(table, currentPageContent);
            } finally {
                if (contentStream != null) {
                    contentStream.close(); // Ensure the stream is closed
                }
            }
        }
    }

    private PDPageContentStream generateContentStream(Table table) throws IOException {

        PDPageContentStream contentStream = new PDPageContentStream(document, document.getPage(document.getNumberOfPages() - 1), PDPageContentStream.AppendMode.APPEND, false);
        // User transformation matrix to change the reference when drawing.
        // This is necessary for the landscape position to draw correctly
        if (table.isLandscape()) {
            contentStream.transform(new Matrix(0, 1, -1, 0, table.getPageSize().getWidth(), 0));
        }
        contentStream.setFont(table.getTextFont(), table.getFontSize());
        return contentStream;
    }

    private String[][] getContentForCurrentPage(Table table, Integer rowsPerPage, int pageCount) {
        int startRange = pageCount * rowsPerPage;
        int endRange = (pageCount * rowsPerPage) + rowsPerPage;
        if (endRange > table.getNumberOfRows()) {
            endRange = table.getNumberOfRows();
        }
        return Arrays.copyOfRange(table.getContent(), startRange, endRange);
    }

    // Draws current page table grid and borderlines and content
    private void drawCurrentPage(Table table, String[][] currentPageContent)
            throws IOException {
        PDPage page = document.getPage(document.getNumberOfPages() - 1);
//        float tableTopY = table.isLandscape() ? table.getPageSize().getWidth() - table.getMargin() : table.getPageSize().getHeight() - table.getMargin();
        float tableTopY = yPosition;
//        if (yPosition == TEXT_MARGIN) {
//            tableTopY = table.isLandscape() ? table.getPageSize().getWidth() - table.getMargin() : table.getPageSize().getHeight() - table.getMargin();
//        } else {
//            tableTopY = yPosition - table.getMargin();
//        }

        // Draws grid and borders
        drawTableGrid(table, currentPageContent, tableTopY);

        // Position cursor to start drawing content
        float nextTextX = table.getMargin() + table.getCellMargin();
        // Calculate center alignment for text in cell considering font height
        float nextTextY = tableTopY - (table.getRowHeight() / 2)
                - ((table.getTextFont().getFontDescriptor().getFontBoundingBox().getHeight() / 1000 * table.getFontSize()) / 4);

        // Write column headers
        writeContentLine(table.getColumnsNamesAsArray(), nextTextX, nextTextY, table);
        nextTextY -= table.getRowHeight();
        nextTextX = table.getMargin() + table.getCellMargin();

        // Write content
        for (int i = 0; i < currentPageContent.length; i++) {
            writeContentLine(currentPageContent[i], nextTextX, nextTextY, table);
            nextTextY -= table.getRowHeight();
            nextTextX = table.getMargin() + table.getCellMargin();
        }
        yPosition = nextTextY;
    }

    // Writes the content for one line
    private void writeContentLine(String[] lineContent, float nextTextX, float nextTextY,
                                  Table table) throws IOException {
        for (int i = 0; i < table.getNumberOfColumns(); i++) {
            String text = lineContent[i];
            contentStream.beginText();
            contentStream.newLineAtOffset(nextTextX, nextTextY);
            contentStream.showText(text != null ? text : "");
            contentStream.endText();
            nextTextX += table.getColumns().get(i).getWidth();
        }
    }

    private void drawTableGrid(Table table, String[][] currentPageContent, float tableTopY)
            throws IOException {
        // Draw row lines
        float nextY = tableTopY;
        for (int i = 0; i <= currentPageContent.length + 1; i++) {
            contentStream.moveTo(table.getMargin(), nextY);
            contentStream.lineTo(table.getMargin() + table.getWidth(), nextY);
            contentStream.stroke();
            nextY -= table.getRowHeight();
        }

        // Draw column lines
        final float tableYLength = table.getRowHeight() + (table.getRowHeight() * currentPageContent.length);
        final float tableBottomY = tableTopY - tableYLength;
        float nextX = table.getMargin();
        for (int i = 0; i < table.getNumberOfColumns(); i++) {
            contentStream.moveTo(nextX, tableTopY);
            contentStream.lineTo(nextX, tableBottomY);
            contentStream.stroke();
            nextX += table.getColumns().get(i).getWidth();
        }
        contentStream.moveTo(nextX, tableTopY);
        contentStream.lineTo(nextX, tableBottomY);
        contentStream.stroke();

    }

    /**
     * Wraps text to fit within specified width constraints, breaking on word boundaries.
     * <p>
     * This utility method processes text content by:
     * - Splitting text into individual words
     * - Building lines that fit within the maximum width
     * - Breaking at word boundaries to preserve readability
     * - Calculating text width using font metrics
     * <p>
     * The method ensures that no line exceeds the specified maximum width when
     * rendered with the given font and font size. Words are never broken in the
     * middle; instead, they are moved to the next line if they don't fit.
     * <p>
     * Width calculation uses PDFBox font metrics to accurately measure the
     * rendered width of text in the PDF coordinate system.
     * <p>
     * Usage example:
     * <pre>
     * {@code
     * List<String> lines = wrapText("Long text content...", font, 12f, 400f);
     * for (String line : lines) {
     *     // Render each line in the PDF
     * }
     * }
     * </pre>
     *
     * @param text The text content to be wrapped; null or empty strings return empty list
     * @param font The PDFont to use for width calculations
     * @param fontSize The font size in points for width calculations
     * @param maxWidth The maximum width in points that each line should not exceed
     * @return List of text lines that fit within the specified width constraints
     * @throws IOException if an error occurs during font metric calculations
     */
    public static List<String> wrapText(String text, PDFont font, float fontSize, float maxWidth) throws IOException {
        List<String> lines = new ArrayList<>();
        String[] words = new String[0];
        if (text != null && text.length() > 0) {
            words = text.split(" ");
        }
        StringBuilder currentLine = new StringBuilder();

        for (String word : words) {
            String lineWithWord = currentLine.length() == 0 ? word : currentLine + " " + word;
            float size = font.getStringWidth(lineWithWord) / 1000 * fontSize;
            if (size <= maxWidth) {
                currentLine.append(currentLine.length() == 0 ? word : " " + word);
            } else {
                lines.add(currentLine.toString());
                currentLine = new StringBuilder(word);
            }
        }

        if (currentLine.length() > 0) {
            lines.add(currentLine.toString());
        }

        return lines;
    }
}