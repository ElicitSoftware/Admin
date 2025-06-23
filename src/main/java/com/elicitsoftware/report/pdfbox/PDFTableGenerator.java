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

import jakarta.enterprise.context.RequestScoped;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.util.Matrix;

import java.io.IOException;
import java.util.Arrays;

/**
 * PDFTableGenerator is a request-scoped service responsible for generating PDF table content.
 * <p>
 * This service provides comprehensive table generation capabilities for PDF documents including:
 * - Multi-page table rendering with automatic pagination
 * - Consistent column width and row height management
 * - Grid border drawing with proper line spacing
 * - Content positioning within table cells
 * - Page-by-page content distribution
 * <p>
 * The generator uses Apache PDFBox for low-level PDF operations and works with the
 * {@link Table} model to define table structure, content, and formatting properties.
 * It automatically handles pagination when table content exceeds available page space.
 * <p>
 * Key features:
 * - Automatic calculation of rows per page based on available height
 * - Consistent table grid rendering across multiple pages
 * - Proper content stream management for each page
 * - Precise positioning of table elements
 * <p>
 * Usage example:
 * <pre>
 * {@code
 * @Inject
 * PDFTableGenerator generator;
 * 
 * Table table = new TableBuilder()
 *     .setColumns(columns)
 *     .setContent(data)
 *     .build();
 * generator.generatePDF(document, table);
 * }
 * </pre>
 * 
 * @see Table
 * @see TableBuilder
 * @see PDDocument
 * @since 1.0.0
 */
@RequestScoped
public class PDFTableGenerator {

    /**
     * Generates a PDF table from the provided Table object and adds it to the document.
     * <p>
     * This method serves as the main entry point for table generation and delegates
     * the actual rendering to the drawTable method. It provides a clean interface
     * for integrating table generation into larger PDF creation workflows.
     *
     * @param document The PDDocument to add the table pages to
     * @param table The Table object containing structure, content, and formatting information
     * @throws IOException if an error occurs during PDF generation
     * @see #drawTable(PDDocument, Table)
     */
    public void generatePDF(PDDocument document, Table table) throws IOException {
        drawTable(document, table);
    }

    /**
     * Configures table pagination and draws the table content across multiple pages as needed.
     * <p>
     * This method handles the core table rendering logic by:
     * - Calculating the number of rows that fit on each page
     * - Determining the total number of pages required
     * - Creating content streams for each page
     * - Distributing table content across pages
     * - Drawing each page with proper formatting
     * <p>
     * The pagination calculation accounts for table height, row height, and available
     * page space. Each page maintains consistent table structure and formatting.
     * <p>
     * Process flow:
     * 1. Calculate rows per page based on available height
     * 2. Determine total pages needed for all content
     * 3. For each page: generate content stream, get page content, draw content
     * 4. Ensure proper grid rendering and content positioning
     *
     * @param document The PDDocument to add table pages to
     * @param table The Table object containing all table data and formatting
     * @throws IOException if an error occurs during content stream operations
     * @see #generateContentStream(PDDocument, Table)
     * @see #getContentForCurrentPage(Table, Integer, int)
     * @see #drawCurrentPage(PDDocument, Table, String[][], PDPageContentStream)
     */
    public void drawTable(PDDocument document, Table table) throws IOException {
        // Calculate pagination
        Integer rowsPerPage = (int) Math.floor(table.getHeight() / table.getRowHeight()) - 1; // subtract
        Integer numberOfPages = (int) Math.ceil(table.getNumberOfRows().floatValue() / rowsPerPage);

        // Generate each page, get the content and draw it
        for (int pageCount = 0; pageCount < numberOfPages; pageCount++) {
            //We will need this when we have a multipage table.
//            PDFPage page = generatePage(doc, table);
            PDPageContentStream contentStream = generateContentStream(document, table);
            String[][] currentPageContent = getContentForCurrentPage(table, rowsPerPage, pageCount);
            drawCurrentPage(document, table, currentPageContent, contentStream);
        }
    }

    // Draws current page table grid and borderlines and content
    private void drawCurrentPage(PDDocument document, Table table, String[][] currentPageContent, PDPageContentStream contentStream)
            throws IOException {
        PDPage page = document.getPage(document.getNumberOfPages() - 1);
        float tableTopY = table.isLandscape() ? table.getPageSize().getWidth() - table.getMargin() : table.getPageSize().getHeight() - table.getMargin();
//        float tableTopY = 0;
//        if (page.cursorY == 0) {
//            tableTopY = table.isLandscape() ? table.getPageSize().getWidth() - table.getMargin() : table.getPageSize().getHeight() - table.getMargin();
//        } else {
//            tableTopY = page.cursorY - table.getMargin();
//        }

        // Draws grid and borders
        drawTableGrid(table, currentPageContent, contentStream, tableTopY);

        // Position cursor to start drawing content
        float nextTextX = table.getMargin() + table.getCellMargin();
        // Calculate center alignment for text in cell considering font height
        float nextTextY = tableTopY - (table.getRowHeight() / 2)
                - ((table.getTextFont().getFontDescriptor().getFontBoundingBox().getHeight() / 1000 * table.getFontSize()) / 4);

        // Write column headers
        writeContentLine(table.getColumnsNamesAsArray(), contentStream, nextTextX, nextTextY, table);
        nextTextY -= table.getRowHeight();
        nextTextX = table.getMargin() + table.getCellMargin();

        // Write content
        for (int i = 0; i < currentPageContent.length; i++) {
            writeContentLine(currentPageContent[i], contentStream, nextTextX, nextTextY, table);
            nextTextY -= table.getRowHeight();
            nextTextX = table.getMargin() + table.getCellMargin();
        }

        contentStream.close();
//        page.cursorY = nextTextY;
    }

    // Writes the content for one line
    private void writeContentLine(String[] lineContent, PDPageContentStream contentStream, float nextTextX, float nextTextY,
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

    private void drawTableGrid(Table table, String[][] currentPageContent, PDPageContentStream contentStream, float tableTopY)
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

    private String[][] getContentForCurrentPage(Table table, Integer rowsPerPage, int pageCount) {
        int startRange = pageCount * rowsPerPage;
        int endRange = (pageCount * rowsPerPage) + rowsPerPage;
        if (endRange > table.getNumberOfRows()) {
            endRange = table.getNumberOfRows();
        }
        return Arrays.copyOfRange(table.getContent(), startRange, endRange);
    }

    // We will need this when we have a table larger that one page.
//    private PDFPage generatePage(PDDocument doc, Table table) {
//        PDFPage page = new PDFPage();
//        page.setMediaBox(table.getPageSize());
//        page.setRotation(table.isLandscape() ? 90 : 0);
//        doc.addPage(page);
//        return page;
//    }

    private PDPageContentStream generateContentStream(PDDocument document, Table table) throws IOException {

        PDPageContentStream contentStream = new PDPageContentStream(document, document.getPage(document.getNumberOfPages() - 1), PDPageContentStream.AppendMode.OVERWRITE, false);
        // User transformation matrix to change the reference when drawing.
        // This is necessary for the landscape position to draw correctly
        if (table.isLandscape()) {
            contentStream.transform(new Matrix(0, 1, -1, 0, table.getPageSize().getWidth(), 0));
        }
        contentStream.setFont(table.getTextFont(), table.getFontSize());
        return contentStream;
    }

}
