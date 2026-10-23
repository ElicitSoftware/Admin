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

import com.elicitsoftware.test.ScriptedEntityManager;
import com.elicitsoftware.test.ScriptedEntityManager.Call;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UC-011: Export Respondent Data — the {@code ELICIT_EXPORT_V2} text written for one respondent,
 * built from a scripted entity manager (no database).
 * <p>
 * Covers BR-038 (complete respondent graph), BR-039 (portable, self-describing format, read back
 * by UC-012), BR-040 (orphaned messages skipped), BR-058 (survey by stable key), BR-099 (elements
 * by key and version), BR-100 (department by code) and BR-101 (progress state, timestamps in the
 * database's time zone).
 */
class RespondentExportServiceUnitTest {
    /** Rows as a native query returns them: one {@code Object[]} per row. */
    private static List<Object> rowsOf(Object[]... rows) {
        return new java.util.ArrayList<>(Arrays.asList((Object[]) rows));
    }


    private static final String ZONE = "America/Detroit";
    private static final UUID SURVEY_KEY = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final Timestamp CREATED = Timestamp.from(Instant.parse("2026-01-02T03:04:05Z"));
    private static final String CREATED_LOCAL = "2026-01-01T22:04:05-05:00";

    private final Map<String, List<Object>> rows = new HashMap<>();

    private ScriptedEntityManager database() {
        return new ScriptedEntityManager(call -> {
            if (call.is("current_setting('TIMEZONE')")) {
                return ZONE;
            }
            for (Map.Entry<String, List<Object>> e : rows.entrySet()) {
                if (call.is(e.getKey())) {
                    return e.getValue();
                }
            }
            return List.of();
        });
    }

    private static Object[] respondent(Object finalized) {
        return new Object[]{5, SURVEY_KEY, "AC123", true, 3, CREATED, null, finalized, "Family|History", Instant.parse("2026-03-01T12:00:00Z")};
    }

    private String export(ScriptedEntityManager db, int id) {
        RespondentExportService service = new RespondentExportService();
        service.em = db.entityManager();
        return service.exportRespondent(id);
    }

    private static List<String> withoutGenerated(String text) {
        List<String> lines = new ArrayList<>(Arrays.asList(text.split("\n", -1)));
        lines.removeIf(l -> l.startsWith("# generated: "));
        return lines;
    }

    private void fullGraph() {
        rows.put("FROM survey.respondents r", rowsOf(respondent(OffsetDateTime.parse("2026-02-01T10:00:00+01:00"))));
        rows.put("FROM survey.answers a", rowsOf(new Object[]{1L, 1, 0, 2, 0, 3, 0,
                UUID.fromString("22222222-2222-2222-2222-222222222222"), 4,
                UUID.fromString("33333333-3333-3333-3333-333333333333"), 5,
                "0-1-2", "a|b\\c\nd\re", "val", false, CREATED, null, CREATED}));
        rows.put("FROM survey.dependents d", rowsOf(new Object[]{9L, "0-1-2", "0-1-3",
                UUID.fromString("44444444-4444-4444-4444-444444444444"), 1, false}));
        rows.put("FROM survey.subjects s", rowsOf(
                new Object[]{30L, "X1", "Jane", "Doe", null, java.sql.Date.valueOf("1980-02-03"), "j@x.org", "555", "DEPT", "Oncology", 4, CREATED},
                new Object[]{31L, "X2", "Ann", "Roe", "M", null, "a@x.org", null, "DEPT", "Oncology", 4, CREATED}));
        rows.put("FROM survey.messages m", rowsOf(
                new Object[]{40L, 31L, 1, "text/plain", "Hi", "Body", CREATED, CREATED},
                new Object[]{41L, 99L, 1, "text/plain", "Orphan", "Body", CREATED, null},
                new Object[]{42L, 30L, 2, "text/html", "Re", "<p>x</p>", CREATED, null}));
        rows.put("FROM survey.respondent_psa rp", rowsOf(new Object[]{50L, 5, "PSA1", 2, "done", null, CREATED, null}));
    }

    @Test
    void given_aRespondentWithItsWholeGraph_when_exported_then_everyRecordIsWrittenInTheDatabaseZone() {
        fullGraph();
        ScriptedEntityManager db = database();
        String text = export(db, 5);

        assertTrue(text.contains("\n# generated: "), text);
        assertEquals(List.of(
                "# ELICIT_EXPORT_V2",
                "# respondent_id: 5",
                "# survey_key: " + SURVEY_KEY,
                "# survey_name: Family\\|History",
                "# access_code: AC123",
                "# answers: 1",
                "# dependents: 1",
                "# subjects: 2",
                "# messages: 3",
                "# respondent_psa: 1",
                "# timezone: " + ZONE,
                "",
                "respondents: " + SURVEY_KEY + "|AC123|true|3|" + CREATED_LOCAL + "||2026-02-01T04:00-05:00|2026-03-01T07:00-05:00",
                "answers: 1|0|2|0|3|0|22222222-2222-2222-2222-222222222222|4|33333333-3333-3333-3333-333333333333|5|0-1-2"
                        + "|a\\|b\\\\c\\nd\\re|val|false|" + CREATED_LOCAL + "||" + CREATED_LOCAL,
                "dependents: 0-1-2|0-1-3|44444444-4444-4444-4444-444444444444|1|false",
                "subjects: 0|X1|Jane|Doe||1980-02-03|j@x.org|555|DEPT|" + CREATED_LOCAL,
                "subjects: 1|X2|Ann|Roe|M||a@x.org||DEPT|" + CREATED_LOCAL,
                "messages: 1|1|text/plain|Hi|Body|" + CREATED_LOCAL + "|" + CREATED_LOCAL,
                "messages: 0|2|text/html|Re|<p>x</p>|" + CREATED_LOCAL + "|",
                "respondent_psa: PSA1|2|done||" + CREATED_LOCAL + "|",
                ""), withoutGenerated(text));

        for (String table : List.of("FROM survey.respondents r", "FROM survey.answers a", "FROM survey.dependents d",
                "FROM survey.subjects s", "FROM survey.messages m", "FROM survey.respondent_psa rp")) {
            List<Call> calls = db.calls(table);
            assertEquals(1, calls.size(), table);
            assertEquals(5, calls.get(0).param("respondentId"), table);
        }
    }

    @Test
    void given_anExport_when_importedBack_then_theRecordsCarryTheSameValues() {
        fullGraph();
        String text = export(database(), 5);

        ScriptedEntityManager target = new ScriptedEntityManager(call -> {
            if (call.terminal().equals("executeUpdate")) {
                return null;
            }
            if (call.is("FROM survey.surveys")) {
                return rowsOf(new Object[]{70L, "FHHS"});
            }
            if (call.is("count(*)")) {
                return 0L;
            }
            if (call.is("FROM survey.sections_questions") || call.is("FROM survey.questions") || call.is("FROM survey.relationships")) {
                return List.of(1L);
            }
            return 1L;
        });
        RespondentImportService importer = new RespondentImportService();
        importer.em = target.entityManager();
        RespondentImportService.ImportResult result = importer.importFromFile(
                new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));

        assertTrue(result.isSuccess(), String.valueOf(result.getErrors()));
        assertEquals(Map.of("respondents", 1, "answers", 1, "dependents", 1, "subjects", 2, "messages", 2,
                "respondent_psa", 1), result.getCounts());
        Call answer = target.calls("INSERT INTO survey.answers").get(0);
        assertEquals("a|b\\c\nd\re", answer.param(12));
        assertEquals(CREATED, answer.param(15));
        Call respondent = target.calls("INSERT INTO survey.respondents").get(0);
        assertEquals("AC123", respondent.param(3));
        assertEquals(Timestamp.from(Instant.parse("2026-02-01T09:00:00Z")), respondent.param(8));
        assertEquals(Timestamp.from(Instant.parse("2026-03-01T12:00:00Z")), respondent.param(9));
    }

    @Test
    void given_noSuchRespondent_when_exported_then_itIsRefused() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> export(database(), 42));
        assertEquals("Respondent not found: 42", e.getMessage());
    }

    @Test
    void given_aDepartmentWithoutCode_when_exported_then_theExportIsRefused() {
        rows.put("FROM survey.respondents r", rowsOf(respondent(null)));
        rows.put("FROM survey.subjects s", rowsOf(
                new Object[]{30L, "X1", "Jane", "Doe", null, null, "j@x.org", null, null, "Oncology", 4, CREATED}));
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> export(database(), 5));
        assertEquals("Department 'Oncology' (id 4) has no code; assign a department code before exporting", e.getMessage());
    }

    @Test
    void given_aRowThatIsNotAColumnArray_when_exported_then_theQueryIsNamed() {
        rows.put("FROM survey.respondents r", rowsOf(respondent(null)));
        rows.put("FROM survey.answers a", List.<Object>of("oops"));
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> export(database(), 5));
        assertEquals("Unexpected row type for answers: java.lang.String", e.getMessage());

        rows.put("FROM survey.answers a", Arrays.asList((Object) null));
        e = assertThrows(IllegalStateException.class, () -> export(database(), 5));
        assertEquals("Unexpected row type for answers: null", e.getMessage());
    }

    @Test
    void given_eachQueryReturnsANonArrayRow_when_exported_then_theQueryNameIsReported() {
        rows.put("FROM survey.respondents r", List.<Object>of("x"));
        assertEquals("Unexpected row type for respondent: java.lang.String",
                assertThrows(IllegalStateException.class, () -> export(database(), 5)).getMessage());
        rows.put("FROM survey.respondents r", rowsOf(respondent(null)));
        for (String[] q : new String[][]{{"FROM survey.dependents d", "dependents"}, {"FROM survey.subjects s", "subjects"},
                {"FROM survey.messages m", "messages"}, {"FROM survey.respondent_psa rp", "respondent_psa"}}) {
            rows.put(q[0], List.<Object>of(1));
            assertEquals("Unexpected row type for " + q[1] + ": java.lang.Integer",
                    assertThrows(IllegalStateException.class, () -> export(database(), 5)).getMessage());
            rows.remove(q[0]);
        }
    }

    @Test
    void given_otherDateAndTimestampTypes_when_exported_then_theyAreFormattedOrRefused() {
        TimeZone saved = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        try {
            rows.put("FROM survey.respondents r", rowsOf(respondent(null)));
            rows.put("FROM survey.subjects s", rowsOf(
                    new Object[]{30L, "X1", "F", "L", null, new java.util.Date(Instant.parse("1980-02-03T12:00:00Z").toEpochMilli()),
                            "e", null, "D", "N", 4, CREATED},
                    new Object[]{31L, "X2", "F", "L", null, "1981|01", "e", null, "D", "N", 4, CREATED}));
            String text = export(database(), 5);
            assertTrue(text.contains("subjects: 0|X1|F|L||1980-02-03|e||D|"), text);
            assertTrue(text.contains("subjects: 1|X2|F|L||1981\\|01|e||D|"), text);

            rows.put("FROM survey.subjects s", rowsOf(new Object[]{30L, "X1", "F", "L", null, null, "e", null, "D", "N", 4, "2026"}));
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> export(database(), 5));
            assertEquals("Unexpected timestamp type: java.lang.String", e.getMessage());
        } finally {
            TimeZone.setDefault(saved);
        }
    }

    @Test
    void given_aMissingCreationTime_when_exported_then_theExportTimeIsWritten() {
        Object[] r = respondent(null);
        r[5] = null;
        rows.put("FROM survey.respondents r", rowsOf(r));
        String text = export(database(), 5);
        String line = text.lines().filter(l -> l.startsWith("respondents: ")).findFirst().orElseThrow();
        String created = line.split("\\|")[4];
        OffsetDateTime parsed = OffsetDateTime.parse(created);
        assertTrue(Math.abs(parsed.toInstant().getEpochSecond() - Instant.now().getEpochSecond()) < 60, created);
        assertTrue(line.endsWith("||2026-03-01T07:00-05:00"), line);
    }
}
