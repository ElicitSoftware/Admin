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

import com.elicitsoftware.diagnostics.CheckResult;
import com.vaadin.flow.component.html.Span;

/**
 * Shared rendering for the System screens: a status badge per {@link CheckResult}.
 */
final class SystemBadges {

    private SystemBadges() {
        // Static helpers only
    }

    static Span status(CheckResult result) {
        return badge(switch (result.status()) {
            case UP -> "OK";
            case DOWN -> "Failed";
            case UNKNOWN -> "Not checked";
        }, switch (result.status()) {
            case UP -> "badge success";
            case DOWN -> "badge error";
            case UNKNOWN -> "badge contrast";
        });
    }

    static Span presence(boolean present) {
        return present ? badge("Present", "badge success") : badge("Absent", "badge error");
    }

    static Span badge(String text, String theme) {
        Span span = new Span(text);
        span.getElement().getThemeList().add(theme);
        return span;
    }
}
