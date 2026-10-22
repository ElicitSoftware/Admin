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

import com.elicitsoftware.participation.ParticipationTables.Section;
import com.elicitsoftware.participation.ParticipationTables.Table;
import com.elicitsoftware.report.ReportResponse;
import com.elicitsoftware.report.pdf.Content;
import com.elicitsoftware.report.pdf.PDFDocument;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The two exports of the Participation page (UC-031 BR-126): the same sections the page shows,
 * as a CSV file and as the content of a PDF for {@link com.elicitsoftware.report.PDFService}.
 * Aggregates only; row-level data stays the respondent export (UC-011).
 */
public final class ParticipationExport {

    /** The byte order mark that makes a spreadsheet read the CSV as UTF-8. */
    private static final String BOM = "﻿";

    /** The PDF service's font; it draws WinAnsi (Latin) characters only. */
    private static final PDFont PDF_FONT = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

    private static final DateTimeFormatter FILE_DAY = DateTimeFormatter.ofPattern("yyyyMMdd");

    /**
     * What the exports print above the tables.
     *
     * @param surveyName  the survey's name
     * @param departments the departments counted, comma-separated
     * @param from        the first registration day, or {@code null}
     * @param to          the last registration day, or {@code null}
     */
    public record Heading(String surveyName, String departments, LocalDate from, LocalDate to) {
    }

    private ParticipationExport() {
    }

    /**
     * The CSV file: the filter, then one block per table, blocks separated by a blank line.
     *
     * @param p       the computed figures
     * @param cells   the suppression rule and the console's locale
     * @param heading the filter
     * @return the file's bytes, UTF-8 with a byte order mark
     */
    public static byte[] csv(Participation p, SmallCell cells, Heading heading) {
        ParticipationTables tables = new ParticipationTables(cells);
        StringBuilder out = new StringBuilder(BOM);
        for (List<String> line : filterLines(tables, heading)) {
            row(out, line);
        }
        for (Section section : tables.sections(p)) {
            out.append("\r\n");
            row(out, List.of(section.title()));
            if (section.note() != null) {
                row(out, List.of(section.note()));
            }
            for (Table table : section.tables()) {
                if (table.title() != null) {
                    row(out, List.of(table.title()));
                }
                row(out, table.headers());
                for (List<String> cellsOfRow : table.rows()) {
                    row(out, cellsOfRow);
                }
            }
        }
        return out.toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * The PDF's content, for {@code PDFService.generatePDF}. Its own text is in the console's
     * language unless the PDF font cannot draw that language's script, in which case it is in
     * English; a survey text character the font cannot draw becomes "?" (BR-126).
     *
     * @param p       the computed figures
     * @param cells   the suppression rule and the console's locale
     * @param heading the filter
     * @return one report response holding the whole document
     */
    public static ReportResponse pdf(Participation p, SmallCell cells, Heading heading) {
        SmallCell used = cells;
        if (!drawable(new ParticipationTables(cells).t("participationView.export.pdfTitle", "")
                + new ParticipationTables(cells).t("participationView.metric.registered"))) {
            used = new SmallCell(cells.threshold(), Locale.ENGLISH);
        }
        ParticipationTables tables = new ParticipationTables(used);
        List<Content> contents = new ArrayList<>();
        for (List<String> line : filterLines(tables, heading)) {
            contents.add(text(line.get(0) + ": " + line.get(1)));
        }
        for (Section section : tables.sections(p)) {
            contents.add(text(section.card() ? section.title() : section.title().toUpperCase(used.locale())));
            if (section.note() != null) {
                contents.add(text(section.note()));
            }
            for (Table table : section.tables()) {
                if (table.title() != null) {
                    contents.add(text(table.title()));
                }
                if (table.chart() != null) {
                    // BR-129: the page's own SVG, drawn inline above its table.
                    Content chart = new Content();
                    chart.svg = table.chart();
                    chart.inline = true;
                    contents.add(chart);
                }
                com.elicitsoftware.report.pdf.Table pdfTable = new com.elicitsoftware.report.pdf.Table();
                pdfTable.headers = table.headers().stream().map(ParticipationExport::drawableText).toArray(String[]::new);
                pdfTable.widths = new float[table.headers().size()];
                java.util.Arrays.fill(pdfTable.widths, table.headers().size() > 4 ? 52f : 90f);
                pdfTable.body = table.rows().stream()
                        .map(r -> r.stream().map(ParticipationExport::drawableText).toArray(String[]::new))
                        .toArray(String[][]::new);
                contents.add(new Content(pdfTable));
            }
        }
        PDFDocument document = new PDFDocument();
        document.title = drawableText(tables.t("participationView.export.pdfTitle", heading.surveyName()));
        document.content = contents.toArray(new Content[0]);
        return new ReportResponse(document.title, "", document);
    }

    /**
     * The file name, {@code participation-<survey name>-<yyyyMMdd>.<extension>}, with anything
     * unsafe for a file system replaced.
     *
     * @param surveyName the survey's name
     * @param day        the day of the export
     * @param extension  {@code csv} or {@code pdf}
     * @return the file name
     */
    public static String fileName(String surveyName, LocalDate day, String extension) {
        String safe = surveyName == null || surveyName.isBlank() ? "survey" : surveyName.trim().replaceAll("[^\\p{L}\\p{N}._-]+", "_");
        return "participation-" + safe + "-" + FILE_DAY.format(day) + "." + extension;
    }

    /**
     * The text with every character the PDF font cannot draw replaced by "?".
     *
     * @param text any text
     * @return text the PDF font can draw
     */
    public static String drawableText(String text) {
        if (text == null || drawable(text)) {
            return text;
        }
        StringBuilder out = new StringBuilder(text.length());
        text.codePoints().forEach(cp -> {
            String one = new String(Character.toChars(cp));
            out.append(drawable(one) ? one : "?");
        });
        return out.toString();
    }

    static boolean drawable(String text) {
        try {
            PDF_FONT.encode(text);
            return true;
        } catch (IllegalArgumentException | IOException e) {
            return false;
        }
    }

    private static List<List<String>> filterLines(ParticipationTables tables, Heading heading) {
        return List.of(
                List.of(tables.t("participationView.filter.survey"), heading.surveyName()),
                List.of(tables.t("participationView.filter.departments"), heading.departments()),
                List.of(tables.t("participationView.export.registered"), tables.range(heading.from(), heading.to())),
                List.of(tables.t("participationView.export.threshold"), String.valueOf(tables.threshold())));
    }

    private static Content text(String text) {
        Content content = new Content();
        content.text = drawableText(text);
        return content;
    }

    /**
     * Survey text is authored, and a spreadsheet would run a cell that starts like a formula; such
     * a cell is prefixed with an apostrophe. A negative number is left as it is.
     */
    static String neutralized(String cell) {
        if (cell.isEmpty()) {
            return cell;
        }
        char first = cell.charAt(0);
        boolean formula = first == '=' || first == '+' || first == '@' || first == '\t' || first == '\r'
                || (first == '-' && !cell.matches("-[0-9][0-9.,]*"));
        return formula ? "'" + cell : cell;
    }

    /** One RFC 4180 record. */
    private static void row(StringBuilder out, List<String> cells) {
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) {
                out.append(',');
            }
            String cell = neutralized(cells.get(i) == null ? "" : cells.get(i));
            if (cell.indexOf(',') >= 0 || cell.indexOf('"') >= 0 || cell.indexOf('\n') >= 0 || cell.indexOf('\r') >= 0) {
                out.append('"').append(cell.replace("\"", "\"\"")).append('"');
            } else {
                out.append(cell);
            }
        }
        out.append("\r\n");
    }
}
