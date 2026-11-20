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

import com.elicitsoftware.participation.Participation.Card;
import com.elicitsoftware.participation.Participation.Counts;
import com.elicitsoftware.participation.Participation.DateSummary;
import com.elicitsoftware.participation.Participation.Durations;
import com.elicitsoftware.participation.Participation.Kind;
import com.elicitsoftware.participation.Participation.NumberSummary;
import com.elicitsoftware.participation.Participation.OptionCount;
import com.elicitsoftware.participation.Participation.Progress;
import com.elicitsoftware.participation.Participation.QuestionStats;
import com.elicitsoftware.participation.Participation.StepProgress;
import com.elicitsoftware.participation.Participation.Week;
import com.elicitsoftware.participation.ParticipationTables.Section;
import com.elicitsoftware.participation.ParticipationTables.Table;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Locale;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UC-031: Monitor a Survey's Participation — the text tables the page and both exports share
 * (BR-126): every count suppressed the same way (BR-122), the distribution suited to each kind
 * of question (BR-125), the charts beside their tables (BR-129) and the drop-off and time per
 * step (BR-130, BR-132, BR-133).
 */
class ParticipationTablesTest {

    private static final Progress NO_PROGRESS = new Progress(List.of(), 0, 0, Durations.NONE, 0);

    private final ParticipationTables tables = new ParticipationTables(new SmallCell(5, Locale.ENGLISH));

    private static Participation participation(List<Week> weeks, List<Card> cards) {
        return new Participation(new Counts(40, 10, 18, 12, 8), List.of(), weeks, cards, NO_PROGRESS, 14);
    }

    @Test
    void given_noWeeksAndNoMarks_when_sections_then_explainedInsteadOfTabled() {
        List<Section> sections = tables.sections(participation(List.of(), List.of()));

        assertEquals(List.of("Participation", "By department", "By week", "Progress", "Marked questions"),
                sections.stream().map(Section::title).toList());
        assertEquals("Counts from 1 to 4 are shown as \"< 5\", and the rates built on them are withheld, so that a"
                + " narrow filter cannot single out a respondent.", sections.get(0).note());
        assertNull(sections.get(1).note());
        assertEquals("Nobody in this filter has been registered yet.", sections.get(2).note());
        assertTrue(sections.get(2).tables().isEmpty());
        assertTrue(sections.get(4).note().startsWith("The author of this survey has marked no questions"));
        assertTrue(sections.stream().noneMatch(Section::card));
    }

    @Test
    void given_weeksAndACard_when_sections_then_tabledAndTheCardFollows() {
        Card card = new Card("Smoker", List.of());

        List<Section> sections = tables.sections(participation(List.of(new Week(LocalDate.of(2026, 1, 5), 40, 3, 0)),
                List.of(card)));

        assertEquals(6, sections.size());
        assertNull(sections.get(2).note());
        assertEquals(1, sections.get(2).tables().size());
        assertTrue(sections.get(4).note().startsWith("Among finished respondents."));
        Section smoker = sections.get(5);
        assertEquals("Smoker", smoker.title());
        assertTrue(smoker.card());
        assertEquals("This mark covers no question that holds an answer.", smoker.note());
        assertTrue(smoker.tables().isEmpty());
    }

    @Test
    void given_noSuppression_when_sections_then_noSuppressionNote() {
        ParticipationTables off = new ParticipationTables(new SmallCell(1, Locale.ENGLISH));

        assertNull(off.sections(participation(List.of(), List.of())).get(0).note());
        assertEquals(1, off.threshold());
        assertEquals(5, tables.threshold());
    }

    @Test
    void given_counts_when_overall_then_statesRatesAndStalledWithAFunnel() {
        Table overall = tables.overall(new Counts(40, 10, 18, 12, 3), "Stalled");

        assertEquals(List.of(List.of("Registered", "40"), List.of("Not started", "10"), List.of("In progress", "18"),
                List.of("Finished", "12"), List.of("Started", "30"), List.of("Start rate", "75%"),
                List.of("Completion rate", "40%"), List.of("Overall rate", "30%"), List.of("Stalled", "< 5")),
                overall.rows());
        assertEquals(List.of("Measure", "Value"), overall.headers());
        assertNull(overall.title());
        assertTrue(overall.chart().startsWith("<svg"), overall.chart());
        assertTrue(overall.chart().contains("Registered, started and finished"), overall.chart());
    }

    @Test
    void given_departments_when_departments_then_oneRowEachWithTheirRates() {
        Table departments = tables.departments(List.of(
                new Participation.DepartmentCounts("Oncology", new Counts(40, 10, 18, 12, 6)),
                new Participation.DepartmentCounts("Clinic", new Counts(3, 1, 2, 0, 2))), "Stalled");

        assertEquals(List.of("Department", "Registered", "Not started", "In progress", "Finished", "Start rate",
                "Completion rate", "Overall rate", "Stalled"), departments.headers());
        assertEquals(List.of(List.of("Oncology", "40", "10", "18", "12", "75%", "40%", "30%", "6"),
                List.of("Clinic", "< 5", "< 5", "< 5", "0", "—", "—", "—", "< 5")), departments.rows());
        assertNull(departments.chart());
    }

    @Test
    void given_weeks_when_weeks_then_oneRowEachWithMediumDatesAndAColumnChart() {
        Table weeks = tables.weeks(List.of(new Week(LocalDate.of(2026, 1, 5), 40, 3, 0),
                new Week(LocalDate.of(2026, 1, 12), 1234, 5, 6)));

        assertEquals(List.of(List.of("Jan 5, 2026", "40", "< 5", "0"), List.of("Jan 12, 2026", "1,234", "5", "6")), weeks.rows());
        assertEquals(List.of("Week of", "Registered", "Started", "Finished"), weeks.headers());
        assertTrue(weeks.chart().contains("<svg"), weeks.chart());
        assertTrue(weeks.chart().contains("12 Jan"), "the chart labels each week by its Monday: " + weeks.chart());
    }

    @Test
    void given_aQuestion_when_card_then_itsFiguresThenItsDistribution() {
        QuestionStats q = new QuestionStats("Smoke?", Kind.CHOICE, 20, 16, 10,
                List.of(new OptionCount("Yes", 6, true), new OptionCount("9", 4, false)), null, null);
        QuestionStats free = new QuestionStats("Why?", Kind.COUNT_ONLY, 20, 16, 3, List.of(), null, null);

        Section card = tables.card(new Card("Smoking", List.of(q, free)), 32);

        assertNull(card.note());
        assertEquals(3, card.tables().size(), "a count-only question has no distribution");
        Table figures = card.tables().get(0);
        assertEquals("Smoke?", figures.title());
        assertEquals(List.of("Presented", "Respondents", "Answered", "Response rate", "Reach"), figures.headers());
        assertEquals(List.of(List.of("20", "16", "10", "50%", "50%")), figures.rows());
        Table distribution = card.tables().get(1);
        assertEquals(List.of("Option", "Count", "Share"), distribution.headers());
        assertEquals(List.of(List.of("Yes", "6", "60%"), List.of("9 (not a current option)", "< 5", "—")), distribution.rows());
        assertTrue(distribution.chart().contains("<svg"));
        assertEquals(List.of(List.of("20", "16", "< 5", "—", "50%")), card.tables().get(2).rows());
    }

    @Test
    void given_aCheckbox_when_distribution_then_yesAndNoTranslated() {
        QuestionStats q = new QuestionStats("Agree", Kind.CHECKBOX, 10, 10, 10,
                List.of(new OptionCount("yes", 7, true), new OptionCount("no", 3, true)), null, null);

        assertEquals(List.of(List.of("Yes", "7", "70%"), List.of("No", "< 5", "—")), tables.distribution(q).rows());
    }

    @Test
    void given_enoughNumbers_when_distribution_then_theSummaryInTheLocale() {
        QuestionStats q = new QuestionStats("Age", Kind.NUMBER, 9, 9, 9, List.of(), new NumberSummary(9,
                new BigDecimal("1"), new BigDecimal("2.5"), new BigDecimal("1234.567"), new BigDecimal("40"), new BigDecimal("99")), null);

        Table t = tables.distribution(q);

        assertEquals(List.of("Statistic", "Value"), t.headers());
        assertEquals(List.of(List.of("Answers", "9"), List.of("Minimum", "1"), List.of("First quartile", "2.5"),
                List.of("Median", "1,234.57"), List.of("Third quartile", "40"), List.of("Maximum", "99")), t.rows());
        assertNull(t.chart());
    }

    @Test
    void given_fewOrNoNumbers_when_distribution_then_theSummaryIsWithheld() {
        QuestionStats few = new QuestionStats("Age", Kind.NUMBER, 3, 3, 3, List.of(), new NumberSummary(3,
                BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE), null);
        QuestionStats none = new QuestionStats("Age", Kind.NUMBER, 0, 0, 0, List.of(), null, null);

        assertEquals(List.of(List.of("Answers", "< 5"), List.of("Minimum", "—"), List.of("First quartile", "—"),
                List.of("Median", "—"), List.of("Third quartile", "—"), List.of("Maximum", "—")), tables.distribution(few).rows());
        assertEquals(List.of("Answers", "0"), tables.distribution(none).rows().get(0));
        assertTrue(tables.distribution(none).rows().stream().skip(1).allMatch(r -> r.get(1).equals(SmallCell.NO_VALUE)));
    }

    @Test
    void given_enoughDates_when_distribution_then_rangeAndOneRowPerMonth() {
        TreeMap<YearMonth, Long> perMonth = new TreeMap<>();
        perMonth.put(YearMonth.of(2026, 1), 3L);
        perMonth.put(YearMonth.of(2026, 3), 7L);
        QuestionStats q = new QuestionStats("When", Kind.DATE, 10, 10, 10, List.of(), null,
                new DateSummary(10, LocalDate.of(2026, 1, 5), LocalDate.of(2026, 3, 1), perMonth));

        Table t = tables.distribution(q);

        assertEquals(List.of(List.of("Answers", "10"), List.of("Earliest", "Jan 5, 2026"), List.of("Latest", "Mar 1, 2026"),
                List.of("Jan 2026", "< 5"), List.of("Mar 2026", "7")), t.rows());
    }

    @Test
    void given_fewOrNoDates_when_distribution_then_theRangeIsWithheld() {
        QuestionStats few = new QuestionStats("When", Kind.DATE, 2, 2, 2, List.of(), null,
                new DateSummary(2, LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 6), new TreeMap<>(java.util.Map.of(YearMonth.of(2026, 1), 2L))));
        QuestionStats none = new QuestionStats("When", Kind.DATE, 0, 0, 0, List.of(), null, null);

        assertEquals(List.of(List.of("Answers", "< 5"), List.of("Earliest", "—"), List.of("Latest", "—"),
                List.of("Jan 2026", "< 5")), tables.distribution(few).rows());
        assertEquals(List.of(List.of("Answers", "0"), List.of("Earliest", "—"), List.of("Latest", "—")),
                tables.distribution(none).rows());
    }

    @Test
    void given_aCountOnlyQuestion_when_distribution_then_none() {
        assertNull(tables.distribution(new QuestionStats("Why", Kind.COUNT_ONLY, 1, 1, 1, List.of(), null, null)));
    }

    @Test
    void given_steps_when_progress_then_dropOffAndTimeTablesWithUnnamedStepsNumbered() {
        Progress progress = new Progress(List.of(
                new StepProgress(1, "Household", 12, 6, new Durations(12, 30L, 600L, 7200L)),
                new StepProgress(2, null, 3, 0, new Durations(3, 60L, 60L, 60L)),
                new StepProgress(3, " ", 0, 0, Durations.NONE)),
                7, 2, new Durations(9, 59L, 3600L, 90000L), 0);

        Section section = tables.progress(progress, "Stalled");

        assertEquals("Progress", section.title());
        assertTrue(section.note().startsWith("A respondent in progress stopped"));
        assertFalse(section.note().contains("left out"), section.note());
        Table stopped = section.tables().get(0);
        assertEquals("Where respondents in progress stopped", stopped.title());
        assertEquals(List.of("Step", "Stopped here", "Stalled"), stopped.headers());
        assertEquals(List.of(List.of("Household", "12", "6"), List.of("Step 2", "< 5", "0"), List.of("Step 3", "0", "0"),
                List.of("Not recorded", "7", "< 5")), stopped.rows());
        assertTrue(stopped.chart().contains("<svg"));
        Table time = section.tables().get(1);
        assertEquals("Time spent by finished respondents", time.title());
        assertEquals(List.of("Step", "Respondents", "First quartile", "Median", "Third quartile"), time.headers());
        assertEquals(List.of(List.of("Household", "12", "< 1 min", "10 min", "2 h 0 min"),
                List.of("Step 2", "< 5", "—", "—", "—"), List.of("Step 3", "0", "—", "—", "—"),
                List.of("Whole survey (first access to finish)", "9", "< 1 min", "1 h 0 min", "1 d 1 h")), time.rows());
        assertNull(time.chart());
    }

    @Test
    void given_finishedRespondentsUnmeasured_when_progress_then_theNoteSaysHowMany() {
        Section section = tables.progress(new Progress(List.of(), 0, 0, Durations.NONE, 6), "Stalled");

        assertTrue(section.note().contains("6 finished respondents took the survey before pages were recorded"), section.note());
        assertEquals(List.of(List.of("Not recorded", "0", "0")), section.tables().get(0).rows());
    }

    @Test
    void given_durations_when_formatted_then_theLargestTwoUnits() {
        assertEquals("—", tables.duration(null));
        assertEquals("< 1 min", tables.duration(0L));
        assertEquals("< 1 min", tables.duration(59L));
        assertEquals("1 min", tables.duration(60L));
        assertEquals("59 min", tables.duration(3599L));
        assertEquals("1 h 0 min", tables.duration(3600L));
        assertEquals("23 h 59 min", tables.duration(86399L));
        assertEquals("1 d 0 h", tables.duration(86400L));
        assertEquals("2 d 3 h", tables.duration(2 * 86400L + 3 * 3600L + 59 * 60L));
    }

    @Test
    void given_aRange_when_printed_then_openEndsAreAnyDate() {
        assertEquals("Jan 1, 2026 and Feb 28, 2026", tables.range(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 28)));
        assertEquals("any date and Feb 28, 2026", tables.range(null, LocalDate.of(2026, 2, 28)));
        assertEquals("any date and any date", tables.range(null, null));
    }

    @Test
    void given_anotherLocale_when_rendered_then_itsWordsAndNumbers() {
        ParticipationTables spanish = new ParticipationTables(new SmallCell(5, Locale.forLanguageTag("es-419")));

        assertEquals("Participación", spanish.sections(participation(List.of(), List.of())).get(0).title());
        assertEquals(spanish.t("participationView.duration.minutes", "2"), spanish.duration(120L));
    }
}
