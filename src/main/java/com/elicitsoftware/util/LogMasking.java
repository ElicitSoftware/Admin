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

/**
 * Masks tokens and email addresses for logging.
 * <p>
 * Respondent survey tokens and participant email addresses are PII/credential-adjacent
 * values. The application's default {@code com.elicitsoftware} log category is INFO in
 * production, but one {@code ELICIT_LOG_LEVEL=DEBUG} override away from writing these
 * values into logs verbatim. Masking (rather than relying solely on log level) keeps
 * troubleshooting useful - enough of the value survives to correlate a log line with a
 * specific record - without persisting the full value into log aggregation.
 */
public final class LogMasking {

    private LogMasking() {
        // Utility class
    }

    /**
     * Masks a token down to its last 4 characters, e.g. {@code "****xz2q"} -&gt; {@code "*****2q"}.
     *
     * @param token the raw token value
     * @return the masked token, or the original value if it's too short to mask meaningfully
     */
    public static String maskToken(String token) {
        if (token == null) {
            return null;
        }
        int visible = Math.min(4, token.length());
        return "*".repeat(Math.max(0, token.length() - visible)) + token.substring(token.length() - visible);
    }

    /**
     * Masks an email address's local part, keeping the domain visible for troubleshooting,
     * e.g. {@code "jane.doe@example.org"} -&gt; {@code "j*******@example.org"}.
     *
     * @param email the raw email address
     * @return the masked email address, or the original value if it doesn't look like an email
     */
    public static String maskEmail(String email) {
        if (email == null) {
            return null;
        }
        int at = email.indexOf('@');
        if (at <= 0) {
            return email;
        }
        String local = email.substring(0, at);
        String domain = email.substring(at);
        return local.charAt(0) + "*".repeat(Math.max(1, local.length() - 1)) + domain;
    }
}
