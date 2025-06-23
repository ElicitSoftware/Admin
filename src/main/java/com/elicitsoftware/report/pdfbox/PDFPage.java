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

import org.apache.pdfbox.pdmodel.PDPage;

/**
 * PDFPage extends the standard PDFBox PDPage class to add cursor position tracking.
 * <p>
 * This enhanced page class provides additional functionality for tracking the current
 * Y position (cursor) on the page, which is useful for sequential content placement
 * and automatic page layout management.
 * <p>
 * The cursorY field maintains the current vertical position where the next content
 * should be placed, allowing for easier content flow management without manual
 * position calculation throughout the document generation process.
 * <p>
 * Key features:
 * - Extends standard PDPage functionality
 * - Tracks current Y position for content placement
 * - Simplifies sequential content positioning
 * - Maintains state for layout management
 * <p>
 * Usage example:
 * <pre>
 * {@code
 * PDFPage page = new PDFPage();
 * page.cursorY = 750f; // Start near top of page
 * // Add content and update cursorY as needed
 * page.cursorY -= lineHeight; // Move cursor down
 * }
 * </pre>
 * 
 * @see PDPage
 * @since 1.0.0
 */
public class PDFPage extends PDPage {
    
    /**
     * Current Y position cursor for content placement on this page.
     * <p>
     * This field tracks the vertical position where the next content element
     * should be placed. It starts at 0 and should be updated as content is
     * added to the page. The Y coordinate system in PDFBox has origin at
     * bottom-left, so higher values are toward the top of the page.
     */
    public float cursorY = 0;

    /**
     * Constructs a new PDFPage with default settings and cursor position at 0.
     * <p>
     * The page is initialized with standard PDPage properties and the cursor
     * position is set to 0. The cursor should be updated to an appropriate
     * starting position (typically near the top margin) before adding content.
     */
    public PDFPage() {
        super();
    }
}
