package com.elicitsoftware.util;

/*-
 * ***LICENSE_START***
 * Elicit Survey
 * %%
 * Copyright (C) 2025 The Regents of the University of Michigan - Rogel Cancer Center
 * %%
 * PolyForm Noncommercial License 1.0.0
 * <https://polyformproject.org/licenses/noncommercial/1.0.0>
 * ***LICENSE_END***
 */

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link RandomString}.
 *
 * <p>Traceability: UC-015 (Generate Survey Access Token). The token generator
 * underpins the unique access token created for every respondent, so these
 * tests pin down the length, character-set, and determinism guarantees the
 * token-generation flow depends on.</p>
 */
class RandomStringTest {

    /** UC-015: generated tokens must have exactly the configured length. */
    @Test
    void nextStringHasConfiguredLength() {
        RandomString generator = new RandomString(9, new Random(42L));
        assertEquals(9, generator.nextString().length());
    }

    /** UC-015: the static helper produces a string of the requested length. */
    @Test
    void generateHelperHonoursLength() {
        assertEquals(16, RandomString.generate(16).length());
    }

    /** UC-015: tokens draw only from the supplied symbol set. */
    @Test
    void nextStringOnlyUsesConfiguredSymbols() {
        String symbols = "AB";
        RandomString generator = new RandomString(50, new Random(1L), symbols);
        String token = generator.nextString();
        for (char c : token.toCharArray()) {
            assertTrue(symbols.indexOf(c) >= 0, "unexpected character: " + c);
        }
    }

    /**
     * UC-015: the default character set excludes visually ambiguous digits and
     * vowels. Digits 0, 1, 3 and vowels (incl. uppercase O) are dropped; ordinary
     * consonants such as 'l' remain.
     */
    @Test
    void defaultAlphabetExcludesAmbiguousCharacters() {
        assertEquals(-1, RandomString.alphanum.indexOf('0'));
        assertEquals(-1, RandomString.alphanum.indexOf('1'));
        assertEquals(-1, RandomString.alphanum.indexOf('3'));
        assertEquals(-1, RandomString.alphanum.indexOf('O'));
        assertEquals(-1, RandomString.alphanum.indexOf('A'));
    }

    /** UC-015: a fixed seed yields reproducible output (supports deterministic tests). */
    @Test
    void sameSeedProducesSameString() {
        String first = new RandomString(12, new Random(7L)).nextString();
        String second = new RandomString(12, new Random(7L)).nextString();
        assertEquals(first, second);
    }

    /** UC-015: different seeds should not collide for a reasonable length. */
    @Test
    void differentSeedsProduceDifferentStrings() {
        String first = new RandomString(12, new Random(7L)).nextString();
        String second = new RandomString(12, new Random(8L)).nextString();
        assertNotEquals(first, second);
    }

    /** UC-015: invalid configuration is rejected up front. */
    @Test
    void rejectsInvalidLengthAndSymbolSet() {
        assertThrows(IllegalArgumentException.class, () -> new RandomString(0, new Random()));
        assertThrows(IllegalArgumentException.class, () -> new RandomString(8, new Random(), "X"));
    }
}
