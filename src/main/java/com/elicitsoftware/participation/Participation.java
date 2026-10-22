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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.SortedMap;

/**
 * What the Participation page shows for one filter (UC-031), as raw counts: suppression and
 * formatting are applied later, by {@link ParticipationTables}, so the page and both exports
 * render one computation.
 *
 * @param overall     the participation of the whole population (BR-121)
 * @param departments the same counts per department in the filter, by department name
 * @param weeks       the counts per week, every week from the first registration to the last event (BR-128)
 * @param cards       one card per {@code ADMINISTRATOR} mark, in the author's order (BR-123)
 */
public record Participation(Counts overall, List<DepartmentCounts> departments, List<Week> weeks, List<Card> cards) {

    /**
     * Respondents by state (BR-121).
     *
     * @param registered every respondent in the population
     * @param notStarted no first access and not finalized
     * @param inProgress first access and not finalized
     * @param finished   finalized
     */
    public record Counts(long registered, long notStarted, long inProgress, long finished) {

        /** Started is in progress plus finished. */
        public long started() {
            return inProgress + finished;
        }
    }

    /** The counts of one department. */
    public record DepartmentCounts(String department, Counts counts) {
    }

    /**
     * One ISO week (BR-128): how many of the population were registered, started and finished in it.
     *
     * @param monday the first day of the week
     */
    public record Week(LocalDate monday, long registered, long started, long finished) {
    }

    /** One administrator mark and the questions it covers (BR-123). */
    public record Card(String title, List<QuestionStats> questions) {
    }

    /** How a question's answers are summarized (BR-125). */
    public enum Kind {
        CHOICE, CHECKBOX, NUMBER, DATE, COUNT_ONLY
    }

    /**
     * One question of a card, among finished respondents (BR-124).
     *
     * @param text        the question's text, in the survey's base language
     * @param kind        how its answers are summarized
     * @param presented   non-deleted answer rows, one per instance
     * @param respondents distinct respondents those rows belong to
     * @param answered    rows with an answer
     * @param options     choice and checkbox: one row per option, in option order
     * @param numbers     number: the summary, or {@code null} when there is no numeric answer
     * @param dates       date: the summary, or {@code null} when there is no date answer
     */
    public record QuestionStats(String text, Kind kind, long presented, long respondents, long answered,
                                List<OptionCount> options, NumberSummary numbers, DateSummary dates) {
    }

    /**
     * One option of a choice question.
     *
     * @param label  the option's text, or the stored value when no current option carries it
     * @param count  answers that chose it
     * @param listed whether the option is on the question's current option list
     */
    public record OptionCount(String label, long count, boolean listed) {
    }

    /** n, minimum, quartiles (linear interpolation) and maximum of a number question's answers. */
    public record NumberSummary(long n, BigDecimal min, BigDecimal q1, BigDecimal median, BigDecimal q3, BigDecimal max) {
    }

    /** The earliest and latest answer of a date question and the count per month. */
    public record DateSummary(long n, LocalDate earliest, LocalDate latest, SortedMap<YearMonth, Long> perMonth) {
    }
}
