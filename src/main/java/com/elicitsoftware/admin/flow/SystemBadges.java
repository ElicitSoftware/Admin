package com.elicitsoftware.admin.flow;

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

import com.elicitsoftware.admin.i18n.Translations;
import com.elicitsoftware.diagnostics.CheckResult;
import com.vaadin.flow.component.html.Span;

/**
 * Shared rendering for the System screens: a status badge per {@link CheckResult}. The labels are
 * looked up through {@link Translations} because the helpers are static (UC-026).
 */
final class SystemBadges {

    private SystemBadges() {
        // Static helpers only
    }

    static Span status(CheckResult result) {
        return badge(switch (result.status()) {
            case UP -> Translations.get("system.badge.ok");
            case DOWN -> Translations.get("system.badge.failed");
            case UNKNOWN -> Translations.get("system.badge.notChecked");
        }, switch (result.status()) {
            case UP -> "success";
            case DOWN -> "error";
            case UNKNOWN -> "contrast";
        });
    }

    static Span presence(boolean present) {
        return present ? badge(Translations.get("system.badge.present"), "success")
                : badge(Translations.get("system.badge.absent"), "error");
    }

    /** A Lumo badge in the given tone ({@code success}, {@code error} or {@code contrast}). */
    static Span badge(String text, String tone) {
        Span span = new Span(text);
        span.getElement().getThemeList().add("badge");
        span.getElement().getThemeList().add(tone);
        return span;
    }

    /** {@code {0} ms} in the current language. */
    static String millis(long durationMs) {
        return Translations.get("system.milliseconds", Long.toString(durationMs));
    }
}
