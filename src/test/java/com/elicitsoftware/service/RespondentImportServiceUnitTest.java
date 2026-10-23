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

import com.elicitsoftware.service.RespondentImportService.ImportResult;
import com.elicitsoftware.service.RespondentImportService.ImportValidationException;
import com.elicitsoftware.test.ScriptedEntityManager;
import com.elicitsoftware.test.ScriptedEntityManager.Call;
import jakarta.persistence.NoResultException;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UC-012: Import Respondent Data — the parsing, validation and re-linking of an
 * {@code ELICIT_EXPORT_V2} file, run against a scripted entity manager (no database).
 * <p>
 * Covers BR-041 (header required), BR-042 (all-or-nothing: a bad reference aborts), BR-043 (fresh
 * identifiers with re-linked references), BR-059 (survey by key), BR-102 (department by code),
 * BR-103 (elements by key and exact version), BR-104 (respondent state preserved), BR-105
 * (access-code collision) and BR-106 (only the current format version).
 */
class RespondentImportServiceUnitTest {
    /** Rows as a native query returns them: one {@code Object[]} per row. */
    private static List<Object> rowsOf(Object[]... rows) {
        return new java.util.ArrayList<>(Arrays.asList((Object[]) rows));
    }


    private static final String SURVEY_KEY = "11111111-1111-1111-1111-111111111111";
    private static final String SQ_KEY = "22222222-2222-2222-2222-222222222222";
    private static final String Q_KEY = "33333333-3333-3333-3333-333333333333";
    private static final String REL_KEY = "44444444-4444-4444-4444-444444444444";
    private static final String T1 = "2026-01-02T03:04:05Z";
    private static final String T2 = "2026-01-03T00:00:00+02:00";
    private static final String HEADER = "# ELICIT_EXPORT_V2\n";
    private static final String RESPONDENT = "respondents: " + SURVEY_KEY + "|AC123|t|3|" + T1 + "||" + T2 + "|" + T2 + "\n";
    private static final String ANSWER = "answers: 1|0|2|0|3|0|" + SQ_KEY + "|4|" + Q_KEY + "|5|0-1-2|Display \\| text|val\\nline|false|"
            + T1 + "|" + T1 + "|" + T2 + "\n";
    private static final String DEPENDENT = "dependents: 0-1-2|0-1-3|" + REL_KEY + "|1|f\n";
    private static final String SUBJECT = "subjects: 0|X1|Jane|Doe||1980-02-03|j@x.org|555|DEPT|" + T1 + "\n";
    private static final String MESSAGE = "messages: 0|1|text/plain|Hi|Body|" + T1 + "|\n";
    private static final String PSA = "respondent_psa: PSA1|2|done||" + T1 + "|\n";

    /** Answers every lookup the import makes with a hit; {@code overrides} replace by SQL fragment. */
    private static ScriptedEntityManager database(Map<String, Object> overrides) {
        Function<Call, Object> script = call -> {
            for (Map.Entry<String, Object> o : overrides.entrySet()) {
                if (call.is(o.getKey())) {
                    return o.getValue();
                }
            }
            if (call.terminal().equals("executeUpdate")) {
                return null;
            }
            if (call.is("FROM survey.surveys")) {
                return rowsOf(new Object[]{7L, "FHHS"});
            }
            if (call.is("count(*) FROM survey.respondents")) {
                return 0L;
            }
            if (call.is("nextval('survey.respondents_seq')")) {
                return 100L;
            }
            if (call.is("nextval('survey.subjects_seq')")) {
                return 200L;
            }
            if (call.is("FROM survey.sections_questions")) {
                return List.of(11L);
            }
            if (call.is("FROM survey.questions")) {
                return List.of(12L);
            }
            if (call.is("FROM survey.relationships")) {
                return List.of(13L);
            }
            if (call.is("FROM survey.answers")) {
                return 21L;
            }
            if (call.is("FROM survey.departments")) {
                return 5L;
            }
            if (call.is("FROM survey.post_survey_actions")) {
                return 9L;
            }
            throw new AssertionError("unexpected query: " + call.sql());
        };
        return new ScriptedEntityManager(script);
    }

    private static ScriptedEntityManager database() {
        return database(Map.of());
    }

    private static ImportResult run(ScriptedEntityManager db, String text) {
        RespondentImportService service = new RespondentImportService();
        service.em = db.entityManager();
        return service.importFromFile(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
    }

    private static Call insert(ScriptedEntityManager db, String table) {
        List<Call> inserts = db.calls("INSERT INTO survey." + table + " ");
        assertEquals(1, inserts.size(), "inserts into " + table);
        return inserts.get(0);
    }

    private static Timestamp ts(String value) {
        return Timestamp.from(java.time.OffsetDateTime.parse(value).toInstant());
    }

    private static Map<String, Object> override(String fragment, Object result) {
        Map<String, Object> map = new HashMap<>();
        map.put(fragment, result);
        return map;
    }

    @Test
    void given_aCompleteExport_when_imported_then_everyRecordIsInsertedAndCounted() {
        ScriptedEntityManager db = database();
        ImportResult result = run(db, HEADER + "# respondent_id: 1\n\n" + RESPONDENT + ANSWER + DEPENDENT + SUBJECT + MESSAGE + PSA);

        assertTrue(result.isSuccess());
        assertTrue(result.getErrors().isEmpty());
        assertEquals(6, result.getRecordsImported());
        assertEquals(Map.of("respondents", 1, "answers", 1, "dependents", 1, "subjects", 1, "messages", 1,
                "respondent_psa", 1), result.getCounts());
    }

    @Test
    void given_aRespondentRow_when_imported_then_itGetsAFreshIdOnTheResolvedSurveyAndKeepsItsState() {
        ScriptedEntityManager db = database();
        run(db, HEADER + RESPONDENT);

        Call survey = db.calls("FROM survey.surveys").get(0);
        assertEquals(java.util.UUID.fromString(SURVEY_KEY), survey.param(1));
        Call collision = db.calls("count(*) FROM survey.respondents").get(0);
        assertEquals(7L, collision.param(1));
        assertEquals("AC123", collision.param(2));

        Call row = insert(db, "respondents");
        assertEquals(100L, row.param(1));
        assertEquals(7L, row.param(2));
        assertEquals("AC123", row.param(3));
        assertEquals(Boolean.TRUE, row.param(4));
        assertEquals(3, row.param(5));
        assertEquals(ts(T1), row.param(6));
        assertNull(row.param(7));
        assertEquals(ts(T2), row.param(8));
        assertEquals(ts(T2), row.param(9));
    }

    @Test
    void given_aRespondentWrittenBeforeLastActivityExisted_when_imported_then_lastActivityIsNullAndStateIsCarried() {
        ScriptedEntityManager db = database();
        run(db, HEADER + "respondents: " + SURVEY_KEY + "|AC9|false|x|" + T1 + "||\n");

        Call row = insert(db, "respondents");
        assertEquals(Boolean.FALSE, row.param(4));
        assertNull(row.param(5), "a non-numeric login count is stored as null");
        assertNull(row.param(8));
        assertNull(row.param(9));
    }

    @Test
    void given_aRespondentWithoutActiveFlag_when_imported_then_itIsActive() {
        ScriptedEntityManager db = database();
        run(db, HEADER + "respondents: " + SURVEY_KEY + "|AC9||1|" + T1 + "||\n");
        assertEquals(Boolean.TRUE, insert(db, "respondents").param(4));
    }

    @Test
    void given_anAnswerRow_when_imported_then_itIsLinkedToTheNewRespondentAndTheResolvedElements() {
        ScriptedEntityManager db = database();
        run(db, HEADER + RESPONDENT + ANSWER);

        Call sq = db.calls("FROM survey.sections_questions").get(0);
        assertEquals(java.util.UUID.fromString(SQ_KEY), sq.param(1));
        assertEquals(4, sq.param(2));
        Call q = db.calls("FROM survey.questions").get(0);
        assertEquals(java.util.UUID.fromString(Q_KEY), q.param(1));
        assertEquals(5, q.param(2));

        Call row = insert(db, "answers");
        assertEquals(7L, row.param(1));
        assertEquals(100L, row.param(2));
        assertEquals(1, row.param(3));
        assertEquals(0, row.param(4));
        assertEquals(2, row.param(5));
        assertEquals(0, row.param(6));
        assertEquals(3, row.param(7));
        assertEquals(0, row.param(8));
        assertEquals(11L, row.param(9));
        assertEquals(12L, row.param(10));
        assertEquals("0-1-2", row.param(11));
        assertEquals("Display | text", row.param(12));
        assertEquals("val\nline", row.param(13));
        assertEquals(Boolean.FALSE, row.param(14));
        assertEquals(ts(T1), row.param(15));
        assertEquals(ts(T1), row.param(16));
        assertEquals(5, row.param(17));
        assertEquals(ts(T2), row.param(18));
    }

    @Test
    void given_anAnswerWithoutElementReferences_when_imported_then_theyAreNullAndVersionIsZero() {
        ScriptedEntityManager db = database();
        run(db, HEADER + RESPONDENT + "answers: 1|0|2|0|3|0|||||k|t|v|t||\n");

        Call row = insert(db, "answers");
        assertNull(row.param(9));
        assertNull(row.param(10));
        assertEquals(0, row.param(17));
        assertEquals(Boolean.TRUE, row.param(14), "'t' is PostgreSQL's true");
        assertNull(row.param(15));
        assertNull(row.param(18), "a file written before presented_dt existed has no 17th field");
        assertTrue(db.calls("FROM survey.questions").isEmpty());
        assertTrue(db.calls("FROM survey.sections_questions").isEmpty());
    }

    @Test
    void given_twoAnswersOnTheSameElements_when_imported_then_eachElementIsLookedUpOnce() {
        ScriptedEntityManager db = database();
        ImportResult result = run(db, HEADER + RESPONDENT + ANSWER + ANSWER);

        assertEquals(2, result.getCounts().get("answers"));
        assertEquals(1, db.calls("FROM survey.questions").size());
        assertEquals(1, db.calls("FROM survey.sections_questions").size());
        Call second = db.calls("INSERT INTO survey.answers").get(1);
        assertEquals(11L, second.param(9), "the cached section-question id is reused");
        assertEquals(12L, second.param(10), "the cached question id is reused");
    }

    @Test
    void given_aDependentRow_when_imported_then_itIsRelinkedByDisplayKeyAndRelationship() {
        ScriptedEntityManager db = database();
        run(db, HEADER + RESPONDENT + DEPENDENT + DEPENDENT.replace("|f\n", "|\n"));

        List<Call> answerLookups = db.calls("FROM survey.answers");
        assertEquals(4, answerLookups.size());
        assertEquals(100L, answerLookups.get(0).param(1));
        assertEquals("0-1-2", answerLookups.get(0).param(2));
        assertEquals("0-1-3", answerLookups.get(1).param(2));
        assertEquals(1, db.calls("FROM survey.relationships").size(), "the relationship is cached");

        Call row = db.calls("INSERT INTO survey.dependents").get(0);
        assertEquals(100L, row.param(1));
        assertEquals(21L, row.param(2));
        assertEquals(21L, row.param(3));
        assertEquals(13L, row.param(4));
        assertEquals(Boolean.FALSE, row.param(5));
        assertNull(db.calls("INSERT INTO survey.dependents").get(1).param(5), "an empty flag stays null");
        assertEquals(13L, db.calls("INSERT INTO survey.dependents").get(1).param(4), "the cached relationship id is reused");
    }

    @Test
    void given_subjectsAndMessages_when_imported_then_messagesFollowTheirSubjectIndex() {
        ScriptedEntityManager db = database();
        run(db, HEADER + RESPONDENT + SUBJECT + "subjects: 1|X2|Ann|Roe|M|not-a-date|a@x.org|1|DEPT|" + T1 + "\n"
                + "messages: 1|2|text/html|S|B|" + T1 + "|" + T2 + "\n");

        assertEquals(1, db.calls("FROM survey.departments").size(), "the department is cached by code");
        assertEquals("DEPT", db.calls("FROM survey.departments").get(0).param(1));
        List<Call> subjects = db.calls("INSERT INTO survey.subjects");
        assertEquals(2, subjects.size());
        Call first = subjects.get(0);
        assertEquals(200L, first.param(1));
        assertEquals("X1", first.param(2));
        assertEquals("Jane", first.param(3));
        assertEquals("Doe", first.param(4));
        assertNull(first.param(5));
        assertEquals(java.sql.Date.valueOf("1980-02-03"), first.param(6));
        assertEquals("j@x.org", first.param(7));
        assertEquals("555", first.param(8));
        assertEquals(5L, first.param(9));
        assertEquals(7L, first.param(10));
        assertEquals(100L, first.param(11));
        assertEquals(ts(T1), first.param(12));
        assertNull(subjects.get(1).param(6), "an unparseable date of birth is stored as null");
        assertEquals(5L, subjects.get(1).param(9), "the cached department id is reused");

        Call message = insert(db, "messages");
        assertEquals(200L, message.param(1));
        assertEquals(2, message.param(2));
        assertEquals("text/html", message.param(3));
        assertEquals("S", message.param(4));
        assertEquals("B", message.param(5));
        assertEquals(ts(T1), message.param(6));
        assertEquals(ts(T2), message.param(7));
    }

    @Test
    void given_aPsaRow_when_imported_then_theActionIsResolvedByNameOnTheSurvey() {
        ScriptedEntityManager db = database();
        run(db, HEADER + RESPONDENT + PSA);

        Call lookup = db.calls("FROM survey.post_survey_actions").get(0);
        assertEquals(7L, lookup.param(1));
        assertEquals("PSA1", lookup.param(2));
        Call row = insert(db, "respondent_psa");
        assertEquals(100L, row.param(1));
        assertEquals(9L, row.param(2));
        assertEquals(2, row.param(3));
        assertEquals("done", row.param(4));
        assertNull(row.param(5));
        assertEquals(ts(T1), row.param(6));
        assertNull(row.param(7));
    }

    @Test
    void given_anEmptyFile_when_imported_then_theMissingHeaderIsReported() {
        ImportResult result = run(database(), "\n# a comment\n");
        assertFalse(result.isSuccess());
        assertEquals(0, result.getRecordsImported());
        assertEquals(List.of("File does not contain valid format header"), result.getErrors());
    }

    @Test
    void given_dataBeforeTheHeader_when_imported_then_theLineIsRefused() {
        ScriptedEntityManager db = database();
        ImportResult result = run(db, "\n\n" + RESPONDENT);
        assertFalse(result.isSuccess());
        assertEquals(List.of("Line 3: File does not start with valid format header (expected # ELICIT_EXPORT_V2)"),
                result.getErrors());
        assertTrue(db.calls().isEmpty());
    }

    @Test
    void given_aLegacyV1File_when_imported_then_itIsRefusedWithReexportInstructions() {
        ImportResult result = run(database(), "# note\n# ELICIT_EXPORT_V1\n" + RESPONDENT);
        assertFalse(result.isSuccess());
        assertEquals(0, result.getRecordsImported());
        assertEquals(1, result.getErrors().size());
        assertTrue(result.getErrors().get(0).startsWith("Line 2: This file is in the ELICIT_EXPORT_V1 format"),
                result.getErrors().get(0));
        assertTrue(result.getErrors().get(0).contains("(which now produces ELICIT_EXPORT_V2)"));
    }

    @Test
    void given_anUnknownFormatVersion_when_imported_then_itIsUnsupported() {
        ImportResult result = run(database(), "# ELICIT_EXPORT_V3 generated later\n" + RESPONDENT);
        assertFalse(result.isSuccess());
        assertEquals(List.of("Line 1: Unsupported export format ELICIT_EXPORT_V3 (expected # ELICIT_EXPORT_V2)"),
                result.getErrors());
    }

    @Test
    void given_aValidHeaderWithTrailingText_when_imported_then_laterFormatLinesAreIgnored() {
        ImportResult result = run(database(), "# ELICIT_EXPORT_V2 written by Admin\n# ELICIT_EXPORT_V1\n" + RESPONDENT);
        assertTrue(result.isSuccess(), String.valueOf(result.getErrors()));
        assertEquals(1, result.getRecordsImported());
    }

    @Test
    void given_badLines_when_imported_then_eachIsReportedAndTheRestStillRun() {
        ScriptedEntityManager db = database();
        ImportResult result = run(db, HEADER + "no colon here\n" + ": 1|2\n" + "Widgets: 1|2\n" + "answers: 1\n" + "dependents: 1\n"
                + "subjects: 1\n" + "respondent_psa: 1\n" + "messages: 0\n" + "RESPONDENTS: " + RESPONDENT.substring(13));

        assertFalse(result.isSuccess());
        assertEquals(List.of(
                "Line 2: Invalid format, missing colon separator",
                "Line 3: Unknown table: ",
                "Line 4: Unknown table: widgets",
                "Line 5: Cannot insert answer before respondent",
                "Line 6: Cannot insert dependent before respondent",
                "Line 7: Cannot insert subject before respondent",
                "Line 8: Cannot insert PSA before respondent",
                "Line 9: Invalid subject index for message: 0"), result.getErrors());
        assertEquals(1, result.getCounts().get("respondents"), "the table name is case-insensitive");
        assertEquals(1, result.getRecordsImported());
    }

    @Test
    void given_messageIndexesThatPointNowhere_when_imported_then_eachIsRefused() {
        ImportResult result = run(database(), HEADER + RESPONDENT + "subjects: 1|X|F|L||||1|DEPT|\n"
                + "messages: -1|1|t|s|b||\n" + "messages: 0|1|t|s|b||\n" + "messages: 2|1|t|s|b||\n" + "messages: 1|1|t|s|b||\n");

        assertEquals(List.of(
                "Line 4: Invalid subject index for message: -1",
                "Line 5: Invalid subject index for message: 0",
                "Line 6: Invalid subject index for message: 2"), result.getErrors());
        assertEquals(1, result.getCounts().get("messages"));
        assertEquals(1, result.getCounts().get("subjects"));
    }

    @Test
    void given_aRowWithTooFewFields_when_imported_then_theImportFailsAtThatLine() {
        assertFailsWith(HEADER + "respondents: a|b|c\n", RuntimeException.class,
                "Import failed at line 2: Respondent requires 7 fields, got 3");
        assertFailsWith(HEADER + RESPONDENT + "answers: 1|2\n", RuntimeException.class,
                "Import failed at line 3: Answer requires 16 fields, got 2");
        assertFailsWith(HEADER + RESPONDENT + "dependents: 1|2|3|4\n", RuntimeException.class,
                "Import failed at line 3: Dependent requires 5 fields, got 4");
        assertFailsWith(HEADER + RESPONDENT + "subjects: 1|2|3|4|5|6|7|8|9\n", RuntimeException.class,
                "Import failed at line 3: Subject requires 10 fields, got 9");
        assertFailsWith(HEADER + RESPONDENT + SUBJECT + "messages: 0|1|2|3|4|5\n", RuntimeException.class,
                "Import failed at line 4: Message requires 7 fields, got 6");
        assertFailsWith(HEADER + RESPONDENT + "respondent_psa: 1|2|3|4|5\n", RuntimeException.class,
                "Import failed at line 3: Respondent PSA requires 6 fields, got 5");
    }

    @Test
    void given_anUnresolvableSurvey_when_imported_then_theValidationMessageNamesIt() {
        assertFailsWith(HEADER + "respondents: nope|AC|t|1|" + T1 + "||\n", ImportValidationException.class,
                "Line 2: Invalid survey_key 'nope'; expected a UUID");
        assertFailsWith(HEADER + "respondents: |AC|t|1|" + T1 + "||\n", ImportValidationException.class,
                "Line 2: Invalid survey_key ''; expected a UUID");
        assertFailsWith(database(override("FROM survey.surveys", List.of())), HEADER + RESPONDENT,
                ImportValidationException.class, "Line 2: No survey with survey_key " + SURVEY_KEY
                        + " exists in this instance; apply the survey definition (UC-018) first");
    }

    @Test
    void given_anAccessCodeAlreadyOnTheSurvey_when_imported_then_theImportIsRefused() {
        ScriptedEntityManager db = database(override("count(*) FROM survey.respondents", 1L));
        assertFailsWith(db, HEADER + RESPONDENT, ImportValidationException.class,
                "Line 2: Access code 'AC123' already exists on survey FHHS (id 7) in this instance; the import was not performed");
        assertTrue(db.calls("INSERT").isEmpty());
    }

    @Test
    void given_aSurveyWithoutName_when_theAccessCodeCollides_then_theMessageStillReads() {
        ScriptedEntityManager db = database(Map.of("FROM survey.surveys", rowsOf(new Object[]{8, null}),
                "count(*) FROM survey.respondents", 2));
        assertFailsWith(db, HEADER + RESPONDENT, ImportValidationException.class,
                "Line 2: Access code 'AC123' already exists on survey null (id 8) in this instance; the import was not performed");
    }

    @Test
    void given_anUnresolvableDepartment_when_imported_then_theImportIsRefused() {
        assertFailsWith(HEADER + RESPONDENT + "subjects: 0|X|F|L||||1||\n", ImportValidationException.class,
                "Line 3: Subject has no department code; the export is incomplete");
        assertFailsWith(database(override("FROM survey.departments", null)), HEADER + RESPONDENT + SUBJECT,
                ImportValidationException.class, "Line 3: No department with code 'DEPT' exists in this instance; create the department before importing");
        assertFailsWith(database(override("FROM survey.departments", new NoResultException())), HEADER + RESPONDENT + SUBJECT,
                ImportValidationException.class, "Line 3: No department with code 'DEPT' exists in this instance; create the department before importing");
        assertFailsWith(database(override("FROM survey.departments", "5")), HEADER + RESPONDENT + SUBJECT,
                ImportValidationException.class, "Line 3: No department with code 'DEPT' exists in this instance; create the department before importing");
    }

    @Test
    void given_anUnresolvableElement_when_imported_then_theElementKindAndVersionAreNamed() {
        String answer = "answers: 1|0|2|0|3|0|%s|%s|%s|%s|k|t|v|f|" + T1 + "|\n";
        assertFailsWith(HEADER + RESPONDENT + answer.formatted("bad", "4", "", ""), ImportValidationException.class,
                "Line 3: Invalid section-question key 'bad'; expected a UUID");
        assertFailsWith(HEADER + RESPONDENT + answer.formatted("", "", "bad", "4"), ImportValidationException.class,
                "Line 3: Invalid question key 'bad'; expected a UUID");
        assertFailsWith(HEADER + RESPONDENT + answer.formatted("", "", Q_KEY, "x"), ImportValidationException.class,
                "Line 3: Invalid question version 'x'; expected an integer");
        assertFailsWith(database(override("FROM survey.questions", List.of())), HEADER + RESPONDENT + answer.formatted("", "", Q_KEY, "4"),
                ImportValidationException.class, "Line 3: No question with key " + Q_KEY
                        + " version 4 in this instance; update the survey definition (UC-017) so the source survey's versions exist here");
        assertFailsWith(database(override("FROM survey.sections_questions", List.of(1L, 2L))),
                HEADER + RESPONDENT + answer.formatted(SQ_KEY, "4", "", ""), ImportValidationException.class,
                "Line 3: Multiple section-question rows carry key " + SQ_KEY + " version 4 in this instance; the survey definition is inconsistent");
        assertFailsWith(database(override("FROM survey.relationships", List.of())), HEADER + RESPONDENT + DEPENDENT,
                ImportValidationException.class, "Line 3: No relationship with key " + REL_KEY
                        + " version 1 in this instance; update the survey definition (UC-017) so the source survey's versions exist here");
    }

    @Test
    void given_anUnresolvableDependentEnd_when_imported_then_theSideIsNamed() {
        assertFailsWith(HEADER + RESPONDENT + "dependents: |0-1|" + REL_KEY + "|1|f\n", ImportValidationException.class,
                "Line 3: Dependent has no upstream display_key; the export is incomplete");
        assertFailsWith(HEADER + RESPONDENT + "dependents: 0-1||" + REL_KEY + "|1|f\n", ImportValidationException.class,
                "Line 3: Dependent has no downstream display_key; the export is incomplete");
        assertFailsWith(database(override("FROM survey.answers", null)), HEADER + RESPONDENT + DEPENDENT,
                ImportValidationException.class, "Line 3: No answer with display_key '0-1-2' was imported for this respondent;"
                        + " the dependent's upstream reference cannot be resolved");
    }

    @Test
    void given_anUnknownPostSurveyAction_when_imported_then_theImportIsRefused() {
        assertFailsWith(database(override("FROM survey.post_survey_actions", null)), HEADER + RESPONDENT + PSA,
                ImportValidationException.class, "Line 3: No post_survey_action named 'PSA1' found for survey FHHS (id 7) in this instance");
    }

    @Test
    void given_aValidationFailure_when_rethrown_then_theOriginalIsTheCause() {
        RespondentImportService service = new RespondentImportService();
        service.em = database().entityManager();
        ImportValidationException e = assertThrows(ImportValidationException.class, () -> service.importFromFile(
                new ByteArrayInputStream((HEADER + "respondents: nope|AC|t|1|" + T1 + "||\n").getBytes(StandardCharsets.UTF_8))));
        ImportValidationException cause = assertInstanceOf(ImportValidationException.class, e.getCause());
        assertEquals("Invalid survey_key 'nope'; expected a UUID", cause.getMessage());
        assertInstanceOf(IllegalArgumentException.class, cause.getCause());
    }

    @Test
    void given_escapedFieldValues_when_parsed_then_escapesAreDecoded() {
        ScriptedEntityManager db = database();
        run(db, HEADER + RESPONDENT + "answers: 1|0|2|0|3|0|||||a\\\\b|c\\rd|e\\xf|t||\n");
        Call row = insert(db, "answers");
        assertEquals("a\\b", row.param(11));
        assertEquals("c\rd", row.param(12));
        assertEquals("e\\xf", row.param(13), "an unknown escape keeps its backslash");
    }

    @Test
    void given_anUnreadableStream_when_imported_then_theReadFailureIsReported() {
        RespondentImportService service = new RespondentImportService();
        service.em = database().entityManager();
        InputStream broken = new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("disk gone");
            }
        };
        ImportResult result = service.importFromFile(broken);
        assertFalse(result.isSuccess());
        assertEquals(List.of("Failed to read file: disk gone"), result.getErrors());
        assertEquals(0, result.getCounts().get("respondents"));
    }

    @Test
    void given_anImportResult_when_built_then_itExposesWhatItWasGiven() {
        ImportResult result = new ImportResult(true, 3, List.of("e"), Map.of("answers", 3));
        assertTrue(result.isSuccess());
        assertEquals(3, result.getRecordsImported());
        assertEquals(List.of("e"), result.getErrors());
        assertEquals(Map.of("answers", 3), result.getCounts());
        assertEquals("m", new ImportValidationException("m").getMessage());
    }

    private static void assertFailsWith(String text, Class<? extends RuntimeException> type, String message) {
        assertFailsWith(database(), text, type, message);
    }

    private static void assertFailsWith(ScriptedEntityManager db, String text, Class<? extends RuntimeException> type,
                                        String message) {
        RuntimeException e = assertThrows(RuntimeException.class, () -> run(db, text));
        assertEquals(type, e.getClass());
        assertEquals(message, e.getMessage());
    }
}
