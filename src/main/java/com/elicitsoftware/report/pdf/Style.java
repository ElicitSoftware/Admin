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
 * Defines visual styling properties for PDF content elements in the Elicit reporting system.
 *
 * <p>This class encapsulates the formatting characteristics that can be applied to
 * {@link Content} elements within a {@link PDFDocument}. It provides a comprehensive
 * set of styling options for controlling the appearance of text, tables, charts,
 * and other visual elements in generated PDF reports.</p>
 *
 * <p><strong>Styling Categories:</strong></p>
 * <ul>
 *   <li><strong>Typography:</strong> Font size, weight (bold), and text color</li>
 *   <li><strong>Layout:</strong> Text alignment and margin spacing</li>
 *   <li><strong>Spacing:</strong> Margin arrays for precise positioning</li>
 *   <li><strong>Visual Effects:</strong> Color schemes and emphasis styling</li>
 * </ul>
 *
 * <p><strong>CSS-Like Approach:</strong></p>
 * <p>This styling system follows CSS-like conventions for familiar and intuitive
 * formatting control. Properties are designed to be easily understood by developers
 * familiar with web styling standards.</p>
 *
 * <p><strong>Usage Examples:</strong></p>
 * <pre>{@code
 * // Heading style
 * Style headingStyle = new Style();
 * headingStyle.fontSize = 18;
 * headingStyle.bold = true;
 * headingStyle.color = "#2C3E50";
 * headingStyle.alignment = "center";
 * headingStyle.margin = new Integer[]{20, 0, 10, 0}; // top, right, bottom, left
 *
 * // Body text style
 * Style bodyStyle = new Style();
 * bodyStyle.fontSize = 12;
 * bodyStyle.color = "#333333";
 * bodyStyle.alignment = "left";
 * bodyStyle.margin = new Integer[]{5, 10, 5, 10};
 *
 * // Table header style
 * Style tableHeaderStyle = new Style();
 * tableHeaderStyle.fontSize = 14;
 * tableHeaderStyle.bold = true;
 * tableHeaderStyle.color = "#FFFFFF";
 * tableHeaderStyle.alignment = "center";
 * }</pre>
 *
 * <p><strong>JSON Serialization:</strong></p>
 * <p>The class is optimized for JSON serialization with {@code @JsonInclude(JsonInclude.Include.NON_NULL)}
 * to ensure compact JSON output by excluding null properties. This is particularly
 * useful when styles are transmitted over APIs or stored in configuration files.</p>
 *
 * <p><strong>Integration with Content:</strong></p>
 * <p>Style objects are referenced by name in {@link Content} elements through the
 * {@code style} field, enabling consistent application of formatting across
 * different content types within a document.</p>
 *
 * @author Elicit Software
 * @version 1.0
 * @since 1.0
 * @see Content
 * @see PDFDocument
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Style {

    /**
     * Text alignment for content elements.
     *
     * <p>Controls the horizontal positioning of text within its container.
     * This property follows standard CSS alignment conventions and affects
     * how text is positioned within table cells, paragraphs, and other
     * content blocks.</p>
     *
     * <p><strong>Supported Values:</strong></p>
     * <ul>
     *   <li><strong>"left":</strong> Align text to the left edge (default)</li>
     *   <li><strong>"center":</strong> Center text horizontally</li>
     *   <li><strong>"right":</strong> Align text to the right edge</li>
     *   <li><strong>"justify":</strong> Justify text to both edges (if supported)</li>
     * </ul>
     *
     * <p><strong>Usage:</strong></p>
     * <pre>{@code
     * Style headerStyle = new Style();
     * headerStyle.alignment = "center";  // Center-align headers
     *
     * Style bodyStyle = new Style();
     * bodyStyle.alignment = "left";      // Left-align body text
     * }</pre>
     */
    public String alignment;

    /**
     * Font weight control for text emphasis.
     *
     * <p>Determines whether text should be rendered in bold font weight.
     * This is a boolean property that enables or disables bold formatting
     * for the styled content element.</p>
     *
     * <p><strong>Values:</strong></p>
     * <ul>
     *   <li><strong>true:</strong> Render text in bold font weight</li>
     *   <li><strong>false:</strong> Render text in normal font weight</li>
     *   <li><strong>null:</strong> Use default font weight (typically normal)</li>
     * </ul>
     *
     * <p><strong>Usage:</strong></p>
     * <pre>{@code
     * Style headingStyle = new Style();
     * headingStyle.bold = true;   // Bold headings
     *
     * Style bodyStyle = new Style();
     * headingStyle.bold = false;  // Normal weight body text
     * }</pre>
     */
    public Boolean bold;

    /**
     * Text and foreground color specification.
     *
     * <p>Defines the color used for rendering text and other foreground elements.
     * Colors can be specified in various formats including hexadecimal, RGB,
     * or named color values following standard CSS color conventions.</p>
     *
     * <p><strong>Supported Color Formats:</strong></p>
     * <ul>
     *   <li><strong>Hexadecimal:</strong> "#FF0000", "#f00", "#FF0000FF" (with alpha)</li>
     *   <li><strong>RGB:</strong> "rgb(255, 0, 0)" or "rgba(255, 0, 0, 0.5)"</li>
     *   <li><strong>Named Colors:</strong> "red", "blue", "green", "black", "white"</li>
     *   <li><strong>System Colors:</strong> "transparent" for no color</li>
     * </ul>
     *
     * <p><strong>Common Use Cases:</strong></p>
     * <pre>{@code
     * Style errorStyle = new Style();
     * errorStyle.color = "#FF0000";     // Red for errors
     *
     * Style successStyle = new Style();
     * successStyle.color = "#008000";   // Green for success messages
     *
     * Style mutedStyle = new Style();
     * mutedStyle.color = "#6C757D";     // Gray for secondary text
     * }</pre>
     */
    public String color;

    /**
     * Font size in points for text rendering.
     *
     * <p>Specifies the size of the font used for rendering text content.
     * The value is expressed in points (pt), which is a standard unit
     * for typography in print media (1 point = 1/72 inch).</p>
     *
     * <p><strong>Typical Font Sizes:</strong></p>
     * <ul>
     *   <li><strong>8-9pt:</strong> Fine print, footnotes</li>
     *   <li><strong>10-12pt:</strong> Body text, normal content</li>
     *   <li><strong>14-16pt:</strong> Subheadings, emphasized text</li>
     *   <li><strong>18-24pt:</strong> Main headings</li>
     *   <li><strong>24pt+:</strong> Large titles, cover text</li>
     * </ul>
     *
     * <p><strong>Usage Examples:</strong></p>
     * <pre>{@code
     * Style titleStyle = new Style();
     * titleStyle.fontSize = 24;    // Large title
     *
     * Style bodyStyle = new Style();
     * bodyStyle.fontSize = 12;     // Standard body text
     *
     * Style footnoteStyle = new Style();
     * footnoteStyle.fontSize = 8;  // Small footnote text
     * }</pre>
     */
    public Integer fontSize;

    /**
     * Margin spacing around content elements.
     *
     * <p>Defines the whitespace around a content element using a four-value
     * array following the CSS box model convention. Margins create space
     * between the element and its surrounding content or container boundaries.</p>
     *
     * <p><strong>Array Structure:</strong></p>
     * <ul>
     *   <li><strong>Index 0:</strong> Top margin</li>
     *   <li><strong>Index 1:</strong> Right margin</li>
     *   <li><strong>Index 2:</strong> Bottom margin</li>
     *   <li><strong>Index 3:</strong> Left margin</li>
     * </ul>
     *
     * <p><strong>Units and Values:</strong></p>
     * <ul>
     *   <li><strong>Positive integers:</strong> Margin size in points</li>
     *   <li><strong>Zero:</strong> No margin on that side</li>
     *   <li><strong>Null array:</strong> Use default margins</li>
     * </ul>
     *
     * <p><strong>Usage Examples:</strong></p>
     * <pre>{@code
     * Style sectionStyle = new Style();
     * sectionStyle.margin = new Integer[]{20, 0, 10, 0};  // Top: 20pt, Bottom: 10pt
     *
     * Style paragraphStyle = new Style();
     * paragraphStyle.margin = new Integer[]{5, 5, 5, 5};  // 5pt margin all sides
     *
     * Style noMarginStyle = new Style();
     * noMarginStyle.margin = new Integer[]{0, 0, 0, 0};   // No margins
     * }</pre>
     *
     * <p><strong>CSS Analogy:</strong></p>
     * <p>This property is equivalent to CSS margin shorthand:
     * {@code margin: top right bottom left;}</p>
     */
    public Integer[] margin;

    /**
     * Default constructor.
     *
     * <p>Creates a new Style instance with all properties set to null,
     * allowing for selective assignment of styling properties. This
     * constructor enables flexible style creation where only specific
     * properties need to be defined.</p>
     *
     * <p><strong>Default State:</strong></p>
     * <ul>
     *   <li>All properties are null</li>
     *   <li>PDF rendering will use system/document defaults</li>
     *   <li>Only explicitly set properties will override defaults</li>
     * </ul>
     *
     * <p><strong>Usage Pattern:</strong></p>
     * <pre>{@code
     * Style customStyle = new Style();
     * customStyle.fontSize = 14;        // Set only what's needed
     * customStyle.bold = true;
     * // alignment, color, margin remain null (use defaults)
     * }</pre>
     */
    public Style() {
        // Default constructor - all properties start as null
    }
}
