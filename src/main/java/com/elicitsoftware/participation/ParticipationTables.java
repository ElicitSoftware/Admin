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

import com.elicitsoftware.admin.i18n.Translations;
import com.elicitsoftware.participation.Participation.Card;
import com.elicitsoftware.participation.Participation.Counts;
import com.elicitsoftware.participation.Participation.DateSummary;
import com.elicitsoftware.participation.Participation.DepartmentCounts;
import com.elicitsoftware.participation.Participation.NumberSummary;
import com.elicitsoftware.participation.Participation.OptionCount;
import com.elicitsoftware.participation.Participation.QuestionStats;
import com.elicitsoftware.participation.Participation.Week;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Turns a {@link Participation} into the titled tables of text the page shows and both exports
 * write (UC-031 BR-126), every count passing through one {@link SmallCell} (BR-122). Building the
 * text once is what keeps the page, the CSV and the PDF from disagreeing.
 */
public final class ParticipationTables {

    /**
     * One table.
     *
     * @param title   a caption, or {@code null}
     * @param headers the column headers
     * @param rows    the cells, row by row
     */
    public record Table(String title, List<String> headers, List<List<String>> rows) {
    }

    /**
     * A titled group of tables: the participation, the departments, the weeks, or one card.
     *
     * @param title  the heading
     * @param note   an explanation under the heading, or {@code null}
     * @param card   whether this is a marked element's card (a lower heading level)
     * @param tables the tables, possibly none
     */
    public record Section(String title, String note, boolean card, List<Table> tables) {
    }

    private final SmallCell cells;
    private final Locale locale;

    /**
     * @param cells the suppression rule and the locale every value is rendered in
     */
    public ParticipationTables(SmallCell cells) {
        this.cells = cells;
        this.locale = cells.locale();
    }

    /**
     * Renders the whole page.
     *
     * @param p the computed figures
     * @return the sections in page order
     */
    public List<Section> sections(Participation p) {
        List<Section> sections = new ArrayList<>();
        sections.add(new Section(t("participationView.section.participation"),
                cells.threshold() > 1 ? t("participationView.suppressionNote", String.valueOf(cells.threshold() - 1), String.valueOf(cells.threshold())) : null, false,
                List.of(overall(p.overall()))));
        sections.add(new Section(t("participationView.section.departments"), null, false, List.of(departments(p.departments()))));
        sections.add(new Section(t("participationView.section.weeks"),
                p.weeks().isEmpty() ? t("participationView.noWeeks") : null, false,
                p.weeks().isEmpty() ? List.of() : List.of(weeks(p.weeks()))));
        sections.add(new Section(t("participationView.section.questions"),
                p.cards().isEmpty() ? t("participationView.noMarks") : t("participationView.questionsNote"), false, List.of()));
        for (Card card : p.cards()) {
            sections.add(card(card, p.overall().finished()));
        }
        return sections;
    }

    Table overall(Counts c) {
        List<List<String>> rows = new ArrayList<>();
        rows.add(List.of(t("participationView.metric.registered"), cells.count(c.registered())));
        rows.add(List.of(t("participationView.metric.notStarted"), cells.count(c.notStarted())));
        rows.add(List.of(t("participationView.metric.inProgress"), cells.count(c.inProgress())));
        rows.add(List.of(t("participationView.metric.finished"), cells.count(c.finished())));
        rows.add(List.of(t("participationView.metric.started"), cells.count(c.started())));
        rows.add(List.of(t("participationView.metric.startRate"), cells.rate(c.started(), c.registered())));
        rows.add(List.of(t("participationView.metric.completionRate"), cells.rate(c.finished(), c.started())));
        rows.add(List.of(t("participationView.metric.overallRate"), cells.rate(c.finished(), c.registered())));
        return new Table(null, List.of(t("participationView.col.measure"), t("participationView.col.value")), rows);
    }

    Table departments(List<DepartmentCounts> departments) {
        List<List<String>> rows = new ArrayList<>();
        for (DepartmentCounts d : departments) {
            Counts c = d.counts();
            rows.add(List.of(d.department(), cells.count(c.registered()), cells.count(c.notStarted()),
                    cells.count(c.inProgress()), cells.count(c.finished()), cells.rate(c.started(), c.registered()),
                    cells.rate(c.finished(), c.started()), cells.rate(c.finished(), c.registered())));
        }
        return new Table(null, List.of(t("participationView.col.department"), t("participationView.metric.registered"),
                t("participationView.metric.notStarted"), t("participationView.metric.inProgress"),
                t("participationView.metric.finished"), t("participationView.metric.startRate"),
                t("participationView.metric.completionRate"), t("participationView.metric.overallRate")), rows);
    }

    Table weeks(List<Week> weeks) {
        DateTimeFormatter day = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale);
        List<List<String>> rows = new ArrayList<>();
        for (Week w : weeks) {
            rows.add(List.of(day.format(w.monday()), cells.count(w.registered()), cells.count(w.started()), cells.count(w.finished())));
        }
        return new Table(null, List.of(t("participationView.col.week"), t("participationView.metric.registered"),
                t("participationView.metric.started"), t("participationView.metric.finished")), rows);
    }

    Section card(Card card, long finished) {
        List<Table> tables = new ArrayList<>();
        for (QuestionStats q : card.questions()) {
            tables.add(new Table(q.text(), List.of(t("participationView.question.presented"),
                    t("participationView.question.respondents"), t("participationView.question.answered"),
                    t("participationView.question.responseRate"), t("participationView.question.reach")),
                    List.of(List.of(cells.count(q.presented()), cells.count(q.respondents()), cells.count(q.answered()),
                            cells.rate(q.answered(), q.presented()), cells.rate(q.respondents(), finished)))));
            Table distribution = distribution(q);
            if (distribution != null) {
                tables.add(distribution);
            }
        }
        String note = card.questions().isEmpty() ? t("participationView.noQuestions") : null;
        return new Section(card.title(), note, true, tables);
    }

    /** BR-125: the distribution suited to the question's type, or {@code null} for a count-only one. */
    Table distribution(QuestionStats q) {
        return switch (q.kind()) {
            case CHOICE, CHECKBOX -> {
                List<List<String>> rows = new ArrayList<>();
                for (OptionCount o : q.options()) {
                    String label = switch (q.kind()) {
                        case CHECKBOX -> t("participationView.checkbox." + o.label());
                        default -> o.listed() ? o.label() : t("participationView.dist.notListed", o.label());
                    };
                    rows.add(List.of(label, cells.count(o.count()), cells.rate(o.count(), q.answered())));
                }
                yield new Table(null, List.of(t("participationView.dist.option"), t("participationView.dist.count"),
                        t("participationView.dist.share")), rows);
            }
            case NUMBER -> {
                NumberSummary s = q.numbers();
                long n = s == null ? 0 : s.n();
                boolean shown = cells.summaryShown(n);
                List<List<String>> rows = new ArrayList<>();
                rows.add(List.of(t("participationView.dist.n"), cells.count(n)));
                rows.add(List.of(t("participationView.dist.min"), shown ? number(s.min()) : SmallCell.NO_VALUE));
                rows.add(List.of(t("participationView.dist.q1"), shown ? number(s.q1()) : SmallCell.NO_VALUE));
                rows.add(List.of(t("participationView.dist.median"), shown ? number(s.median()) : SmallCell.NO_VALUE));
                rows.add(List.of(t("participationView.dist.q3"), shown ? number(s.q3()) : SmallCell.NO_VALUE));
                rows.add(List.of(t("participationView.dist.max"), shown ? number(s.max()) : SmallCell.NO_VALUE));
                yield new Table(null, List.of(t("participationView.dist.statistic"), t("participationView.col.value")), rows);
            }
            case DATE -> {
                DateSummary s = q.dates();
                long n = s == null ? 0 : s.n();
                boolean shown = cells.summaryShown(n);
                DateTimeFormatter day = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale);
                DateTimeFormatter month = DateTimeFormatter.ofPattern("MMM yyyy", locale);
                List<List<String>> rows = new ArrayList<>();
                rows.add(List.of(t("participationView.dist.n"), cells.count(n)));
                rows.add(List.of(t("participationView.dist.earliest"), shown ? day.format(s.earliest()) : SmallCell.NO_VALUE));
                rows.add(List.of(t("participationView.dist.latest"), shown ? day.format(s.latest()) : SmallCell.NO_VALUE));
                if (s != null) {
                    for (Map.Entry<YearMonth, Long> m : s.perMonth().entrySet()) {
                        rows.add(List.of(month.format(m.getKey().atDay(1)), cells.count(m.getValue())));
                    }
                }
                yield new Table(null, List.of(t("participationView.dist.statistic"), t("participationView.col.value")), rows);
            }
            case COUNT_ONLY -> null;
        };
    }

    private String number(BigDecimal value) {
        NumberFormat format = NumberFormat.getNumberInstance(locale);
        format.setMaximumFractionDigits(2);
        return format.format(value);
    }

    /** The registration range as the exports print it, "any" for an open end. */
    String range(LocalDate from, LocalDate to) {
        DateTimeFormatter day = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale);
        String any = t("participationView.export.any");
        return t("participationView.export.range", from == null ? any : day.format(from), to == null ? any : day.format(to));
    }

    /** The suppression threshold the tables were rendered with. */
    int threshold() {
        return cells.threshold();
    }

    String t(String key, Object... params) {
        return Translations.get(locale, key, params);
    }
}
