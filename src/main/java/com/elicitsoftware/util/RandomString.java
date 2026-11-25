package com.elicitsoftware.util;

/*-
 * ***LICENSE_START***
 * Elicit Admin
 * %%
 * Copyright (C) 2025 - 2026 Elicit Software LLC
 * %%
 * PolyForm Noncommercial License 1.0.0
 * <https://polyformproject.org/licenses/noncommercial/1.0.0>
 * ***LICENSE_END***
 */

import java.security.SecureRandom;
import java.util.Locale;
import java.util.Objects;
import java.util.Random;

/**
 * Generates random strings from a set of characters. Admin uses it for respondents' access codes
 * ({@code AccessCodeService}, which draws from its own alphabet, Admin UC-015 BR-050).
 * <p>
 * The built-in sets leave out vowels, so a string is unlikely to spell a word, and the digits
 * 0, 1 and 3. {@link #lower} is {@link #upper} in lower case and so still holds {@code l}; an
 * alphabet that must avoid {@code l}/{@code I}/{@code 1} confusion names its own characters, as the
 * access code alphabet does.
 * <p>
 * {@link SecureRandom} is the source unless a constructor is given another. An instance reuses one
 * buffer and is not safe for concurrent use: give each thread its own.
 * <p>
 * Example:
 * <pre>{@code
 * RandomString accessCodes = new RandomString(9, new SecureRandom(), alphabet);
 * String accessCode = accessCodes.nextString();
 * }</pre>
 *
 * @see SecureRandom
 */
public class RandomString {

    /**
     * Uppercase letters excluding vowels and visually similar characters.
     * <p>
     * This character set contains uppercase consonants that are visually distinct
     * and unlikely to form recognizable words. Excluded characters: A, E, I, O, U, Y.
     */
    public static final String upper = "BCDFGHJKLMNPQRSTVWXZ";

    /**
     * {@link #upper} in lower case. It includes {@code l}, which can be misread as {@code I} or
     * {@code 1}.
     */
    public static final String lower = upper.toLowerCase(Locale.ROOT);

    /**
     * Digits excluding visually confusing numbers.
     * <p>
     * This digit set excludes characters that could be confused with letters:
     * - 0 (zero) - can be confused with O (letter O)
     * - 1 (one) - can be confused with I (letter I) or l (lowercase L)
     * - 3 (three) - can be confused with 8 (eight) in some fonts
     */
    public static final String digits = "2456789";

    /** {@link #upper}, {@link #lower} and {@link #digits} together: 47 characters. */
    public static final String alphanum = upper + lower + digits;

    /**
     * Random number generator instance for this string generator.
     * <p>
     * This can be either a SecureRandom for cryptographic security or a
     * standard Random for performance-focused applications.
     */
    private final Random random;

    /**
     * Character array containing the symbol set for random selection.
     * <p>
     * This array is created once during construction for efficient
     * character selection during string generation.
     */
    private final char[] symbols;

    /**
     * Pre-allocated character buffer for string construction.
     * <p>
     * This buffer is reused for each string generation to avoid
     * repeated memory allocation and improve performance.
     */
    private final char[] buf;

    /**
     * Constructs a generator with the given length, random source and characters. A character that
     * appears more than once in {@code symbols} is drawn that much more often.
     *
     * @param length  the length of the strings to generate; at least 1
     * @param random  the random source; not null
     * @param symbols the characters to draw from; at least 2 characters long
     * @throws IllegalArgumentException if length &lt; 1 or symbols is shorter than 2 characters
     * @throws NullPointerException     if random is null
     */
    public RandomString(int length, Random random, String symbols) {
        if (length < 1) throw new IllegalArgumentException();
        if (symbols.length() < 2) throw new IllegalArgumentException();
        this.random = Objects.requireNonNull(random);
        this.symbols = symbols.toCharArray();
        this.buf = new char[length];
    }

    /**
     * Constructs a generator with the given length and random source, drawing from {@link #alphanum}.
     *
     * @param length the length of the strings to generate; at least 1
     * @param random the random source; not null
     * @throws IllegalArgumentException if length &lt; 1
     * @throws NullPointerException     if random is null
     */
    public RandomString(int length, Random random) {
        this(length, random, alphanum);
    }

    /**
     * Constructs a generator with the given length, drawing from {@link #alphanum} with a
     * {@link SecureRandom}.
     *
     * @param length the length of the strings to generate; at least 1
     * @throws IllegalArgumentException if length &lt; 1
     */
    public RandomString(int length) {
        this(length, new SecureRandom());
    }

    /**
     * Constructs a generator of 21-character strings from {@link #alphanum} with a
     * {@link SecureRandom}: about 117 bits of randomness (21 &times; log2 47).
     */
    public RandomString() {
        this(21);
    }

    /**
     * Constructs a generator with the given length and characters, with a {@link SecureRandom}.
     *
     * @param length  the length of the strings to generate; at least 1
     * @param symbols the characters to draw from, copied; at least 2
     * @throws IllegalArgumentException if length &lt; 1 or there are fewer than 2 symbols
     */
    public RandomString(int length, char[] symbols) {
        if (length < 1) throw new IllegalArgumentException();
        if (symbols.length < 2) throw new IllegalArgumentException();
        this.random = new SecureRandom();
        this.symbols = symbols.clone();
        this.buf = new char[length];
    }

    /**
     * Generates one string of the given length from {@link #alphanum} with a {@link SecureRandom}.
     *
     * @param length the length of the string; at least 1
     * @return a random string
     * @throws IllegalArgumentException if length &lt; 1
     */
    public static String generate(int length) {
        return new RandomString(length, alphanum.toCharArray()).nextString();
    }

    /**
     * Generates a string of the configured length, each character drawn independently from the
     * configured characters. Not safe for concurrent use of one instance.
     *
     * @return a new random string
     */
    public String nextString() {
        for (int idx = 0; idx < buf.length; ++idx)
            buf[idx] = symbols[random.nextInt(symbols.length)];
        return new String(buf);
    }
}
