package com.elicitsoftware.report.pdf;

/*-
 * ***LICENSE_START***
 * Elicit FHHS
 * %%
 * Copyright (C) 2025 The Regents of the University of Michigan - Rogel Cancer Center
 * %%
 * PolyForm Noncommercial License 1.0.0
 * <https://polyformproject.org/licenses/noncommercial/1.0.0>
 * ***LICENSE_END***
 */

/**
 * Table represents a simple table structure for content definition in PDF generation.
 * <p>
 * This is a simplified table model used primarily for content definition within
 * the {@link Content} class. It provides a basic structure for tabular data with:
 * - Column headers as a string array
 * - Column widths as a float array
 * - Table body content as a 2D string array
 * <p>
 * This class differs from the more complex {@link com.elicitsoftware.report.pdfbox.Table}
 * in that it focuses on data structure rather than rendering configuration. It serves
 * as a simple data transfer object for table content that will be processed by
 * PDF generation services.
 * <p>
 * The arrays should be coordinated such that:
 * - headers.length == widths.length (same number of columns)
 * - body[n].length == headers.length (each row has same number of columns)
 * <p>
 * Usage example:
 * <pre>
 * {@code
 * Table table = new Table();
 * table.headers = new String[]{"Name", "Age", "Email"};
 * table.widths = new float[]{150f, 50f, 200f};
 * table.body = new String[][]{
 *     {"John Doe", "30", "john@example.com"},
 *     {"Jane Smith", "25", "jane@example.com"}
 * };
 * }
 * </pre>
 *
 * @see Content
 * @see com.elicitsoftware.report.pdfbox.Table
 * @since 1.0.0
 */
public class Table {

    /**
     * Array of column header names.
     * <p>
     * Each element represents the header text for the corresponding column.
     * The length of this array determines the number of columns in the table.
     */
    public String[] headers;

    /**
     * Array of column widths in PDF coordinate points.
     * <p>
     * Each element specifies the width for the corresponding column.
     * Should have the same length as the headers array.
     */
    public float[] widths;

    /**
     * 2D array containing the table body content.
     * <p>
     * Each sub-array represents a table row, and each element within
     * a sub-array represents a cell value. All rows should have the
     * same number of columns as defined by the headers array.
     */
    public String[][] body;

    /**
     * Default constructor for object instantiation.
     * <p>
     * Creates a new Table instance with uninitialized fields.
     * Fields should be populated after construction to define
     * the table structure and content.
     */
    public Table() {
        // Default constructor
    }
}
