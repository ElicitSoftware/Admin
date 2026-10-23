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
import com.elicitsoftware.participation.Participation.DepartmentCounts;
import com.elicitsoftware.participation.Participation.Durations;
import com.elicitsoftware.participation.Participation.Kind;
import com.elicitsoftware.participation.Participation.NumberSummary;
import com.elicitsoftware.participation.Participation.OptionCount;
import com.elicitsoftware.participation.Participation.Progress;
import com.elicitsoftware.participation.Participation.QuestionStats;
import com.elicitsoftware.participation.Participation.StepProgress;
import com.elicitsoftware.participation.Participation.Week;
import com.elicitsoftware.participation.ParticipationService.AnswerGroup;
import com.elicitsoftware.participation.ParticipationService.Filter;
import com.elicitsoftware.participation.ParticipationService.Item;
import com.elicitsoftware.participation.ParticipationService.Respondent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UC-031: Monitor a Survey's Participation — the figures the page is built from, computed by
 * {@link ParticipationService} from native-query rows a {@link NativeQueryStub} hands back:
 * the population and its filter (BR-120), the participation states and stalled respondents
 * (BR-121, BR-131), Monday weeks (BR-128), drop-off and time per step (BR-130, BR-132, BR-133),
 * and the marked-question cards with their distributions (BR-123 to BR-125).
 */
class ParticipationServiceUnitTest {

    private static final ZoneId PARIS = ZoneId.of("Europe/Paris");
    private static final Instant NOW = Instant.parse("2026-03-01T12:00:00Z");
    private static final String RESPONDENTS = "SELECT r.id, s.department_id";
    private static final String STEPS = "SELECT display_order, name FROM survey.steps";
    private static final String LAST_STEPS = "DISTINCT ON (a.respondent_id)";
    private static final String STEP_STARTS = "min(a.presented_dt)";
    private static final String MARKS = "FROM survey.report_items";
    private static final String GROUPS = "GROUPING SETS";
    private static final String ITEMS = "FROM survey.questions q JOIN";
    private static final String PLACED = "SELECT question_id FROM survey.sections_questions";
    private static final String SECTION_QUESTIONS = "SELECT sq.question_id FROM survey.sections_questions sq";
    private static final String SECTION_TITLE = "SELECT (SELECT st.name";
    private static final String OPTIONS = "FROM survey.select_items";

    private NativeQueryStub db;
    private ParticipationService service;

    @BeforeEach
    void setUp() {
        db = new NativeQueryStub();
        service = new ParticipationService();
        service.em = db.entityManager();
        service.zone = PARIS;
        service.clock = Clock.fixed(NOW, ZoneOffset.UTC);
        service.stalledDays = 14;
    }

    private static OffsetDateTime at(String instant) {
        return OffsetDateTime.parse(instant);
    }

    private static Object[] row(Object... values) {
        return values;
    }

    private static Filter filter(Long... departments) {
        return new Filter(7, List.of(departments), null, null);
    }

    /** Six respondents across two departments, each with a different state. */
    private void sixRespondents() {
        db.on(RESPONDENTS, List.of(
                // not started; registered 23:30 UTC on Sunday, which is already Monday in Paris
                row(1L, 10L, at("2026-01-04T23:30:00Z"), null, null, null),
                // in progress, active recently, stopped at step 2
                row(2, 10, Instant.parse("2026-01-06T09:00:00Z"), Instant.parse("2026-01-07T09:00:00Z"), null,
                        Instant.parse("2026-02-20T09:00:00Z")),
                // in progress, never active after the first access, no presented row
                row(BigInteger.valueOf(3), 20L, Timestamp.from(Instant.parse("2026-01-07T09:00:00Z")),
                        Timestamp.from(Instant.parse("2026-01-08T09:00:00Z")), null, null),
                // finished, with a stamp for each of three steps shown out of order
                row(4L, 20L, at("2026-01-20T09:00:00Z"), at("2026-01-21T10:00:00Z"), at("2026-01-21T11:00:00Z"),
                        at("2026-01-21T11:00:00Z")),
                // in progress and stalled, stopped at a step that is no longer in the survey
                row(5L, 10L, at("2026-01-21T09:00:00Z"), at("2026-01-22T10:00:00Z"), null, at("2026-02-01T09:00:00Z")),
                // finished without a first access and without any stamp
                row(6L, 10L, at("2026-01-22T09:00:00Z"), null, at("2026-02-03T09:00:00Z"), null)));
        db.on(STEPS, List.of(row(1, "Household {Q#}"), row(2, "People"), row(2, "Duplicate of 2"), row(3, null)));
        db.on(LAST_STEPS, List.of(row(2L, 2), row(5L, 4)));
        db.on(STEP_STARTS, List.of(row(4L, 1, at("2026-01-21T10:00:00Z")), row(4L, 3, at("2026-01-21T10:20:00Z")),
                row(4L, 2, at("2026-01-21T10:30:00Z"))));
        db.on(MARKS, List.of());
    }

    // ---- compute: participation (BR-120, BR-121, BR-128, BR-131) ------------------------------------

    @Test
    void given_sixRespondents_when_compute_then_overallCountsByState() {
        sixRespondents();

        Participation p = service.compute(filter(10L, 20L), Map.of(10L, "Alpha", 20L, "Beta"));

        assertEquals(new Counts(6, 1, 3, 2, 2), p.overall());
        assertEquals(14, p.stalledDays());
        assertTrue(p.cards().isEmpty());
    }

    @Test
    void given_departmentNamesInTableOrder_when_compute_then_onlyFilteredDepartmentsInThatOrder() {
        sixRespondents();
        Map<Long, String> names = new LinkedHashMap<>();
        names.put(20L, "Beta");
        names.put(30L, "Gamma");
        names.put(10L, "Alpha");

        Participation p = service.compute(filter(10L, 20L), names);

        assertEquals(List.of(new DepartmentCounts("Beta", new Counts(2, 0, 1, 1, 1)),
                new DepartmentCounts("Alpha", new Counts(4, 1, 2, 1, 1))), p.departments());
    }

    @Test
    void given_sixRespondents_when_compute_then_everyMondayFromFirstRegistrationToLastEvent() {
        sixRespondents();

        Participation p = service.compute(filter(10L, 20L), Map.of());

        assertEquals(List.of(
                new Week(LocalDate.of(2026, 1, 5), 3, 2, 0),
                new Week(LocalDate.of(2026, 1, 12), 0, 0, 0),
                new Week(LocalDate.of(2026, 1, 19), 3, 2, 1),
                new Week(LocalDate.of(2026, 1, 26), 0, 0, 0),
                new Week(LocalDate.of(2026, 2, 2), 0, 0, 1)), p.weeks());
    }

    @Test
    void given_sixRespondents_when_compute_then_dropOffAndTimePerStep() {
        sixRespondents();

        Progress progress = service.compute(filter(10L, 20L), Map.of()).progress();

        assertEquals(List.of(
                new StepProgress(1, "Household", 0, 0, new Durations(1, 1200L, 1200L, 1200L)),
                new StepProgress(2, "People", 1, 0, new Durations(1, 1800L, 1800L, 1800L)),
                new StepProgress(3, null, 0, 0, new Durations(1, 600L, 600L, 600L)),
                new StepProgress(4, null, 1, 1, Durations.NONE)), progress.steps());
        assertEquals(1, progress.notRecorded());
        assertEquals(1, progress.notRecordedStalled());
        assertEquals(new Durations(1, 3600L, 3600L, 3600L), progress.wholeSurvey());
        assertEquals(1, progress.finishedUnmeasured(), "respondent 6 finished with no stamp");
    }

    @Test
    void given_aRegistrationRange_when_compute_then_boundInTheZoneWithAnExclusiveEnd() {
        sixRespondents();
        Filter filter = new Filter(7, List.of(10L), LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));

        service.compute(filter, Map.of());

        NativeQueryStub.Executed respondents = db.executed(RESPONDENTS).getFirst();
        assertTrue(respondents.sql().contains(
                "r.survey_id = :surveyId AND s.department_id IN (:departments) AND r.created_dt >= :from"
                        + " AND r.created_dt < :toExclusive"), respondents.sql());
        assertEquals(Map.of("surveyId", 7, "departments", List.of(10L),
                "from", OffsetDateTime.of(2026, 1, 1, 0, 0, 0, 0, ZoneOffset.ofHours(1)),
                "toExclusive", OffsetDateTime.of(2026, 2, 1, 0, 0, 0, 0, ZoneOffset.ofHours(1))), respondents.parameters());
        for (String query : List.of(LAST_STEPS, STEP_STARTS)) {
            NativeQueryStub.Executed e = db.executed(query).getFirst();
            assertTrue(e.sql().contains("AND r.created_dt >= :from AND r.created_dt < :toExclusive"), e.sql());
            assertEquals(respondents.parameters(), e.parameters());
        }
        assertEquals(Map.of("surveyId", 7), db.executed(STEPS).getFirst().parameters());
    }

    @Test
    void given_onlyAnOpenStart_when_compute_then_onlyTheEndIsBound() {
        sixRespondents();

        service.compute(new Filter(7, List.of(10L), null, LocalDate.of(2026, 1, 31)), Map.of());

        NativeQueryStub.Executed respondents = db.executed(RESPONDENTS).getFirst();
        assertFalse(respondents.sql().contains(":from"), respondents.sql());
        assertTrue(respondents.sql().contains("r.created_dt < :toExclusive"), respondents.sql());
        assertFalse(respondents.parameters().containsKey("from"));
        assertTrue(respondents.parameters().containsKey("toExclusive"));
    }

    @Test
    void given_onlyAnOpenEnd_when_compute_then_onlyTheStartIsBound() {
        sixRespondents();

        service.compute(new Filter(7, List.of(10L), LocalDate.of(2026, 1, 1), null), Map.of());

        NativeQueryStub.Executed respondents = db.executed(RESPONDENTS).getFirst();
        assertTrue(respondents.sql().contains("r.created_dt >= :from"), respondents.sql());
        assertFalse(respondents.sql().contains(":toExclusive"), respondents.sql());
        assertTrue(respondents.parameters().containsKey("from"));
        assertFalse(respondents.parameters().containsKey("toExclusive"));
    }

    @Test
    void given_noDepartment_when_compute_then_nobodyIsCountedAndNoRespondentIsRead() {
        db.on(STEPS, List.<Object[]>of(row(1, "Household")));
        db.on(MARKS, List.<Object[]>of(row(1, 100, null, null, "Smoker")));
        db.on(ITEMS, List.<Object[]>of(row(100, "Do you smoke?", "RADIO", null)));

        Participation p = service.compute(filter(), Map.of(10L, "Alpha"));

        assertEquals(new Counts(0, 0, 0, 0, 0), p.overall());
        assertTrue(p.departments().isEmpty());
        assertTrue(p.weeks().isEmpty());
        assertEquals(List.of(new StepProgress(1, "Household", 0, 0, Durations.NONE)), p.progress().steps());
        assertEquals(0, p.progress().finishedUnmeasured());
        assertEquals(1, p.cards().size(), "the cards are listed even when nobody is counted");
        assertEquals(List.of(new QuestionStats("Do you smoke?", Kind.CHOICE, 0, 0, 0, List.of(), null, null)),
                p.cards().getFirst().questions());
        assertTrue(db.executed(RESPONDENTS).isEmpty());
        assertTrue(db.executed(LAST_STEPS).isEmpty());
        assertTrue(db.executed(STEP_STARTS).isEmpty());
        assertTrue(db.executed(GROUPS).isEmpty(), "BR-120: an empty filter counts no answer");
    }

    @Test
    void given_anUnexpectedTimestampType_when_readingRespondents_then_itIsRefused() {
        db.on(RESPONDENTS, List.<Object[]>of(row(1L, 10L, "2026-01-01", null, null, null)));

        IllegalStateException e = assertThrows(IllegalStateException.class, () -> service.respondents(filter(10L)));

        assertEquals("Unexpected timestamp type class java.lang.String", e.getMessage());
    }

    // ---- counts and stalled (BR-121, BR-131) ----------------------------------------------------------

    @Test
    void given_lastActivityExactlyAtTheCutoff_when_counts_then_notStalled() {
        OffsetDateTime cutoff = at("2026-02-15T12:00:00Z");
        OffsetDateTime first = at("2026-01-01T00:00:00Z");
        List<Respondent> respondents = List.of(
                new Respondent(1, 1, first, first, null, cutoff),
                new Respondent(2, 1, first, first, null, cutoff.minusSeconds(1)),
                new Respondent(3, 1, first, cutoff, null, null),
                new Respondent(4, 1, first, cutoff.minusSeconds(1), null, null));

        assertEquals(new Counts(4, 0, 4, 0, 2), ParticipationService.counts(respondents, cutoff));
    }

    @Test
    void given_aRespondentNotInProgress_when_stalled_then_never() {
        OffsetDateTime old = at("2020-01-01T00:00:00Z");
        OffsetDateTime cutoff = at("2026-01-01T00:00:00Z");

        assertFalse(new Respondent(1, 1, old, null, null, old).stalled(cutoff), "not started");
        assertFalse(new Respondent(1, 1, old, old, old, old).stalled(cutoff), "finished");
        assertTrue(new Respondent(1, 1, old, old, null, null).stalled(cutoff));
        assertFalse(new Respondent(1, 1, old, old, null, cutoff).stalled(cutoff), "the last activity wins over the first access");
    }

    // ---- progress (BR-130, BR-132, BR-133) ------------------------------------------------------------

    @Test
    void given_aStepStampedAfterTheFinish_when_progress_then_itsTimeIsZeroNotNegative() {
        db.on(STEPS, List.of());
        db.on(LAST_STEPS, List.of());
        db.on(STEP_STARTS, List.of(row(1L, 1, at("2026-01-01T10:00:00Z")), row(1L, 2, at("2026-01-01T12:00:00Z"))));
        OffsetDateTime finished = at("2026-01-01T11:00:00Z");
        List<Respondent> respondents = List.of(
                new Respondent(1, 1, finished, at("2026-01-01T12:30:00Z"), finished, null));

        Progress progress = service.progress(filter(1L), respondents, at("2026-01-01T00:00:00Z"));

        assertEquals(List.of(new StepProgress(1, null, 0, 0, new Durations(1, 7200L, 7200L, 7200L)),
                new StepProgress(2, null, 0, 0, new Durations(1, 0L, 0L, 0L))), progress.steps());
        assertEquals(new Durations(1, 0L, 0L, 0L), progress.wholeSurvey(), "a first access after the finish counts as zero");
        assertEquals(0, progress.finishedUnmeasured());
    }

    @Test
    void given_aFinishedRespondentWithAnEmptyStampSet_when_progress_then_unmeasured() {
        db.on(STEPS, List.of());
        db.on(LAST_STEPS, List.of());
        db.on(STEP_STARTS, List.of());
        OffsetDateTime t = at("2026-01-01T11:00:00Z");

        Progress progress = service.progress(filter(1L), List.of(new Respondent(1, 1, t, t, t, null),
                new Respondent(2, 1, t, null, null, null)), t);

        assertTrue(progress.steps().isEmpty());
        assertEquals(1, progress.finishedUnmeasured());
        assertEquals(0, progress.notRecorded(), "a respondent who has not started has not stopped anywhere");
    }

    @Test
    void given_durations_when_summarised_then_quartilesRoundedToTheSecond() {
        assertEquals(Durations.NONE, ParticipationService.durations(null));
        assertEquals(Durations.NONE, ParticipationService.durations(List.of()));
        assertEquals(new Durations(5, 20L, 30L, 40L), ParticipationService.durations(List.of(50L, 10L, 40L, 20L, 30L)));
        assertEquals(new Durations(2, 1L, 2L, 2L), ParticipationService.durations(List.of(1L, 2L)), "1.25, 1.5, 1.75");
        assertEquals(new Durations(3, 5L, 5L, 8L), ParticipationService.durations(List.of(5L, 5L, 10L)), "ties are counted");
    }

    // ---- distributions (BR-125) -----------------------------------------------------------------------

    @Test
    void given_numbers_when_summarised_then_typeSevenQuartilesAndNonNumbersIgnored() {
        Map<String, Long> values = new LinkedHashMap<>();
        values.put("4", 1L);
        values.put("2", 1L);
        values.put("1", 1L);
        values.put("3.0", 1L);
        values.put("many", 5L);

        NumberSummary s = ParticipationService.numbers(values);

        assertEquals(4, s.n());
        assertEquals(new BigDecimal("1"), s.min());
        assertEquals(new BigDecimal("1.75"), s.q1());
        assertEquals(new BigDecimal("2.5"), s.median());
        assertEquals(new BigDecimal("3.25"), s.q3());
        assertEquals(new BigDecimal("4"), s.max());
    }

    @Test
    void given_repeatedNumbers_when_summarised_then_eachAnswerCounts() {
        NumberSummary s = ParticipationService.numbers(Map.of("10", 3L, "20", 1L));

        assertEquals(4, s.n());
        assertNumber("10", s.min());
        assertNumber("10", s.q1());
        assertNumber("10", s.median());
        assertNumber("12.5", s.q3());
        assertNumber("20", s.max());
    }

    private static void assertNumber(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), expected + " but was " + actual);
    }

    @Test
    void given_noNumber_when_summarised_then_null() {
        assertNull(ParticipationService.numbers(Map.of()));
        assertNull(ParticipationService.numbers(Map.of("n/a", 3L)));
    }

    @Test
    void given_aQuantileBetweenTwoValues_when_computed_then_roundedToTwoDecimals() {
        TreeMap<BigDecimal, Long> sorted = new TreeMap<>(Map.of(BigDecimal.ZERO, 1L, BigDecimal.ONE, 1L));
        sorted.put(BigDecimal.valueOf(2), 1L);
        sorted.put(BigDecimal.valueOf(3), 1L);

        assertEquals(new BigDecimal("0.67"), ParticipationService.quantile(new TreeMap<>(Map.of(BigDecimal.ZERO, 1L,
                BigDecimal.valueOf(2), 1L)), 2, 1, 3));
        assertEquals(new BigDecimal("3"), ParticipationService.quantile(sorted, 4, 1, 1));
        assertEquals(BigDecimal.ZERO, ParticipationService.quantile(sorted, 4, 0, 4));
    }

    @Test
    void given_dates_when_summarised_then_rangeAndCountPerMonth() {
        Map<String, Long> values = new LinkedHashMap<>();
        values.put("2026-03-01", 1L);
        values.put("2026-01-05", 2L);
        values.put("2026-01-20", 1L);
        values.put("yesterday", 4L);

        DateSummary s = ParticipationService.dates(values);

        assertEquals(4, s.n());
        assertEquals(LocalDate.of(2026, 1, 5), s.earliest());
        assertEquals(LocalDate.of(2026, 3, 1), s.latest());
        assertEquals(Map.of(YearMonth.of(2026, 1), 3L, YearMonth.of(2026, 3), 1L), s.perMonth());
    }

    @Test
    void given_noDate_when_summarised_then_null() {
        assertNull(ParticipationService.dates(Map.of()));
        assertNull(ParticipationService.dates(Map.of("soon", 1L)));
    }

    private static AnswerGroup total(int question, long rows, long respondents, long answered) {
        return new AnswerGroup(question, null, false, rows, respondents, answered);
    }

    private static AnswerGroup value(int question, String value, long rows) {
        return new AnswerGroup(question, value, true, rows, 0, 0);
    }

    @Test
    void given_aSingleChoiceWithCurrentOptions_when_stats_then_optionsInOrderThenUnlistedValues() {
        db.on(OPTIONS, List.of(row("1", "Yes, {<NAME>|often}"), row("2", null), row(null, "Blank")));
        List<AnswerGroup> groups = List.of(total(100, 12, 10, 9), value(100, "1", 3), value(100, " 1 ", 1),
                value(100, "9", 2), value(100, "  ", 5), value(100, null, 4), value(100, "", 7));

        QuestionStats q = service.stats(new Item(100, "Smoke?", "RADIO", 5), groups);

        assertEquals(new QuestionStats("Smoke?", Kind.CHOICE, 12, 10, 9, List.of(
                new OptionCount("Yes, often", 4, true), new OptionCount("2", 0, true),
                new OptionCount("Blank", 0, true), new OptionCount("9", 2, false)), null, null), q);
        assertEquals(Map.of("group", 5), db.executed(OPTIONS).getFirst().parameters());
        assertTrue(db.executed(OPTIONS).getFirst().sql().contains("effective_to = " + ParticipationService.CURRENT));
    }

    @Test
    void given_aMultipleChoice_when_stats_then_eachCodeOfAStoredListIsCounted() {
        List<AnswerGroup> groups = List.of(total(1, 3, 3, 3), value(1, "a,b", 2), value(1, "b, ,c", 1));

        for (String type : List.of("MULTI_SELECT", "CHECKBOX_GROUP", "MULTI_SELECT_COMBOBOX")) {
            QuestionStats q = service.stats(new Item(1, "Pick", type, null), groups);

            assertEquals(Kind.CHOICE, q.kind(), type);
            assertEquals(List.of(new OptionCount("a", 2, false), new OptionCount("b", 3, false),
                    new OptionCount("c", 1, false)), q.options(), type);
        }
        assertTrue(db.executed.isEmpty(), "a question without a select group reads no option");
    }

    @Test
    void given_aSingleChoiceStoringACommaList_when_stats_then_theValueIsNotSplit() {
        for (String type : List.of("COMBOBOX", "RADIO")) {
            QuestionStats q = service.stats(new Item(1, "Pick", type, null), List.of(value(1, "a,b", 2)));

            assertEquals(List.of(new OptionCount("a,b", 2, false)), q.options(), type);
        }
    }

    @Test
    void given_aCheckbox_when_stats_then_yesAndNoOfTheAnswered() {
        QuestionStats q = service.stats(new Item(1, "Agree", "CHECKBOX", null),
                List.of(total(1, 6, 6, 5), value(1, "true", 3), value(1, "TRUE", 1), value(1, "false", 1)));

        assertEquals(new QuestionStats("Agree", Kind.CHECKBOX, 6, 6, 5,
                List.of(new OptionCount("yes", 4, true), new OptionCount("no", 1, true)), null, null), q);
    }

    @Test
    void given_numberDateAndOtherTypes_when_stats_then_theSummarySuitedToEach() {
        List<AnswerGroup> numbers = List.of(total(1, 2, 2, 2), value(1, "3", 1), value(1, "5", 1));
        for (String type : List.of("INTEGER", "DOUBLE")) {
            QuestionStats q = service.stats(new Item(1, "How many", type, null), numbers);
            assertEquals(Kind.NUMBER, q.kind());
            assertEquals(new BigDecimal("4"), q.numbers().median());
            assertNull(q.dates());
            assertTrue(q.options().isEmpty());
        }

        QuestionStats date = service.stats(new Item(1, "When", "DATE_PICKER", null),
                List.of(total(1, 1, 1, 1), value(1, "2026-02-03", 1)));
        assertEquals(Kind.DATE, date.kind());
        assertEquals(LocalDate.of(2026, 2, 3), date.dates().earliest());
        assertNull(date.numbers());

        QuestionStats text = service.stats(new Item(1, "Why", "TEXT", null), List.of(total(1, 4, 3, 2)));
        assertEquals(new QuestionStats("Why", Kind.COUNT_ONLY, 4, 3, 2, List.of(), null, null), text);
        assertEquals(Kind.COUNT_ONLY, service.stats(new Item(1, "?", null, null), List.of()).kind());
    }

    // ---- cards (BR-123, BR-124) -----------------------------------------------------------------------

    @Test
    void given_marksOfEachKind_when_cards_then_oneCardEachTitledAndScoped() {
        db.on(MARKS, List.of(
                row(1, 100, null, null, null),
                row(2, null, 200, null, "  "),
                row(3, null, null, 300, "Custom {Q#} label"),
                row(4, null, 201, null, null),
                row(5, null, null, 301, null)));
        db.on(GROUPS, p -> {
            if (Integer.valueOf(100).equals(p.get("markQuestion"))) {
                return List.of(row(100, null, 1, 5L, 4L, 3L), row(100, "1", 0, 3L, 3L, 3L));
            }
            if (Integer.valueOf(300).equals(p.get("markSection"))) {
                return List.<Object[]>of(row(999, null, 1, 1L, 1L, 0L));
            }
            return List.of();
        });
        db.on(ITEMS, p -> switch ((Integer) p.get("id")) {
            case 100 -> List.<Object[]>of(row(100, "Do you smoke, {<FIRST_NAME>|friend}?", "RADIO", null));
            case 101 -> List.<Object[]>of(row(101, "Age", "INTEGER", null));
            case 102 -> List.<Object[]>of(row(102, "Intro", "HTML", null));
            case 103 -> List.<Object[]>of(row(103, "Name", "TEXT", null));
            case 999 -> List.<Object[]>of(row(999, "Retired question", "TEXT", null));
            default -> List.of();
        });
        db.on(PLACED, p -> Integer.valueOf(200).equals(p.get("id")) ? List.of(101) : List.of());
        db.on(SECTION_QUESTIONS, p -> Integer.valueOf(300).equals(p.get("id")) ? List.of(102, 103, 102) : List.of());
        db.on(SECTION_TITLE, p -> Integer.valueOf(300).equals(p.get("id"))
                ? List.<Object[]>of(row("Household", "Members")) : List.of());

        List<Card> cards = service.cards(filter(10L));

        assertEquals(List.of("Do you smoke, friend?", "Age", "Custom label", "", ""), cards.stream().map(Card::title).toList());
        assertEquals(List.of(new QuestionStats("Do you smoke, friend?", Kind.CHOICE, 5, 4, 3,
                List.of(new OptionCount("1", 3, false)), null, null)), cards.get(0).questions());
        assertEquals(List.of("Age"), cards.get(1).questions().stream().map(QuestionStats::text).toList());
        assertEquals(List.of(new QuestionStats("Name", Kind.COUNT_ONLY, 0, 0, 0, List.of(), null, null),
                        new QuestionStats("Retired question", Kind.COUNT_ONLY, 1, 1, 0, List.of(), null, null)),
                cards.get(2).questions(), "the section's questions in order, less an HTML block, then a question only the answers name");
        assertTrue(cards.get(3).questions().isEmpty());

        assertEquals(Map.of("surveyId", 7), db.executed(MARKS).getFirst().parameters());
        List<NativeQueryStub.Executed> groups = db.executed(GROUPS);
        assertEquals(5, groups.size());
        assertTrue(groups.get(0).sql().contains("AND (q.question_id = :markQuestion))"), groups.get(0).sql());
        assertEquals(Map.of("surveyId", 7, "departments", List.of(10L), "markQuestion", 100), groups.get(0).parameters());
        assertTrue(groups.get(1).sql().contains("sq.sections_question_id = :markPlacement"), groups.get(1).sql());
        assertEquals(200, groups.get(1).parameters().get("markPlacement"));
        assertTrue(groups.get(2).sql().contains("ss.steps_sections_id = :markSection"), groups.get(2).sql());
        assertEquals(300, groups.get(2).parameters().get("markSection"));
    }

    @Test
    void given_sectionTitlesWithMissingParts_when_cards_then_theTitleIsWhatIsKnown() {
        db.on(MARKS, List.of(row(1, null, null, 1, null), row(2, null, null, 2, null), row(3, null, null, 3, null)));
        db.on(GROUPS, List.of());
        db.on(SECTION_QUESTIONS, List.of());
        db.on(SECTION_TITLE, p -> switch ((Integer) p.get("id")) {
            case 1 -> List.<Object[]>of(row(null, "Members"));
            case 2 -> List.<Object[]>of(row("Household", null));
            default -> List.<Object[]>of(row("Step {S#}", "Section {<N>}"));
        });

        List<Card> cards = service.cards(filter(10L));

        assertEquals(List.of("Members", "Household", "Step › Section N"), cards.stream().map(Card::title).toList());
    }

    @Test
    void given_answerGroups_when_read_then_perQuestionAndPerValueRowsAreTold() {
        db.on(GROUPS, List.of(row(BigInteger.valueOf(5), null, 1, 4L, 3, 2L), row(5, 7, 0, 2, 2, 2)));

        List<AnswerGroup> groups = service.answerGroups(filter(10L), "true", new java.util.HashMap<>(Map.of("x", 1)));

        assertEquals(List.of(new AnswerGroup(5, null, false, 4, 3, 2), new AnswerGroup(5, "7", true, 2, 2, 2)), groups);
        String sql = db.executed(GROUPS).getFirst().sql();
        assertTrue(sql.contains("CASE WHEN t.name IN ('CHECKBOX', 'CHECKBOX_GROUP', 'COMBOBOX', 'DATE_PICKER', 'DOUBLE',"
                + " 'INTEGER', 'MULTI_SELECT', 'MULTI_SELECT_COMBOBOX', 'RADIO') THEN a.text_value END"), sql);
        assertTrue(sql.contains("AND t.name NOT IN ('HTML', 'MODAL') AND (true)"), sql);
        assertEquals(1, db.executed(GROUPS).getFirst().parameters().get("x"));
    }

    @Test
    void given_itemsWithDuplicatesAndValuelessTypes_when_questions_then_oneStatEachInItemOrder() {
        List<Item> items = List.of(new Item(2, "Second", "TEXT", null), new Item(1, "First", "TEXT", null),
                new Item(2, "Again", "TEXT", null), new Item(3, "Popup", "MODAL", null));

        List<QuestionStats> stats = service.questions(items, List.of(total(1, 1, 1, 1)));

        assertEquals(List.of("Second", "First"), stats.stream().map(QuestionStats::text).toList());
        assertEquals(List.of(0L, 1L), stats.stream().map(QuestionStats::presented).toList());
        assertTrue(db.executed.isEmpty(), "every answered question is listed, so nothing more is read");
    }

    // ---- authored text (BR-123) -----------------------------------------------------------------------

    @Test
    void given_placeholders_when_plain_then_defaultsPhrasesAndNoCounters() {
        assertNull(ParticipationService.plain(null));
        assertEquals("No braces  here ", ParticipationService.plain("No braces  here "), "untouched without a placeholder");
        assertEquals("Hello friend!", ParticipationService.plain("Hello {<NAME>|friend}!"));
        assertEquals("Hello NAME and OTHER!", ParticipationService.plain("Hello {<NAME> and <OTHER>}!"));
        assertEquals("Question about you", ParticipationService.plain("{ Q# } Question  {S#} about {<P>|}you"));
        assertEquals("Cost $5", ParticipationService.plain("Cost {<X>|$5}"), "a replacement is taken literally");
    }

    @Test
    void given_theStepsOfASurvey_when_stepNames_then_firstNamePerOrderAndCurrentOnly() {
        db.on(STEPS, List.of(row(2L, "B {<X>|b}"), row(1, null), row(2, "ignored")));

        Map<Integer, String> names = service.stepNames(7);

        assertEquals(List.of(2, 1), new ArrayList<>(names.keySet()));
        assertEquals("B b", names.get(2));
        assertTrue(names.containsKey(1));
        assertNull(names.get(1));
        assertTrue(db.executed(STEPS).getFirst().sql().contains("effective_to = " + ParticipationService.CURRENT));
    }

    @Test
    void given_theWeeksOfNobody_when_weeks_then_none() {
        assertTrue(service.weeks(List.of()).isEmpty());
        assertTrue(service.weeks(Arrays.asList(new Respondent(1, 1, null, null, null, null))).isEmpty());
    }
}
