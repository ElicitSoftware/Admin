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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Covers {@link SurveyDefinitionFileFields#rebaseDisplayKey(String, Number)} — the rule that lets
 * one authored {@code .elicit} file be applied to several deployments that each allocate the
 * survey a different id.
 * <p>
 * A plain unit test on purpose: the rule is pure string arithmetic on the leading component of a
 * display key, and it is worth being able to run it without booting Quarkus or a database.
 */
class SurveyDefinitionDisplayKeyRebaseTest {

    @Test
    void replacesTheSurveyComponentAndLeavesEveryOtherPositionAlone() {
        assertEquals("0007-0002-0000-0003-0000-0000-0000",
                SurveyDefinitionFileFields.rebaseDisplayKey("0001-0002-0000-0003-0000-0000-0000", 7));
    }

    @Test
    void padsTheSurveyComponentToFourDigits() {
        assertEquals("0001-0001-0000-0001-0000-0000-0000",
                SurveyDefinitionFileFields.rebaseDisplayKey("0042-0001-0000-0001-0000-0000-0000", 1));
    }

    @Test
    void doesNotTruncateASurveyIdWiderThanFourDigits() {
        // LPAD-style padding is a minimum width, not a field size — DisplayKey parses the
        // component with Integer.parseInt, which is happy either way.
        assertEquals("12345-0001-0000-0001-0000-0000-0000",
                SurveyDefinitionFileFields.rebaseDisplayKey("0001-0001-0000-0001-0000-0000-0000", 12345));
    }

    @Test
    void rebasingOntoTheSameIdIsAnIdentity() {
        // This is what makes the update path's unchanged-comparison stable: applying the same
        // file twice must not report a difference and re-version the row.
        String key = "0003-0004-0000-0005-0000-0000-0000";
        assertEquals(key, SurveyDefinitionFileFields.rebaseDisplayKey(key, 3));
    }

    @Test
    void handlesAnUnpaddedKeyOfTheKindTheEngineFallsBackTo() {
        // SessionPersistenceService defaults to "0-0-0-0-0-0-0" when a survey has no initial key.
        assertEquals("0009-0-0-0-0-0-0",
                SurveyDefinitionFileFields.rebaseDisplayKey("0-0-0-0-0-0-0", 9));
    }

    @Test
    void treatsAbsentKeysAsAbsentRatherThanInventingOne() {
        assertNull(SurveyDefinitionFileFields.rebaseDisplayKey(null, 5));
        assertNull(SurveyDefinitionFileFields.rebaseDisplayKey("", 5));
    }

    @Test
    void leavesAValueWithNoSeparatorUntouched() {
        // Nothing here is a survey component, so there is nothing to replace. Returning the value
        // as-is beats guessing at a format this code does not recognise.
        assertEquals("garbage", SurveyDefinitionFileFields.rebaseDisplayKey("garbage", 3));
    }

    @Test
    void replacesUpToTheFirstSeparatorEvenWhenTheRestIsNotNumeric() {
        // Documents the actual rule — everything before the first "-" is the survey component —
        // rather than implying the whole key is validated. It is not.
        assertEquals("0003-a-key", SurveyDefinitionFileFields.rebaseDisplayKey("not-a-key", 3));
    }

    @Test
    void returnsTheKeyUnchangedWhenNoSurveyIdIsKnown() {
        assertEquals("0001-0002-0000-0003-0000-0000-0000",
                SurveyDefinitionFileFields.rebaseDisplayKey("0001-0002-0000-0003-0000-0000-0000", null));
    }
}
