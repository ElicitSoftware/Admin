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


import com.elicitsoftware.admin.i18n.Translations;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Raised when a CSV upload has a row that names no survey (UC-003 BR-175, UC-010 BR-178). The
 * whole file is rejected and nothing is imported; the message is the warning the uploader reads,
 * naming the lines without a survey.
 */
public class CsvSurveyMissingException extends Exception {

    private static final long serialVersionUID = 1L;

    private final List<Integer> lines;

    /**
     * Creates the exception with the uploader's warning naming the given lines.
     *
     * @param lines the line numbers of the rows that name no survey
     */
    public CsvSurveyMissingException(List<Integer> lines) {
        super(Translations.get("csvImport.surveyMissing",
                lines.stream().map(String::valueOf).collect(Collectors.joining(", "))));
        this.lines = List.copyOf(lines);
    }

    /**
     * Returns the line numbers of the rows that name no survey.
     *
     * @return the line numbers of the rows that name no survey
     */
    public List<Integer> getLines() {
        return lines;
    }
}
