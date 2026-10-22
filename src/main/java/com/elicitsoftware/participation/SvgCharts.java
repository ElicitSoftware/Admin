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

import java.util.List;
import java.util.Locale;

/**
 * The Participation page's charts, written as SVG strings (UC-031 BR-129). One SVG is shown on the
 * page and embedded in the PDF, so the two cannot differ; nothing here depends on a browser.
 * <p>
 * The marks follow one set of specs: bars at most 16px thick with a 4px rounded data end and a
 * square baseline, a 2px gap between touching columns, a hairline baseline, text in ink colors
 * only, and the series colors in one fixed order validated for color-vision deficiency on white.
 * A value suppressed by {@link SmallCell} draws no bar and is labeled "&lt; k" at the baseline,
 * so a chart never shows what its table hides. Every mark carries a {@code <title>}, which a
 * browser shows on hover; the chart as a whole has {@code role="img"} and an accessible name, and
 * the table beside it is its text equivalent.
 */
public final class SvgCharts {

    /** Categorical slots 1 to 3 (blue, orange, aqua), validated together on a white surface. */
    static final String[] SERIES = {"#2a78d6", "#eb6834", "#1baf7a"};
    static final String INK = "#0b0b0b";
    static final String INK_SECONDARY = "#52514e";
    static final String HAIRLINE = "#d9d8d3";
    static final String FONT = "font-family=\"Helvetica, Arial, sans-serif\"";

    static final int WIDTH = 640;
    static final int BAR = 16;
    static final int ROW = 28;
    static final int LABEL_WIDTH = 190;
    static final int VALUE_ROOM = 64;
    static final int LABEL_CHARS = 30;

    /**
     * One bar: its label, its count, and the count as the table renders it.
     *
     * @param label the category
     * @param count the raw count, which sets the bar's length unless it is suppressed
     * @param text  the count as shown, from {@link SmallCell#count(long)}
     */
    public record Bar(String label, long count, String text) {
    }

    /** One group of columns, such as a week: its label and one value per series. */
    public record Group(String label, long[] counts, String[] texts) {
    }

    private SvgCharts() {
    }

    /**
     * Horizontal bars of one series: the funnel, a question's options, drop-off by step.
     *
     * @param name  the chart's accessible name
     * @param bars  the bars, top to bottom
     * @param cells the suppression rule
     * @return the SVG
     */
    public static String bars(String name, List<Bar> bars, SmallCell cells) {
        int height = Math.max(1, bars.size()) * ROW + 8;
        long max = bars.stream().filter(b -> !cells.suppressed(b.count())).mapToLong(Bar::count).max().orElse(0);
        int x0 = LABEL_WIDTH;
        int span = WIDTH - LABEL_WIDTH - VALUE_ROOM;
        StringBuilder svg = open(name, WIDTH, height);
        svg.append(line(x0, 4, x0, height - 4));
        int y = 4;
        for (Bar bar : bars) {
            int mid = y + ROW / 2;
            String label = shorten(bar.label());
            svg.append("<text x=\"").append(x0 - 8).append("\" y=\"").append(mid + 4)
                    .append("\" text-anchor=\"end\" font-size=\"12\" fill=\"").append(INK).append("\" ").append(FONT).append(">")
                    .append(escape(label)).append("</text>");
            int length = cells.suppressed(bar.count()) || max == 0 ? 0 : (int) Math.round((double) bar.count() / max * span);
            if (length > 0) {
                svg.append("<path d=\"").append(horizontalBar(x0, mid - BAR / 2, length, BAR)).append("\" fill=\"")
                        .append(SERIES[0]).append("\"><title>").append(escape(bar.label() + ": " + bar.text()))
                        .append("</title></path>");
            }
            svg.append("<text x=\"").append(x0 + length + 6).append("\" y=\"").append(mid + 4)
                    .append("\" font-size=\"12\" fill=\"").append(cells.suppressed(bar.count()) ? INK_SECONDARY : INK)
                    .append("\" ").append(FONT).append(">").append(escape(bar.text())).append("</text>");
            y += ROW;
        }
        return svg.append("</svg>").toString();
    }

    /**
     * Columns of up to three series per group, with a legend: the weeks.
     *
     * @param name   the chart's accessible name
     * @param series the series' names, in slot order
     * @param groups the groups, left to right
     * @param cells  the suppression rule
     * @return the SVG
     */
    public static String columns(String name, List<String> series, List<Group> groups, SmallCell cells) {
        int top = 34;
        int plot = 160;
        int bottom = top + plot;
        int left = 44;
        // Each group gets an equal share of the plot; its columns are capped at 16px and centered in
        // it, so a few weeks spread across the width and many weeks narrow down (to 3px) before
        // the chart grows wider than the page.
        int available = WIDTH - left - 16;
        int groupWidth = Math.max(series.size() * 5 + 6, available / Math.max(1, groups.size()));
        int column = Math.max(3, Math.min(16, (groupWidth - 8) / series.size() - 2));
        int width = Math.max(WIDTH, left + groups.size() * groupWidth + 16);
        int inset = (groupWidth - series.size() * (column + 2) + 2) / 2;
        int height = bottom + 30;
        long max = 0;
        for (Group g : groups) {
            for (long c : g.counts()) {
                if (!cells.suppressed(c)) {
                    max = Math.max(max, c);
                }
            }
        }
        long ceiling = niceCeiling(max);

        StringBuilder svg = open(name, width, height);
        // Legend, always present for more than one series: a swatch beside ink text.
        int lx = left;
        for (int s = 0; s < series.size(); s++) {
            svg.append("<rect x=\"").append(lx).append("\" y=\"8\" width=\"10\" height=\"10\" rx=\"2\" fill=\"")
                    .append(SERIES[s]).append("\"/>");
            svg.append("<text x=\"").append(lx + 14).append("\" y=\"17\" font-size=\"12\" fill=\"").append(INK)
                    .append("\" ").append(FONT).append(">").append(escape(series.get(s))).append("</text>");
            lx += 22 + series.get(s).length() * 7;
        }
        // A recessive scale: the baseline, a midline and the top, each a hairline with its value.
        // These are scale values, not counts of anyone, so they are not suppressed.
        long[] ticks = ceiling == 0 ? new long[]{0} : new long[]{0, ceiling / 2, ceiling};
        for (long tick : ticks) {
            int ty = ceiling == 0 ? bottom : bottom - (int) Math.round((double) tick / ceiling * plot);
            svg.append(line(left, ty, width - 8, ty));
            svg.append("<text x=\"").append(left - 6).append("\" y=\"").append(ty + 4)
                    .append("\" text-anchor=\"end\" font-size=\"11\" fill=\"").append(INK_SECONDARY).append("\" ")
                    .append(FONT).append(">").append(tick).append("</text>");
        }
        int every = Math.max(1, (int) Math.ceil(groups.size() / 8.0));
        int x = left;
        for (int g = 0; g < groups.size(); g++) {
            Group group = groups.get(g);
            for (int s = 0; s < series.size() && s < group.counts().length; s++) {
                long count = group.counts()[s];
                int cx = x + inset + s * (column + 2);
                if (cells.suppressed(count)) {
                    svg.append("<text x=\"").append(cx + column / 2.0).append("\" y=\"").append(bottom - 3)
                            .append("\" font-size=\"8\" fill=\"").append(INK_SECONDARY).append("\" ").append(FONT)
                            .append(" transform=\"rotate(-90 ").append(cx + column / 2.0).append(' ').append(bottom - 3)
                            .append(")\">").append(escape(group.texts()[s])).append("<title>")
                            .append(escape(group.label() + ", " + series.get(s) + ": " + group.texts()[s]))
                            .append("</title></text>");
                    continue;
                }
                int h = ceiling == 0 ? 0 : (int) Math.round((double) count / ceiling * plot);
                if (h > 0) {
                    svg.append("<path d=\"").append(verticalBar(cx, bottom, column, h)).append("\" fill=\"")
                            .append(SERIES[s]).append("\"><title>")
                            .append(escape(group.label() + ", " + series.get(s) + ": " + group.texts()[s]))
                            .append("</title></path>");
                }
            }
            if (g % every == 0) {
                svg.append("<text x=\"").append(x + groupWidth / 2.0).append("\" y=\"").append(bottom + 16)
                        .append("\" text-anchor=\"middle\" font-size=\"11\" fill=\"").append(INK_SECONDARY).append("\" ")
                        .append(FONT).append(">").append(escape(group.label())).append("</text>");
            }
            x += groupWidth;
        }
        return svg.append("</svg>").toString();
    }

    /** A bar growing right from the baseline: square at the baseline, 4px rounded at its end. */
    static String horizontalBar(int x, int y, int length, int thickness) {
        int r = Math.min(4, Math.min(length, thickness / 2));
        return String.format(Locale.ROOT, "M%d,%d h%d a%d,%d 0 0 1 %d,%d v%d a%d,%d 0 0 1 %d,%d h%d z",
                x, y, length - r, r, r, r, r, thickness - 2 * r, r, r, -r, r, -(length - r));
    }

    /** A column growing up from the baseline: square at the baseline, 4px rounded at its top. */
    static String verticalBar(int x, int baseline, int width, int height) {
        int r = Math.min(4, Math.min(height, width / 2));
        return String.format(Locale.ROOT, "M%d,%d v%d a%d,%d 0 0 1 %d,%d h%d a%d,%d 0 0 1 %d,%d v%d z",
                x, baseline, -(height - r), r, r, r, -r, width - 2 * r, r, r, r, r, height - r);
    }

    /** The smallest of 1, 2 and 5 times a power of ten that is at least {@code max}. */
    static long niceCeiling(long max) {
        if (max <= 0) {
            return 0;
        }
        long power = 1;
        while (power * 10 <= max) {
            power *= 10;
        }
        for (long step : new long[]{1, 2, 5, 10}) {
            if (step * power >= max) {
                return step * power;
            }
        }
        return 10 * power;
    }

    private static StringBuilder open(String name, int width, int height) {
        return new StringBuilder(4096)
                .append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"").append(width).append("\" height=\"")
                .append(height).append("\" viewBox=\"0 0 ").append(width).append(' ').append(height)
                .append("\" role=\"img\" aria-label=\"").append(escape(name)).append("\" class=\"participation-chart\"><title>")
                .append(escape(name)).append("</title>");
    }

    private static String line(int x1, int y1, int x2, int y2) {
        return "<line x1=\"" + x1 + "\" y1=\"" + y1 + "\" x2=\"" + x2 + "\" y2=\"" + y2
                + "\" stroke=\"" + HAIRLINE + "\" stroke-width=\"1\"/>";
    }

    /** A label longer than the label column is cut with an ellipsis; the full text stays in the table and the tooltip. */
    static String shorten(String label) {
        if (label == null) {
            return "";
        }
        return label.length() <= LABEL_CHARS ? label : label.substring(0, LABEL_CHARS - 1).stripTrailing() + "…";
    }

    static String escape(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(text.length() + 8);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '&' -> out.append("&amp;");
                case '"' -> out.append("&quot;");
                default -> {
                    if (c >= 0x20 || c == '\t' || c == '\n') {
                        out.append(c);
                    }
                }
            }
        }
        return out.toString();
    }
}
