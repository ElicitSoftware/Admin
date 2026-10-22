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
import com.elicitsoftware.participation.Participation.DepartmentCounts;
import com.elicitsoftware.participation.Participation.Kind;
import com.elicitsoftware.participation.Participation.OptionCount;
import com.elicitsoftware.participation.Participation.QuestionStats;
import com.elicitsoftware.participation.Participation.Week;
import com.elicitsoftware.report.ReportResponse;
import com.elicitsoftware.report.pdf.Content;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** UC-031 BR-122 and BR-126: the exports carry the page's tables, suppressed, and nothing more. */
class ParticipationExportTest {

    private static final ParticipationExport.Heading HEADING =
            new ParticipationExport.Heading("Census, 2026", "All departments", LocalDate.of(2026, 1, 1), null);

    private static Participation sample() {
        QuestionStats smoker = new QuestionStats("=Do you smoke?", Kind.CHOICE, 12, 12, 11,
                List.of(new OptionCount("Yes", 3, true), new OptionCount("No", 8, true)), null, null);
        Participation.Progress progress = new Participation.Progress(List.of(
                new Participation.StepProgress(1, "Household", 12, 6, new Participation.Durations(12, 300L, 420L, 900L)),
                new Participation.StepProgress(2, "People", 3, 2, new Participation.Durations(3, 60L, 60L, 60L))),
                3, 0, new Participation.Durations(12, 1800L, 5400L, 90000L), 0);
        return new Participation(new Counts(40, 10, 18, 12, 8),
                List.of(new DepartmentCounts("Oncology", new Counts(37, 9, 16, 12, 6)), new DepartmentCounts("Clinic", new Counts(3, 1, 2, 0, 2))),
                List.of(new Week(LocalDate.of(2026, 1, 5), 40, 30, 12)),
                List.of(new Card("Smoker", List.of(smoker))), progress, 14);
    }

    @Test
    void csvHoldsEveryTableWithTheSameSuppression() {
        String csv = new String(ParticipationExport.csv(sample(), new SmallCell(5, Locale.ENGLISH), HEADING), StandardCharsets.UTF_8);
        assertTrue(csv.startsWith("﻿"), "UTF-8 byte order mark");
        assertTrue(csv.contains("Survey,\"Census, 2026\"\r\n"), csv);
        assertTrue(csv.contains("Registered between,\"Jan 1, 2026 and any date\"\r\n"), csv);
        assertTrue(csv.contains("Clinic,< 5,< 5,< 5,0,—,—,—,< 5\r\n"), csv);
        assertTrue(csv.contains("Oncology,37,9,16,12,76%,43%,32%,6\r\n"), csv);
        // BR-130 to BR-132: drop-off with stalled, and time per step with suppression.
        assertTrue(csv.contains("Household,12,6\r\n"), csv);
        assertTrue(csv.contains("People,< 5,< 5\r\n"), csv);
        assertTrue(csv.contains("Household,12,5 min,7 min,15 min\r\n"), csv);
        assertTrue(csv.contains("People,< 5,—,—,—\r\n"), csv);
        assertTrue(csv.contains("Whole survey (first access to finish),12,30 min,1 h 30 min,1 d 1 h\r\n"), csv);
        assertFalse(csv.contains("<svg"), "the CSV carries tables only");
        assertTrue(csv.contains("Yes,< 5,—\r\n"), csv);
        assertTrue(csv.contains("No,8,73%\r\n"), csv);
        // A cell that would start a spreadsheet formula is neutralized.
        assertTrue(csv.contains("'=Do you smoke?\r\n"), csv);
        assertFalse(csv.contains(",3,"), "a suppressed count must not appear as itself");
    }

    @Test
    void pdfFallsBackToEnglishWhenItsFontCannotDrawTheLanguage() {
        ReportResponse arabic = ParticipationExport.pdf(sample(), new SmallCell(5, Locale.forLanguageTag("ar")), HEADING);
        assertTrue(arabic.pdf.title.startsWith("Participation: "), arabic.pdf.title);
        ReportResponse spanish = ParticipationExport.pdf(sample(), new SmallCell(5, Locale.forLanguageTag("es-419")), HEADING);
        assertTrue(spanish.pdf.title.startsWith("Participación: "), spanish.pdf.title);
        assertTrue(Arrays.stream(spanish.pdf.content).allMatch(c -> c.text == null || ParticipationExport.drawable(c.text)));
    }

    @Test
    void pdfReplacesSurveyTextItCannotDraw() {
        assertEquals("Edad ? years", ParticipationExport.drawableText("Edad ع years"));
        assertEquals("Año", ParticipationExport.drawableText("Año"));
    }

    @Test
    void pdfHasOneTableForEveryTableOnThePage() {
        ReportResponse pdf = ParticipationExport.pdf(sample(), new SmallCell(5, Locale.ENGLISH), HEADING);
        long tables = Arrays.stream(pdf.pdf.content).filter(c -> c.table != null).count();
        // participation, departments, weeks, drop-off, time per step, the question's figures and its distribution
        assertEquals(7, tables);
        // BR-129: four charts (funnel, weeks, drop-off, options), each drawn inline before its table.
        List<Content> charts = Arrays.stream(pdf.pdf.content).filter(c -> c.svg != null).toList();
        assertEquals(4, charts.size());
        assertTrue(charts.stream().allMatch(c -> c.inline));
        Content departments = Arrays.stream(pdf.pdf.content).filter(c -> c.table != null).skip(1).findFirst().orElseThrow();
        assertEquals("< 5", departments.table.body[1][1]);
    }

    @Test
    void fileNameIsSafeAndDated() {
        assertEquals("participation-Census_2026-20261022.csv",
                ParticipationExport.fileName("Census, 2026", LocalDate.of(2026, 10, 22), "csv"));
    }
}
