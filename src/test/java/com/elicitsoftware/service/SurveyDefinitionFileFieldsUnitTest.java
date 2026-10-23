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
import jakarta.persistence.Query;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UC-014: Import Survey Definition and UC-017: Update Survey Definition — the field parsing and
 * id resolution both consumers of the {@code ELICIT_SURVEY_EXPORT_V1} format share
 * ({@link SurveyDefinitionFileFields}): the revision header (BR-072, BR-070), escaping, retired
 * records (BR-076, BR-068), value comparison of numeric orders (BR-109), translatable fields
 * (BR-110) and the mapping of source ids onto this deployment's ids (BR-045).
 * <p>
 * The display-key rebase has its own test, {@link SurveyDefinitionDisplayKeyRebaseTest}.
 */
class SurveyDefinitionFileFieldsUnitTest {

    // ---------------------------------------------------------------- revision header

    @Test
    void given_aRevisionHeader_when_parsed_then_theOffsetDateTimeIsReturned() {
        assertEquals(OffsetDateTime.parse("2026-03-04T05:06:07.123456Z"),
                SurveyDefinitionFileFields.parseRevisionHeader("# survey_revision:   2026-03-04T05:06:07.123456Z  "));
    }

    @Test
    void given_anyOtherCommentLine_when_parsed_then_thereIsNoRevision() {
        assertNull(SurveyDefinitionFileFields.parseRevisionHeader("# generated: 2026-03-04T05:06:07Z"));
        assertNull(SurveyDefinitionFileFields.parseRevisionHeader("# ELICIT_SURVEY_EXPORT_V1"));
    }

    @Test
    void given_anEmptyRevisionHeader_when_parsed_then_itIsRejectedNotTreatedAsUnknown() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> SurveyDefinitionFileFields.parseRevisionHeader("# survey_revision:   "));
        assertEquals(Translations.get("definition.emptyHeader", SurveyDefinitionFileFields.REVISION_HEADER), e.getMessage());
    }

    @Test
    void given_anUnparseableRevisionHeader_when_parsed_then_itIsRejectedWithTheValue() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> SurveyDefinitionFileFields.parseRevisionHeader("# survey_revision: yesterday"));
        assertEquals(Translations.get("definition.unparseableHeader", SurveyDefinitionFileFields.REVISION_HEADER, "yesterday"),
                e.getMessage());
        assertTrue(e.getCause() instanceof java.time.format.DateTimeParseException);
    }

    @Test
    void given_aKeyWithAnEmptySurveyComponent_when_rebased_then_theSurveyIdIsPrefixed() {
        assertEquals("0003-0001", SurveyDefinitionFileFields.rebaseDisplayKey("-0001", 3));
    }

    // ---------------------------------------------------------------- parseFields

    @Test
    void given_plainFields_when_parsed_then_theyAreSplitOnPipes() {
        assertArrayEquals(new String[]{"1", "a", "", "b"}, SurveyDefinitionFileFields.parseFields("1|a||b"));
    }

    @Test
    void given_aTrailingPipe_when_parsed_then_aFinalEmptyFieldIsKept() {
        assertArrayEquals(new String[]{"a", ""}, SurveyDefinitionFileFields.parseFields("a|"));
        assertArrayEquals(new String[]{""}, SurveyDefinitionFileFields.parseFields(""));
    }

    @Test
    void given_escapes_when_parsed_then_theyAreDecoded() {
        assertArrayEquals(new String[]{"a|b", "c\\d", "e\nf", "g\rh"},
                SurveyDefinitionFileFields.parseFields("a\\|b|c\\\\d|e\\nf|g\\rh"));
    }

    @Test
    void given_anUnknownEscape_when_parsed_then_theBackslashIsKept() {
        assertArrayEquals(new String[]{"\\t", "x"}, SurveyDefinitionFileFields.parseFields("\\t|x"));
    }

    // ---------------------------------------------------------------- isRetired

    private static String[] row(int length, int index, String value) {
        String[] fields = new String[length];
        java.util.Arrays.fill(fields, "");
        fields[index] = value;
        return fields;
    }

    @Test
    void given_aClosedEffectiveTo_when_checked_then_theRecordIsRetired() {
        assertTrue(SurveyDefinitionFileFields.isRetired("steps", row(11, 8, "2026-01-02 03:04:05+00")));
        assertTrue(SurveyDefinitionFileFields.isRetired("questions", row(20, 17, "2026-01-02T03:04:05Z")));
    }

    @Test
    void given_theOpenEndedSentinel_when_checked_then_theRecordIsCurrent() {
        assertFalse(SurveyDefinitionFileFields.isRetired("steps", row(11, 8, "9999-12-31 23:59:59+00")));
    }

    @Test
    void given_eachTablesEffectiveToPosition_when_closed_then_thatPositionIsRead() {
        Map<String, Integer> expected = Map.of("select_groups", 7, "select_items", 8, "steps", 8,
                "sections", 8, "steps_sections", 9, "questions", 17, "sections_questions", 7,
                "relationships", 16, "translations", 10);
        assertEquals(expected, SurveyDefinitionFileFields.EFFECTIVE_TO_INDEX);
        expected.forEach((table, index) -> {
            assertTrue(SurveyDefinitionFileFields.isRetired(table, row(index + 1, index, "2020-01-01 00:00:00+00")), table);
            // The position just before it is not effective_to.
            assertFalse(SurveyDefinitionFileFields.isRetired(table, row(index + 1, index - 1, "2020-01-01 00:00:00+00")), table);
        });
    }

    @Test
    void given_aRowTooShortToCarryEffectiveTo_when_checked_then_itIsNotRetired() {
        assertFalse(SurveyDefinitionFileFields.isRetired("steps", row(8, 7, "2020-01-01 00:00:00+00")));
    }

    @Test
    void given_aTableWithoutHistory_when_checked_then_itIsNeverRetired() {
        assertFalse(SurveyDefinitionFileFields.isRetired("reports", row(20, 8, "2020-01-01 00:00:00+00")));
        assertFalse(SurveyDefinitionFileFields.isRetired("surveys", row(20, 8, "2020-01-01 00:00:00+00")));
    }

    @Test
    void given_anEmptyEffectiveTo_when_checked_then_itIsNotRetired() {
        assertFalse(SurveyDefinitionFileFields.isRetired("steps", row(11, 8, "")));
    }

    @Test
    void given_anUnparseableEffectiveTo_when_checked_then_onlyTheSentinelPrefixCountsAsOpen() {
        assertTrue(SurveyDefinitionFileFields.isRetired("steps", row(11, 8, "closed")));
        assertFalse(SurveyDefinitionFileFields.isRetired("steps", row(11, 8, "9999-12-31 infinity")));
    }

    // ---------------------------------------------------------------- translatable fields

    @Test
    void given_aTranslatableField_when_checked_then_itIsAccepted() {
        assertTrue(SurveyDefinitionFileFields.isTranslatable("questions", "text"));
        assertTrue(SurveyDefinitionFileFields.isTranslatable("select_items", "display_text"));
    }

    @Test
    void given_aMachineReadField_when_checked_then_itIsRefused() {
        assertFalse(SurveyDefinitionFileFields.isTranslatable("questions", "default_value"));
        assertFalse(SurveyDefinitionFileFields.isTranslatable("surveys", "name"));
    }

    @Test
    void given_anUnknownElementType_when_checked_then_itIsRefused() {
        assertFalse(SurveyDefinitionFileFields.isTranslatable("dimensions", "name"));
    }

    // ---------------------------------------------------------------- scalar parsers

    @Test
    void given_emptyOrNull_when_nullIfEmpty_then_null() {
        assertNull(SurveyDefinitionFileFields.nullIfEmpty(null));
        assertNull(SurveyDefinitionFileFields.nullIfEmpty(""));
        assertEquals(" ", SurveyDefinitionFileFields.nullIfEmpty(" "));
    }

    @Test
    void given_integers_when_parsed_then_invalidAndEmptyAreNull() {
        assertEquals(42, SurveyDefinitionFileFields.parseIntOrNull(" 42 "));
        assertNull(SurveyDefinitionFileFields.parseIntOrNull(null));
        assertNull(SurveyDefinitionFileFields.parseIntOrNull(""));
        assertNull(SurveyDefinitionFileFields.parseIntOrNull("1.5"));
    }

    @Test
    void given_longs_when_parsed_then_invalidAndEmptyAreNull() {
        assertEquals(9_000_000_000L, SurveyDefinitionFileFields.parseLongOrNull(" 9000000000 "));
        assertNull(SurveyDefinitionFileFields.parseLongOrNull(null));
        assertNull(SurveyDefinitionFileFields.parseLongOrNull(""));
        assertNull(SurveyDefinitionFileFields.parseLongOrNull("x"));
    }

    @Test
    void given_decimals_when_parsed_then_fractionsSurviveAndInvalidIsNull() {
        assertEquals(new BigDecimal("1.5"), SurveyDefinitionFileFields.parseDecimalOrNull(" 1.5 "));
        assertNull(SurveyDefinitionFileFields.parseDecimalOrNull(null));
        assertNull(SurveyDefinitionFileFields.parseDecimalOrNull(""));
        assertNull(SurveyDefinitionFileFields.parseDecimalOrNull("one"));
    }

    @Test
    void given_booleans_when_parsed_then_trueAndPostgresTAreTrue() {
        assertTrue(SurveyDefinitionFileFields.parseBooleanOrFalse("true"));
        assertTrue(SurveyDefinitionFileFields.parseBooleanOrFalse("TRUE"));
        assertTrue(SurveyDefinitionFileFields.parseBooleanOrFalse("t"));
        assertTrue(SurveyDefinitionFileFields.parseBooleanOrFalse("T"));
        assertFalse(SurveyDefinitionFileFields.parseBooleanOrFalse("f"));
        assertFalse(SurveyDefinitionFileFields.parseBooleanOrFalse(""));
        assertFalse(SurveyDefinitionFileFields.parseBooleanOrFalse(null));
    }

    // ---------------------------------------------------------------- sameNumber (BR-109)

    @Test
    void given_equalValuesOfDifferentScaleOrType_when_compared_then_theyAreTheSame() {
        assertTrue(SurveyDefinitionFileFields.sameNumber(new BigDecimal("1"), new BigDecimal("1.00")));
        assertTrue(SurveyDefinitionFileFields.sameNumber(new BigDecimal("2"), 2));
        assertTrue(SurveyDefinitionFileFields.sameNumber(new BigDecimal("1.5"), "1.5"));
    }

    @Test
    void given_differentValues_when_compared_then_theyDiffer() {
        assertFalse(SurveyDefinitionFileFields.sameNumber(new BigDecimal("1.5"), new BigDecimal("1.4")));
        assertFalse(SurveyDefinitionFileFields.sameNumber(new BigDecimal("1"), 2L));
    }

    @Test
    void given_nulls_when_compared_then_onlyTwoNullsAreTheSame() {
        assertTrue(SurveyDefinitionFileFields.sameNumber(null, null));
        assertFalse(SurveyDefinitionFileFields.sameNumber(null, BigDecimal.ONE));
        assertFalse(SurveyDefinitionFileFields.sameNumber(BigDecimal.ONE, null));
    }

    // ---------------------------------------------------------------- id resolution

    @Test
    void given_aMappedSourceId_when_resolved_then_theNewIdIsReturned() {
        Map<Long, Long> map = Map.of(7L, 70L);
        assertEquals(70L, SurveyDefinitionFileFields.resolveRequired(7L, map, "step"));
        assertEquals(70L, SurveyDefinitionFileFields.resolveNullable(7L, map, "step"));
    }

    @Test
    void given_noSourceId_when_resolvedAsRequired_then_itFails() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> SurveyDefinitionFileFields.resolveRequired(null, Map.of(), "step (x)"));
        assertEquals(Translations.get("definition.noSourceId", "step (x)"), e.getMessage());
    }

    @Test
    void given_noSourceId_when_resolvedAsNullable_then_null() {
        assertNull(SurveyDefinitionFileFields.resolveNullable(null, Map.of(), "step"));
    }

    @Test
    void given_anUnmappedSourceId_when_resolved_then_bothFormsFail() {
        IllegalStateException required = assertThrows(IllegalStateException.class,
                () -> SurveyDefinitionFileFields.resolveRequired(9L, Map.of(1L, 2L), "step (x)"));
        assertEquals(Translations.get("definition.noIdMapping", "step (x)", "9"), required.getMessage());
        IllegalStateException nullable = assertThrows(IllegalStateException.class,
                () -> SurveyDefinitionFileFields.resolveNullable(9L, Map.of(1L, 2L), "step (y)"));
        assertEquals(Translations.get("definition.noIdMapping", "step (y)", "9"), nullable.getMessage());
    }

    // ---------------------------------------------------------------- query helpers

    @Test
    void given_aSurrogateId_when_theDurableIdIsRead_then_theTablesOwnColumnIsSelected() {
        ScriptedEntityManager db = new ScriptedEntityManager();
        db.answer = call -> 77;
        assertEquals(77L, SurveyDefinitionFileFields.getDurableId(db.em, "step_id", "survey.steps", 5L));
        ScriptedEntityManager.Call call = db.calls.get(0);
        assertEquals("SELECT step_id FROM survey.steps WHERE id = ?1", call.sql());
        assertEquals(5L, call.param(1));
    }

    @Test
    void given_aNumericResult_when_read_then_itIsALong() {
        ScriptedEntityManager db = new ScriptedEntityManager();
        db.answer = call -> 12;
        Query q = db.em.createNativeQuery("SELECT 12");
        assertEquals(12L, SurveyDefinitionFileFields.getLongResult(q));
    }

    @Test
    void given_aNonNumericOrMissingResult_when_read_then_null() {
        ScriptedEntityManager db = new ScriptedEntityManager();
        db.answer = call -> "twelve";
        assertNull(SurveyDefinitionFileFields.getLongResult(db.em.createNativeQuery("SELECT 'x'")));
        db.answer = call -> new jakarta.persistence.NoResultException();
        assertNull(SurveyDefinitionFileFields.getLongResult(db.em.createNativeQuery("SELECT 'x'")));
        db.answer = call -> null;
        assertNull(SurveyDefinitionFileFields.getLongResult(db.em.createNativeQuery("SELECT 'x'")));
    }
}
