package com.elicitsoftware.participation;

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

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** UC-031 BR-122 / NFR-018: small counts are suppressed and the rates built on them withheld. */
class SmallCellTest {

    private final SmallCell cells = new SmallCell(5, Locale.ENGLISH);

    @Test
    void countsFromOneToBelowTheThresholdAreSuppressed() {
        assertEquals("0", cells.count(0));
        assertEquals("< 5", cells.count(1));
        assertEquals("< 5", cells.count(4));
        assertEquals("5", cells.count(5));
        assertEquals("1,234", cells.count(1234));
    }

    @Test
    void aRateIsWithheldWhenEitherSideIsSuppressed() {
        assertEquals("50%", cells.rate(10, 20));
        assertEquals(SmallCell.NO_VALUE, cells.rate(3, 20));
        assertEquals(SmallCell.NO_VALUE, cells.rate(4, 4));
        assertEquals(SmallCell.NO_VALUE, cells.rate(0, 0));
        assertEquals("0%", cells.rate(0, 20));
    }

    @Test
    void aSummaryNeedsAtLeastThresholdValues() {
        assertFalse(cells.summaryShown(0));
        assertFalse(cells.summaryShown(4));
        assertTrue(cells.summaryShown(5));
    }

    @Test
    void aThresholdOfOneTurnsSuppressionOff() {
        SmallCell off = new SmallCell(1, Locale.ENGLISH);
        assertEquals("1", off.count(1));
        assertEquals("100%", off.rate(1, 1));
        assertTrue(off.summaryShown(1));
    }
}
