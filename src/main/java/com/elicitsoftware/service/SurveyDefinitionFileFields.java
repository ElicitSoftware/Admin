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

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Field-parsing and ID-resolution helpers shared by {@link SurveyDefinitionImportService} and
 * {@link SurveyDefinitionUpdateService} — both consume the same {@code ELICIT_SURVEY_EXPORT_V1}
 * pipe-delimited line format (see {@link SurveyDefinitionExportService}), they just differ in
 * what they do with each parsed row (always-create vs. match-and-version).
 */
final class SurveyDefinitionFileFields {

    private SurveyDefinitionFileFields() {
    }

    /**
     * Header line carrying the file's revision identifier — the exporting system's timestamp at
     * the moment the file was written (see {@link SurveyDefinitionExportService}).
     */
    static final String REVISION_HEADER = "# survey_revision:";

    /**
     * Extracts the revision from a header line, or returns {@code null} if the line is not the
     * revision header.
     * <p>
     * A file written before this header existed simply has no such line, and every consumer
     * treats a {@code null} revision as "unknown" rather than an error — the header is additive
     * to {@code ELICIT_SURVEY_EXPORT_V1}, not a new format version, since both parsers already
     * skip every {@code #} line they don't recognise.
     *
     * @param line one trimmed line from the file
     * @return the parsed revision, or {@code null} if this line is not the revision header
     * @throws IllegalArgumentException if the line IS the revision header but its value is not a
     *     parseable ISO-8601 offset date-time — a corrupt revision is not silently downgraded to
     *     "unknown", because that would quietly disable the regression check in
     *     {@link SurveyDefinitionUpdateService}
     */
    static OffsetDateTime parseRevisionHeader(String line) {
        if (!line.startsWith(REVISION_HEADER)) {
            return null;
        }
        String value = line.substring(REVISION_HEADER.length()).trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("File carries an empty \"" + REVISION_HEADER + "\" header");
        }
        try {
            return OffsetDateTime.parse(value);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(
                    "Unparseable \"" + REVISION_HEADER + "\" header value: " + value, e);
        }
    }

    /**
     * Parses one data line's pipe-delimited fields with escape sequence handling
     * ({@code \|}, {@code \\}, {@code \n}, {@code \r}).
     */
    static String[] parseFields(String data) {
        List<String> fieldList = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean escaped = false;

        for (int i = 0; i < data.length(); i++) {
            char c = data.charAt(i);
            if (escaped) {
                switch (c) {
                    case '|' -> current.append('|');
                    case '\\' -> current.append('\\');
                    case 'n' -> current.append('\n');
                    case 'r' -> current.append('\r');
                    default -> { current.append('\\'); current.append(c); }
                }
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == '|') {
                fieldList.add(current.toString());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }
        fieldList.add(current.toString());
        return fieldList.toArray(new String[0]);
    }

    /**
     * Converts empty input fields to {@code null} for nullable DB columns.
     */
    static String nullIfEmpty(String value) {
        return (value == null || value.isEmpty()) ? null : value;
    }

    /**
     * Rebases a display key's leading survey component onto the survey id this deployment
     * actually allocated.
     * <p>
     * A display key is {@code survey-step-stepInstance-section-sectionInstance-question-questionInstance}
     * (see {@code com.elicitsoftware.DisplayKey} in the Survey module), and its first component is
     * load-bearing at runtime: the Survey engine parses it back out and binds it as the
     * {@code surveyId} query parameter when resolving steps, sections and relationships. The
     * exporting deployment writes <em>its own</em> survey id into that position, but the importing
     * deployment allocates a fresh id from {@code survey.surveys_seq}. Carried over verbatim, every
     * key would point at whatever survey happens to hold the source id here — usually nothing at
     * all — and the imported survey would not navigate.
     * <p>
     * This is why the same authored file can be handed to several deployments: it lands on a
     * different id at each site, and each site rewrites the keys to match. Only the first component
     * is touched; step/section/question positions are properties of the definition, not of the
     * deployment, and are preserved exactly.
     *
     * @param displayKey the key as written in the file, possibly {@code null} or empty
     * @param surveyId the survey id allocated by this deployment
     * @return the key with its survey component replaced, or {@code null} if there was no key
     */
    static String rebaseDisplayKey(String displayKey, Number surveyId) {
        String value = nullIfEmpty(displayKey);
        if (value == null || surveyId == null) {
            return value;
        }
        int firstSeparator = value.indexOf('-');
        if (firstSeparator < 0) {
            // Not a structured display key. Leave it exactly as the file had it rather than
            // guess at a format this code does not recognise.
            return value;
        }
        return String.format("%04d", surveyId.longValue()) + value.substring(firstSeparator);
    }

    /**
     * Parses an integer field, returning {@code null} for empty or invalid input.
     */
    static Integer parseIntOrNull(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Parses a long field, returning {@code null} for empty or invalid input.
     */
    static Long parseLongOrNull(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Parses a boolean field with support for PostgreSQL-style {@code t} values.
     * Empty input defaults to {@code false}.
     */
    static boolean parseBooleanOrFalse(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        return Boolean.parseBoolean(value) || "t".equalsIgnoreCase(value);
    }

    /**
     * Resolves a required (NOT NULL) FK reference. Throws if not found in map.
     */
    static Long resolveRequired(Long oldId, Map<Long, Long> map, String entityDesc) {
        if (oldId == null) {
            throw new IllegalStateException("Expected non-null source id for " + entityDesc);
        }
        Long newId = map.get(oldId);
        if (newId == null) {
            throw new IllegalStateException("No ID mapping found for " + entityDesc + " source_id=" + oldId);
        }
        return newId;
    }

    /**
     * Resolves a nullable FK reference. Returns null if oldId is null; throws if oldId is
     * non-null but has no mapping in the map.
     */
    static Long resolveNullable(Long oldId, Map<Long, Long> map, String entityDesc) {
        if (oldId == null) {
            return null;
        }
        Long newId = map.get(oldId);
        if (newId == null) {
            throw new IllegalStateException("No ID mapping found for " + entityDesc + " source_id=" + oldId);
        }
        return newId;
    }

    /**
     * Reads back the durable Kimball Type 2 id a table's own {@code DEFAULT nextval(...)}
     * assigned to the row just inserted, keyed by that row's surrogate {@code id}.
     */
    static Long getDurableId(EntityManager em, String durableColumn, String table, Long surrogateId) {
        Query query = em.createNativeQuery(
                "SELECT " + durableColumn + " FROM " + table + " WHERE id = ?1");
        query.setParameter(1, surrogateId);
        return ((Number) query.getSingleResult()).longValue();
    }

    /**
     * Executes a scalar query expected to return one numeric ID.
     *
     * @return numeric result as {@link Long}, or {@code null} when no result/invalid type
     */
    static Long getLongResult(Query query) {
        try {
            Object result = query.getSingleResult();
            if (result instanceof Number n) {
                return n.longValue();
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }
}
