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

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * UC-018: Apply Survey Definition — the checks {@link SurveyDefinitionApplyService} makes before
 * it routes a file: its size, and the survey key it carries (BR-073), refusing a file it cannot
 * route rather than guessing (BR-075) and doing no work of its own when it refuses (BR-074).
 * <p>
 * The routing itself looks the key up with a Panache static and is covered by the booted
 * {@code SurveyDefinitionApplyServiceTest}; every rejection happens before that lookup, so the
 * import and update services here are left unset: touching either would fail the test.
 */
class SurveyDefinitionApplyServiceUnitTest {

    SurveyDefinitionApplyService service;

    @BeforeEach
    void setUp() {
        service = new SurveyDefinitionApplyService();
    }

    private SurveyDefinitionApplyService.ApplyResult apply(String content) {
        return service.apply(content.getBytes(StandardCharsets.UTF_8), "f.elicit");
    }

    private static void assertRejected(SurveyDefinitionApplyService.ApplyResult result, String message) {
        assertEquals(SurveyDefinitionApplyService.ApplyResult.Action.REJECTED, result.action());
        assertFalse(result.success());
        assertNull(result.surveyKey());
        assertNull(result.detail());
        assertNull(result.reporting());
        assertEquals(message, result.message());
    }

    @Test
    void given_noBytes_when_applied_then_theFileIsRefusedAsEmpty() {
        assertRejected(service.apply(null, "f"), Translations.get("definition.fileEmpty"));
        assertRejected(service.apply(new byte[0], "f"), Translations.get("definition.fileEmpty"));
    }

    @Test
    void given_aFileOverTheLimit_when_applied_then_itIsRefusedNamingTheLimit() {
        byte[] data = new byte[SurveyDefinitionApplyService.MAX_FILE_BYTES + 1];
        Arrays.fill(data, (byte) '#');
        assertRejected(service.apply(data, "f"), Translations.get("definition.fileTooLarge", "5"));
    }

    @Test
    void given_aFileExactlyAtTheLimit_when_applied_then_itIsRead() {
        byte[] data = new byte[SurveyDefinitionApplyService.MAX_FILE_BYTES];
        Arrays.fill(data, (byte) '#');
        assertRejected(service.apply(data, "f"), Translations.get("definition.notExport"));
        assertEquals(5 * 1024 * 1024, SurveyDefinitionApplyService.MAX_FILE_BYTES);
    }

    @Test
    void given_noSurveysRecord_when_applied_then_itIsNotAnExport() {
        assertRejected(apply("# ELICIT_SURVEY_EXPORT_V1\n\nno colon\nsteps: 1|2|3\n  \n"),
                Translations.get("definition.notExport"));
    }

    @Test
    void given_aSurveysRecordWithOneField_when_applied_then_itIsMalformed() {
        assertRejected(apply("# ELICIT_SURVEY_EXPORT_V1\nsurveys: 11\n"),
                Translations.get("definition.surveysMalformed", "1"));
    }

    @Test
    void given_aSurveysRecordWithoutAKey_when_applied_then_itCannotBeRouted() {
        assertRejected(apply("# header\nsurveys: 11||Name|1\n"), Translations.get("definition.noSurveyKey"));
        assertRejected(apply("surveys: 11|\n"), Translations.get("definition.noSurveyKey"));
    }

    @Test
    void given_anInvalidKey_when_applied_then_itIsRefusedNamingTheValue() {
        assertRejected(apply("  SURVEYS : 11|not-a-key|Name\n"),
                Translations.get("definition.invalidValue", "survey_key", "not-a-key"));
    }

    @Test
    void given_theFirstSurveysRecord_when_read_then_laterOnesAreIgnored() {
        assertRejected(apply("steps: 1|x\nsurveys: 11||N\nsurveys: 12|bad|N\n"), Translations.get("definition.noSurveyKey"));
    }

    @Test
    void given_aResultWithoutReporting_when_built_then_reportingIsNull() {
        SurveyDefinitionApplyService.ApplyResult result = new SurveyDefinitionApplyService.ApplyResult(
                SurveyDefinitionApplyService.ApplyResult.Action.IMPORT, null, true, "m", "d");
        assertEquals("m", result.message());
        assertEquals("d", result.detail());
        assertNull(result.reporting());
    }
}
