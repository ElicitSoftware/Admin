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

import com.elicitsoftware.participation.Participation.Card;
import com.elicitsoftware.participation.Participation.Counts;
import com.elicitsoftware.participation.Participation.DateSummary;
import com.elicitsoftware.participation.Participation.DepartmentCounts;
import com.elicitsoftware.participation.Participation.Durations;
import com.elicitsoftware.participation.Participation.Progress;
import com.elicitsoftware.participation.Participation.StepProgress;
import com.elicitsoftware.participation.Participation.Kind;
import com.elicitsoftware.participation.Participation.NumberSummary;
import com.elicitsoftware.participation.Participation.OptionCount;
import com.elicitsoftware.participation.Participation.QuestionStats;
import com.elicitsoftware.participation.Participation.Week;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Computes the Participation page of one survey (UC-031) live from the survey tables (BR-127):
 * the participation of the filtered respondents (BR-120, BR-121), per department and per week
 * (BR-128), and one card per {@code ADMINISTRATOR} mark with the presented, answered and
 * distribution figures of every question it covers (BR-123 to BR-125); and the progress of the
 * population: where respondents in progress stopped, which of them are stalled, and how long
 * finished respondents spent on each step (BR-130 to BR-133).
 * <p>
 * Counts are returned raw; suppression is {@link SmallCell}'s, applied when the result is
 * rendered. Data access is native SQL because the survey tables it reads ({@code answers},
 * {@code report_items}, the versioned structure) are Survey's and are not mapped here.
 */
@ApplicationScoped
public class ParticipationService {

    /** The {@code effective_to} of a current version. */
    static final String CURRENT = "TIMESTAMPTZ '9999-12-31 23:59:59+00'";

    /** BR-123: types that hold no answer. */
    static final Set<String> VALUELESS_TYPES = Set.of("HTML", "MODAL");

    static final Set<String> CHOICE_TYPES = Set.of("RADIO", "COMBOBOX", "MULTI_SELECT", "CHECKBOX_GROUP", "MULTI_SELECT_COMBOBOX");
    static final Set<String> MULTIPLE_CHOICE_TYPES = Set.of("MULTI_SELECT", "CHECKBOX_GROUP", "MULTI_SELECT_COMBOBOX");
    static final Set<String> NUMBER_TYPES = Set.of("INTEGER", "DOUBLE");
    static final Set<String> DATE_TYPES = Set.of("DATE_PICKER");
    static final String CHECKBOX = "CHECKBOX";

    /**
     * A placeholder as Survey writes it (Survey {@code QuestionManager.PLACEHOLDER}): group 1 is the
     * phrase, group 2 the default text, absent when the placeholder has none.
     */
    static final java.util.regex.Pattern PLACEHOLDER = java.util.regex.Pattern.compile("\\{([^{}|]+)(?:\\|([^{}]*))?}");

    /** A token inside a phrase, {@code <NAME>}. */
    static final java.util.regex.Pattern TOKEN_REF = java.util.regex.Pattern.compile("<([A-Za-z0-9_]+)>");

    /** Survey's per-instance counters, which mean nothing across respondents. */
    static final Set<String> COUNTERS = Set.of("Q#", "S#");

    /** The types whose stored value a distribution reads (BR-125); every other value is only counted. */
    static final Set<String> VALUE_TYPES;

    static {
        Set<String> types = new LinkedHashSet<>(CHOICE_TYPES);
        types.addAll(NUMBER_TYPES);
        types.addAll(DATE_TYPES);
        types.add(CHECKBOX);
        VALUE_TYPES = Set.copyOf(types);
    }

    /**
     * What to count.
     *
     * @param surveyId      the survey
     * @param departmentIds the departments to count, already limited to the user's (BR-120); empty counts nobody
     * @param from          the first registration day, or {@code null}
     * @param to            the last registration day, inclusive, or {@code null}
     */
    public record Filter(int surveyId, Collection<Long> departmentIds, LocalDate from, LocalDate to) {
    }

    /** One respondent as the participation counts read it. */
    record Respondent(long id, long departmentId, OffsetDateTime created, OffsetDateTime firstAccess,
                      OffsetDateTime finalized, OffsetDateTime lastActivity) {

        boolean inProgress() {
            return finalized == null && firstAccess != null;
        }

        /** BR-131: in progress, and nothing since {@code cutoff}. */
        boolean stalled(OffsetDateTime cutoff) {
            OffsetDateTime last = lastActivity != null ? lastActivity : firstAccess;
            return inProgress() && last != null && last.isBefore(cutoff);
        }
    }

    /** A question a mark covers, as its latest version describes it. */
    record Item(int questionId, String text, String type, Integer selectGroupId) {
    }

    /** One aggregated answer row: per question, or per question and stored value. */
    record AnswerGroup(int questionId, String value, boolean perValue, long rows, long respondents, long answered) {
    }

    @Inject
    EntityManager em;

    /** The zone weeks and registration days are reckoned in (BR-120, BR-128). */
    ZoneId zone = ZoneId.systemDefault();

    /** The clock stalled respondents are measured against (BR-131); replaced in tests. */
    Clock clock = Clock.systemUTC();

    /** BR-131: days without activity after which a respondent in progress is stalled. */
    @ConfigProperty(name = "elicit.participation.stalled-days", defaultValue = "14")
    int stalledDays;

    /**
     * Computes the page.
     *
     * @param filter          what to count
     * @param departmentNames the name of every department in the filter, by id, in the order the
     *                        department table lists them
     * @return the raw figures
     */
    @Transactional
    public Participation compute(Filter filter, Map<Long, String> departmentNames) {
        OffsetDateTime cutoff = OffsetDateTime.now(clock).minusDays(stalledDays);
        if (filter.departmentIds().isEmpty()) {
            return new Participation(new Counts(0, 0, 0, 0, 0), List.of(), List.of(), cards(filter),
                    progress(filter, List.of(), cutoff), stalledDays);
        }
        List<Respondent> respondents = respondents(filter);
        Counts overall = counts(respondents, cutoff);
        List<DepartmentCounts> departments = new ArrayList<>();
        for (Map.Entry<Long, String> department : departmentNames.entrySet()) {
            if (!filter.departmentIds().contains(department.getKey())) {
                continue;
            }
            long id = department.getKey();
            departments.add(new DepartmentCounts(department.getValue(),
                    counts(respondents.stream().filter(r -> r.departmentId() == id).toList(), cutoff)));
        }
        return new Participation(overall, departments, weeks(respondents), cards(filter),
                progress(filter, respondents, cutoff), stalledDays);
    }

    // ---- participation (BR-120, BR-121, BR-128) -------------------------------------------------

    /** The population's WHERE clause; the respondent is {@code r} and their subject {@code s}. */
    private String population(Filter filter) {
        StringBuilder where = new StringBuilder("r.survey_id = :surveyId AND s.department_id IN (:departments)");
        if (filter.from() != null) {
            where.append(" AND r.created_dt >= :from");
        }
        if (filter.to() != null) {
            where.append(" AND r.created_dt < :toExclusive");
        }
        return where.toString();
    }

    private Query bind(Query query, Filter filter) {
        query.setParameter("surveyId", filter.surveyId());
        query.setParameter("departments", filter.departmentIds());
        if (filter.from() != null) {
            query.setParameter("from", filter.from().atStartOfDay(zone).toOffsetDateTime());
        }
        if (filter.to() != null) {
            query.setParameter("toExclusive", filter.to().plusDays(1).atStartOfDay(zone).toOffsetDateTime());
        }
        return query;
    }

    @SuppressWarnings("unchecked")
    List<Respondent> respondents(Filter filter) {
        String sql = """
                SELECT r.id, s.department_id, r.created_dt, r.first_access_dt, r.finalized_dt, r.last_activity_dt
                FROM survey.respondents r
                JOIN survey.subjects s ON s.respondent_id = r.id
                WHERE %s
                """.formatted(population(filter));
        List<Object[]> rows = bind(em.createNativeQuery(sql), filter).getResultList();
        List<Respondent> respondents = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            respondents.add(new Respondent(((Number) row[0]).longValue(), ((Number) row[1]).longValue(),
                    timestamp(row[2]), timestamp(row[3]), timestamp(row[4]), timestamp(row[5])));
        }
        return respondents;
    }

    static Counts counts(List<Respondent> respondents, OffsetDateTime stalledCutoff) {
        long notStarted = 0;
        long inProgress = 0;
        long finished = 0;
        long stalled = 0;
        for (Respondent r : respondents) {
            if (r.finalized() != null) {
                finished++;
            } else if (r.firstAccess() != null) {
                inProgress++;
                if (r.stalled(stalledCutoff)) {
                    stalled++;
                }
            } else {
                notStarted++;
            }
        }
        return new Counts(respondents.size(), notStarted, inProgress, finished, stalled);
    }

    List<Week> weeks(List<Respondent> respondents) {
        TreeMap<LocalDate, long[]> byWeek = new TreeMap<>();
        for (Respondent r : respondents) {
            tally(byWeek, r.created(), 0);
            tally(byWeek, r.firstAccess(), 1);
            tally(byWeek, r.finalized(), 2);
        }
        List<Week> weeks = new ArrayList<>();
        if (byWeek.isEmpty()) {
            return weeks;
        }
        for (LocalDate monday = byWeek.firstKey(); !monday.isAfter(byWeek.lastKey()); monday = monday.plusWeeks(1)) {
            long[] n = byWeek.getOrDefault(monday, new long[3]);
            weeks.add(new Week(monday, n[0], n[1], n[2]));
        }
        return weeks;
    }

    private void tally(Map<LocalDate, long[]> byWeek, OffsetDateTime at, int column) {
        if (at == null) {
            return;
        }
        LocalDate monday = at.atZoneSameInstant(zone).toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        byWeek.computeIfAbsent(monday, k -> new long[3])[column]++;
    }

    // ---- progress (BR-130 to BR-133) ------------------------------------------------------------

    Progress progress(Filter filter, List<Respondent> respondents, OffsetDateTime cutoff) {
        Map<Integer, String> stepNames = stepNames(filter.surveyId());
        Map<Long, Respondent> byId = new HashMap<>();
        for (Respondent r : respondents) {
            byId.put(r.id(), r);
        }

        // BR-130: the step of each respondent in progress's most recently presented row.
        Map<Integer, long[]> stopped = new TreeMap<>();
        long notRecorded = 0;
        long notRecordedStalled = 0;
        Map<Long, Integer> lastStep = respondents.isEmpty() ? Map.of() : lastPresentedSteps(filter);
        for (Respondent r : respondents) {
            if (!r.inProgress()) {
                continue;
            }
            Integer step = lastStep.get(r.id());
            boolean stalled = r.stalled(cutoff);
            if (step == null) {
                notRecorded++;
                notRecordedStalled += stalled ? 1 : 0;
            } else {
                long[] n = stopped.computeIfAbsent(step, k -> new long[2]);
                n[0]++;
                n[1] += stalled ? 1 : 0;
            }
        }

        // BR-132: per finished respondent, each step from its first presented row to the next step's.
        Map<Integer, List<Long>> spent = new TreeMap<>();
        long measured = 0;
        long finished = 0;
        List<Long> whole = new ArrayList<>();
        Map<Long, TreeMap<Integer, OffsetDateTime>> starts = respondents.isEmpty() ? Map.of() : stepStarts(filter);
        for (Respondent r : respondents) {
            if (r.finalized() == null) {
                continue;
            }
            finished++;
            if (r.firstAccess() != null) {
                whole.add(Math.max(0, Duration.between(r.firstAccess(), r.finalized()).toSeconds()));
            }
            TreeMap<Integer, OffsetDateTime> mine = starts.get(r.id());
            if (mine == null || mine.isEmpty()) {
                continue;
            }
            measured++;
            List<Map.Entry<Integer, OffsetDateTime>> inOrder = new ArrayList<>(mine.entrySet());
            inOrder.sort(Map.Entry.comparingByValue());
            for (int i = 0; i < inOrder.size(); i++) {
                OffsetDateTime end = i + 1 < inOrder.size() ? inOrder.get(i + 1).getValue() : r.finalized();
                long seconds = Math.max(0, Duration.between(inOrder.get(i).getValue(), end).toSeconds());
                spent.computeIfAbsent(inOrder.get(i).getKey(), k -> new ArrayList<>()).add(seconds);
            }
        }

        java.util.SortedSet<Integer> orders = new java.util.TreeSet<>(stepNames.keySet());
        orders.addAll(stopped.keySet());
        orders.addAll(spent.keySet());
        List<StepProgress> steps = new ArrayList<>();
        for (Integer order : orders) {
            long[] n = stopped.getOrDefault(order, new long[2]);
            steps.add(new StepProgress(order, stepNames.get(order), n[0], n[1], durations(spent.get(order))));
        }
        return new Progress(steps, notRecorded, notRecordedStalled, durations(whole), finished - measured);
    }

    /** The current steps of the survey by display order, which is what {@code answers.step} holds. */
    @SuppressWarnings("unchecked")
    Map<Integer, String> stepNames(int surveyId) {
        List<Object[]> rows = em.createNativeQuery("""
                        SELECT display_order, name FROM survey.steps
                        WHERE survey_id = :surveyId AND effective_to = %s
                        ORDER BY display_order, id
                        """.formatted(CURRENT))
                .setParameter("surveyId", surveyId)
                .getResultList();
        Map<Integer, String> names = new LinkedHashMap<>();
        for (Object[] row : rows) {
            names.putIfAbsent(((Number) row[0]).intValue(), row[1] == null ? null : plain(String.valueOf(row[1])));
        }
        return names;
    }

    /** BR-130: each respondent in progress with a presented row, and the step of the latest one. */
    @SuppressWarnings("unchecked")
    Map<Long, Integer> lastPresentedSteps(Filter filter) {
        String sql = """
                SELECT DISTINCT ON (a.respondent_id) a.respondent_id, a.step
                FROM survey.answers a
                JOIN survey.respondents r ON r.id = a.respondent_id
                JOIN survey.subjects s ON s.respondent_id = r.id
                WHERE %s AND r.first_access_dt IS NOT NULL AND r.finalized_dt IS NULL
                  AND a.presented_dt IS NOT NULL AND a.deleted = false
                ORDER BY a.respondent_id, a.presented_dt DESC, a.display_key DESC
                """.formatted(population(filter));
        Map<Long, Integer> steps = new HashMap<>();
        for (Object[] row : (List<Object[]>) bind(em.createNativeQuery(sql), filter).getResultList()) {
            steps.put(((Number) row[0]).longValue(), ((Number) row[1]).intValue());
        }
        return steps;
    }

    /** BR-132: for each finished respondent, when each step they were shown was first presented. */
    @SuppressWarnings("unchecked")
    Map<Long, TreeMap<Integer, OffsetDateTime>> stepStarts(Filter filter) {
        String sql = """
                SELECT a.respondent_id, a.step, min(a.presented_dt)
                FROM survey.answers a
                JOIN survey.respondents r ON r.id = a.respondent_id
                JOIN survey.subjects s ON s.respondent_id = r.id
                WHERE %s AND r.finalized_dt IS NOT NULL AND a.presented_dt IS NOT NULL AND a.deleted = false
                GROUP BY a.respondent_id, a.step
                """.formatted(population(filter));
        Map<Long, TreeMap<Integer, OffsetDateTime>> starts = new HashMap<>();
        for (Object[] row : (List<Object[]>) bind(em.createNativeQuery(sql), filter).getResultList()) {
            starts.computeIfAbsent(((Number) row[0]).longValue(), k -> new TreeMap<>())
                    .put(((Number) row[1]).intValue(), timestamp(row[2]));
        }
        return starts;
    }

    /** The quartiles of a list of durations in seconds, rounded to the second. */
    static Durations durations(List<Long> seconds) {
        if (seconds == null || seconds.isEmpty()) {
            return Durations.NONE;
        }
        TreeMap<BigDecimal, Long> sorted = new TreeMap<>();
        for (Long s : seconds) {
            sorted.merge(BigDecimal.valueOf(s), 1L, Long::sum);
        }
        long n = seconds.size();
        return new Durations(n, quantile(sorted, n, 1, 4).setScale(0, RoundingMode.HALF_UP).longValue(),
                quantile(sorted, n, 1, 2).setScale(0, RoundingMode.HALF_UP).longValue(),
                quantile(sorted, n, 3, 4).setScale(0, RoundingMode.HALF_UP).longValue());
    }

    // ---- marked questions (BR-123 to BR-125) ----------------------------------------------------

    @SuppressWarnings("unchecked")
    List<Card> cards(Filter filter) {
        List<Object[]> marks = em.createNativeQuery("""
                        SELECT id, question_id, sections_question_id, steps_sections_id, label
                        FROM survey.report_items
                        WHERE survey_id = :surveyId AND report_type = 'ADMINISTRATOR'
                        ORDER BY display_order, id
                        """)
                .setParameter("surveyId", filter.surveyId())
                .getResultList();
        List<Card> cards = new ArrayList<>();
        for (Object[] mark : marks) {
            Integer questionId = integer(mark[1]);
            Integer sectionsQuestionId = integer(mark[2]);
            Integer stepsSectionsId = integer(mark[3]);
            String label = mark[4] == null || String.valueOf(mark[4]).isBlank() ? null : plain(String.valueOf(mark[4]));

            List<Item> items;
            String title;
            String condition;
            Map<String, Object> params = new HashMap<>();
            if (questionId != null) {
                items = itemsOf(List.of(questionId));
                title = items.isEmpty() ? null : items.get(0).text();
                condition = "q.question_id = :markQuestion";
                params.put("markQuestion", questionId);
            } else if (sectionsQuestionId != null) {
                Integer placed = placedQuestion(sectionsQuestionId);
                items = placed == null ? List.of() : itemsOf(List.of(placed));
                title = items.isEmpty() ? null : items.get(0).text();
                condition = "sq.sections_question_id = :markPlacement";
                params.put("markPlacement", sectionsQuestionId);
            } else {
                items = itemsOf(sectionQuestions(stepsSectionsId));
                title = sectionTitle(stepsSectionsId);
                condition = """
                        EXISTS (SELECT 1 FROM survey.steps_sections ss
                                WHERE ss.steps_sections_id = :markSection AND ss.section_id = sq.section_id
                                  AND ss.step_display_order = a.step AND ss.section_display_order = a.section)""";
                params.put("markSection", stepsSectionsId);
            }
            List<AnswerGroup> groups = answerGroups(filter, condition, params);
            cards.add(new Card(label != null ? label : title == null ? "" : title, questions(items, groups)));
        }
        return cards;
    }

    /**
     * BR-124: per question, and per question and stored value, the answer rows of the finished
     * respondents of the population that the mark's {@code condition} covers.
     */
    @SuppressWarnings("unchecked")
    List<AnswerGroup> answerGroups(Filter filter, String condition, Map<String, Object> params) {
        if (filter.departmentIds().isEmpty()) {
            return List.of();
        }
        // The value column is computed in a subquery so that GROUPING() sees the very column the
        // grouping sets name; the type names are this class's constants, written as literals.
        String sql = """
                SELECT question_id, value, GROUPING(value), count(*), count(DISTINCT respondent_id),
                       count(*) FILTER (WHERE answered)
                FROM (SELECT q.question_id, a.respondent_id,
                             CASE WHEN t.name IN (%s) THEN a.text_value END AS value,
                             (t.name = 'CHECKBOX' OR btrim(coalesce(a.text_value, '')) <> '') AS answered
                      FROM survey.answers a
                      JOIN survey.respondents r ON r.id = a.respondent_id
                      JOIN survey.subjects s ON s.respondent_id = r.id
                      JOIN survey.questions q ON q.id = a.question_id
                      JOIN survey.question_types t ON t.id = q.type_id
                      LEFT JOIN survey.sections_questions sq ON sq.id = a.section_question_id
                      WHERE %s AND r.finalized_dt IS NOT NULL AND a.deleted = false
                        AND t.name NOT IN (%s) AND (%s)) covered
                GROUP BY GROUPING SETS ((question_id), (question_id, value))
                """.formatted(literals(VALUE_TYPES), population(filter), literals(VALUELESS_TYPES), condition);
        Query query = bind(em.createNativeQuery(sql), filter);
        params.forEach(query::setParameter);
        List<AnswerGroup> groups = new ArrayList<>();
        for (Object[] row : (List<Object[]>) query.getResultList()) {
            boolean perQuestion = ((Number) row[2]).intValue() == 1;
            groups.add(new AnswerGroup(((Number) row[0]).intValue(), row[1] == null ? null : String.valueOf(row[1]),
                    !perQuestion, ((Number) row[3]).longValue(), ((Number) row[4]).longValue(), ((Number) row[5]).longValue()));
        }
        return groups;
    }

    /** One {@link QuestionStats} per item, then one for any question the answers name that no item lists. */
    List<QuestionStats> questions(List<Item> items, List<AnswerGroup> groups) {
        Map<Integer, Item> byQuestion = new LinkedHashMap<>();
        for (Item item : items) {
            if (!VALUELESS_TYPES.contains(item.type())) {
                byQuestion.putIfAbsent(item.questionId(), item);
            }
        }
        List<Integer> unlisted = groups.stream().map(AnswerGroup::questionId)
                .filter(id -> !byQuestion.containsKey(id)).distinct().toList();
        for (Item item : itemsOf(unlisted)) {
            byQuestion.putIfAbsent(item.questionId(), item);
        }
        List<QuestionStats> stats = new ArrayList<>();
        for (Item item : byQuestion.values()) {
            List<AnswerGroup> mine = groups.stream().filter(g -> g.questionId() == item.questionId()).toList();
            stats.add(stats(item, mine));
        }
        return stats;
    }

    QuestionStats stats(Item item, List<AnswerGroup> groups) {
        long presented = 0;
        long respondents = 0;
        long answered = 0;
        Map<String, Long> values = new LinkedHashMap<>();
        for (AnswerGroup g : groups) {
            if (g.perValue()) {
                if (g.value() != null && !g.value().isBlank()) {
                    values.merge(g.value().trim(), g.rows(), Long::sum);
                }
            } else {
                presented = g.rows();
                respondents = g.respondents();
                answered = g.answered();
            }
        }
        String type = item.type() == null ? "" : item.type();
        if (CHOICE_TYPES.contains(type)) {
            return new QuestionStats(item.text(), Kind.CHOICE, presented, respondents, answered,
                    choices(item, values, MULTIPLE_CHOICE_TYPES.contains(type)), null, null);
        }
        if (CHECKBOX.equals(type)) {
            long yes = values.entrySet().stream().filter(e -> Boolean.parseBoolean(e.getKey())).mapToLong(Map.Entry::getValue).sum();
            return new QuestionStats(item.text(), Kind.CHECKBOX, presented, respondents, answered,
                    List.of(new OptionCount("yes", yes, true), new OptionCount("no", answered - yes, true)), null, null);
        }
        if (NUMBER_TYPES.contains(type)) {
            return new QuestionStats(item.text(), Kind.NUMBER, presented, respondents, answered, List.of(), numbers(values), null);
        }
        if (DATE_TYPES.contains(type)) {
            return new QuestionStats(item.text(), Kind.DATE, presented, respondents, answered, List.of(), null, dates(values));
        }
        return new QuestionStats(item.text(), Kind.COUNT_ONLY, presented, respondents, answered, List.of(), null, null);
    }

    /** BR-125: the current options in order, then any stored value no current option carries. */
    @SuppressWarnings("unchecked")
    List<OptionCount> choices(Item item, Map<String, Long> values, boolean multiple) {
        Map<String, Long> perCode = new LinkedHashMap<>();
        for (Map.Entry<String, Long> value : values.entrySet()) {
            if (multiple) {
                for (String code : value.getKey().split(",")) {
                    if (!code.isBlank()) {
                        perCode.merge(code.trim(), value.getValue(), Long::sum);
                    }
                }
            } else {
                perCode.merge(value.getKey(), value.getValue(), Long::sum);
            }
        }
        List<OptionCount> options = new ArrayList<>();
        if (item.selectGroupId() != null) {
            List<Object[]> current = em.createNativeQuery("""
                            SELECT coded_value, display_text FROM survey.select_items
                            WHERE select_group_id = :group AND effective_to = %s
                            ORDER BY display_order, id
                            """.formatted(CURRENT))
                    .setParameter("group", item.selectGroupId())
                    .getResultList();
            for (Object[] option : current) {
                String code = option[0] == null ? "" : String.valueOf(option[0]);
                String text = option[1] == null ? code : plain(String.valueOf(option[1]));
                Long count = perCode.remove(code);
                options.add(new OptionCount(text, count == null ? 0 : count, true));
            }
        }
        perCode.forEach((code, count) -> options.add(new OptionCount(code, count, false)));
        return options;
    }

    static NumberSummary numbers(Map<String, Long> values) {
        TreeMap<BigDecimal, Long> sorted = new TreeMap<>();
        for (Map.Entry<String, Long> value : values.entrySet()) {
            try {
                sorted.merge(new BigDecimal(value.getKey()), value.getValue(), Long::sum);
            } catch (NumberFormatException e) {
                // BR-125: a value that is not a number is not counted.
            }
        }
        long n = sorted.values().stream().mapToLong(Long::longValue).sum();
        if (n == 0) {
            return null;
        }
        return new NumberSummary(n, sorted.firstKey(), quantile(sorted, n, 1, 4), quantile(sorted, n, 1, 2),
                quantile(sorted, n, 3, 4), sorted.lastKey());
    }

    /** The p-quantile, p = num ÷ den, by linear interpolation between order statistics (type 7). */
    static BigDecimal quantile(TreeMap<BigDecimal, Long> sorted, long n, int num, int den) {
        long scaled = (n - 1) * num;
        long lower = scaled / den;
        BigDecimal fraction = BigDecimal.valueOf(scaled % den).divide(BigDecimal.valueOf(den), MathContext.DECIMAL64);
        BigDecimal low = nth(sorted, lower);
        if (fraction.signum() == 0) {
            return low;
        }
        BigDecimal high = nth(sorted, lower + 1);
        return low.add(high.subtract(low).multiply(fraction)).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros();
    }

    private static BigDecimal nth(TreeMap<BigDecimal, Long> sorted, long index) {
        long seen = 0;
        for (Map.Entry<BigDecimal, Long> e : sorted.entrySet()) {
            seen += e.getValue();
            if (index < seen) {
                return e.getKey();
            }
        }
        return sorted.lastKey();
    }

    static DateSummary dates(Map<String, Long> values) {
        TreeMap<LocalDate, Long> sorted = new TreeMap<>();
        for (Map.Entry<String, Long> value : values.entrySet()) {
            try {
                sorted.merge(LocalDate.parse(value.getKey()), value.getValue(), Long::sum);
            } catch (DateTimeParseException e) {
                // BR-125: a value that is not a date is not counted.
            }
        }
        long n = sorted.values().stream().mapToLong(Long::longValue).sum();
        if (n == 0) {
            return null;
        }
        TreeMap<YearMonth, Long> perMonth = new TreeMap<>();
        sorted.forEach((date, count) -> perMonth.merge(YearMonth.from(date), count, Long::sum));
        return new DateSummary(n, sorted.firstKey(), sorted.lastKey(), perMonth);
    }

    // ---- the survey's structure, latest version of each durable element -------------------------

    @SuppressWarnings("unchecked")
    List<Item> itemsOf(List<Integer> questionIds) {
        List<Item> items = new ArrayList<>();
        for (Integer questionId : questionIds) {
            List<Object[]> rows = em.createNativeQuery("""
                            SELECT q.question_id, q.text, t.name, q.select_group_id
                            FROM survey.questions q JOIN survey.question_types t ON t.id = q.type_id
                            WHERE q.question_id = :id ORDER BY q.version DESC LIMIT 1
                            """)
                    .setParameter("id", questionId)
                    .getResultList();
            for (Object[] row : rows) {
                items.add(new Item(((Number) row[0]).intValue(), plain(String.valueOf(row[1])), String.valueOf(row[2]), integer(row[3])));
            }
        }
        return items;
    }

    @SuppressWarnings("unchecked")
    private Integer placedQuestion(int sectionsQuestionId) {
        List<Object> rows = em.createNativeQuery("""
                        SELECT question_id FROM survey.sections_questions
                        WHERE sections_question_id = :id ORDER BY version DESC LIMIT 1
                        """)
                .setParameter("id", sectionsQuestionId)
                .getResultList();
        return rows.isEmpty() ? null : integer(rows.get(0));
    }

    /** The questions currently placed in the section a section placement names, in section order. */
    @SuppressWarnings("unchecked")
    private List<Integer> sectionQuestions(int stepsSectionsId) {
        List<Object> rows = em.createNativeQuery("""
                        SELECT sq.question_id FROM survey.sections_questions sq
                        WHERE sq.effective_to = %s AND sq.section_id =
                              (SELECT section_id FROM survey.steps_sections WHERE steps_sections_id = :id ORDER BY version DESC LIMIT 1)
                        ORDER BY sq.display_order, sq.id
                        """.formatted(CURRENT))
                .setParameter("id", stepsSectionsId)
                .getResultList();
        return rows.stream().map(ParticipationService::integer).distinct().toList();
    }

    /** "Step › Section" for a section placement, from the latest versions of the three. */
    @SuppressWarnings("unchecked")
    private String sectionTitle(int stepsSectionsId) {
        List<Object[]> rows = em.createNativeQuery("""
                        SELECT (SELECT st.name FROM survey.steps st WHERE st.step_id = ss.step_id ORDER BY st.version DESC LIMIT 1),
                               (SELECT se.name FROM survey.sections se WHERE se.section_id = ss.section_id ORDER BY se.version DESC LIMIT 1)
                        FROM survey.steps_sections ss
                        WHERE ss.steps_sections_id = :id ORDER BY ss.version DESC LIMIT 1
                        """)
                .setParameter("id", stepsSectionsId)
                .getResultList();
        if (rows.isEmpty()) {
            return null;
        }
        String step = rows.get(0)[0] == null ? "" : plain(String.valueOf(rows.get(0)[0]));
        String section = rows.get(0)[1] == null ? "" : plain(String.valueOf(rows.get(0)[1]));
        if (step.isBlank()) {
            return section;
        }
        return section.isBlank() ? step : step + " › " + section;
    }

    /**
     * BR-123: authored text as an aggregate shows it. Every placeholder is shown as its default
     * text, since no one respondent's values apply; one without a default shows its phrase with
     * the token marks removed, and Survey's counters ({Q#}, {S#}) are dropped.
     */
    static String plain(String text) {
        if (text == null || text.indexOf('{') < 0) {
            return text;
        }
        java.util.regex.Matcher m = PLACEHOLDER.matcher(text);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String phrase = m.group(1);
            String replacement;
            if (COUNTERS.contains(phrase.trim())) {
                replacement = "";
            } else if (m.group(2) != null) {
                replacement = m.group(2);
            } else {
                replacement = TOKEN_REF.matcher(phrase).replaceAll("$1");
            }
            m.appendReplacement(out, java.util.regex.Matcher.quoteReplacement(replacement));
        }
        m.appendTail(out);
        return out.toString().replaceAll("\\s{2,}", " ").trim();
    }

    /** {@code 'A', 'B'} for a set of constant type names. */
    private static String literals(Set<String> names) {
        return names.stream().sorted().map(n -> "'" + n + "'").collect(java.util.stream.Collectors.joining(", "));
    }

    private static Integer integer(Object o) {
        return o == null ? null : ((Number) o).intValue();
    }

    private static OffsetDateTime timestamp(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof OffsetDateTime t) {
            return t;
        }
        if (o instanceof java.time.Instant i) {
            return i.atOffset(java.time.ZoneOffset.UTC);
        }
        if (o instanceof java.sql.Timestamp t) {
            return t.toInstant().atOffset(java.time.ZoneOffset.UTC);
        }
        throw new IllegalStateException("Unexpected timestamp type " + o.getClass());
    }
}
