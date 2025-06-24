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

/**
 * Represents an individual content element within a PDF document in the Elicit reporting system.
 *
 * <p>This class models the fundamental building blocks of PDF content, supporting various types
 * of elements including text, images (SVG), tables, and styled content. Content objects are
 * used to construct PDF documents through composition, allowing for flexible document layouts
 * and formatting.</p>
 *
 * <p><strong>Supported Content Types:</strong></p>
 * <ul>
 *   <li><strong>Text Content:</strong> Plain text with optional styling</li>
 *   <li><strong>SVG Graphics:</strong> Scalable vector graphics for charts and diagrams</li>
 *   <li><strong>Tables:</strong> Structured tabular data with formatting</li>
 *   <li><strong>Styled Elements:</strong> Content with applied CSS-like styling</li>
 * </ul>
 *
 * <p><strong>JSON Serialization:</strong></p>
 * <p>This class is designed for JSON serialization/deserialization as part of PDF generation
 * workflows. The {@code @JsonInclude(JsonInclude.Include.NON_NULL)} annotation ensures
 * that only non-null fields are included in the JSON output, keeping the serialized
 * content compact and clean.</p>
 *
 * <p><strong>PDF Generation Workflow:</strong></p>
 * <ol>
 *   <li>Content objects are created with specific content types (text, SVG, table)</li>
 *   <li>Style references are applied for formatting</li>
 *   <li>Content is assembled into PDFDocument structures</li>
 *   <li>PDF generation service processes the content hierarchy</li>
 *   <li>Final PDF is rendered with appropriate formatting</li>
 * </ol>
 *
 * <p><strong>Usage Examples:</strong></p>
 * <pre>{@code
 * // Text content with styling
 * Content textContent = new Content();
 * textContent.text = "Survey Results Summary";
 * textContent.style = "heading1";
 *
 * // SVG chart content
 * Content chartContent = new Content();
 * chartContent.svg = "<svg>...</svg>";
 * chartContent.style = "chart";
 *
 * // Table content
 * Table dataTable = new Table();
 * // ... configure table
 * Content tableContent = new Content(dataTable);
 * }</pre>
 *
 * <p><strong>Style Integration:</strong></p>
 * <p>The style field references style definitions that control formatting aspects such as
 * fonts, colors, spacing, and layout. Styles are typically defined in accompanying
 * {@link Style} objects and referenced by name.</p>
 *
 * <p><strong>License Information:</strong></p>
 * <p>This software is licensed under the PolyForm Noncommercial License 1.0.0.
 * Copyright (C) 2025 The Regents of the University of Michigan - Rogel Cancer Center.</p>
 *
 * @author Elicit Software
 * @version 1.0
 * @since 1.0
 * @see PDFDocument
 * @see Table
 * @see Style
 * @see com.elicitsoftware.report.PDFService
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Content {

    /**
     * Textual content to be rendered in the PDF.
     *
     * <p>This field contains plain text that will be rendered with the specified
     * style formatting. The text can include survey data, headings, descriptions,
     * or any other textual information needed in the report.</p>
     *
     * <p><strong>Usage Examples:</strong></p>
     * <ul>
     *   <li>Report titles and headings</li>
     *   <li>Survey response summaries</li>
     *   <li>Statistical descriptions</li>
     *   <li>Explanatory text and footnotes</li>
     * </ul>
     */
    public String text;

    /**
     * SVG (Scalable Vector Graphics) content for charts and diagrams.
     *
     * <p>Contains SVG markup that will be rendered as vector graphics in the PDF.
     * This is commonly used for displaying survey result charts, graphs, and
     * other visual representations of data.</p>
     *
     * <p><strong>Common Uses:</strong></p>
     * <ul>
     *   <li>Bar charts showing survey response distributions</li>
     *   <li>Pie charts for categorical data</li>
     *   <li>Line graphs for trend analysis</li>
     *   <li>Custom diagrams and illustrations</li>
     * </ul>
     *
     * <p><strong>Format:</strong> Complete SVG markup including the root {@code <svg>} element</p>
     */
    public String svg;

    /**
     * Table content for structured data presentation.
     *
     * <p>Contains a {@link Table} object that defines tabular data to be rendered
     * in the PDF. Tables are used for presenting survey results, statistics,
     * and other structured data in rows and columns.</p>
     *
     * <p><strong>Table Features:</strong></p>
     * <ul>
     *   <li>Multi-column data presentation</li>
     *   <li>Header and body row formatting</li>
     *   <li>Cell-level styling and alignment</li>
     *   <li>Responsive column width handling</li>
     * </ul>
     *
     * @see Table
     */
    public Table table;

    /**
     * Style reference for formatting this content element.
     *
     * <p>String identifier that references a style definition used to format
     * this content. The style controls visual aspects such as fonts, colors,
     * spacing, alignment, and other presentation properties.</p>
     *
     * <p><strong>Style Categories:</strong></p>
     * <ul>
     *   <li><strong>Text Styles:</strong> "heading1", "heading2", "body", "caption"</li>
     *   <li><strong>Chart Styles:</strong> "chart", "legend", "axis-label"</li>
     *   <li><strong>Table Styles:</strong> "data-table", "summary-table", "header-row"</li>
     *   <li><strong>Layout Styles:</strong> "section", "footer", "page-break"</li>
     * </ul>
     *
     * @see Style
     */
    public String style;

    /**
     * Default constructor for creating an empty Content object.
     *
     * <p>Creates a new Content instance with all fields initialized to null.
     * Fields can be set individually after construction based on the specific
     * content type needed.</p>
     */
    public Content() {
        super();
    }

    /**
     * Constructor for creating Content with a table.
     *
     * <p>Convenience constructor for creating Content objects that specifically
     * contain tabular data. This is commonly used when generating reports that
     * include data tables for survey results or statistical summaries.</p>
     *
     * <p><strong>Usage Example:</strong></p>
     * <pre>{@code
     * Table surveyResults = new Table();
     * // ... configure table with survey data
     * Content tableContent = new Content(surveyResults);
     * }</pre>
     *
     * @param table the Table object containing the structured data to display
     * @see Table
     */
    public Content(Table table) {
        super();
        this.table = table;
    }
}
