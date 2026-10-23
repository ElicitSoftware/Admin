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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UC-013: Export Survey Definition — the {@code ELICIT_SURVEY_EXPORT_V1} file
 * {@link SurveyDefinitionExportService} writes from the rows its native queries return, driven
 * against a {@link ScriptedEntityManager}.
 * <p>
 * Business rules covered: BR-044 (definition tables only), BR-045 (source identifiers written as
 * read), BR-060 (the survey key travels in the header and the record), BR-072 (every export is
 * stamped with a revision) and BR-073 (the rows the queries return, retired ones included, are all
 * written). The field escaping is checked against the parser the import uses, so what one side
 * writes the other reads back.
 */
class SurveyDefinitionExportServiceUnitTest {

    /** Each record type in file order, with its field count. */
    static final Map<String, Integer> COLUMNS = new LinkedHashMap<>();

    static {
        COLUMNS.put("select_groups", 10);
        COLUMNS.put("select_items", 11);
        COLUMNS.put("steps", 11);
        COLUMNS.put("sections", 11);
        COLUMNS.put("steps_sections", 12);
        COLUMNS.put("questions", 20);
        COLUMNS.put("sections_questions", 10);
        COLUMNS.put("relationships", 19);
        COLUMNS.put("reports", 6);
        COLUMNS.put("post_survey_actions", 6);
        COLUMNS.put("dimensions", 3);
        COLUMNS.put("ontology", 5);
        COLUMNS.put("metadata", 7);
        COLUMNS.put("report_items", 8);
        COLUMNS.put("translations", 13);
    }

    static final Object[] SURVEY = {5, "11111111-1111-1111-1111-111111111111", "Name|with\\odd", 2, "Title",
            null, "0005-0001-0000-0001-0000-0000-0000", "http://post", "pub", "cmt", "es", "en,es"};

    ScriptedEntityManager db;
    SurveyDefinitionExportService service;
    final Map<String, List<?>> rows = new HashMap<>();

    @BeforeEach
    void setUp() {
        db = new ScriptedEntityManager();
        rows.clear();
        rows.put("surveys", List.of((Object) SURVEY));
        db.answer = call -> {
            String sql = call.sql();
            int from = sql.indexOf("FROM survey.") + "FROM survey.".length();
            int end = from;
            while (end < sql.length() && (Character.isLetter(sql.charAt(end)) || sql.charAt(end) == '_')) {
                end++;
            }
            return rows.getOrDefault(sql.substring(from, end), List.of());
        };
        service = new SurveyDefinitionExportService();
        service.em = db.em;
    }

    private static Object[] row(String table, int n, int columns) {
        Object[] row = new Object[columns];
        for (int i = 0; i < columns; i++) {
            row[i] = table + n + "." + i;
        }
        return row;
    }

    private static String line(String table, Object[] row) {
        return table + ": " + Arrays.stream(row).map(String::valueOf).collect(Collectors.joining("|"));
    }

    @Test
    void given_aSurveyWithOneRecordOfEachKind_when_exported_then_everyRecordIsWrittenInDependencyOrder() {
        List<String> expected = new ArrayList<>();
        COLUMNS.forEach((table, columns) -> {
            Object[] row = row(table, 1, columns);
            rows.put(table, List.of((Object) row));
            expected.add(line(table, row));
        });
        Object[] secondStep = row("steps", 2, 11);
        rows.put("steps", List.of((Object) row("steps", 1, 11), (Object) secondStep));
        expected.add(expected.indexOf(line("steps", row("steps", 1, 11))) + 1, line("steps", secondStep));

        String out = service.exportSurvey(5);
        List<String> lines = Arrays.asList(out.split("\n", -1));

        int blank = lines.indexOf("");
        List<String> data = lines.subList(blank + 1, lines.size() - 1);
        assertEquals("surveys: 5|11111111-1111-1111-1111-111111111111|Name\\|with\\\\odd|2|Title|"
                + "|0005-0001-0000-0001-0000-0000-0000|http://post|pub|cmt|es|en,es", data.get(0));
        assertEquals(expected, data.subList(1, data.size()));
        assertEquals("", lines.get(lines.size() - 1), "the file ends with a newline");
        assertTrue(out.endsWith("translations1.12\n"));

        // Every query is scoped to the survey being exported.
        assertEquals(16, db.calls.size());
        db.calls.forEach(c -> assertEquals(5, c.param("surveyId"), c.sql()));
    }

    @Test
    void given_aSurvey_when_exported_then_theHeaderNamesItCountsEveryTableAndStampsARevision() {
        rows.put("steps", List.of((Object) row("steps", 1, 11), (Object) row("steps", 2, 11)));
        rows.put("translations", List.of((Object) row("translations", 1, 13)));
        OffsetDateTime before = OffsetDateTime.now();

        String out = service.exportSurvey(5);
        List<String> header = Arrays.stream(out.split("\n")).takeWhile(l -> !l.isEmpty()).toList();

        assertEquals("# ELICIT_SURVEY_EXPORT_V1", header.get(0));
        assertEquals("# survey_id: 5", header.get(1));
        assertEquals("# survey_key: 11111111-1111-1111-1111-111111111111", header.get(2));
        assertEquals("# survey_name: Name|with\\odd", header.get(3), "the name is written raw in a comment");
        assertEquals("# surveys: 1", header.get(4));
        List<String> counts = header.subList(5, 5 + COLUMNS.size());
        List<String> expectedCounts = new ArrayList<>();
        COLUMNS.keySet().forEach(t -> expectedCounts.add("# " + t + ": " + (t.equals("steps") ? 2 : t.equals("translations") ? 1 : 0)));
        assertEquals(expectedCounts, counts);
        String generated = header.get(5 + COLUMNS.size());
        String revisionLine = header.get(6 + COLUMNS.size());
        assertTrue(generated.startsWith("# generated: "), generated);
        OffsetDateTime revision = SurveyDefinitionFileFields.parseRevisionHeader(revisionLine);
        assertEquals(generated.substring("# generated: ".length()), revision.toString(),
                "the revision is the generation instant");
        assertTrue(!revision.isBefore(before) && !revision.isAfter(OffsetDateTime.now()));
        assertEquals(7 + COLUMNS.size(), header.size());
    }

    @Test
    void given_awkwardValues_when_exported_then_theImportParserReadsThemBack() {
        Object[] step = {41, "k", 1.5, "a|b", "c\\d", "e\nf\rg", 0, null, "9999-12-31 23:59:59+00", "", "\\n literal"};
        rows.put("steps", List.of((Object) step));

        String stepLine = Arrays.stream(service.exportSurvey(5).split("\n"))
                .filter(l -> l.startsWith("steps: ")).findFirst().orElseThrow();
        String[] parsed = SurveyDefinitionFileFields.parseFields(stepLine.substring("steps: ".length()));

        assertArrayEquals(new String[]{"41", "k", "1.5", "a|b", "c\\d", "e\nf\rg", "0", "", "9999-12-31 23:59:59+00", "",
                "\\n literal"}, parsed);
    }

    @Test
    void given_noSuchSurvey_when_exported_then_itIsRefused() {
        rows.put("surveys", List.of());
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> service.exportSurvey(9));
        assertEquals("Survey not found: 9", e.getMessage());
        assertEquals(1, db.calls.size());
    }

    @Test
    void given_aQueryReturningScalarRows_when_exported_then_theRowShapeIsRejected() {
        rows.put("metadata", List.of("not a row"));
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> service.exportSurvey(5));
        assertEquals("Unexpected row type for metadata: java.lang.String", e.getMessage());

        List<Object> nullRow = new ArrayList<>();
        nullRow.add(null);
        rows.put("metadata", List.of());
        rows.put("surveys", nullRow);
        IllegalStateException n = assertThrows(IllegalStateException.class, () -> service.exportSurvey(5));
        assertEquals("Unexpected row type for surveys: null", n.getMessage());
    }

    @Test
    void given_eachRecordQuery_when_exported_then_itNamesItsOwnTableInRowErrors() {
        for (String table : COLUMNS.keySet()) {
            setUp();
            rows.put(table, List.of(42));
            IllegalStateException e = assertThrows(IllegalStateException.class, () -> service.exportSurvey(5), table);
            assertEquals("Unexpected row type for " + table + ": java.lang.Integer", e.getMessage());
        }
    }
}
