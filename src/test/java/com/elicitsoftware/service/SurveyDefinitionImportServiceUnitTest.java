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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UC-014: Import Survey Definition — {@link SurveyDefinitionImportService} reading an
 * {@code ELICIT_SURVEY_EXPORT_V1} file and writing it as a new survey through native SQL, driven
 * against a {@link ScriptedEntityManager} so the parsing, validation, id re-mapping (BR-045) and
 * audit logging run without a database.
 * <p>
 * Business rules covered: BR-047 (one survey per file), BR-048 (reused shared lookups), BR-049
 * (a malformed record fails the whole import), BR-061 (keys preserved or assigned), BR-062 (no
 * duplicate deployment), BR-076 (retired records are not installed), BR-110 (translations) and
 * BR-112 (report marks).
 */
class SurveyDefinitionImportServiceUnitTest {

    static final String SURVEY_KEY = "11111111-1111-1111-1111-111111111111";
    static final String QUESTION_KEY = "71717171-7171-7171-7171-717171717171";
    static final String OPEN = "9999-12-31 23:59:59+00";
    static final String HEADER = "# ELICIT_SURVEY_EXPORT_V1";
    static final String REVISION = "2026-05-06T07:08:09Z";

    static String key(int n) {
        return String.format("%08d-0000-0000-0000-%012d", n, n);
    }

    static final String SURVEY_LINE = "surveys: 11|" + SURVEY_KEY + "|Family|5|Title|Desc|0011-0001-0000-0001-0000-0000-0000|http://post|pub|cmt|es|en,es";

    static final Map<String, String> LINES = new LinkedHashMap<>();

    static {
        LINES.put("surveys", SURVEY_LINE);
        LINES.put("select_groups", "select_groups: 21|" + key(21) + "|YesNo|sg desc||0|2020-01-01|" + OPEN + "||");
        LINES.put("select_items", "select_items: 31|" + key(31) + "|21|Yes|1|Y|0|2020-01-01|" + OPEN + "||");
        LINES.put("steps", "steps: 41|" + key(41) + "|1.5|Step One||step desc|0|2020-01-01|" + OPEN + "||");
        LINES.put("sections", "sections: 51|" + key(51) + "|2|Section One|SecDim|sec desc|0|2020-01-01|" + OPEN + "||");
        LINES.put("steps_sections", "steps_sections: 61|" + key(61) + "|41|1|51|2.25|0011-0001-0000-0001-0000-0000-0000|0|2020-01-01|" + OPEN + "||");
        LINES.put("questions", "questions: 71|" + QUESTION_KEY + "|3|Text|Short|Tip|t|1|10|vt|21|mask|ph|dv|var|0|2020-01-01|" + OPEN + "||");
        LINES.put("sections_questions", "sections_questions: 81|" + key(81) + "|71|51|3.5|0|2020-01-01|" + OPEN + "||");
        LINES.put("relationships", "relationships: 91|" + key(91) + "|41|81|41|61|81|1|2|rd|tok|ref|dup|oup|0|2020-01-01|" + OPEN + "||");
        LINES.put("reports", "reports: 101|" + key(101) + "|Report|rep desc|http://report|4");
        LINES.put("post_survey_actions", "post_survey_actions: 102|" + key(102) + "|Action|act desc|http://action|2");
        LINES.put("dimensions", "dimensions: 111|" + key(111) + "|Gender");
        LINES.put("ontology", "ontology: 121|" + key(121) + "|Female|F|111");
        LINES.put("metadata", "metadata: 131|" + key(131) + "|61|71|81|121|value");
        LINES.put("report_items", "report_items: 141|" + key(141) + "|RESPONDENT|61|71|81|label|3");
        LINES.put("translations", "translations: 151|" + key(151) + "|questions|" + QUESTION_KEY + "|text|es|Texto|hash1|0|2020-01-01|" + OPEN + "||");
    }

    /** Minimum field count per table, as the service checks it. */
    static final Map<String, Integer> REQUIRED = new LinkedHashMap<>();

    static {
        REQUIRED.put("surveys", 10);
        REQUIRED.put("select_groups", 10);
        REQUIRED.put("select_items", 11);
        REQUIRED.put("steps", 11);
        REQUIRED.put("sections", 11);
        REQUIRED.put("steps_sections", 12);
        REQUIRED.put("questions", 20);
        REQUIRED.put("sections_questions", 10);
        REQUIRED.put("relationships", 19);
        REQUIRED.put("reports", 6);
        REQUIRED.put("post_survey_actions", 6);
        REQUIRED.put("dimensions", 3);
        REQUIRED.put("ontology", 5);
        REQUIRED.put("metadata", 7);
        REQUIRED.put("report_items", 8);
        REQUIRED.put("translations", 13);
    }

    ScriptedEntityManager db;
    SurveyDefinitionImportService service;
    final List<String> log = new ArrayList<>();
    final List<Object[]> logArgs = new ArrayList<>();
    long sequence;
    Long existingSurveyId;
    Long existingDimensionId;
    Long existingOntologyId;

    @BeforeEach
    void setUp() {
        db = new ScriptedEntityManager();
        log.clear();
        logArgs.clear();
        sequence = 100;
        db.answer = this::answer;
        service = new SurveyDefinitionImportService();
        service.em = db.em;
        service.surveyLogService = new SurveyLogService() {
            @Override
            public void logSuccess(Long surveyId, UUID surveyKey, String action, String fileName, String summary,
                    OffsetDateTime revision) {
                log.add("SUCCESS");
                logArgs.add(new Object[]{surveyId, surveyKey, action, fileName, summary, revision});
            }

            @Override
            public void logFailure(Long surveyId, UUID surveyKey, String action, String fileName, String errorMessage,
                    OffsetDateTime revision) {
                log.add("FAILURE");
                logArgs.add(new Object[]{surveyId, surveyKey, action, fileName, errorMessage, revision});
            }
        };
    }

    private Object answer(ScriptedEntityManager.Call call) {
        String sql = call.sql();
        if (call.kind() == ScriptedEntityManager.Kind.UPDATE) {
            return 1;
        }
        if (sql.startsWith("SELECT nextval(")) {
            return ++sequence;
        }
        if (sql.contains("COALESCE(MAX(display_order), 0) + 1")) {
            return 4;
        }
        if (sql.startsWith("SELECT id FROM survey.surveys WHERE survey_key")) {
            return existingSurveyId;
        }
        if (sql.startsWith("SELECT id FROM survey.dimensions")) {
            return existingDimensionId;
        }
        if (sql.startsWith("SELECT id FROM survey.ontology")) {
            return existingOntologyId;
        }
        if (sql.endsWith(" WHERE id = ?1")) {
            // The durable id a row's own default assigned: modelled as surrogate + 1000.
            return ((Number) call.param(1)).longValue() + 1000;
        }
        throw new AssertionError("Unexpected query: " + sql);
    }

    private static InputStream file(String... lines) {
        return new ByteArrayInputStream(String.join("\n", lines).getBytes(StandardCharsets.UTF_8));
    }

    private static InputStream fullFile() {
        List<String> lines = new ArrayList<>();
        lines.add(HEADER);
        lines.add("# survey_revision: " + REVISION);
        lines.add("");
        lines.addAll(LINES.values());
        return file(lines.toArray(String[]::new));
    }

    private ScriptedEntityManager.Call insertInto(String table) {
        List<ScriptedEntityManager.Call> matches = db.calls.stream()
                .filter(c -> c.sql().matches("(?s)\\s*INSERT INTO survey\\." + table + "\\s.*")).toList();
        assertEquals(1, matches.size(), "inserts into " + table);
        return matches.get(0);
    }

    private long durableOf(String table) {
        return ((Number) insertInto(table).param(1)).longValue() + 1000;
    }

    private String line(int n, String message) {
        return Translations.get("definition.line", String.valueOf(n), message);
    }

    // ---------------------------------------------------------------- the happy path

    @Test
    void given_aCompleteFile_when_imported_then_everyRecordIsInstalledAndCounted() {
        SurveyDefinitionImportService.ImportResult result = service.importFromFile(fullFile(), "fhhs.elicit");

        assertTrue(result.isSuccess(), String.valueOf(result.getErrors()));
        assertEquals(List.of(), result.getErrors());
        assertEquals(UUID.fromString(SURVEY_KEY), result.getSurveyKey());
        for (String table : REQUIRED.keySet()) {
            assertEquals(1, result.getCounts().get(table), table);
        }
        assertEquals(16, result.getRecordsImported());
        assertEquals(List.of("SUCCESS"), log);
        Object[] args = logArgs.get(0);
        assertEquals(((Number) insertInto("surveys").param(1)).longValue(), args[0]);
        assertEquals(UUID.fromString(SURVEY_KEY), args[1]);
        assertEquals("IMPORT", args[2]);
        assertEquals("fhhs.elicit", args[3]);
        assertEquals(result.getCounts().toString(), args[4]);
        assertEquals(OffsetDateTime.parse(REVISION), args[5]);
    }

    @Test
    void given_aSurveyRecord_when_imported_then_itGetsANewIdTheNextDisplayOrderAndARebasedKey() {
        service.importFromFile(fullFile(), "f");

        ScriptedEntityManager.Call survey = insertInto("surveys");
        long newId = ((Number) survey.param(1)).longValue();
        assertEquals(101L, newId);
        assertEquals(UUID.fromString(SURVEY_KEY), survey.param(2));
        assertEquals("Family", survey.param(3));
        assertEquals(4, survey.param(4));
        assertEquals("Title", survey.param(5));
        assertEquals("Desc", survey.param(6));
        assertEquals("0101-0001-0000-0001-0000-0000-0000", survey.param(7));
        assertEquals("http://post", survey.param(8));
        assertEquals("es", survey.param(9));
        assertEquals("en,es", survey.param(10));
        assertEquals(UUID.fromString(SURVEY_KEY), db.only("WHERE survey_key = ?1").param(1));
    }

    @Test
    void given_aSurveyRecordWrittenBeforeLanguagesAndKeys_when_imported_then_defaultsAreApplied() {
        SurveyDefinitionImportService.ImportResult result = service.importFromFile(
                file(HEADER, "surveys: 11||Old||||||pub|cmt"), "f");

        assertTrue(result.isSuccess());
        ScriptedEntityManager.Call survey = insertInto("surveys");
        assertEquals("en", survey.param(9));
        assertNull(survey.param(10));
        assertNull(survey.param(5));
        assertNull(survey.param(7));
        UUID generated = (UUID) survey.param(2);
        assertNotNull(generated);
        assertEquals(generated, result.getSurveyKey());
        // No revision header: the success is logged with an unknown revision.
        assertNull(logArgs.get(0)[5]);
    }

    @Test
    void given_aSurveyRecordWithAnEmptyBaseLanguage_when_imported_then_englishIsAssumed() {
        service.importFromFile(file(HEADER, "surveys: 11|" + SURVEY_KEY + "|N|1|T|D||||||fr"), "f");
        ScriptedEntityManager.Call survey = insertInto("surveys");
        assertEquals("en", survey.param(9));
        assertEquals("fr", survey.param(10));
    }

    @Test
    void given_structuralRecords_when_imported_then_everyReferenceIsRemappedToTheNewDurableIds() {
        service.importFromFile(fullFile(), "f");
        long surveyId = ((Number) insertInto("surveys").param(1)).longValue();

        ScriptedEntityManager.Call group = insertInto("select_groups");
        assertEquals(surveyId, group.param(2));
        assertEquals(UUID.fromString(key(21)), group.param(3));
        assertEquals("YesNo", group.param(4));
        assertEquals("sg desc", group.param(5));
        assertEquals("Text", group.param(6), "an absent data type defaults to Text");

        ScriptedEntityManager.Call item = insertInto("select_items");
        assertEquals(surveyId, item.param(1));
        assertEquals(UUID.fromString(key(31)), item.param(2));
        assertEquals(durableOf("select_groups"), item.param(3));
        assertEquals("Yes", item.param(4));
        assertEquals(1, item.param(5));
        assertEquals("Y", item.param(6));

        ScriptedEntityManager.Call step = insertInto("steps");
        assertEquals(surveyId, step.param(2));
        assertEquals(new BigDecimal("1.5"), step.param(4));
        assertEquals("Step One", step.param(5));
        assertEquals("Step One", step.param(6), "an absent dimension name defaults to the step name");
        assertEquals("step desc", step.param(7));

        ScriptedEntityManager.Call section = insertInto("sections");
        assertEquals(new BigDecimal("2"), section.param(4));
        assertEquals("Section One", section.param(5));
        assertEquals("SecDim", section.param(6));
        assertEquals("sec desc", section.param(7));

        ScriptedEntityManager.Call ss = insertInto("steps_sections");
        assertEquals(surveyId, ss.param(2));
        assertEquals(UUID.fromString(key(61)), ss.param(3));
        assertEquals(durableOf("steps"), ss.param(4));
        assertEquals(new BigDecimal("1"), ss.param(5));
        assertEquals(durableOf("sections"), ss.param(6));
        assertEquals(new BigDecimal("2.25"), ss.param(7));
        assertEquals(String.format("%04d", surveyId) + "-0001-0000-0001-0000-0000-0000", ss.param(8));

        ScriptedEntityManager.Call q = insertInto("questions");
        assertEquals(UUID.fromString(QUESTION_KEY), q.param(3));
        assertEquals(3, q.param(4));
        assertEquals("Text", q.param(5));
        assertEquals("Short", q.param(6));
        assertEquals("Tip", q.param(7));
        assertEquals(true, q.param(8));
        assertEquals(1, q.param(9));
        assertEquals(10, q.param(10));
        assertEquals("vt", q.param(11));
        assertEquals(durableOf("select_groups"), q.param(12));
        assertEquals("mask", q.param(13));
        assertEquals("ph", q.param(14));
        assertEquals("dv", q.param(15));
        assertEquals("var", q.param(16));

        ScriptedEntityManager.Call sq = insertInto("sections_questions");
        assertEquals(durableOf("questions"), sq.param(4));
        assertEquals(durableOf("sections"), sq.param(5));
        assertEquals(new BigDecimal("3.5"), sq.param(6));

        ScriptedEntityManager.Call rel = insertInto("relationships");
        assertEquals(surveyId, rel.param(1));
        assertEquals(UUID.fromString(key(91)), rel.param(2));
        assertEquals(durableOf("steps"), rel.param(3));
        assertEquals(durableOf("sections_questions"), rel.param(4));
        assertEquals(durableOf("steps"), rel.param(5));
        assertEquals(durableOf("steps_sections"), rel.param(6));
        assertEquals(durableOf("sections_questions"), rel.param(7));
        assertEquals(1, rel.param(8));
        assertEquals(2, rel.param(9));
        assertEquals("rd", rel.param(10));
        assertEquals("tok", rel.param(11));
        assertEquals("ref", rel.param(12));
        assertEquals("dup", rel.param(13));
        assertEquals("oup", rel.param(14));
    }

    @Test
    void given_type1AndLookupRecords_when_imported_then_theyAreInsertedUnderTheNewSurvey() {
        service.importFromFile(fullFile(), "f");
        long surveyId = ((Number) insertInto("surveys").param(1)).longValue();

        ScriptedEntityManager.Call report = insertInto("reports");
        assertEquals(surveyId, report.param(1));
        assertEquals(UUID.fromString(key(101)), report.param(2));
        assertEquals("Report", report.param(3));
        assertEquals("rep desc", report.param(4));
        assertEquals("http://report", report.param(5));
        assertEquals(4, report.param(6));

        ScriptedEntityManager.Call action = insertInto("post_survey_actions");
        assertEquals(surveyId, action.param(1));
        assertEquals(UUID.fromString(key(102)), action.param(2));
        assertEquals("Action", action.param(3));
        assertEquals("act desc", action.param(4));
        assertEquals("http://action", action.param(5));
        assertEquals(2, action.param(6));

        ScriptedEntityManager.Call dimension = insertInto("dimensions");
        long dimensionId = ((Number) dimension.param(1)).longValue();
        assertEquals(UUID.fromString(key(111)), dimension.param(2));
        assertEquals("Gender", dimension.param(3));
        assertEquals("Gender", db.only("SELECT id FROM survey.dimensions").param(1));

        ScriptedEntityManager.Call ontology = insertInto("ontology");
        long ontologyId = ((Number) ontology.param(1)).longValue();
        assertEquals(surveyId, ontology.param(2));
        assertEquals(UUID.fromString(key(121)), ontology.param(3));
        assertEquals("Female", ontology.param(4));
        assertEquals("F", ontology.param(5));
        assertEquals(dimensionId, ontology.param(6), "a dimension id that is not durable is mapped as-is");
        ScriptedEntityManager.Call ontologyLookup = db.only("SELECT id FROM survey.ontology");
        assertEquals("Female", ontologyLookup.param(1));
        assertEquals("F", ontologyLookup.param(2));

        ScriptedEntityManager.Call meta = insertInto("metadata");
        assertEquals(surveyId, meta.param(1));
        assertEquals(UUID.fromString(key(131)), meta.param(2));
        assertEquals(durableOf("steps_sections"), meta.param(3));
        assertEquals(durableOf("questions"), meta.param(4));
        assertEquals(durableOf("sections_questions"), meta.param(5));
        assertEquals(ontologyId, meta.param(6));
        assertEquals("value", meta.param(7));

        ScriptedEntityManager.Call mark = insertInto("report_items");
        assertEquals(surveyId, mark.param(1));
        assertEquals(UUID.fromString(key(141)), mark.param(2));
        assertEquals("RESPONDENT", mark.param(3));
        assertEquals(durableOf("steps_sections"), mark.param(4));
        assertEquals(durableOf("questions"), mark.param(5));
        assertEquals(durableOf("sections_questions"), mark.param(6));
        assertEquals("label", mark.param(7));
        assertEquals(3, mark.param(8));

        ScriptedEntityManager.Call tr = insertInto("translations");
        assertEquals(surveyId, tr.param(1));
        assertEquals(UUID.fromString(key(151)), tr.param(2));
        assertEquals("questions", tr.param(3));
        assertEquals(UUID.fromString(QUESTION_KEY), tr.param(4));
        assertEquals("text", tr.param(5));
        assertEquals("es", tr.param(6));
        assertEquals("Texto", tr.param(7));
        assertEquals("hash1", tr.param(8));
    }

    @Test
    void given_optionalFieldsLeftEmpty_when_imported_then_theyAreNullOrDefaulted() {
        SurveyDefinitionImportService.ImportResult result = service.importFromFile(file(HEADER, SURVEY_LINE,
                "steps: 41|" + key(41) + "|||||0|x|" + OPEN + "||",
                "select_groups: 21|" + key(21) + "|G||Number|0|x|" + OPEN + "||",
                "questions: 71||1|T||||||||||||0|x|" + OPEN + "||",
                "report_items: 141|" + key(141) + "|ADMIN||||lbl|",
                "dimensions: 111||NoKey",
                "ontology: 121||O|T|"), "f");

        assertTrue(result.isSuccess(), String.valueOf(result.getErrors()));
        ScriptedEntityManager.Call step = insertInto("steps");
        assertNull(step.param(4));
        assertNull(step.param(5));
        assertEquals("", step.param(6), "no name and no dimension name: an empty dimension name");
        assertEquals("Number", insertInto("select_groups").param(6));
        ScriptedEntityManager.Call q = insertInto("questions");
        assertNotNull(q.param(3), "a question without an element key is given a fresh one");
        assertEquals(false, q.param(8));
        assertNull(q.param(12), "no select group");
        ScriptedEntityManager.Call mark = insertInto("report_items");
        assertNull(mark.param(4));
        assertNull(mark.param(5));
        assertNull(mark.param(6));
        assertEquals(0, mark.param(8), "an absent order is 0");
        assertNotNull(insertInto("dimensions").param(2));
        assertNull(insertInto("ontology").param(6));
    }

    @Test
    void given_anExistingDimensionAndOntology_when_imported_then_theyAreReusedNotInserted() {
        existingDimensionId = 5L;
        existingOntologyId = 6L;
        SurveyDefinitionImportService.ImportResult result = service.importFromFile(file(HEADER, SURVEY_LINE,
                LINES.get("steps"), LINES.get("sections"), LINES.get("steps_sections"),
                LINES.get("dimensions"), LINES.get("ontology"),
                "metadata: 131|" + key(131) + "|61|||121|v"), "f");

        assertTrue(result.isSuccess());
        assertEquals(1, result.getCounts().get("dimensions"));
        assertEquals(1, result.getCounts().get("ontology"));
        assertTrue(db.callsContaining("INSERT INTO survey.dimensions").isEmpty());
        assertTrue(db.callsContaining("INSERT INTO survey.ontology").isEmpty());
        assertEquals(6L, insertInto("metadata").param(6));
    }

    @Test
    void given_anExistingDimension_when_aNewOntologyIsImported_then_itPointsAtTheExistingDimension() {
        existingDimensionId = 5L;
        service.importFromFile(file(HEADER, SURVEY_LINE, LINES.get("dimensions"), LINES.get("ontology")), "f");
        assertEquals(5L, insertInto("ontology").param(6));
    }

    // ---------------------------------------------------------------- header and framing

    @Test
    void given_noFormatHeader_when_imported_then_theFirstDataLineIsRefusedAndNothingIsWritten() {
        SurveyDefinitionImportService.ImportResult result = service.importFromFile(file("# something", SURVEY_LINE), "f");

        assertFalse(result.isSuccess());
        assertEquals(0, result.getRecordsImported());
        assertEquals(List.of(line(2, Translations.get("definition.badHeader", "ELICIT_SURVEY_EXPORT_V1"))), result.getErrors());
        assertTrue(db.calls.isEmpty());
        assertEquals(List.of("FAILURE"), log);
        assertNull(logArgs.get(0)[0]);
        assertEquals("IMPORT", logArgs.get(0)[2]);
        assertEquals(result.getErrors().get(0), logArgs.get(0)[4]);
    }

    @Test
    void given_onlyComments_when_imported_then_theMissingHeaderIsReported() {
        SurveyDefinitionImportService.ImportResult result = service.importFromFile(file("# a", "", "# b"), "f");

        assertFalse(result.isSuccess());
        assertEquals(List.of(Translations.get("definition.noHeader")), result.getErrors());
        assertNull(result.getSurveyKey());
        assertEquals(List.of("FAILURE"), log);
    }

    @Test
    void given_aCorruptRevisionHeader_when_imported_then_theFileIsRefusedAtThatLine() {
        SurveyDefinitionImportService.ImportResult result = service.importFromFile(
                file(HEADER, "# survey_revision: nope", SURVEY_LINE), "f");

        assertFalse(result.isSuccess());
        assertEquals(1, result.getErrors().size());
        assertTrue(result.getErrors().get(0).startsWith(line(2, "")), result.getErrors().get(0));
        assertTrue(db.calls.isEmpty());
        assertEquals(List.of("FAILURE"), log);
    }

    @Test
    void given_aRevisionHeader_when_theImportFails_then_theFailureIsLoggedWithThatRevision() {
        SurveyDefinitionImportService.ImportResult result = service.importFromFile(
                file("# survey_revision: " + REVISION, "# other", SURVEY_LINE), "f");
        assertFalse(result.isSuccess());
        assertEquals(OffsetDateTime.parse(REVISION), logArgs.get(0)[5]);
    }

    @Test
    void given_lineProblemsThatDoNotAbort_when_imported_then_eachIsReportedAndTheResultFails() {
        SurveyDefinitionImportService.ImportResult result = service.importFromFile(file(HEADER, SURVEY_LINE,
                "no colon here",
                "widgets: 1|2",
                "surveys: 12|" + key(12) + "|Second|1|T|D||||"), "f");

        assertFalse(result.isSuccess());
        assertEquals(List.of(
                line(3, Translations.get("definition.noColon")),
                line(4, Translations.get("definition.unknownTable", "widgets")),
                line(5, Translations.get("definition.multipleSurveys"))), result.getErrors());
        assertEquals(1, result.getCounts().get("surveys"));
        assertEquals(1, result.getRecordsImported());
        assertEquals(UUID.fromString(SURVEY_KEY), result.getSurveyKey());
        assertEquals(1, db.callsContaining("INSERT INTO survey.surveys").size());
        assertEquals(List.of("FAILURE"), log);
        assertEquals(UUID.fromString(SURVEY_KEY), logArgs.get(0)[1]);
        assertNull(logArgs.get(0)[0], "a failed import never references the rolled-back survey by id");
    }

    @Test
    void given_aLineStartingWithItsColon_when_imported_then_itNamesAnEmptyTable() {
        SurveyDefinitionImportService.ImportResult result = service.importFromFile(file(HEADER, SURVEY_LINE, ": 1|2"), "f");
        assertEquals(List.of(line(3, Translations.get("definition.unknownTable", ""))), result.getErrors());
    }

    @Test
    void given_aSurveyRecordWithABaseLanguageButNoPublishedSet_when_imported_then_onlyTheBaseLanguageIsSet() {
        service.importFromFile(file(HEADER, "surveys: 11|" + SURVEY_KEY + "|N|1|T|D|||||fr"), "f");
        ScriptedEntityManager.Call survey = insertInto("surveys");
        assertEquals("fr", survey.param(9));
        assertNull(survey.param(10));
    }

    @Test
    void given_aSectionWithNoNameOrDimension_when_imported_then_theDimensionNameIsEmpty() {
        service.importFromFile(file(HEADER, SURVEY_LINE, "sections: 51|" + key(51) + "|1|||d|0|x|" + OPEN + "||"), "f");
        ScriptedEntityManager.Call section = insertInto("sections");
        assertNull(section.param(5));
        assertEquals("", section.param(6));
    }

    @Test
    void given_aTableNameInUpperCase_when_imported_then_itIsAccepted() {
        SurveyDefinitionImportService.ImportResult result = service.importFromFile(
                file(HEADER, "  SURVEYS : 11|" + SURVEY_KEY + "|N|1|T|D||||  "), "f");
        assertTrue(result.isSuccess(), String.valueOf(result.getErrors()));
    }

    @Test
    void given_recordsBeforeTheSurveyRecord_when_imported_then_eachIsRefused() {
        Map<String, String> label = Map.ofEntries(
                Map.entry("select_groups", "select_group"), Map.entry("select_items", "select_item"),
                Map.entry("steps", "step"), Map.entry("sections", "section"),
                Map.entry("steps_sections", "steps_section"), Map.entry("questions", "question"),
                Map.entry("sections_questions", "sections_question"), Map.entry("relationships", "relationship"),
                Map.entry("reports", "report"), Map.entry("post_survey_actions", "post_survey_action"),
                Map.entry("ontology", "ontology"), Map.entry("metadata", "metadata"),
                Map.entry("report_items", "report_items"), Map.entry("translations", "translation"));
        label.forEach((table, what) -> {
            setUp();
            SurveyDefinitionImportService.ImportResult result = service.importFromFile(file(HEADER, LINES.get(table)), "f");
            assertFalse(result.isSuccess(), table);
            assertEquals(List.of(line(2, Translations.get("definition.beforeSurvey", what))), result.getErrors(), table);
            assertEquals(0, result.getCounts().getOrDefault(table, 0), table);
            assertTrue(db.calls.isEmpty(), table);
        });
    }

    @Test
    void given_aDimensionBeforeTheSurveyRecord_when_imported_then_itIsAcceptedAsASharedLookup() {
        SurveyDefinitionImportService.ImportResult result = service.importFromFile(
                file(HEADER, LINES.get("dimensions"), SURVEY_LINE), "f");
        assertTrue(result.isSuccess(), String.valueOf(result.getErrors()));
        assertEquals(1, result.getCounts().get("dimensions"));
    }

    @Test
    void given_aRetiredRecord_when_imported_then_itIsSkippedAndCountedAsSuch() {
        SurveyDefinitionImportService.ImportResult result = service.importFromFile(file(HEADER, SURVEY_LINE,
                "steps: 41|" + key(41) + "|1|Gone||d|0|2020-01-01|2025-01-01 00:00:00+00||"), "f");

        assertTrue(result.isSuccess());
        assertEquals(0, result.getCounts().get("steps"));
        assertEquals(1, result.getCounts().get("skipped_retired"));
        assertTrue(db.callsContaining("INSERT INTO survey.steps\n").isEmpty());
        assertTrue(db.callsContaining("survey.steps_seq").isEmpty());
    }

    // ---------------------------------------------------------------- malformed records (BR-049)

    @Test
    void given_aRecordOneFieldShort_when_imported_then_theWholeImportFails() {
        REQUIRED.forEach((table, required) -> {
            setUp();
            String[] parts = LINES.get(table).split(": ", 2);
            String[] fields = SurveyDefinitionFileFields.parseFields(parts[1]);
            String shortLine = table + ": " + String.join("|", java.util.Arrays.copyOf(fields, required - 1));
            List<String> lines = new ArrayList<>(List.of(HEADER));
            if (!table.equals("surveys")) {
                lines.add(SURVEY_LINE);
            }
            lines.add(shortLine);
            int lineNumber = lines.size();
            RuntimeException e = assertThrows(RuntimeException.class,
                    () -> service.importFromFile(file(lines.toArray(String[]::new)), "f"), table);
            String reason = Translations.get("definition.fieldCount", table, String.valueOf(required), String.valueOf(required - 1));
            assertEquals(Translations.get("definition.importFailedAtLine", String.valueOf(lineNumber), reason), e.getMessage(), table);
            assertTrue(e.getCause() instanceof IllegalArgumentException, table);
            assertEquals(List.of("FAILURE"), log, table);
            assertEquals(line(lineNumber, reason), logArgs.get(0)[4], table);
        });
    }

    @Test
    void given_aSurveyKeyAlreadyDeployedHere_when_imported_then_theImportIsRefused() {
        existingSurveyId = 9L;
        RuntimeException e = assertThrows(RuntimeException.class,
                () -> service.importFromFile(file(HEADER, SURVEY_LINE), "f"));

        assertEquals(Translations.get("definition.importFailedAtLine", "2",
                Translations.get("definition.keyExists", SURVEY_KEY, "9")), e.getMessage());
        assertTrue(e.getCause() instanceof IllegalStateException);
        assertTrue(db.callsContaining("INSERT").isEmpty());
        assertEquals(List.of("FAILURE"), log);
    }

    @Test
    void given_anInvalidSurveyKey_when_imported_then_theImportFails() {
        RuntimeException e = assertThrows(RuntimeException.class,
                () -> service.importFromFile(file(HEADER, "surveys: 11|not-a-uuid|N|1|T|D||||"), "f"));
        assertEquals(Translations.get("definition.importFailedAtLine", "2",
                Translations.get("definition.invalidValue", "survey_key", "not-a-uuid")), e.getMessage());
    }

    @Test
    void given_anInvalidElementKey_when_imported_then_theImportFails() {
        RuntimeException e = assertThrows(RuntimeException.class, () -> service.importFromFile(
                file(HEADER, SURVEY_LINE, "steps: 41|bogus|1|S||d|0|x|" + OPEN + "||"), "f"));
        assertEquals(Translations.get("definition.importFailedAtLine", "3",
                Translations.get("definition.invalidValue", "element_key", "bogus")), e.getMessage());
    }

    @Test
    void given_aReferenceToARecordNotInTheFile_when_imported_then_theImportFails() {
        RuntimeException e = assertThrows(RuntimeException.class,
                () -> service.importFromFile(file(HEADER, SURVEY_LINE, LINES.get("select_items")), "f"));
        assertEquals(Translations.get("definition.importFailedAtLine", "3",
                Translations.get("definition.noIdMapping", "select_group (for select_item)", "21")), e.getMessage());

        setUp();
        RuntimeException q = assertThrows(RuntimeException.class,
                () -> service.importFromFile(file(HEADER, SURVEY_LINE, LINES.get("questions")), "f"));
        assertTrue(q.getMessage().contains(Translations.get("definition.noIdMapping", "select_group (for question)", "21")));
    }

    @Test
    void given_aRelationshipWithoutItsUpstreamQuestion_when_imported_then_theImportFails() {
        RuntimeException e = assertThrows(RuntimeException.class, () -> service.importFromFile(
                file(HEADER, SURVEY_LINE, "relationships: 91|" + key(91) + "||||||1|2|||||| 0|x|" + OPEN + "||"), "f"));
        assertTrue(e.getMessage().contains(Translations.get("definition.noSourceId", "sections_question (upstream_sq)")),
                e.getMessage());
    }

    @Test
    void given_metadataWithoutAnOntology_when_imported_then_theImportFails() {
        RuntimeException e = assertThrows(RuntimeException.class, () -> service.importFromFile(
                file(HEADER, SURVEY_LINE, "metadata: 131|" + key(131) + "|||||v"), "f"));
        assertTrue(e.getMessage().contains(Translations.get("definition.noSourceId", "ontology (metadata)")), e.getMessage());
    }

    @Test
    void given_aTranslationOfAFieldWithNoRespondentText_when_imported_then_theImportFails() {
        RuntimeException e = assertThrows(RuntimeException.class, () -> service.importFromFile(file(HEADER, SURVEY_LINE,
                "translations: 151|" + key(151) + "|questions|" + QUESTION_KEY + "|default_value|es|x|h|0|x|" + OPEN + "||"), "f"));
        assertTrue(e.getMessage().endsWith(Translations.get("definition.translationNotTranslatable", "questions.default_value")),
                e.getMessage());
    }

    @Test
    void given_aTranslationWithAnEmptyValue_when_imported_then_theImportFails() {
        RuntimeException e = assertThrows(RuntimeException.class, () -> service.importFromFile(file(HEADER, SURVEY_LINE,
                "translations: 151|" + key(151) + "|questions|" + QUESTION_KEY + "|text|es||h|0|x|" + OPEN + "||"), "f"));
        assertTrue(e.getMessage().endsWith(Translations.get("definition.translationEmpty", "questions.text")), e.getMessage());
    }

    @Test
    void given_aTranslationWithoutItsKeys_when_imported_then_theKeysAreNeverInvented() {
        RuntimeException noKey = assertThrows(RuntimeException.class, () -> service.importFromFile(file(HEADER, SURVEY_LINE,
                "translations: 151||questions|" + QUESTION_KEY + "|text|es|x|h|0|x|" + OPEN + "||"), "f"));
        assertTrue(noKey.getMessage().endsWith(Translations.get("definition.translationMissing", "translation_key")),
                noKey.getMessage());

        setUp();
        RuntimeException noTarget = assertThrows(RuntimeException.class, () -> service.importFromFile(file(HEADER, SURVEY_LINE,
                "translations: 151|" + key(151) + "|questions||text|es|x|h|0|x|" + OPEN + "||"), "f"));
        assertTrue(noTarget.getMessage().endsWith(Translations.get("definition.translationMissing", "element_key")),
                noTarget.getMessage());

        setUp();
        RuntimeException badKey = assertThrows(RuntimeException.class, () -> service.importFromFile(file(HEADER, SURVEY_LINE,
                "translations: 151|zzz|questions|" + QUESTION_KEY + "|text|es|x|h|0|x|" + OPEN + "||"), "f"));
        assertTrue(badKey.getMessage().endsWith(Translations.get("definition.invalidValue", "translation_key", "zzz")),
                badKey.getMessage());
        assertTrue(badKey.getCause().getCause() instanceof IllegalArgumentException);
    }

    @Test
    void given_aDatabaseFailure_when_imported_then_itAbortsAtThatLine() {
        db.answer = call -> call.sql().startsWith("SELECT nextval(") ? new IllegalStateException("boom") : answer(call);
        RuntimeException e = assertThrows(RuntimeException.class,
                () -> service.importFromFile(file(HEADER, SURVEY_LINE), "f"));
        assertEquals(Translations.get("definition.importFailedAtLine", "2", "boom"), e.getMessage());
        assertEquals(line(2, "boom"), logArgs.get(0)[4]);
    }

    @Test
    void given_anUnreadableStream_when_imported_then_theReadFailureIsReported() {
        InputStream broken = new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("disk gone");
            }
        };
        SurveyDefinitionImportService.ImportResult result = service.importFromFile(broken, "f");

        assertFalse(result.isSuccess());
        assertEquals(List.of(Translations.get("definition.readFailed", "disk gone")), result.getErrors());
        assertEquals(List.of("FAILURE"), log);
    }

    @Test
    void given_theLegacyConstructor_when_used_then_thereIsNoSurveyKey() {
        SurveyDefinitionImportService.ImportResult result =
                new SurveyDefinitionImportService.ImportResult(true, 3, List.of(), Map.of("steps", 3));
        assertTrue(result.isSuccess());
        assertEquals(3, result.getRecordsImported());
        assertEquals(Map.of("steps", 3), result.getCounts());
        assertNull(result.getSurveyKey());
    }
}
