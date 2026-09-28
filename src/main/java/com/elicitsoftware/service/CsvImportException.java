package com.elicitsoftware.service;

/*-
 * ***LICENSE_START***
 * Elicit Survey
 * %%
 * Copyright (C) 2025 - 2026 The Regents of the University of Michigan - Rogel Cancer Center
 * %%
 * PolyForm Noncommercial License 1.0.0
 * <https://polyformproject.org/licenses/noncommercial/1.0.0>
 * ***LICENSE_END***
 */

import java.util.List;

/**
 * Raised by {@link CsvImportService#importSubjects} when one or more rows of the uploaded file
 * could not be processed (UC-003 A6).
 * <p>
 * The per-line messages are carried as a list as well as joined into {@link #getMessage()}, so a
 * caller can lay them out as structure. {@code RegisterView} renders one list item per line;
 * {@code AccessCodeService}, which answers in JSON, keeps using the joined message, which is
 * byte-identical to what a plain {@code Exception} carried before.
 */
public class CsvImportException extends Exception {

    private static final long serialVersionUID = 1L;

    /** The message that introduces the per-line failures, and the first line of {@code getMessage()}. */
    public static final String HEADLINE = "Import completed with errors:";

    private final List<String> lineErrors;

    /**
     * @param lineErrors one message per rejected line, each already naming its line number
     */
    public CsvImportException(List<String> lineErrors) {
        super(HEADLINE + "\n" + String.join("\n", lineErrors));
        this.lineErrors = List.copyOf(lineErrors);
    }

    /**
     * Returns the per-line failures, one per rejected row, in the order they were read.
     *
     * @return an unmodifiable list of messages, each naming the line it came from
     */
    public List<String> getLineErrors() {
        return lineErrors;
    }
}
