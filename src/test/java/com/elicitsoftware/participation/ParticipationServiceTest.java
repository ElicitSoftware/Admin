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

import com.elicitsoftware.model.Department;
import com.elicitsoftware.model.Respondent;
import com.elicitsoftware.model.Subject;
import com.elicitsoftware.model.Survey;
import com.elicitsoftware.participation.Participation.Card;
import com.elicitsoftware.participation.Participation.Kind;
import com.elicitsoftware.participation.Participation.OptionCount;
import com.elicitsoftware.participation.Participation.QuestionStats;
import com.elicitsoftware.test.PostgresTestResource;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UC-031: the Participation page's figures reconcile with what was seeded (BR-120 to BR-125,
 * BR-128). One survey, two departments, respondents in each state, and five questions of five
 * types in one section, marked three ways (question, question placement, section placement).
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class ParticipationServiceTest {

    @Inject
    ParticipationService service;

    @Inject
    EntityManager em;

    /** Ids well above anything the sequences hand out in the shared test database. */
    private static final int BASE = 910_000;
    private static final int Q_RADIO = BASE + 1;
    private static final int Q_NUMBER = BASE + 2;
    private static final int Q_TEXT = BASE + 3;
    private static final int Q_CHECKBOX = BASE + 4;
    private static final int Q_DATE = BASE + 5;
    private static final int Q_HTML = BASE + 6;

    /** Monday 2026-01-05 and Monday 2026-01-19, noon UTC. */
    private static final OffsetDateTime WEEK_1 = OffsetDateTime.of(2026, 1, 5, 12, 0, 0, 0, ZoneOffset.UTC);
    private static final OffsetDateTime WEEK_3 = WEEK_1.plusWeeks(2);

    Survey survey;
    Department deptA;
    Department deptB;
    int answerId = BASE;
    int subjectSeq;

    // ---- fixture --------------------------------------------------------------------------------

    private void seed() {
        service.zone = ZoneOffset.UTC;
        survey = new Survey();
        survey.name = "UC-031 survey";
        survey.title = "UC-031";
        survey.displayOrder = 5000;
        survey.persist();
        deptA = department("UC031-A");
        deptB = department("UC031-B");

        int s = survey.id;
        for (String type : List.of("RADIO", "INTEGER", "TEXT", "CHECKBOX", "DATE_PICKER", "HTML")) {
            sql("INSERT INTO survey.question_types (id, name, data_type) SELECT (SELECT coalesce(max(id), 0) + 1 FROM survey.question_types), ?1, 'Text' "
                    + "WHERE NOT EXISTS (SELECT 1 FROM survey.question_types WHERE name = ?1)", type);
        }
        sql("INSERT INTO survey.select_groups (id, survey_id, name, select_group_id) VALUES (?1, ?2, 'UC031 yes/no', ?1)", BASE, s);
        sql("INSERT INTO survey.select_items (id, survey_id, select_group_id, display_text, display_order, coded_value, select_item_id) VALUES (?1, ?2, ?3, 'Yes', 1, '1', ?1)", BASE + 1, s, BASE);
        sql("INSERT INTO survey.select_items (id, survey_id, select_group_id, display_text, display_order, coded_value, select_item_id) VALUES (?1, ?2, ?3, 'No', 2, '2', ?1)", BASE + 2, s, BASE);
        sql("INSERT INTO survey.select_items (id, survey_id, select_group_id, display_text, display_order, coded_value, select_item_id) VALUES (?1, ?2, ?3, 'Unsure', 3, '3', ?1)", BASE + 3, s, BASE);
        sql("INSERT INTO survey.steps (id, survey_id, display_order, name, dimension_name, step_id) VALUES (?1, ?2, 1, 'Health', 'D', ?1)", BASE, s);
        sql("INSERT INTO survey.sections (id, survey_id, display_order, name, dimension_name, section_id) VALUES (?1, ?2, 1, 'Habits', 'D', ?1)", BASE, s);
        sql("INSERT INTO survey.steps_sections (id, survey_id, step_id, step_display_order, section_id, section_display_order, display_key, steps_sections_id) "
                + "VALUES (?1, ?2, ?1, 1, ?1, 1, 'UC031-0001-0001', ?1)", BASE, s);
        question(Q_RADIO, "RADIO", "Do you smoke?", BASE);
        question(Q_NUMBER, "INTEGER", "Cigarettes per day", null);
        question(Q_TEXT, "TEXT", "Anything else?", null);
        question(Q_CHECKBOX, "CHECKBOX", "I consent", null);
        question(Q_DATE, "DATE_PICKER", "When did you start?", null);
        question(Q_HTML, "HTML", "Thank you", null);

        // Marks, deliberately out of display order to check the ordering.
        mark(BASE + 3, "steps_sections_id", BASE, 3, null);                      // a section placement
        mark(BASE + 1, "question_id", Q_RADIO, 1, "Smoker");                     // a question, labelled
        mark(BASE + 2, "sections_question_id", Q_NUMBER, 2, null);               // a question placement
        mark(BASE + 4, "question_id", Q_TEXT, 0, null);
        sql("UPDATE survey.report_items SET report_type = 'RESPONDENT' WHERE id = ?1", BASE + 4); // not this report

        // Department A: six finished, two in progress, one not started. Department B: one finished.
        String[] radio = {"1", "1", "1", "2", "2", ""};
        String[] number = {"10", "20", "30", "40", "50", "abc"};
        String[] text = {"x", "y", "z", "w", "v", " "};
        String[] checkbox = {"true", "true", "true", null, null, null};
        String[] date = {"2026-01-05", "2026-01-20", "2026-01-31", "2026-02-10", "2026-02-11", "2026-02-12"};
        for (int i = 0; i < 6; i++) {
            long r = respondent(deptA, WEEK_1, WEEK_1, WEEK_3);
            answers(r, radio[i], number[i], text[i], checkbox[i], date[i]);
        }
        answers(respondent(deptA, WEEK_1, WEEK_1, null), "1", "99", "q", "true", "2026-01-01");   // in progress: not counted
        respondent(deptA, WEEK_3, WEEK_3, null);
        respondent(deptA, WEEK_3, null, null);
        long b = respondent(deptB, WEEK_3, WEEK_3, WEEK_3);
        answers(b, "7", "60", "u", "true", "2026-03-01");                      // "7" is no current option
        // A deleted row of a finished respondent is not presented.
        answer(b, Q_RADIO, "1", true, 1);
        em.flush();
    }

    private Department department(String code) {
        Department d = new Department();
        d.name = code;
        d.code = code;
        d.defaultMessageId = "1";
        d.fromEmail = "uc031@example.org";
        d.persist();
        return d;
    }

    private void question(int id, String type, String text, Integer group) {
        sql("INSERT INTO survey.questions (id, survey_id, type_id, text, select_group_id, question_id) "
                + "VALUES (?1, ?2, (SELECT id FROM survey.question_types WHERE name = ?3), ?4, ?5, ?1)", id, survey.id, type, text, group);
        sql("INSERT INTO survey.sections_questions (id, survey_id, question_id, section_id, display_order, sections_question_id) "
                + "VALUES (?1, ?2, ?1, ?3, ?4, ?1)", id, survey.id, BASE, id - BASE);
    }

    private void mark(int id, String column, int element, int order, String label) {
        sql("INSERT INTO survey.report_items (id, survey_id, report_type, " + column + ", label, display_order) "
                + "VALUES (?1, ?2, 'ADMINISTRATOR', ?3, ?4, ?5)", id, survey.id, element, label, order);
    }

    private long respondent(Department dept, OffsetDateTime created, OffsetDateTime first, OffsetDateTime finalized) {
        Respondent r = new Respondent();
        r.survey = survey;
        r.accessCode = "UC031-" + (++subjectSeq);
        r.active = true;
        r.firstAccessDt = first == null ? null : Date.from(first.toInstant());
        r.finalizedDt = finalized == null ? null : Date.from(finalized.toInstant());
        r.persistAndFlush();
        sql("UPDATE survey.respondents SET created_dt = ?1 WHERE id = ?2", created, r.id);
        Subject subject = new Subject("UC031-" + subjectSeq, survey.id.longValue(), dept.id, "Pat", "Tester", null,
                LocalDate.of(1990, 1, 15), "pat@example.org", null);
        subject.setRespondent(r);
        subject.persistAndFlush();
        return r.id;
    }

    private void answers(long respondent, String radio, String number, String text, String checkbox, String date) {
        answer(respondent, Q_RADIO, radio, false, 0);
        answer(respondent, Q_NUMBER, number, false, 0);
        answer(respondent, Q_TEXT, text, false, 0);
        answer(respondent, Q_CHECKBOX, checkbox, false, 0);
        answer(respondent, Q_DATE, date, false, 0);
        answer(respondent, Q_HTML, null, false, 0);
    }

    private void answer(long respondent, int question, String value, boolean deleted, int instance) {
        int id = ++answerId;
        sql("INSERT INTO survey.answers (id, survey_id, respondent_id, step, section, section_instance, question_instance, "
                        + "section_question_id, question_id, display_key, display_text, text_value, deleted) "
                        + "VALUES (?1, ?2, ?3, 1, 1, ?4, 0, ?5, ?5, ?6, 'q', ?7, ?8)",
                id, survey.id, respondent, instance, question, "UC031-" + id, value, deleted);
    }

    private void sql(String sql, Object... params) {
        var query = em.createNativeQuery(sql);
        for (int i = 0; i < params.length; i++) {
            query.setParameter(i + 1, params[i]);
        }
        query.executeUpdate();
    }

    private Participation compute(List<Long> departments, LocalDate from, LocalDate to) {
        Map<Long, String> names = new LinkedHashMap<>();
        names.put(deptA.id, deptA.name);
        names.put(deptB.id, deptB.name);
        return service.compute(new ParticipationService.Filter(survey.id, departments, from, to), names);
    }

    private static QuestionStats only(Card card) {
        assertEquals(1, card.questions().size(), card.title());
        return card.questions().get(0);
    }

    // ---- tests ----------------------------------------------------------------------------------

    /** UC-031 BR-121: the states follow the status view, and the departments add up. */
    @Test
    @TestTransaction
    void participationCountsReconcile() {
        seed();
        Participation p = compute(List.of(deptA.id, deptB.id), null, null);
        assertEquals(new Participation.Counts(10, 1, 2, 7), p.overall());
        assertEquals(9, p.overall().started());
        assertEquals(2, p.departments().size());
        assertEquals(new Participation.Counts(9, 1, 2, 6), p.departments().get(0).counts());
        assertEquals(new Participation.Counts(1, 0, 0, 1), p.departments().get(1).counts());
    }

    /** UC-031 BR-120: a department outside the filter is not counted, nor listed. */
    @Test
    @TestTransaction
    void departmentFilterScopesEveryFigure() {
        seed();
        Participation p = compute(List.of(deptB.id), null, null);
        assertEquals(new Participation.Counts(1, 0, 0, 1), p.overall());
        assertEquals(1, p.departments().size());
        QuestionStats radio = only(p.cards().get(0));
        assertEquals(1, radio.presented());
        Participation none = compute(List.of(), null, null);
        assertEquals(0, none.overall().registered());
        assertEquals(0, only(none.cards().get(0)).presented());
    }

    /** UC-031 BR-120: the registration range is inclusive of both days. */
    @Test
    @TestTransaction
    void registrationRangeIsInclusive() {
        seed();
        assertEquals(7, compute(List.of(deptA.id, deptB.id), null, LocalDate.of(2026, 1, 5)).overall().registered());
        assertEquals(3, compute(List.of(deptA.id, deptB.id), LocalDate.of(2026, 1, 19), LocalDate.of(2026, 1, 19)).overall().registered());
        assertEquals(0, compute(List.of(deptA.id, deptB.id), LocalDate.of(2026, 1, 6), LocalDate.of(2026, 1, 18)).overall().registered());
    }

    /** UC-031 BR-128: every week from the first registration to the last event, empty ones too. */
    @Test
    @TestTransaction
    void weeksRunMondayToSundayWithGapsListed() {
        seed();
        List<Participation.Week> weeks = compute(List.of(deptA.id, deptB.id), null, null).weeks();
        assertEquals(3, weeks.size());
        assertEquals(new Participation.Week(LocalDate.of(2026, 1, 5), 7, 7, 0), weeks.get(0));
        assertEquals(new Participation.Week(LocalDate.of(2026, 1, 12), 0, 0, 0), weeks.get(1));
        assertEquals(new Participation.Week(LocalDate.of(2026, 1, 19), 3, 2, 7), weeks.get(2));
    }

    /** UC-031 BR-123: administrator marks only, in display order, a label replacing the text. */
    @Test
    @TestTransaction
    void cardsFollowTheAdministratorMarks() {
        seed();
        List<Card> cards = compute(List.of(deptA.id, deptB.id), null, null).cards();
        assertEquals(List.of("Smoker", "Cigarettes per day", "Health › Habits"), cards.stream().map(Card::title).toList());
        // The section placement covers its five answerable questions in section order; HTML is left out.
        assertEquals(List.of("Do you smoke?", "Cigarettes per day", "Anything else?", "I consent", "When did you start?"),
                cards.get(2).questions().stream().map(QuestionStats::text).toList());
    }

    /** UC-031 BR-124 and BR-125: a choice question, among finished respondents only. */
    @Test
    @TestTransaction
    void choiceQuestionIsCountedPerOption() {
        seed();
        QuestionStats radio = only(compute(List.of(deptA.id, deptB.id), null, null).cards().get(0));
        assertEquals(Kind.CHOICE, radio.kind());
        assertEquals(7, radio.presented());          // the in-progress respondent and the deleted row are not counted
        assertEquals(7, radio.respondents());
        assertEquals(6, radio.answered());           // one blank
        assertEquals(List.of(new OptionCount("Yes", 3, true), new OptionCount("No", 2, true),
                new OptionCount("Unsure", 0, true), new OptionCount("7", 1, false)), radio.options());
    }

    /** UC-031 BR-125: numbers are summarized, a non-number is answered but not counted. */
    @Test
    @TestTransaction
    void numberQuestionIsSummarized() {
        seed();
        QuestionStats number = only(compute(List.of(deptA.id, deptB.id), null, null).cards().get(1));
        assertEquals(Kind.NUMBER, number.kind());
        assertEquals(7, number.answered());
        assertEquals(6, number.numbers().n());
        assertEquals(0, new BigDecimal("10").compareTo(number.numbers().min()));
        assertEquals(0, new BigDecimal("22.5").compareTo(number.numbers().q1()));
        assertEquals(0, new BigDecimal("35").compareTo(number.numbers().median()));
        assertEquals(0, new BigDecimal("47.5").compareTo(number.numbers().q3()));
        assertEquals(0, new BigDecimal("60").compareTo(number.numbers().max()));
    }

    /** UC-031 BR-124 and BR-125: free text is counted only; a presented checkbox is always answered. */
    @Test
    @TestTransaction
    void sectionCardCoversTextCheckboxAndDate() {
        seed();
        List<QuestionStats> section = compute(List.of(deptA.id, deptB.id), null, null).cards().get(2).questions();
        QuestionStats text = section.get(2);
        assertEquals(Kind.COUNT_ONLY, text.kind());
        assertEquals(7, text.presented());
        assertEquals(6, text.answered());            // " " is blank
        assertTrue(text.options().isEmpty());

        QuestionStats checkbox = section.get(3);
        assertEquals(Kind.CHECKBOX, checkbox.kind());
        assertEquals(7, checkbox.answered());
        assertEquals(List.of(new OptionCount("yes", 4, true), new OptionCount("no", 3, true)), checkbox.options());

        QuestionStats date = section.get(4);
        assertEquals(Kind.DATE, date.kind());
        assertEquals(7, date.dates().n());
        assertEquals(LocalDate.of(2026, 1, 5), date.dates().earliest());
        assertEquals(LocalDate.of(2026, 3, 1), date.dates().latest());
        assertEquals(Map.of(YearMonth.of(2026, 1), 3L, YearMonth.of(2026, 2), 3L, YearMonth.of(2026, 3), 1L), date.dates().perMonth());
    }

    /**
     * UC-031: the console connects as surveyadmin_user, not as the superuser the tests run as, so
     * every table the page reads must be granted to it (V0.0.21, V0.0.22). The figures are
     * computed under that role here; a missing grant fails with "permission denied".
     */
    @Test
    @TestTransaction
    void consoleRoleCanReadEverythingThePageReads() {
        seed();
        em.createNativeQuery("SET LOCAL ROLE surveyadmin_user").executeUpdate();
        Participation p = compute(List.of(deptA.id, deptB.id), null, null);
        assertEquals(10, p.overall().registered());
        assertEquals(3, p.cards().size());
        assertEquals(7, p.cards().get(2).questions().get(0).presented());
    }

    /** UC-031 BR-125: quartiles interpolate between order statistics. */
    @Test
    void quantilesInterpolateLinearly() {
        java.util.TreeMap<BigDecimal, Long> values = new java.util.TreeMap<>(Map.of(
                new BigDecimal("1"), 1L, new BigDecimal("2"), 1L, new BigDecimal("3"), 1L, new BigDecimal("4"), 1L));
        assertEquals(0, new BigDecimal("1.75").compareTo(ParticipationService.quantile(values, 4, 1, 4)));
        assertEquals(0, new BigDecimal("2.5").compareTo(ParticipationService.quantile(values, 4, 1, 2)));
        assertNull(ParticipationService.numbers(Map.of("x", 2L)));
    }
}
