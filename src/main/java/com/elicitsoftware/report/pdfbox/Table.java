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
 * Table represents a complete table structure for PDF rendering with comprehensive formatting options.
 * <p>
 * This class encapsulates all the properties needed to define and render a table in a PDF document:
 * - Layout properties (margins, height, page size, orientation)
 * - Row and column structure (row height, column definitions)
 * - Font and text formatting (font type, size)
 * - Content data (table content as 2D array)
 * - Cell formatting (cell margins)
 * <p>
 * The Table class is typically used in conjunction with {@link TableBuilder} for construction
 * and {@link PDFTableGenerator} for PDF rendering. It supports both portrait and landscape
 * orientations and provides automatic width calculations for layout planning.
 * <p>
 * Key features:
 * - Flexible column definitions with custom widths
 * - Configurable page layout and orientation
 * - Font and cell formatting options
 * - Automatic width and column count calculations
 * - Support for multi-page content
 * <p>
 * Usage example:
 * <pre>
 * {@code
 * Table table = new TableBuilder()
 *     .setColumns(columnList)
 *     .setContent(dataArray)
 *     .setRowHeight(20f)
 *     .setHeight(600f)
 *     .build();
 * }
 * </pre>
 * 
 * @see TableBuilder
 * @see PDFTableGenerator
 * @see Column
 * @since 1.0.0
 */
public class Table {

    /**
     * Table margin from page edges in points.
     */
    private float margin;
    
    /**
     * Available height for table content in points.
     */
    private float height;
    
    /**
     * Page size definition for table layout.
     */
    private PDRectangle pageSize;
    
    /**
     * Whether the table should be rendered in landscape orientation.
     */
    private boolean isLandscape;
    
    /**
     * Height of each table row in points.
     */
    private float rowHeight;

    /**
     * Font used for table text content.
     */
    private PDFont textFont;
    
    /**
     * Font size for table text in points.
     */
    private float fontSize;

    /**
     * Total number of rows in the table.
     */
    private Integer numberOfRows;
    
    /**
     * List of column definitions for the table.
     */
    private List<Column> columns;
    
    /**
     * 2D array containing the table content data.
     */
    private String[][] content;
    
    /**
     * Margin within each table cell in points.
     */
    private float cellMargin;

    /**
     * Constructs a new Table with default values.
     * <p>
     * Creates an empty table that should be configured using setter methods
     * or preferably through the {@link TableBuilder} pattern.
     */
    public Table() {
    }

    /**
     * Returns the number of columns in this table.
     * <p>
     * This is a convenience method that returns the size of the columns list.
     * Returns null if columns list is null or not initialized.
     *
     * @return The number of columns as an Integer, or null if columns not set
     */
    public Integer getNumberOfColumns() {
        return this.getColumns().size();
    }

    /**
     * Calculates and returns the total width of the table based on its columns.
     * <p>
     * This method sums up the width of all columns to determine the
     * overall width of the table. It does not include margins or padding.
     *
     * @return The total width of the table in points
     */
    public float getWidth() {
        float tableWidth = 0f;
        for (Column column : columns) {
            tableWidth += column.getWidth();
        }
        return tableWidth;
    }

    /**
     * Returns the margin of the table.
     *
     * @return the margin
     */
    public float getMargin() {
        return margin;
    }

    /**
     * Sets the margin of the table.
     *
     * @param margin the margin to set
     */
    public void setMargin(float margin) {
        this.margin = margin;
    }

    /**
     * Returns the page size for the table.
     *
     * @return the pageSize
     */
    public PDRectangle getPageSize() {
        return pageSize;
    }

    /**
     * Sets the page size for the table.
     *
     * @param pageSize the pageSize to set
     */
    public void setPageSize(PDRectangle pageSize) {
        this.pageSize = pageSize;
    }

    /**
     * Returns the font used for the table text.
     *
     * @return the textFont
     */
    public PDFont getTextFont() {
        return textFont;
    }

    /**
     * Sets the font used for the table text.
     *
     * @param textFont the textFont to set
     */
    public void setTextFont(PDFont textFont) {
        this.textFont = textFont;
    }

    /**
     * Returns the font size for the table text.
     *
     * @return the fontSize
     */
    public float getFontSize() {
        return fontSize;
    }

    /**
     * Sets the font size for the table text.
     *
     * @param fontSize the fontSize to set
     */
    public void setFontSize(float fontSize) {
        this.fontSize = fontSize;
    }

    /**
     * Returns an array of column names in String format.
     * <p>
     * This method extracts the names of the columns from the column definitions
     * and returns them as an array of Strings. The array length matches the
     * number of columns in the table.
     *
     * @return An array of column names as Strings
     */
    public String[] getColumnsNamesAsArray() {
        String[] columnNames = new String[getNumberOfColumns()];
        for (int i = 0; i < getNumberOfColumns(); i++) {
            columnNames[i] = columns.get(i).getName();
        }
        return columnNames;
    }

    /**
     * Returns the list of columns defined for this table.
     *
     * @return the columns
     */
    public List<Column> getColumns() {
        return columns;
    }

    /**
     * Sets the list of columns for this table.
     *
     * @param columns the columns to set
     */
    public void setColumns(List<Column> columns) {
        this.columns = columns;
    }

    /**
     * Returns the total number of rows in the table.
     *
     * @return the numberOfRows
     */
    public Integer getNumberOfRows() {
        return numberOfRows;
    }

    /**
     * Sets the total number of rows in the table.
     *
     * @param numberOfRows the numberOfRows to set
     */
    public void setNumberOfRows(Integer numberOfRows) {
        this.numberOfRows = numberOfRows;
    }

    /**
     * Returns the height available for the table content.
     *
     * @return the height
     */
    public float getHeight() {
        return height;
    }

    /**
     * Sets the height available for the table content.
     *
     * @param height the height to set
     */
    public void setHeight(float height) {
        this.height = height;
    }

    /**
     * Returns the height of each row in the table.
     *
     * @return the rowHeight
     */
    public float getRowHeight() {
        return rowHeight;
    }

    /**
     * Sets the height of each row in the table.
     *
     * @param rowHeight the rowHeight to set
     */
    public void setRowHeight(float rowHeight) {
        this.rowHeight = rowHeight;
    }

    /**
     * Returns the 2D array containing the table content data.
     *
     * @return the content
     */
    public String[][] getContent() {
        return content;
    }

    /**
     * Sets the 2D array containing the table content data.
     *
     * @param content the content to set
     */
    public void setContent(String[][] content) {
        this.content = content;
    }

    /**
     * Returns the margin within each table cell.
     *
     * @return the cellMargin
     */
    public float getCellMargin() {
        return cellMargin;
    }

    /**
     * Sets the margin within each table cell.
     *
     * @param cellMargin the cellMargin to set
     */
    public void setCellMargin(float cellMargin) {
        this.cellMargin = cellMargin;
    }

    /**
     * Returns whether the table is to be rendered in landscape orientation.
     *
     * @return true if landscape, false if portrait
     */
    public boolean isLandscape() {
        return isLandscape;
    }

    /**
     * Sets the orientation of the table rendering.
     *
     * @param isLandscape true for landscape orientation, false for portrait
     */
    public void setLandscape(boolean isLandscape) {
        this.isLandscape = isLandscape;
    }
}
