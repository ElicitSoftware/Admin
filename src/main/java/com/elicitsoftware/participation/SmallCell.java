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
package com.elicitsoftware.participation;

import java.text.NumberFormat;
import java.util.Locale;

/**
 * Small-cell suppression for the Participation page and its exports (UC-031 BR-122, NFR-018).
 * <p>
 * A count greater than zero and below the threshold <em>k</em> is shown as "&lt; k"; zero is
 * shown, because it singles out nobody. A rate or share is withheld when its numerator or its
 * denominator is suppressed, and shown as "—" when its denominator is zero. A threshold of 1 or
 * less turns suppression off. Every rendering of a count on the page, in the CSV and in the PDF
 * goes through one instance of this class, so the three cannot disagree.
 *
 * @param threshold the smallest count shown as itself, {@code elicit.participation.small-cell}
 * @param locale    the locale numbers and percentages are formatted in
 */
public record SmallCell(int threshold, Locale locale) {

    /** What a withheld or undefined rate is shown as. */
    public static final String NO_VALUE = "—";

    /**
     * Whether a count is too small to show.
     *
     * @param count a count of respondents or answers
     * @return {@code true} when the count is greater than zero and below the threshold
     */
    public boolean suppressed(long count) {
        return count > 0 && count < threshold;
    }

    /**
     * Renders a count, or "&lt; k" when it is suppressed.
     *
     * @param count a count of respondents or answers
     * @return the text to show
     */
    public String count(long count) {
        return suppressed(count) ? "< " + threshold : NumberFormat.getIntegerInstance(locale).format(count);
    }

    /**
     * Renders {@code numerator ÷ denominator} as a whole percentage, withheld when either count is
     * suppressed and "—" when the denominator is zero.
     *
     * @param numerator   the part
     * @param denominator the whole
     * @return the text to show
     */
    public String rate(long numerator, long denominator) {
        if (denominator == 0 || suppressed(numerator) || suppressed(denominator)) {
            return NO_VALUE;
        }
        NumberFormat percent = NumberFormat.getPercentInstance(locale);
        percent.setMaximumFractionDigits(0);
        return percent.format((double) numerator / denominator);
    }

    /**
     * Whether a statistic computed from {@code n} values may be shown (BR-122): a minimum, a
     * quartile or a latest date drawn from fewer than <em>k</em> answers is withheld.
     *
     * @param n how many values the statistic is computed from
     * @return {@code true} when the statistic may be shown
     */
    public boolean summaryShown(long n) {
        return n > 0 && !suppressed(n);
    }
}
