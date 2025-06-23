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
 * <p><strong>License Information:</strong></p>
 * <p>This software is licensed under the PolyForm Noncommercial License 1.0.0.
 * Copyright (C) 2025 The Regents of the University of Michigan - Rogel Cancer Center.</p>
 * 
 * @author Elicit Software
 * @version 1.0
 * @since 1.0
 * @see Content
 * @see PDFDocument
 * @see PDFService
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Style {
    public String alignment;
    public Boolean bold;
    public String color;
    public Integer fontSize;
    public Integer[] margin;
}
