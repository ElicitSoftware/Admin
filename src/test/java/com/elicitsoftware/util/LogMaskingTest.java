package com.elicitsoftware.util;

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
 * UC-015: Generate Survey Access Code and UC-004: Send Invitation or Reminder Email — how access
 * codes and email addresses are masked before they reach a log line.
 */
class LogMaskingTest {

    @Test
    void given_anAccessCode_when_masked_then_onlyTheLastFourCharactersShow() {
        assertEquals("*****xz2q", LogMasking.maskAccessCode("ABCDExz2q"));
        assertEquals("*bcde", LogMasking.maskAccessCode("abcde"));
    }

    @Test
    void given_aShortAccessCode_when_masked_then_itIsLeftAsIs() {
        assertEquals("abcd", LogMasking.maskAccessCode("abcd"));
        assertEquals("ab", LogMasking.maskAccessCode("ab"));
        assertEquals("", LogMasking.maskAccessCode(""));
        assertNull(LogMasking.maskAccessCode(null));
    }

    @Test
    void given_anEmail_when_masked_then_theFirstLetterAndDomainShow() {
        assertEquals("j*******@example.org", LogMasking.maskEmail("jane.doe@example.org"));
        assertEquals("a*@x.org", LogMasking.maskEmail("ab@x.org"));
        assertEquals("a*@x.org", LogMasking.maskEmail("a@x.org"), "a one-letter local part still gets one star");
    }

    @Test
    void given_somethingThatIsNotAnEmail_when_masked_then_itIsLeftAsIs() {
        assertEquals("@x.org", LogMasking.maskEmail("@x.org"));
        assertEquals("plain", LogMasking.maskEmail("plain"));
        assertNull(LogMasking.maskEmail(null));
    }
}
