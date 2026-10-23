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
import com.elicitsoftware.model.Survey;
import com.elicitsoftware.service.SurveyDefinitionUpdateService.ChangeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import static com.elicitsoftware.service.SurveyDefinitionImportServiceUnitTest.LINES;
import static com.elicitsoftware.service.SurveyDefinitionImportServiceUnitTest.QUESTION_KEY;
import static com.elicitsoftware.service.SurveyDefinitionImportServiceUnitTest.SURVEY_KEY;
import static com.elicitsoftware.service.SurveyDefinitionImportServiceUnitTest.key;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UC-017: Update Survey Definition — how {@link SurveyDefinitionUpdateService} reconciles each
 * record of a file with the target survey's current rows, driven against a
 * {@link ScriptedEntityManager}.
 * <p>
 * {@code updateFromFile} itself opens with {@code Survey.findById}, a Panache static that only
 * works inside a booted application, so this test reaches the per-record steps it delegates to
 * directly (they are private, hence the reflection). Business rules covered: BR-063 (key match),
 * BR-064 (shell and Type 1 in place, Type 2 versioned), BR-065 (key and id never change), BR-067
 * (matched by element key), BR-068 (retire closes, never deletes), BR-069/BR-070 (revision
 * regression), BR-109 (compared by value: a field that differs versions, nothing else does),
 * BR-110 (translations), BR-111 (languages), BR-112 and BR-113 (report marks; unlisted reports
 * deleted).
 */
class SurveyDefinitionUpdateServiceUnitTest {

    static final int SURVEY_ID = 7;
    static final String OPEN = "9999-12-31 23:59:59+00";

    ScriptedEntityManager db;
    SurveyDefinitionUpdateService service;
    final Map<String, List<Object>> currentRows = new HashMap<>();
    final Map<String, List<Object>> latestRows = new HashMap<>();
    final List<Object[]> log = new ArrayList<>();
    OffsetDateTime appliedRevision;
    Long existingDimensionId;
    Long existingOntologyId;
    List<Object> languages;
    long sequence;

    final Map<Long, Long> selectGroupIds = Map.of(21L, 1021L);
    final Map<Long, Long> stepIds = Map.of(41L, 2041L);
    final Map<Long, Long> sectionIds = Map.of(51L, 2051L);
    final Map<Long, Long> stepsSectionIds = Map.of(61L, 2061L);
    final Map<Long, Long> questionIds = Map.of(71L, 2071L);
    final Map<Long, Long> sectionsQuestionIds = Map.of(81L, 2081L);
    final Map<Long, Long> dimensionIds = Map.of(111L, 2111L);
    final Map<Long, Long> ontologyIds = Map.of(121L, 2121L);

    @BeforeEach
    void setUp() {
        db = new ScriptedEntityManager();
        db.answer = this::answer;
        currentRows.clear();
        latestRows.clear();
        log.clear();
        appliedRevision = null;
        existingDimensionId = null;
        existingOntologyId = null;
        languages = List.of();
        sequence = 900;
        service = new SurveyDefinitionUpdateService();
        service.em = db.em;
        service.surveyLogService = new SurveyLogService() {
            @Override
            public void logSuccess(Long surveyId, UUID surveyKey, String action, String fileName, String summary,
                    OffsetDateTime revision) {
                log.add(new Object[]{"SUCCESS", surveyId, surveyKey, action, fileName, summary, revision});
            }

            @Override
            public void logFailure(Long surveyId, UUID surveyKey, String action, String fileName, String errorMessage,
                    OffsetDateTime revision) {
                log.add(new Object[]{"FAILURE", surveyId, surveyKey, action, fileName, errorMessage, revision});
            }

            @Override
            public OffsetDateTime findLatestAppliedRevision(UUID surveyKey) {
                assertEquals(UUID.fromString(SURVEY_KEY), surveyKey);
                return appliedRevision;
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
        if (sql.startsWith("SELECT id FROM survey.dimensions WHERE name")) {
            return existingDimensionId;
        }
        if (sql.startsWith("SELECT id FROM survey.ontology WHERE name")) {
            return existingOntologyId;
        }
        if (sql.startsWith("SELECT base_language, content_languages")) {
            return languages;
        }
        if (sql.contains("ORDER BY version DESC LIMIT 1")) {
            return latestRows.getOrDefault(tableOf(sql), List.of());
        }
        if (sql.contains(" WHERE survey_id = ?1 AND ")) {
            return currentRows.getOrDefault(tableOf(sql), List.of());
        }
        if (sql.startsWith("SELECT ") && sql.endsWith(" WHERE id = ?1")) {
            return ((Number) call.param(1)).longValue() + 1000;
        }
        throw new AssertionError("Unexpected query: " + sql);
    }

    private static String tableOf(String sql) {
        int from = sql.indexOf(" FROM ") + 6;
        return sql.substring(from, sql.indexOf(' ', from));
    }

    // ---------------------------------------------------------------- reflection plumbing

    private Object call(String name, Object... args) {
        for (Method m : SurveyDefinitionUpdateService.class.getDeclaredMethods()) {
            if (m.getName().equals(name) && m.getParameterCount() == args.length) {
                m.setAccessible(true);
                try {
                    return m.invoke(service, args);
                } catch (InvocationTargetException e) {
                    if (e.getCause() instanceof RuntimeException re) {
                        throw re;
                    }
                    throw new AssertionError(e.getCause());
                } catch (IllegalAccessException e) {
                    throw new AssertionError(e);
                }
            }
        }
        throw new AssertionError("No method " + name + "/" + args.length);
    }

    private static Object accessor(Object record, String name) {
        try {
            Method m = record.getClass().getDeclaredMethod(name);
            m.setAccessible(true);
            return m.invoke(record);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static ChangeType changeOf(Object outcome) {
        return outcome instanceof ChangeType type ? type : (ChangeType) accessor(outcome, "changeType");
    }

    private static Long durableOf(Object outcome) {
        return (Long) accessor(outcome, "durableId");
    }

    private static String[] fields(String table) {
        return SurveyDefinitionFileFields.parseFields(LINES.get(table).split(": ", 2)[1]);
    }

    private static String[] with(String[] fields, int index, String value) {
        String[] copy = fields.clone();
        copy[index] = value;
        return copy;
    }

    private List<ScriptedEntityManager.Call> writes() {
        return db.calls.stream().filter(c -> c.kind() == ScriptedEntityManager.Kind.UPDATE).toList();
    }

    private ScriptedEntityManager.Call insertInto(String table) {
        List<ScriptedEntityManager.Call> matches = db.calls.stream()
                .filter(c -> c.sql().matches("(?s)\\s*INSERT INTO survey\\." + table + "\\s.*")).toList();
        assertEquals(1, matches.size(), "inserts into " + table + ": " + db.calls);
        return matches.get(0);
    }

    private static Object different(Object value) {
        if (value instanceof Long l) {
            return l + 1;
        }
        if (value instanceof Integer i) {
            return i + 1;
        }
        if (value instanceof BigDecimal d) {
            return d.add(BigDecimal.ONE);
        }
        if (value instanceof Boolean b) {
            return !b;
        }
        if (value instanceof UUID) {
            return UUID.randomUUID();
        }
        return value + "-changed";
    }

    // ---------------------------------------------------------------- the per-table specification

    /**
     * One table's reconciliation: how to invoke it with the file's record, the stored row that
     * equals that record, the columns of it that are compared, and whether a difference versions
     * the row (Type 2) or updates it in place (Type 1).
     */
    record Spec(String table, Supplier<Object> upsert, Object[] stored, int firstCompared, boolean type2) {
    }

    private List<Spec> specs() {
        return List.of(
                new Spec("select_groups", () -> call("upsertSelectGroup", fields("select_groups"), SURVEY_ID),
                        new Object[]{500L, 1021L, 2, "YesNo", "sg desc", "Text"}, 3, true),
                new Spec("select_items", () -> call("upsertSelectItem", fields("select_items"), SURVEY_ID, selectGroupIds),
                        new Object[]{501L, 2, 1021L, "Yes", 1, "Y"}, 2, true),
                new Spec("steps", () -> call("upsertStep", fields("steps"), SURVEY_ID),
                        new Object[]{502L, 2041L, 2, new BigDecimal("1.50"), "Step One", "Step One", "step desc"}, 3, true),
                new Spec("sections", () -> call("upsertSection", fields("sections"), SURVEY_ID),
                        new Object[]{503L, 2051L, 2, new BigDecimal("2.0"), "Section One", "SecDim", "sec desc"}, 3, true),
                new Spec("steps_sections", () -> call("upsertStepsSection", fields("steps_sections"), SURVEY_ID, stepIds, sectionIds),
                        new Object[]{504L, 2061L, 2, 2041L, new BigDecimal("1"), 2051L, new BigDecimal("2.25"),
                                "0007-0001-0000-0001-0000-0000-0000"}, 3, true),
                new Spec("questions", () -> call("upsertQuestion", fields("questions"), SURVEY_ID, selectGroupIds),
                        new Object[]{505L, 2071L, 2, 3, "Text", "Short", "Tip", true, 1, 10, "vt", 1021L,
                                "mask", "ph", "dv", "var"}, 3, true),
                new Spec("translations", () -> call("upsertTranslation", fields("translations"), SURVEY_ID),
                        new Object[]{506L, 2151L, 2, "questions", UUID.fromString(QUESTION_KEY), "text", "es", "Texto", "hash1"}, 3, true),
                new Spec("sections_questions", () -> call("upsertSectionsQuestion", fields("sections_questions"), SURVEY_ID,
                        questionIds, sectionIds),
                        new Object[]{507L, 2081L, 2, 2071L, 2051L, new BigDecimal("3.50")}, 3, true),
                new Spec("relationships", () -> call("upsertRelationship", fields("relationships"), SURVEY_ID, stepIds,
                        sectionsQuestionIds, stepsSectionIds),
                        new Object[]{508L, 2, 2041L, 2081L, 2041L, 2061L, 2081L, 1, 2, "rd", "tok", "ref", "dup", "oup"}, 2, true),
                new Spec("reports", () -> call("upsertReport", fields("reports"), SURVEY_ID),
                        new Object[]{509L, "Report", "rep desc", "http://report", 4}, 1, false),
                new Spec("report_items", () -> call("upsertReportItem", fields("report_items"), SURVEY_ID, stepsSectionIds,
                        questionIds, sectionsQuestionIds),
                        new Object[]{510L, "RESPONDENT", 2061L, 2071L, 2081L, "label", 3}, 1, false),
                new Spec("post_survey_actions", () -> call("upsertPostSurveyAction", fields("post_survey_actions"), SURVEY_ID),
                        new Object[]{511L, "Action", "act desc", "http://action", 2}, 1, false),
                new Spec("ontology", () -> call("upsertOrReuseOntology", fields("ontology"), SURVEY_ID, dimensionIds),
                        new Object[]{512L, "Female", "F", 2111L}, 1, false),
                new Spec("metadata", () -> call("upsertMetadata", fields("metadata"), SURVEY_ID, stepsSectionIds, questionIds,
                        sectionsQuestionIds, ontologyIds),
                        new Object[]{513L, 2061L, 2071L, 2081L, 2121L, "value"}, 1, false));
    }

    // ---------------------------------------------------------------- CREATED / UNCHANGED / changed, every table

    @Test
    void given_noCurrentRow_when_aRecordIsReconciled_then_itIsCreatedAtTheTarget() {
        for (Spec spec : specs()) {
            setUp();
            Object outcome = spec.upsert().get();
            assertEquals(ChangeType.CREATED, changeOf(outcome), spec.table());
            ScriptedEntityManager.Call insert = insertInto(spec.table());
            assertTrue(insert.params().containsValue(SURVEY_ID), spec.table());
            assertTrue(db.callsContaining("SET effective_to = NOW()").isEmpty(), spec.table());
            ScriptedEntityManager.Call lookup = db.calls.get(0);
            assertEquals(SURVEY_ID, lookup.param(1), spec.table());
            assertEquals(UUID.fromString(fields(spec.table())[1]), lookup.param(2), spec.table());
        }
    }

    @Test
    void given_aCurrentRowEqualToTheRecord_when_reconciled_then_nothingIsWritten() {
        for (Spec spec : specs()) {
            setUp();
            currentRows.put("survey." + spec.table(), List.of((Object) spec.stored()));
            Object outcome = spec.upsert().get();
            assertEquals(ChangeType.UNCHANGED, changeOf(outcome), spec.table());
            assertEquals(List.of(), writes(), spec.table());
        }
    }

    @Test
    void given_aCurrentRowDifferingInOneComparedColumn_when_reconciled_then_thatAloneChangesTheRow() {
        for (Spec spec : specs()) {
            for (int column = spec.firstCompared(); column < spec.stored().length; column++) {
                setUp();
                Object[] stored = spec.stored().clone();
                stored[column] = different(stored[column]);
                currentRows.put("survey." + spec.table(), List.of((Object) stored));
                Object outcome = spec.upsert().get();
                String what = spec.table() + " column " + column;
                assertEquals(ChangeType.VERSIONED, changeOf(outcome), what);
                if (spec.type2()) {
                    ScriptedEntityManager.Call close = db.only("SET effective_to = NOW() WHERE id = ?1");
                    assertEquals(stored[0], close.param(1), what);
                    assertTrue(close.sql().startsWith("UPDATE survey." + spec.table() + " "), what);
                    ScriptedEntityManager.Call insert = insertInto(spec.table());
                    assertTrue(insert.sql().contains("'9999-12-31 23:59:59+00'"), what);
                    assertTrue(insert.params().containsValue(3), what + ": version + 1");
                } else {
                    List<ScriptedEntityManager.Call> updates = db.calls.stream()
                            .filter(c -> c.sql().matches("(?s)UPDATE survey\\." + spec.table() + "\\s.*")).toList();
                    assertEquals(1, updates.size(), what);
                    ScriptedEntityManager.Call update = updates.get(0);
                    assertTrue(update.params().containsValue(stored[0]), what);
                    assertTrue(db.callsContaining("INSERT").isEmpty(), what);
                }
            }
        }
    }

    // ---------------------------------------------------------------- per-table detail

    @Test
    void given_aNewSelectGroup_when_created_then_itsDurableIdIsReadBackAndTextIsTheDefaultType() {
        Object outcome = call("upsertSelectGroup", with(fields("select_groups"), 4, ""), SURVEY_ID);
        ScriptedEntityManager.Call insert = insertInto("select_groups");
        assertEquals(901L, insert.param(1));
        assertEquals(SURVEY_ID, insert.param(2));
        assertEquals(UUID.fromString(key(21)), insert.param(3));
        assertEquals("YesNo", insert.param(4));
        assertEquals("sg desc", insert.param(5));
        assertEquals("Text", insert.param(6));
        assertEquals(1901L, durableOf(outcome));
        assertEquals(901L, db.only("SELECT select_group_id FROM survey.select_groups WHERE id = ?1").param(1));
    }

    @Test
    void given_aChangedSelectGroup_when_versioned_then_theSameDurableIdCarriesTheNextVersion() {
        currentRows.put("survey.select_groups", List.of((Object) new Object[]{500L, 1021L, 4, "Old", "sg desc", "Text"}));
        Object outcome = call("upsertSelectGroup", fields("select_groups"), SURVEY_ID);
        assertEquals(1021L, durableOf(outcome));
        ScriptedEntityManager.Call insert = insertInto("select_groups");
        assertEquals(901L, insert.param(1));
        assertEquals("YesNo", insert.param(4));
        assertEquals(1021L, insert.param(7));
        assertEquals(5, insert.param(8));
    }

    @Test
    void given_aChangedSelectItem_when_versioned_then_itsDurableIdIsCopiedFromTheClosedRow() {
        currentRows.put("survey.select_items", List.of((Object) new Object[]{501L, 6, 1021L, "Old", 1, "Y"}));
        assertEquals(ChangeType.VERSIONED, call("upsertSelectItem", fields("select_items"), SURVEY_ID, selectGroupIds));
        ScriptedEntityManager.Call insert = insertInto("select_items");
        assertEquals(SURVEY_ID, insert.param(1));
        assertEquals(UUID.fromString(key(31)), insert.param(2));
        assertEquals(1021L, insert.param(3));
        assertEquals("Yes", insert.param(4));
        assertEquals(1, insert.param(5));
        assertEquals("Y", insert.param(6));
        assertEquals(501L, insert.param(7));
        assertEquals(7, insert.param(8));
    }

    @Test
    void given_aNewStep_when_created_then_anAbsentDimensionNameIsTheStepName() {
        Object outcome = call("upsertStep", fields("steps"), SURVEY_ID);
        ScriptedEntityManager.Call insert = insertInto("steps");
        assertEquals(new BigDecimal("1.5"), insert.param(4));
        assertEquals("Step One", insert.param(5));
        assertEquals("Step One", insert.param(6));
        assertEquals("step desc", insert.param(7));
        assertEquals(1901L, durableOf(outcome));
    }

    @Test
    void given_aStepWithNoNameOrDimension_when_created_then_theDimensionNameIsEmpty() {
        call("upsertStep", with(with(fields("steps"), 3, ""), 4, ""), SURVEY_ID);
        assertEquals("", insertInto("steps").param(6));
        setUp();
        call("upsertSection", with(with(fields("sections"), 3, ""), 4, ""), SURVEY_ID);
        assertEquals("", insertInto("sections").param(6));
        setUp();
        call("upsertSection", with(fields("sections"), 4, ""), SURVEY_ID);
        assertEquals("Section One", insertInto("sections").param(6));
    }

    @Test
    void given_aChangedStepOrSection_when_versioned_then_theDurableIdAndNextVersionAreWritten() {
        currentRows.put("survey.steps", List.of((Object) new Object[]{502L, 2041L, 0, new BigDecimal("9"), "Step One", "Step One", "step desc"}));
        Object step = call("upsertStep", fields("steps"), SURVEY_ID);
        assertEquals(2041L, durableOf(step));
        ScriptedEntityManager.Call insert = insertInto("steps");
        assertEquals(901L, insert.param(1));
        assertEquals(2041L, insert.param(8));
        assertEquals(1, insert.param(9));

        setUp();
        currentRows.put("survey.sections", List.of((Object) new Object[]{503L, 2051L, 0, new BigDecimal("9"), "Section One", "SecDim", "sec desc"}));
        Object section = call("upsertSection", fields("sections"), SURVEY_ID);
        assertEquals(2051L, durableOf(section));
        ScriptedEntityManager.Call sectionInsert = insertInto("sections");
        assertEquals(new BigDecimal("2"), sectionInsert.param(4));
        assertEquals("Section One", sectionInsert.param(5));
        assertEquals("SecDim", sectionInsert.param(6));
        assertEquals("sec desc", sectionInsert.param(7));
        assertEquals(2051L, sectionInsert.param(8));
        assertEquals(1, sectionInsert.param(9));
    }

    @Test
    void given_aStepsSection_when_reconciled_then_itsReferencesAndDisplayKeyAreThisDeploymentsOwn() {
        Object created = call("upsertStepsSection", fields("steps_sections"), SURVEY_ID, stepIds, sectionIds);
        ScriptedEntityManager.Call insert = insertInto("steps_sections");
        assertEquals(2041L, insert.param(4));
        assertEquals(new BigDecimal("1"), insert.param(5));
        assertEquals(2051L, insert.param(6));
        assertEquals(new BigDecimal("2.25"), insert.param(7));
        assertEquals("0007-0001-0000-0001-0000-0000-0000", insert.param(8));
        assertEquals(1901L, durableOf(created));

        setUp();
        currentRows.put("survey.steps_sections", List.of((Object) new Object[]{504L, 2061L, 1, 2041L, new BigDecimal("1"),
                2051L, new BigDecimal("2.25"), "0011-0001-0000-0001-0000-0000-0000"}));
        Object versioned = call("upsertStepsSection", fields("steps_sections"), SURVEY_ID, stepIds, sectionIds);
        assertEquals(ChangeType.VERSIONED, changeOf(versioned), "the file's raw key is not this deployment's");
        assertEquals(2061L, durableOf(versioned));
        ScriptedEntityManager.Call vInsert = insertInto("steps_sections");
        assertEquals("0007-0001-0000-0001-0000-0000-0000", vInsert.param(8));
        assertEquals(2061L, vInsert.param(9));
        assertEquals(2, vInsert.param(10));
    }

    @Test
    void given_aQuestion_when_reconciled_then_everyColumnIsWrittenAndVersionsKeepTheDurableId() {
        Object created = call("upsertQuestion", fields("questions"), SURVEY_ID, selectGroupIds);
        ScriptedEntityManager.Call insert = insertInto("questions");
        List<Object> expected = List.of(901L, SURVEY_ID, UUID.fromString(QUESTION_KEY), 3, "Text", "Short", "Tip", true,
                1, 10, "vt", 1021L, "mask", "ph", "dv", "var");
        for (int i = 0; i < expected.size(); i++) {
            assertEquals(expected.get(i), insert.param(i + 1), "parameter " + (i + 1));
        }
        assertEquals(1901L, durableOf(created));

        setUp();
        currentRows.put("survey.questions", List.of((Object) new Object[]{505L, 2071L, 8, 3, "Old", "Short", "Tip", true, 1, 10,
                "vt", 1021L, "mask", "ph", "dv", "var"}));
        Object versioned = call("upsertQuestion", fields("questions"), SURVEY_ID, selectGroupIds);
        assertEquals(2071L, durableOf(versioned));
        ScriptedEntityManager.Call vInsert = insertInto("questions");
        for (int i = 1; i < expected.size(); i++) {
            assertEquals(expected.get(i), vInsert.param(i + 1), "parameter " + (i + 1));
        }
        assertEquals(2071L, vInsert.param(17));
        assertEquals(9, vInsert.param(18));
    }

    @Test
    void given_aQuestionWithoutASelectGroup_when_created_then_itHasNone() {
        call("upsertQuestion", with(with(fields("questions"), 10, ""), 6, "f"), SURVEY_ID, Map.of());
        ScriptedEntityManager.Call insert = insertInto("questions");
        assertNull(insert.param(12));
        assertEquals(false, insert.param(8));
    }

    @Test
    void given_aQuestionNamingAnUnknownSelectGroup_when_reconciled_then_itFails() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> call("upsertQuestion", fields("questions"), SURVEY_ID, Map.of()));
        assertEquals(Translations.get("definition.noIdMapping", "select_group (for question)", "21"), e.getMessage());
    }

    @Test
    void given_aTranslation_when_reconciled_then_itIsMatchedByItsOwnKeyAndVersioned() {
        Object created = call("upsertTranslation", fields("translations"), SURVEY_ID);
        ScriptedEntityManager.Call insert = insertInto("translations");
        List<Object> expected = List.of(901L, SURVEY_ID, UUID.fromString(key(151)), "questions",
                UUID.fromString(QUESTION_KEY), "text", "es", "Texto", "hash1");
        for (int i = 0; i < expected.size(); i++) {
            assertEquals(expected.get(i), insert.param(i + 1), "parameter " + (i + 1));
        }
        assertEquals(1901L, durableOf(created));
        assertEquals(UUID.fromString(key(151)), db.calls.get(0).param(2));

        setUp();
        currentRows.put("survey.translations", List.of((Object) new Object[]{506L, 2151L, 0, "questions",
                UUID.fromString(QUESTION_KEY), "text", "es", "Texto", "stale"}));
        Object versioned = call("upsertTranslation", fields("translations"), SURVEY_ID);
        assertEquals(2151L, durableOf(versioned));
        ScriptedEntityManager.Call vInsert = insertInto("translations");
        assertEquals("hash1", vInsert.param(9));
        assertEquals(2151L, vInsert.param(10));
        assertEquals(1, vInsert.param(11));
    }

    @Test
    void given_aMalformedTranslation_when_reconciled_then_itIsRefused() {
        IllegalArgumentException notTranslatable = assertThrows(IllegalArgumentException.class,
                () -> call("upsertTranslation", with(fields("translations"), 4, "mask"), SURVEY_ID));
        assertEquals(Translations.get("definition.translationNotTranslatable", "questions.mask"), notTranslatable.getMessage());

        IllegalArgumentException empty = assertThrows(IllegalArgumentException.class,
                () -> call("upsertTranslation", with(fields("translations"), 6, ""), SURVEY_ID));
        assertEquals(Translations.get("definition.translationEmpty", "questions.text"), empty.getMessage());

        IllegalStateException noTarget = assertThrows(IllegalStateException.class,
                () -> call("upsertTranslation", with(fields("translations"), 3, ""), SURVEY_ID));
        assertEquals(Translations.get("definition.noElementKey", "translations (element_key)"), noTarget.getMessage());
        assertTrue(db.calls.isEmpty());
    }

    @Test
    void given_aChangedSectionsQuestion_when_versioned_then_theDurableIdAndNextVersionAreWritten() {
        Object created = call("upsertSectionsQuestion", fields("sections_questions"), SURVEY_ID, questionIds, sectionIds);
        ScriptedEntityManager.Call insert = insertInto("sections_questions");
        assertEquals(2071L, insert.param(4));
        assertEquals(2051L, insert.param(5));
        assertEquals(new BigDecimal("3.5"), insert.param(6));
        assertEquals(1901L, durableOf(created));

        setUp();
        currentRows.put("survey.sections_questions", List.of((Object) new Object[]{507L, 2081L, 3, 2071L, 2051L, new BigDecimal("1")}));
        Object versioned = call("upsertSectionsQuestion", fields("sections_questions"), SURVEY_ID, questionIds, sectionIds);
        assertEquals(2081L, durableOf(versioned));
        ScriptedEntityManager.Call vInsert = insertInto("sections_questions");
        assertEquals(new BigDecimal("3.5"), vInsert.param(6));
        assertEquals(2081L, vInsert.param(7));
        assertEquals(4, vInsert.param(8));
    }

    @Test
    void given_aRelationship_when_reconciled_then_everyReferenceIsResolvedAndVersionsCopyTheDurableId() {
        assertEquals(ChangeType.CREATED, call("upsertRelationship", fields("relationships"), SURVEY_ID, stepIds,
                sectionsQuestionIds, stepsSectionIds));
        ScriptedEntityManager.Call insert = insertInto("relationships");
        List<Object> expected = List.of(SURVEY_ID, UUID.fromString(key(91)), 2041L, 2081L, 2041L, 2061L, 2081L, 1, 2,
                "rd", "tok", "ref", "dup", "oup");
        for (int i = 0; i < expected.size(); i++) {
            assertEquals(expected.get(i), insert.param(i + 1), "parameter " + (i + 1));
        }

        setUp();
        currentRows.put("survey.relationships", List.of((Object) new Object[]{508L, 5, 2041L, 2081L, 2041L, 2061L, 2081L, 1, 2,
                "rd", "tok", "ref", "dup", "old"}));
        assertEquals(ChangeType.VERSIONED, call("upsertRelationship", fields("relationships"), SURVEY_ID, stepIds,
                sectionsQuestionIds, stepsSectionIds));
        ScriptedEntityManager.Call vInsert = insertInto("relationships");
        for (int i = 0; i < expected.size(); i++) {
            assertEquals(expected.get(i), vInsert.param(i + 1), "parameter " + (i + 1));
        }
        assertEquals(508L, vInsert.param(15));
        assertEquals(6, vInsert.param(16));
    }

    @Test
    void given_aRelationshipWithOnlyItsUpstreamQuestion_when_created_then_theOptionalReferencesAreNull() {
        String[] f = fields("relationships");
        f = with(with(with(with(f, 2, ""), 4, ""), 5, ""), 6, "");
        call("upsertRelationship", f, SURVEY_ID, Map.of(), sectionsQuestionIds, Map.of());
        ScriptedEntityManager.Call insert = insertInto("relationships");
        assertNull(insert.param(3));
        assertEquals(2081L, insert.param(4));
        assertNull(insert.param(5));
        assertNull(insert.param(6));
        assertNull(insert.param(7));
    }

    @Test
    void given_type1Records_when_changed_then_theyAreUpdatedInPlace() {
        currentRows.put("survey.reports", List.of((Object) new Object[]{509L, "Old", "rep desc", "http://report", 4}));
        assertEquals(ChangeType.VERSIONED, call("upsertReport", fields("reports"), SURVEY_ID));
        ScriptedEntityManager.Call report = db.only("UPDATE survey.reports");
        assertEquals(List.of("Report", "rep desc", "http://report", 4, 509L), List.copyOf(report.params().values()));
        assertTrue(!db.calls.get(0).sql().contains("effective_to"), "Type 1 tables have no current-row filter");

        setUp();
        currentRows.put("survey.post_survey_actions", List.of((Object) new Object[]{511L, "Old", "act desc", "http://action", 2}));
        assertEquals(ChangeType.VERSIONED, call("upsertPostSurveyAction", fields("post_survey_actions"), SURVEY_ID));
        ScriptedEntityManager.Call action = db.only("UPDATE survey.post_survey_actions");
        assertEquals(List.of("Action", "act desc", "http://action", 2, 511L), List.copyOf(action.params().values()));

        setUp();
        currentRows.put("survey.report_items", List.of((Object) new Object[]{510L, "ADMIN", 2061L, 2071L, 2081L, "label", 3}));
        assertEquals(ChangeType.VERSIONED, call("upsertReportItem", fields("report_items"), SURVEY_ID, stepsSectionIds,
                questionIds, sectionsQuestionIds));
        ScriptedEntityManager.Call item = db.only("UPDATE survey.report_items\n");
        assertEquals(List.of("RESPONDENT", 2061L, 2071L, 2081L, "label", 3, 510L), List.copyOf(item.params().values()));

        setUp();
        currentRows.put("survey.metadata", List.of((Object) new Object[]{513L, 2061L, 2071L, 2081L, 2121L, "old"}));
        assertEquals(ChangeType.VERSIONED, call("upsertMetadata", fields("metadata"), SURVEY_ID, stepsSectionIds, questionIds,
                sectionsQuestionIds, ontologyIds));
        ScriptedEntityManager.Call meta = db.only("UPDATE survey.metadata\n");
        assertEquals(List.of(2061L, 2071L, 2081L, 2121L, "value", 513L), List.copyOf(meta.params().values()));

        setUp();
        currentRows.put("survey.ontology", List.of((Object) new Object[]{512L, "Female", "M", 2111L}));
        Object ontology = call("upsertOrReuseOntology", fields("ontology"), SURVEY_ID, dimensionIds);
        assertEquals(ChangeType.VERSIONED, changeOf(ontology));
        assertEquals(512L, durableOf(ontology));
        ScriptedEntityManager.Call ont = db.only("UPDATE survey.ontology");
        assertEquals(List.of("Female", "F", 2111L, 512L), List.copyOf(ont.params().values()));
    }

    @Test
    void given_type1Records_when_new_then_theyAreInsertedWithEveryColumn() {
        call("upsertReport", fields("reports"), SURVEY_ID);
        assertEquals(List.of(SURVEY_ID, UUID.fromString(key(101)), "Report", "rep desc", "http://report", 4),
                List.copyOf(insertInto("reports").params().values()));
        call("upsertPostSurveyAction", fields("post_survey_actions"), SURVEY_ID);
        assertEquals(List.of(SURVEY_ID, UUID.fromString(key(102)), "Action", "act desc", "http://action", 2),
                List.copyOf(insertInto("post_survey_actions").params().values()));
        call("upsertReportItem", with(fields("report_items"), 7, ""), SURVEY_ID, stepsSectionIds, questionIds, sectionsQuestionIds);
        assertEquals(List.of(SURVEY_ID, UUID.fromString(key(141)), "RESPONDENT", 2061L, 2071L, 2081L, "label", 0),
                List.copyOf(insertInto("report_items").params().values()));
        call("upsertMetadata", fields("metadata"), SURVEY_ID, stepsSectionIds, questionIds, sectionsQuestionIds, ontologyIds);
        assertEquals(List.of(SURVEY_ID, UUID.fromString(key(131)), 2061L, 2071L, 2081L, 2121L, "value"),
                List.copyOf(insertInto("metadata").params().values()));
    }

    @Test
    void given_aReportMarkWithNoOrder_when_storedAtZero_then_itIsUnchanged() {
        currentRows.put("survey.report_items", List.of((Object) new Object[]{510L, "RESPONDENT", 2061L, 2071L, 2081L, "label", 0L}));
        assertEquals(ChangeType.UNCHANGED, call("upsertReportItem", with(fields("report_items"), 7, ""), SURVEY_ID,
                stepsSectionIds, questionIds, sectionsQuestionIds));
    }

    @Test
    void given_anOntologyNotYetHere_when_reconciled_then_anExistingNameAndTagIsReusedOrANewRowInserted() {
        existingOntologyId = 44L;
        Object reused = call("upsertOrReuseOntology", fields("ontology"), SURVEY_ID, dimensionIds);
        assertEquals(ChangeType.UNCHANGED, changeOf(reused));
        assertEquals(44L, durableOf(reused));
        ScriptedEntityManager.Call lookup = db.only("SELECT id FROM survey.ontology WHERE name = ?1 AND tag = ?2");
        assertEquals("Female", lookup.param(1));
        assertEquals("F", lookup.param(2));
        assertEquals(List.of(), writes());

        setUp();
        Object created = call("upsertOrReuseOntology", fields("ontology"), SURVEY_ID, dimensionIds);
        assertEquals(ChangeType.CREATED, changeOf(created));
        assertEquals(901L, durableOf(created));
        assertEquals(List.of(901L, SURVEY_ID, UUID.fromString(key(121)), "Female", "F", 2111L),
                List.copyOf(insertInto("ontology").params().values()));
    }

    @Test
    void given_aDimension_when_reconciled_then_anExistingNameIsReusedOrANewRowInserted() {
        existingDimensionId = 33L;
        Object reused = call("upsertOrReuseDimension", (Object) fields("dimensions"));
        assertEquals(ChangeType.UNCHANGED, changeOf(reused));
        assertEquals(33L, durableOf(reused));
        assertEquals("Gender", db.only("SELECT id FROM survey.dimensions WHERE name = ?1").param(1));
        assertEquals(List.of(), writes());

        setUp();
        Object created = call("upsertOrReuseDimension", (Object) fields("dimensions"));
        assertEquals(ChangeType.CREATED, changeOf(created));
        assertEquals(901L, durableOf(created));
        assertEquals(List.of(901L, UUID.fromString(key(111)), "Gender"), List.copyOf(insertInto("dimensions").params().values()));
    }

    // ---------------------------------------------------------------- malformed records (A5)

    @Test
    void given_aRecordOneFieldShort_when_reconciled_then_itIsRefusedBeforeAnyQuery() {
        Map<String, Integer> required = new LinkedHashMap<>(SurveyDefinitionImportServiceUnitTest.REQUIRED);
        required.remove("surveys");
        Map<String, String> method = Map.ofEntries(
                Map.entry("select_groups", "upsertSelectGroup"), Map.entry("select_items", "upsertSelectItem"),
                Map.entry("steps", "upsertStep"), Map.entry("sections", "upsertSection"),
                Map.entry("steps_sections", "upsertStepsSection"), Map.entry("questions", "upsertQuestion"),
                Map.entry("sections_questions", "upsertSectionsQuestion"), Map.entry("relationships", "upsertRelationship"),
                Map.entry("reports", "upsertReport"), Map.entry("post_survey_actions", "upsertPostSurveyAction"),
                Map.entry("dimensions", "upsertOrReuseDimension"), Map.entry("ontology", "upsertOrReuseOntology"),
                Map.entry("metadata", "upsertMetadata"), Map.entry("report_items", "upsertReportItem"),
                Map.entry("translations", "upsertTranslation"));
        required.forEach((table, count) -> {
            setUp();
            String[] shortRecord = Arrays.copyOf(fields(table), count - 1);
            Object[] args = argsFor(method.get(table), shortRecord);
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                    () -> call(method.get(table), args), table);
            assertEquals(Translations.get("definition.fieldCount", table, String.valueOf(count), String.valueOf(count - 1)),
                    e.getMessage(), table);
            assertTrue(db.calls.isEmpty(), table);
        });
    }

    private Object[] argsFor(String method, String[] f) {
        return switch (method) {
            case "upsertSelectGroup", "upsertStep", "upsertSection", "upsertTranslation", "upsertReport",
                    "upsertPostSurveyAction" -> new Object[]{f, SURVEY_ID};
            case "upsertOrReuseDimension" -> new Object[]{f};
            case "upsertSelectItem", "upsertQuestion" -> new Object[]{f, SURVEY_ID, selectGroupIds};
            case "upsertStepsSection" -> new Object[]{f, SURVEY_ID, stepIds, sectionIds};
            case "upsertSectionsQuestion" -> new Object[]{f, SURVEY_ID, questionIds, sectionIds};
            case "upsertRelationship" -> new Object[]{f, SURVEY_ID, stepIds, sectionsQuestionIds, stepsSectionIds};
            case "upsertOrReuseOntology" -> new Object[]{f, SURVEY_ID, dimensionIds};
            case "upsertMetadata" -> new Object[]{f, SURVEY_ID, stepsSectionIds, questionIds, sectionsQuestionIds, ontologyIds};
            case "upsertReportItem" -> new Object[]{f, SURVEY_ID, stepsSectionIds, questionIds, sectionsQuestionIds};
            default -> throw new AssertionError(method);
        };
    }

    @Test
    void given_aRecordWithoutAnElementKey_when_reconciled_then_itIsRefusedNotDefaulted() {
        IllegalStateException missing = assertThrows(IllegalStateException.class,
                () -> call("upsertStep", with(fields("steps"), 1, ""), SURVEY_ID));
        assertEquals(Translations.get("definition.noElementKey", "steps"), missing.getMessage());

        IllegalArgumentException invalid = assertThrows(IllegalArgumentException.class,
                () -> call("upsertStep", with(fields("steps"), 1, "nope"), SURVEY_ID));
        assertEquals(Translations.get("definition.invalidElementKey", "steps", "nope"), invalid.getMessage());
        assertTrue(invalid.getCause() instanceof IllegalArgumentException);

        assertEquals(UUID.fromString(key(41)), call("requireElementKey", " " + key(41) + " ", "steps"));
    }

    // ---------------------------------------------------------------- retire (BR-068, BR-110)

    @Test
    void given_aRetiredRecordWithACurrentRow_when_applied_then_theRowAndItsTranslationsAreClosed() {
        currentRows.put("survey.steps", List.of((Object) new Object[]{502L, 2041L}));
        Object outcome = call("retireCurrentRow", "survey.steps", "step_key", "step_id", fields("steps"), SURVEY_ID);

        assertEquals(ChangeType.RETIRED, changeOf(outcome));
        assertEquals(2041L, durableOf(outcome));
        ScriptedEntityManager.Call lookup = db.calls.get(0);
        assertEquals("SELECT id, step_id FROM survey.steps WHERE survey_id = ?1 AND step_key = ?2 AND effective_to = '"
                + OPEN + "'", lookup.sql());
        assertEquals(502L, db.only("UPDATE survey.steps SET effective_to = NOW() WHERE id = ?1").param(1));
        ScriptedEntityManager.Call translations = db.only("UPDATE survey.translations SET effective_to = NOW()");
        assertEquals(SURVEY_ID, translations.param(1));
        assertEquals(UUID.fromString(key(41)), translations.param(2));
        assertTrue(translations.sql().contains("element_key = ?2"));
    }

    @Test
    void given_aRetiredTranslation_when_applied_then_onlyThatRowIsClosed() {
        currentRows.put("survey.translations", List.of((Object) new Object[]{506L, 2151L}));
        Object outcome = call("retireCurrentRow", "survey.translations", "translation_key", "translation_id",
                fields("translations"), SURVEY_ID);
        assertEquals(ChangeType.RETIRED, changeOf(outcome));
        assertEquals(1, writes().size());
        assertEquals(506L, writes().get(0).param(1));
    }

    @Test
    void given_aRetiredRecordAlreadyClosedHere_when_applied_then_itsLatestDurableIdIsStillResolved() {
        latestRows.put("survey.steps", List.of((Object) 2041));
        Object outcome = call("retireCurrentRow", "survey.steps", "step_key", "step_id", fields("steps"), SURVEY_ID);
        assertEquals(ChangeType.UNCHANGED, changeOf(outcome));
        assertEquals(2041L, durableOf(outcome));
        assertEquals(List.of(), writes());
        ScriptedEntityManager.Call latest = db.only("ORDER BY version DESC LIMIT 1");
        assertEquals("SELECT step_id FROM survey.steps WHERE survey_id = ?1 AND step_key = ?2 ORDER BY version DESC LIMIT 1",
                latest.sql());
        assertEquals(SURVEY_ID, latest.param(1));
        assertEquals(UUID.fromString(key(41)), latest.param(2));
    }

    @Test
    void given_aRetiredRecordNeverInstalledHere_when_applied_then_nothingHappens() {
        Object outcome = call("retireCurrentRow", "survey.steps", "step_key", "step_id", fields("steps"), SURVEY_ID);
        assertEquals(ChangeType.UNCHANGED, changeOf(outcome));
        assertNull(durableOf(outcome));
        assertEquals(List.of(), writes());
    }

    @Test
    void given_aRetiredRecordWithoutAKey_when_applied_then_theTableIsNamedInTheError() {
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> call("retireCurrentRow",
                "survey.select_items", "select_item_key", "select_item_id", with(fields("select_items"), 1, ""), SURVEY_ID));
        assertEquals(Translations.get("definition.noElementKey", "select_items"), e.getMessage());
    }

    // ---------------------------------------------------------------- findCurrentRow

    @Test
    void given_eachTable_when_itsCurrentRowIsLookedUp_then_onlyType2TablesFilterOnTheOpenEnd() {
        UUID k = UUID.randomUUID();
        for (String table : List.of("survey.reports", "survey.post_survey_actions", "survey.ontology", "survey.metadata",
                "survey.report_items")) {
            setUp();
            assertNull(call("findCurrentRow", table, "k", k, SURVEY_ID, "id"));
            assertEquals("SELECT id FROM " + table + " WHERE survey_id = ?1 AND k = ?2", db.calls.get(0).sql());
        }
        for (String table : List.of("survey.steps", "survey.questions", "survey.translations")) {
            setUp();
            call("findCurrentRow", table, "k", k, SURVEY_ID, "id");
            assertEquals("SELECT id FROM " + table + " WHERE survey_id = ?1 AND k = ?2 AND effective_to = '" + OPEN + "'",
                    db.calls.get(0).sql());
        }
    }

    @Test
    void given_aSingleColumnResult_when_looked_up_then_itIsWrappedAsARow() {
        currentRows.put("survey.reports", List.of((Object) 5L, (Object) 6L));
        Object[] row = (Object[]) call("findCurrentRow", "survey.reports", "report_key", UUID.randomUUID(), SURVEY_ID, "id");
        assertEquals(List.of(5L), Arrays.asList(row));
        Object[] stored = {1L, "x"};
        currentRows.put("survey.reports", List.of((Object) stored));
        assertSame(stored, call("findCurrentRow", "survey.reports", "report_key", UUID.randomUUID(), SURVEY_ID, "id, name"));
    }

    // ---------------------------------------------------------------- BR-113

    @Test
    void given_reportsTheFileNoLongerLists_when_applied_then_exactlyThoseAreDeleted() {
        UUID kept = UUID.randomUUID();
        UUID gone1 = UUID.randomUUID();
        UUID gone2 = UUID.randomUUID();
        db.answer = call -> {
            if (call.sql().startsWith("SELECT report_key")) {
                return List.of(kept, gone1, gone2);
            }
            return call.kind() == ScriptedEntityManager.Kind.UPDATE ? 2 : answer(call);
        };
        int deleted = (int) call("deleteUnlisted", "survey.reports", "report_key", Set.of(kept), SURVEY_ID);

        assertEquals(4, deleted, "the sum of the rows each delete removed");
        assertEquals(SURVEY_ID, db.calls.get(0).param(1));
        assertEquals("SELECT report_key FROM survey.reports WHERE survey_id = ?1", db.calls.get(0).sql());
        List<ScriptedEntityManager.Call> deletes = db.callsContaining("DELETE FROM survey.reports WHERE survey_id = ?1 AND report_key = ?2");
        assertEquals(List.of(gone1, gone2), deletes.stream().map(c -> c.param(2)).toList());
        assertEquals(SURVEY_ID, deletes.get(0).param(1));
    }

    @Test
    void given_everyReportListed_when_applied_then_nothingIsDeleted() {
        UUID kept = UUID.randomUUID();
        db.answer = call -> call.kind() == ScriptedEntityManager.Kind.LIST ? List.of(kept) : 1;
        assertEquals(0, call("deleteUnlisted", "survey.report_items", "report_item_key", Set.of(kept), SURVEY_ID));
        assertEquals(1, db.calls.size());
    }

    // ---------------------------------------------------------------- the survey shell (BR-063..BR-065, BR-111)

    private static Survey target() {
        Survey survey = new Survey();
        survey.id = SURVEY_ID;
        survey.surveyKey = UUID.fromString(SURVEY_KEY);
        survey.name = "Family";
        survey.title = "Title";
        survey.description = "Desc";
        survey.initialDisplayKey = "0007-0001-0000-0001-0000-0000-0000";
        survey.postSurveyURL = "http://post";
        return survey;
    }

    private static String[] surveyFields() {
        return SurveyDefinitionFileFields.parseFields(SurveyDefinitionImportServiceUnitTest.SURVEY_LINE.split(": ", 2)[1]);
    }

    @Test
    void given_theTargetsOwnKey_when_matched_then_theFileBelongsToIt() {
        assertNull(call("matchSurveyKey", surveyFields(), target()));
        assertNull(call("matchSurveyKey", with(surveyFields(), 1, " " + SURVEY_KEY + " "), target()));
    }

    @Test
    void given_anotherSurveysKey_when_matched_then_theMismatchIsReported() {
        UUID other = UUID.randomUUID();
        assertEquals(Translations.get("definition.keyMismatch", other.toString(), SURVEY_KEY, "7"),
                call("matchSurveyKey", with(surveyFields(), 1, other.toString()), target()));
    }

    @Test
    void given_noKey_when_matched_then_theFileIsSentToImport() {
        assertEquals(Translations.get("definition.noSurveyKeyUpdate"), call("matchSurveyKey", with(surveyFields(), 1, ""), target()));
    }

    @Test
    void given_aMalformedSurveyRecord_when_matched_then_itThrows() {
        IllegalArgumentException invalid = assertThrows(IllegalArgumentException.class,
                () -> call("matchSurveyKey", with(surveyFields(), 1, "xyz"), target()));
        assertEquals(Translations.get("definition.invalidValue", "survey_key", "xyz"), invalid.getMessage());
        IllegalArgumentException shortRecord = assertThrows(IllegalArgumentException.class,
                () -> call("matchSurveyKey", Arrays.copyOf(surveyFields(), 9), target()));
        assertEquals(Translations.get("definition.fieldCount", "surveys", "10", "9"), shortRecord.getMessage());
        assertNull(call("matchSurveyKey", Arrays.copyOf(surveyFields(), 10), target()));
    }

    @Test
    void given_aSurveyRecordEqualToTheTarget_when_applied_then_itIsUnchangedButStillWritten() {
        languages = List.of((Object) new Object[]{"es", "en,es"});
        assertEquals(ChangeType.UNCHANGED, call("applySurveyAttributes", surveyFields(), target()));
        ScriptedEntityManager.Call langs = db.only("SELECT base_language, content_languages");
        assertEquals(SURVEY_ID, langs.param(1));
        ScriptedEntityManager.Call update = db.only("UPDATE survey.surveys");
        assertEquals(List.of("Family", "Title", "Desc", "0007-0001-0000-0001-0000-0000-0000", "http://post", "pub", "cmt",
                "es", "en,es", SURVEY_ID), List.copyOf(update.params().values()));
        assertTrue(!update.sql().contains("survey_key") && !update.sql().contains("display_order"));
    }

    @Test
    void given_aSurveyRecordDifferingInOneAttribute_when_applied_then_itIsReportedAsChanged() {
        Map<Integer, String> fieldChanges = Map.of(2, "Renamed", 4, "New title", 5, "New desc",
                6, "0007-0002-0000-0001-0000-0000-0000", 7, "http://elsewhere", 10, "fr", 11, "en");
        fieldChanges.forEach((index, value) -> {
            setUp();
            languages = List.of((Object) new Object[]{"es", "en,es"});
            assertEquals(ChangeType.VERSIONED, call("applySurveyAttributes", with(surveyFields(), index, value), target()),
                    "field " + index);
        });
    }

    @Test
    void given_aSurveyRecordWithoutLanguages_when_applied_then_englishAndNoneAreAssumed() {
        languages = List.of((Object) new Object[]{"en", null});
        assertEquals(ChangeType.UNCHANGED, call("applySurveyAttributes", Arrays.copyOf(surveyFields(), 10), target()));
        ScriptedEntityManager.Call update = db.only("UPDATE survey.surveys");
        assertEquals("en", update.param(8));
        assertNull(update.param(9));

        setUp();
        languages = List.of((Object) new Object[]{"en", null});
        assertEquals(ChangeType.UNCHANGED, call("applySurveyAttributes", with(with(surveyFields(), 10, ""), 11, ""), target()));

        setUp();
        languages = List.of((Object) new Object[]{"en", null});
        assertEquals(ChangeType.VERSIONED, call("applySurveyAttributes", Arrays.copyOf(surveyFields(), 11), target()),
                "an 11-field record still carries its base language");
    }

    @Test
    void given_aTargetWithNoLanguageRow_when_applied_then_itIsChanged() {
        assertEquals(ChangeType.VERSIONED, call("applySurveyAttributes", surveyFields(), target()));
    }

    // ---------------------------------------------------------------- revision regression (BR-069, BR-070)

    @Test
    void given_anOlderRevision_when_checked_then_itIsRefused() {
        appliedRevision = OffsetDateTime.parse("2026-05-06T07:08:09Z");
        OffsetDateTime older = OffsetDateTime.parse("2026-05-06T07:08:08Z");
        assertEquals(Translations.get("definition.revisionRegression", older.toString(), appliedRevision.toString()),
                call("checkRevisionRegression", older, UUID.fromString(SURVEY_KEY)));
    }

    @Test
    void given_anEqualNewerOrUnknownRevision_when_checked_then_itPasses() {
        appliedRevision = OffsetDateTime.parse("2026-05-06T07:08:09Z");
        assertNull(call("checkRevisionRegression", appliedRevision, UUID.fromString(SURVEY_KEY)));
        assertNull(call("checkRevisionRegression", appliedRevision.plusSeconds(1), UUID.fromString(SURVEY_KEY)));
        assertNull(call("checkRevisionRegression", null, UUID.fromString(SURVEY_KEY)));
        appliedRevision = null;
        assertNull(call("checkRevisionRegression", OffsetDateTime.parse("2000-01-01T00:00:00Z"), UUID.fromString(SURVEY_KEY)));
    }

    @Test
    void given_aRevisionMoreFinelyGrainedThanTheDatabase_when_checkedAgainstItsStoredForm_then_itIsNotARegression() {
        OffsetDateTime file = OffsetDateTime.parse("2026-05-06T07:08:09.118452955Z");
        appliedRevision = OffsetDateTime.parse("2026-05-06T07:08:09.118453Z");
        assertNull(call("checkRevisionRegression", file, UUID.fromString(SURVEY_KEY)));
    }

    @Test
    void given_anInstant_when_storedAtMicrosecondPrecision_then_itIsRoundedNotTruncated() {
        assertEquals(OffsetDateTime.parse("2026-05-06T07:08:09.118453Z"),
                SurveyDefinitionUpdateService.asStored(OffsetDateTime.parse("2026-05-06T07:08:09.118452955Z")));
        assertEquals(OffsetDateTime.parse("2026-05-06T07:08:09.118452Z"),
                SurveyDefinitionUpdateService.asStored(OffsetDateTime.parse("2026-05-06T07:08:09.118452499Z")));
        assertEquals(OffsetDateTime.parse("2026-05-06T07:08:09.118453Z"),
                SurveyDefinitionUpdateService.asStored(OffsetDateTime.parse("2026-05-06T07:08:09.118452500Z")));
        assertNull(SurveyDefinitionUpdateService.asStored(null));
    }

    // ---------------------------------------------------------------- audit (survey_log)

    @Test
    void given_anOutcome_when_logged_then_theTargetIsReferencedByIdEitherWay() {
        OffsetDateTime revision = OffsetDateTime.parse("2026-05-06T07:08:09Z");
        Map<String, SurveyDefinitionUpdateService.TableUpdateCounts> counts =
                Map.of("steps", new SurveyDefinitionUpdateService.TableUpdateCounts(1, 2, 3, 4));
        call("logAttempt", target(), "f.elicit", true, List.of(), counts, revision);
        call("logAttempt", target(), "f.elicit", false, List.of("a", "b"), null, revision);

        assertEquals(List.of("SUCCESS", 7L, UUID.fromString(SURVEY_KEY), "UPDATE", "f.elicit", String.valueOf(counts), revision),
                Arrays.asList(log.get(0)));
        assertEquals(List.of("FAILURE", 7L, UUID.fromString(SURVEY_KEY), "UPDATE", "f.elicit", "a; b", revision),
                Arrays.asList(log.get(1)));
    }

    // ---------------------------------------------------------------- counts and results

    @Test
    void given_changeTypes_when_counted_then_eachLandsInItsOwnColumn() throws ReflectiveOperationException {
        Class<?> mutable = Arrays.stream(SurveyDefinitionUpdateService.class.getDeclaredClasses())
                .filter(c -> c.getSimpleName().equals("MutableCounts")).findFirst().orElseThrow();
        Constructor<?> ctor = mutable.getDeclaredConstructor();
        ctor.setAccessible(true);
        Object counts = ctor.newInstance();
        Method record = mutable.getDeclaredMethod("record", ChangeType.class);
        Method retired = mutable.getDeclaredMethod("retired", int.class);
        Method toImmutable = mutable.getDeclaredMethod("toImmutable");
        record.setAccessible(true);
        retired.setAccessible(true);
        toImmutable.setAccessible(true);
        record.invoke(counts, ChangeType.CREATED);
        record.invoke(counts, ChangeType.VERSIONED);
        record.invoke(counts, ChangeType.VERSIONED);
        record.invoke(counts, ChangeType.UNCHANGED);
        record.invoke(counts, ChangeType.UNCHANGED);
        record.invoke(counts, ChangeType.UNCHANGED);
        record.invoke(counts, ChangeType.RETIRED);
        retired.invoke(counts, 3);
        assertEquals(new SurveyDefinitionUpdateService.TableUpdateCounts(1, 2, 3, 4), toImmutable.invoke(counts));
    }

    @Test
    void given_theLegacyConstructor_when_used_then_thereIsNoSurveyKey() {
        SurveyDefinitionUpdateService.UpdateResult result =
                new SurveyDefinitionUpdateService.UpdateResult(false, List.of("x"), Map.of());
        assertEquals(false, result.isSuccess());
        assertEquals(List.of("x"), result.getErrors());
        assertEquals(Map.of(), result.getCounts());
        assertNull(result.getSurveyKey());
        UUID k = UUID.randomUUID();
        assertEquals(k, new SurveyDefinitionUpdateService.UpdateResult(true, List.of(), null, k).getSurveyKey());
    }
}
