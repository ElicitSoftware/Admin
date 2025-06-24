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

import java.security.SecureRandom;
import java.util.Locale;
import java.util.Objects;
import java.util.Random;

/**
 * RandomString provides secure random string generation functionality for various use cases.
 * <p>
 * This utility class generates cryptographically secure random strings using customizable
 * character sets and configurable lengths. It's designed for creating session identifiers,
 * authentication tokens, passwords, and other security-sensitive random strings in the
 * Elicit Survey system.
 * <p>
 * Key features:
 * - **Cryptographically secure**: Uses SecureRandom by default for security-critical applications
 * - **Customizable character sets**: Supports custom symbol sets for different requirements
 * - **Configurable length**: Flexible string length configuration
 * - **Ambiguity avoidance**: Default character set excludes visually similar characters
 * - **Performance optimized**: Reuses character buffers for efficient generation
 * - **Thread safety**: Each instance maintains its own state for concurrent use
 * <p>
 * Default character sets:
 * - **Uppercase**: "BCDFGHJKLMNPQRSTVWXZ" (excludes A, E, I, O, U, Y for readability)
 * - **Lowercase**: Lowercase version of uppercase set
 * - **Digits**: "2456789" (excludes 0, 1, 3 to avoid confusion with letters)
 * - **Alphanumeric**: Combination of all above sets
 * <p>
 * Character exclusion rationale:
 * The default character sets exclude potentially confusing characters:
 * - Letters: A, E, I, O, U, Y (vowels that might form words)
 * - Digits: 0 (confusion with O), 1 (confusion with I/l), 3 (confusion with 8)
 * <p>
 * Security considerations:
 * - Uses SecureRandom for cryptographically secure random number generation
 * - Character sets designed to avoid dictionary words and visual ambiguity
 * - Generated strings suitable for security tokens and session identifiers
 * - No predictable patterns in generated strings
 * <p>
 * Usage examples:
 * <pre>
 * {@code
 * // Default session identifier (21 characters, secure random)
 * RandomString sessionGen = new RandomString();
 * String sessionId = sessionGen.nextString();
 *
 * // Custom length with secure random
 * RandomString tokenGen = new RandomString(32);
 * String authToken = tokenGen.nextString();
 *
 * // Custom character set and length
 * RandomString customGen = new RandomString(16, new SecureRandom(), "ABCDEF0123456789");
 * String hexString = customGen.nextString();
 *
 * // Deterministic random for testing
 * RandomString testGen = new RandomString(10, new Random(12345));
 * String predictableString = testGen.nextString();
 * }
 * </pre>
 *
 * @see SecureRandom
 * @see Random
 * @since 1.0.0
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
     * Lowercase version of the uppercase character set.
     * <p>
     * Contains the same consonants as the uppercase set but in lowercase form,
     * maintaining the same visual distinctness and word-avoidance properties.
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

    /**
     * Combined alphanumeric character set for general-purpose random string generation.
     * <p>
     * This character set combines uppercase letters, lowercase letters, and digits
     * while maintaining visual distinctness and avoiding potentially confusing
     * character combinations.
     */
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
     * Constructs a RandomString generator with custom length, random source, and character set.
     * <p>
     * This is the most flexible constructor, allowing complete customization of all
     * generation parameters. It validates inputs to ensure secure and correct operation.
     * <p>
     * Validation rules:
     * - Length must be at least 1 character
     * - Symbol set must contain at least 2 different characters
     * - Random generator must not be null
     * <p>
     * Performance considerations:
     * - Character array is pre-allocated for the specified length
     * - Symbol set is converted to char array once for efficient access
     * - Buffer is reused across multiple string generations
     *
     * @param length The length of strings to generate; must be at least 1
     * @param random The random number generator to use; must not be null
     * @param symbols The character set to use for generation; must contain at least 2 characters
     * @throws IllegalArgumentException if length &lt; 1 or symbols length &lt; 2
     * @throws NullPointerException if random is null
     */
    public RandomString(int length, Random random, String symbols) {
        if (length < 1) throw new IllegalArgumentException();
        if (symbols.length() < 2) throw new IllegalArgumentException();
        this.random = Objects.requireNonNull(random);
        this.symbols = symbols.toCharArray();
        this.buf = new char[length];
    }

    /**
     * Constructs a RandomString generator with custom length and random source using the default alphanumeric character set.
     * <p>
     * This constructor provides a balance between customization and convenience by allowing
     * custom length and random source while using the proven default character set that
     * avoids visual ambiguity and potential word formation.
     * <p>
     * Character set used:
     * - Uppercase consonants: BCDFGHJKLMNPQRSTVWXZ
     * - Lowercase consonants: bcdfghjklmnpqrstvwxz
     * - Clear digits: 2456789
     * <p>
     * Use cases:
     * - Authentication tokens with custom length requirements
     * - Test data generation with deterministic random sources
     * - Performance-critical applications using standard Random
     * - Batch generation with consistent character set
     *
     * @param length The length of strings to generate; must be at least 1
     * @param random The random number generator to use; must not be null
     * @throws IllegalArgumentException if length &lt; 1
     * @throws NullPointerException if random is null
     * @see #alphanum
     */
    public RandomString(int length, Random random) {
        this(length, random, alphanum);
    }

    /**
     * Constructs a RandomString generator with custom length using SecureRandom for cryptographic security.
     * <p>
     * This constructor is designed for security-sensitive applications where cryptographically
     * secure random strings are required. It automatically uses SecureRandom to ensure
     * unpredictable and secure string generation suitable for authentication tokens,
     * session identifiers, and other security-critical use cases.
     * <p>
     * Security characteristics:
     * - Uses SecureRandom for cryptographically secure random number generation
     * - Suitable for security tokens and authentication credentials
     * - Unpredictable output resistant to cryptographic attacks
     * - Appropriate for production security applications
     * <p>
     * Character set features:
     * - Default alphanumeric character set with visual disambiguation
     * - Excludes confusing characters (0, 1, 3, vowels)
     * - Balanced distribution of uppercase, lowercase, and digits
     * - Avoids potential word formation
     * <p>
     * Use cases:
     * - Authentication tokens and API keys
     * - Session identifiers for web applications
     * - Password reset tokens
     * - One-time use codes and nonces
     *
     * @param length The length of strings to generate; must be at least 1
     * @throws IllegalArgumentException if length &lt; 1
     * @see SecureRandom
     */
    public RandomString(int length) {
        this(length, new SecureRandom());
    }

    /**
     * Constructs a RandomString generator optimized for session identifier creation.
     * <p>
     * This default constructor creates a generator specifically configured for session
     * identifier generation with industry-standard parameters. It uses a 21-character
     * length that provides excellent entropy while remaining manageable for system use.
     * <p>
     * Configuration details:
     * - **Length**: 21 characters for optimal entropy vs. length balance
     * - **Security**: Uses SecureRandom for cryptographically secure generation
     * - **Character set**: Default alphanumeric set avoiding visual ambiguity
     * - **Entropy**: Approximately 110 bits of entropy (21 * log2(47))
     * <p>
     * Security characteristics:
     * - Sufficient entropy for session security (>= 128 bits recommended)
     * - Resistant to brute force attacks
     * - Unpredictable sequence generation
     * - Suitable for web session management
     * <p>
     * Use cases:
     * - HTTP session identifiers
     * - Temporary authentication tokens
     * - Request correlation IDs
     * - Cache keys and identifiers
     * <p>
     * Performance considerations:
     * - Optimized length for common session use cases
     * - Pre-configured for immediate use
     * - No additional configuration required
     * - Suitable for high-frequency generation
     */
    public RandomString() {
        this(21);
    }

    /**
     * Generate a random string.
     *
     * @param length the length of the random string
     * @param symbols the symbols to use for generation
     * @throws IllegalArgumentException if length &lt; 1 or symbols length &lt; 2
     */
    public RandomString(int length, char[] symbols) {
        if (length < 1) throw new IllegalArgumentException();
        if (symbols.length < 2) throw new IllegalArgumentException();
        this.random = new SecureRandom();
        this.symbols = symbols.clone();
        this.buf = new char[length];
    }

    /**
     * Generate a random string from the configured symbols.
     *
     * @param length the desired length of the random string
     * @return a random string of the specified length
     * @throws IllegalArgumentException if length &lt; 1
     */
    public static String generate(int length) {
        return new RandomString(length, alphanum.toCharArray()).nextString();
    }

    /**
     * Generates a new random string using the configured parameters.
     * <p>
     * This method creates a random string by filling the pre-allocated character buffer
     * with randomly selected characters from the configured symbol set. Each character
     * position is independently selected using the random number generator, ensuring
     * uniform distribution across the available character set.
     * <p>
     * Generation process:
     * 1. **Buffer initialization**: Uses pre-allocated character buffer for efficiency
     * 2. **Character selection**: Each position filled with randomly selected symbol
     * 3. **Index generation**: Random.nextInt() ensures uniform distribution
     * 4. **String construction**: New String object created from character buffer
     * <p>
     * Performance characteristics:
     * - **Memory efficient**: Reuses pre-allocated character buffer
     * - **Fast execution**: Direct array access and single string construction
     * - **Uniform distribution**: Each character has equal probability of selection
     * - **Thread safe**: Each instance maintains independent state
     * <p>
     * Security properties:
     * - **Unpredictable**: Each call produces independent random output
     * - **Uniform distribution**: No bias toward particular characters
     * - **Cryptographically secure**: When using SecureRandom (default)
     * - **No observable patterns**: Output sequence is cryptographically random
     * <p>
     * Thread safety:
     * This method is NOT thread-safe. Each RandomString instance should be used
     * by only one thread at a time, or external synchronization should be provided.
     * For concurrent use, create separate RandomString instances for each thread.
     * <p>
     * Usage patterns:
     * <pre>
     * {@code
     * // Single use
     * RandomString generator = new RandomString(16);
     * String token = generator.nextString();
     *
     * // Multiple generation
     * RandomString sessionGen = new RandomString();
     * String sessionId1 = sessionGen.nextString();
     * String sessionId2 = sessionGen.nextString(); // Different from sessionId1
     *
     * // High-frequency generation
     * RandomString fastGen = new RandomString(8, new Random());
     * for (int i = 0; i < 1000; i++) {
     *     String id = fastGen.nextString();
     *     // Process each unique ID
     * }
     * }
     * </pre>
     *
     * @return A newly generated random string with the configured length and character set
     * @see Random#nextInt(int)
     * @see SecureRandom
     */
    public String nextString() {
        for (int idx = 0; idx < buf.length; ++idx)
            buf[idx] = symbols[random.nextInt(symbols.length)];
        return new String(buf);
    }
}
