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

import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;

import java.util.List;

/**
 * TableBuilder provides a builder pattern implementation for constructing Table objects.
 * <p>
 * This builder class allows for fluent, readable configuration of Table objects with
 * all their various properties including dimensions, content, formatting, and layout
 * parameters. It follows the standard builder pattern where each setter method returns
 * the builder instance for method chaining.
 * <p>
 * The builder ensures that Table objects are constructed in a consistent and
 * controlled manner, making it easier to create complex table configurations
 * without dealing with multiple constructor parameters or setter calls.
 * <p>
 * Key features:
 * - Fluent method chaining for readable configuration
 * - Type-safe property setting
 * - Consistent Table object construction
 * - Support for all Table properties
 * <p>
 * Usage example:
 * <pre>
 * {@code
 * Table table = new TableBuilder()
 *     .setHeight(600f)
 *     .setNumberOfRows(10)
 *     .setRowHeight(20f)
 *     .setColumns(columnList)
 *     .setContent(dataArray)
 *     .setCellMargin(2f)
 *     .setTextFont(font)
 *     .setFontSize(10f)
 *     .setMargin(40f)
 *     .setPageSize(PDRectangle.LETTER)
 *     .setLandscape(false)
 *     .build();
 * }
 * </pre>
 * 
 * @see Table
 * @see Column
 * @since 1.0.0
 */
public class TableBuilder {

    /**
     * The Table instance being constructed by this builder.
     */
    private final Table table = new Table();

    /**
     * Sets the available height for table content.
     * <p>
     * This defines the vertical space available for the table on each page,
     * which is used for pagination calculations.
     *
     * @param height The table height in PDF coordinate points
     * @return This TableBuilder instance for method chaining
     */
    public TableBuilder setHeight(float height) {
        table.setHeight(height);
        return this;
    }

    /**
     * Sets the number of rows in the table.
     * <p>
     * This method defines how many rows the table will have. The actual number of
     * rows displayed may vary depending on the content and other settings.
     *
     * @param numberOfRows The desired number of rows
     * @return This TableBuilder instance for method chaining
     */
    public TableBuilder setNumberOfRows(Integer numberOfRows) {
        table.setNumberOfRows(numberOfRows);
        return this;
    }

    /**
     * Sets the height of each row in the table.
     * <p>
     * This defines the vertical space allocated for each table row,
     * affecting both content display and pagination calculations.
     *
     * @param rowHeight The row height in PDF coordinate points
     * @return This TableBuilder instance for method chaining
     */
    public TableBuilder setRowHeight(float rowHeight) {
        table.setRowHeight(rowHeight);
        return this;
    }

    /**
     * Sets the content of the table.
     * <p>
     * This method defines the actual data to be displayed in the table cells.
     * The content is expected to be an array of strings, where each sub-array
     * represents a row in the table.
     *
     * @param content A 2D array of strings representing the table content
     * @return This TableBuilder instance for method chaining
     */
    public TableBuilder setContent(String[][] content) {
        table.setContent(content);
        return this;
    }

    /**
     * Sets the columns configuration for the table.
     * <p>
     * This method allows defining the properties of the table columns, such as
     * width, alignment, and formatting. The columns are defined using a list of
     * Column objects.
     *
     * @param columns A list of Column objects defining the table columns
     * @return This TableBuilder instance for method chaining
     */
    public TableBuilder setColumns(List<Column> columns) {
        table.setColumns(columns);
        return this;
    }

    /**
     * Sets the margin between the cell content and the cell border.
     * <p>
     * This margin is applied inside each table cell, providing spacing between
     * the cell's content and its borders.
     *
     * @param cellMargin The cell margin in PDF coordinate points
     * @return This TableBuilder instance for method chaining
     */
    public TableBuilder setCellMargin(float cellMargin) {
        table.setCellMargin(cellMargin);
        return this;
    }

    /**
     * Sets the margin around the table.
     * <p>
     * This margin is applied outside the table, providing space between the
     * table and the page edges or other elements.
     *
     * @param margin The table margin in PDF coordinate points
     * @return This TableBuilder instance for method chaining
     */
    public TableBuilder setMargin(float margin) {
        table.setMargin(margin);
        return this;
    }

    /**
     * Sets the page size for the table.
     * <p>
     * This method defines the size of the page on which the table will be drawn.
     * It is used to calculate the table's position and scaling on the page.
     *
     * @param pageSize The page size as a PDRectangle object
     * @return This TableBuilder instance for method chaining
     */
    public TableBuilder setPageSize(PDRectangle pageSize) {
        table.setPageSize(pageSize);
        return this;
    }

    /**
     * Sets the table orientation to landscape or portrait.
     * <p>
     * This setting determines the page orientation for the table. Landscape
     * orientation means the table will be wider than it is tall, and portrait
     * means it will be taller than it is wide.
     *
     * @param landscape True for landscape orientation, false for portrait
     * @return This TableBuilder instance for method chaining
     */
    public TableBuilder setLandscape(boolean landscape) {
        table.setLandscape(landscape);
        return this;
    }

    /**
     * Sets the font used for the table text.
     * <p>
     * This method defines the font that will be applied to the text in the table
     * cells. The font should be a valid PDFont object.
     *
     * @param textFont The font to be used for table text
     * @return This TableBuilder instance for method chaining
     */
    public TableBuilder setTextFont(PDFont textFont) {
        table.setTextFont(textFont);
        return this;
    }

    /**
     * Sets the base font size for the table.
     * <p>
     * This method defines the default size of the font used in the table cells.
     * The size is specified in PDF coordinate points.
     *
     * @param fontSize The font size in PDF coordinate points
     * @return This TableBuilder instance for method chaining
     */
    public TableBuilder setFontSize(float fontSize) {
        table.setFontSize(fontSize);
        return this;
    }

    /**
     * Builds and returns the configured Table object.
     * <p>
     * This method creates a Table object based on the current configuration of
     * this builder. Once built, the Table object can be used for rendering
     * in a PDF document.
     *
     * @return The constructed Table object
     */
    public Table build() {
        return table;
    }
}
