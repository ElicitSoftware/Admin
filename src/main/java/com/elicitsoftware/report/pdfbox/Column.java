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

/**
 * Column represents a table column definition with name and width properties.
 * <p>
 * This class encapsulates the basic properties needed to define a column in a PDF table:
 * - Column name (header text)
 * - Column width in points
 * <p>
 * Column objects are used in conjunction with {@link Table} and {@link TableBuilder}
 * to define the structure of tables in PDF documents. The width is specified in
 * PDF coordinate points and determines how much horizontal space the column occupies.
 * <p>
 * The column name is typically used as the header text and for identification
 * purposes during table rendering. Column widths should be calculated to ensure
 * the total width of all columns fits within the available page width.
 * <p>
 * Usage example:
 * <pre>
 * {@code
 * Column nameColumn = new Column("Full Name", 150f);
 * Column ageColumn = new Column("Age", 50f);
 * Column emailColumn = new Column("Email", 200f);
 * 
 * List<Column> columns = Arrays.asList(nameColumn, ageColumn, emailColumn);
 * }
 * </pre>
 * 
 * @see Table
 * @see TableBuilder
 * @since 1.0.0
 */
public class Column {

    /**
     * The name/header text of this column.
     */
    private String name;
    
    /**
     * The width of this column in PDF coordinate points.
     */
    private float width;

    /**
     * Constructs a new Column with the specified name and width.
     * <p>
     * Creates a column definition that can be used in table construction.
     * The width should be specified in PDF coordinate points and should be
     * calculated to ensure proper table layout within page boundaries.
     *
     * @param name The column name/header text; should not be null
     * @param width The column width in PDF coordinate points; should be positive
     */
    public Column(String name, float width) {
        this.name = name;
        this.width = width;
    }

    /**
     * Returns the name/header text of this column.
     *
     * @return The column name as a String
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the name/header text of this column.
     *
     * @param name The new column name; should not be null
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Returns the width of this column in PDF coordinate points.
     *
     * @return The column width as a float value in points
     */
    public float getWidth() {
        return width;
    }

    /**
     * Sets the width of this column in PDF coordinate points.
     *
     * @param width The new column width in points; should be positive
     */
    public void setWidth(float width) {
        this.width = width;
    }
}
