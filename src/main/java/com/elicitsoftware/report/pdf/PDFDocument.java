package com.elicitsoftware.report.pdf;

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

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

/**
 * Represents the complete structure and configuration of a PDF document in the Elicit reporting system.
 * 
 * <p>This class serves as the primary data structure for PDF generation, encapsulating all
 * necessary information to create a formatted PDF report. It organizes document content
 * into structured elements and provides styling definitions for consistent presentation
 * of survey data and analytics.</p>
 * 
 * <p><strong>Document Structure:</strong></p>
 * <ul>
 *   <li><strong>Content Array:</strong> Ordered sequence of content elements (text, tables, charts)</li>
 *   <li><strong>Style Definitions:</strong> Named style configurations for consistent formatting</li>
 *   <li><strong>Page Configuration:</strong> Layout settings including orientation and breaks</li>
 *   <li><strong>Document Metadata:</strong> Title and identification information</li>
 * </ul>
 * 
 * <p><strong>PDF Generation Workflow:</strong></p>
 * <ol>
 *   <li>Create PDFDocument instance with content and styles</li>
 *   <li>Configure page layout and orientation settings</li>
 *   <li>Pass document to {@link PDFService} for rendering</li>
 *   <li>Receive generated PDF byte array for delivery</li>
 * </ol>
 * 
 * <p><strong>Content Organization:</strong></p>
 * <p>Content elements are processed sequentially, allowing for complex document layouts
 * with mixed content types. Each content element can reference styles by name, enabling
 * consistent formatting across the document.</p>
 * 
 * <p><strong>Usage Examples:</strong></p>
 * <pre>{@code
 * // Create document for survey report
 * PDFDocument document = new PDFDocument();
 * document.title = "Survey Results Report - Q1 2025";
 * document.landscape = false;
 * 
 * // Add content elements
 * Content[] content = new Content[3];
 * content[0] = new Content();
 * content[0].text = "Survey Results Summary";
 * content[0].style = "heading1";
 * 
 * content[1] = new Content(surveyDataTable);
 * content[1].style = "data-table";
 * 
 * content[2] = new Content();
 * content[2].svg = generateChartSVG(surveyData);
 * content[2].style = "chart";
 * 
 * document.content = content;
 * 
 * // Define styles
 * Map<String, Style> styles = new HashMap<>();
 * styles.put("heading1", createHeadingStyle());
 * styles.put("data-table", createTableStyle());
 * styles.put("chart", createChartStyle());
 * document.styles = styles;
 * }</pre>
 * 
 * <p><strong>JSON Serialization:</strong></p>
 * <p>This class is optimized for JSON serialization, making it suitable for web API
 * interactions and template-based PDF generation workflows. The
 * {@code @JsonInclude(JsonInclude.Include.NON_NULL)} annotation ensures clean
 * serialization by excluding null fields.</p>
 * 
 * <p><strong>License Information:</strong></p>
 * <p>This software is licensed under the PolyForm Noncommercial License 1.0.0.
 * Copyright (C) 2025 The Regents of the University of Michigan - Rogel Cancer Center.</p>
 * 
 * @author Elicit Software
 * @version 1.0
 * @since 1.0
 * @see Content
 * @see Style
 * @see PDFService
 * @see Table
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PDFDocument {
    
    /**
     * Array of content elements that comprise the PDF document.
     * 
     * <p>Contains an ordered sequence of {@link Content} objects that define the
     * document structure. Each element represents a distinct piece of content
     * (text, table, chart, etc.) and is rendered in the order specified in this array.</p>
     * 
     * <p><strong>Content Types:</strong></p>
     * <ul>
     *   <li><strong>Text Elements:</strong> Headings, paragraphs, captions</li>
     *   <li><strong>Table Elements:</strong> Survey data tables and summaries</li>
     *   <li><strong>Chart Elements:</strong> SVG-based visualizations and graphs</li>
     *   <li><strong>Layout Elements:</strong> Spacers, dividers, page breaks</li>
     * </ul>
     * 
     * @see Content
     */
    public Content[] content;
    
    /**
     * Flag indicating whether to insert a page break before this document section.
     * 
     * <p>When set to {@code true}, the PDF generator will insert a page break before
     * rendering this document content. This is useful for creating multi-section
     * reports where each section should start on a new page.</p>
     * 
     * <p><strong>Use Cases:</strong></p>
     * <ul>
     *   <li>Starting new report sections on fresh pages</li>
     *   <li>Separating different survey analysis components</li>
     *   <li>Creating executive summaries on separate pages</li>
     * </ul>
     */
    public boolean pageBreak = false;
    
    /**
     * Flag indicating whether the document should be rendered in landscape orientation.
     * 
     * <p>When set to {@code true}, the document is rendered in landscape mode,
     * which is particularly useful for wide tables, charts, or content that
     * benefits from horizontal space.</p>
     * 
     * <p><strong>Landscape Benefits:</strong></p>
     * <ul>
     *   <li><strong>Wide Tables:</strong> Survey data with many columns</li>
     *   <li><strong>Horizontal Charts:</strong> Timeline or bar charts</li>
     *   <li><strong>Spreadsheet-like Data:</strong> Detailed response matrices</li>
     * </ul>
     * 
     * <p><strong>Default:</strong> {@code false} (portrait orientation)</p>
     */
    public boolean landscape = false;
    
    /**
     * Title of the PDF document for metadata and display purposes.
     * 
     * <p>Provides a descriptive title for the PDF document that may be used
     * in document metadata, headers, or initial document display. This title
     * helps identify the document content and purpose.</p>
     * 
     * <p><strong>Usage Examples:</strong></p>
     * <ul>
     *   <li>"Patient Experience Survey Report - Q1 2025"</li>
     *   <li>"Department Performance Analysis - Cardiology"</li>
     *   <li>"Survey Response Summary - [Survey Name]"</li>
     * </ul>
     */
    public String title;
    
    /**
     * Map of style definitions keyed by style identifier names.
     * 
     * <p>Contains {@link Style} objects that define the visual formatting
     * properties for content elements. Content elements reference these styles
     * by name through their {@code style} field, enabling consistent formatting
     * across the document.</p>
     * 
     * <p><strong>Style Categories:</strong></p>
     * <ul>
     *   <li><strong>Typography:</strong> Font families, sizes, weights, colors</li>
     *   <li><strong>Layout:</strong> Margins, padding, alignment, spacing</li>
     *   <li><strong>Tables:</strong> Border styles, cell formatting, header styles</li>
     *   <li><strong>Charts:</strong> Color schemes, legend formatting, axis styles</li>
     * </ul>
     * 
     * <p><strong>Example Style Names:</strong></p>
     * <ul>
     *   <li>"heading1", "heading2", "body-text"</li>
     *   <li>"data-table", "summary-table", "header-row"</li>
     *   <li>"chart-title", "axis-label", "legend"</li>
     * </ul>
     * 
     * @see Style
     */
    public Map<String, Style> styles;
}
