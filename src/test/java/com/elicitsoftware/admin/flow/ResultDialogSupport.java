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

import com.vaadin.browserless.ComponentQuery;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.ListItem;

import java.util.List;

/**
 * Reads a {@link ResultDialog}'s summary back out, for the tests of the views that open one
 * ({@code SurveyDefinitionApplyView}, {@code RespondentImportView}, {@code RegisterView}).
 *
 * <p>The dialog lays its summary out as structure rather than as one pre-formatted string, so
 * these are the seams a test asserts on: the sentences, the sub-headings and the detail lines.
 * That is what proves the layout no longer depends on the inert Lumo utility classes (#80, #85).</p>
 */
final class ResultDialogSupport {

    private ResultDialogSupport() {
    }

    /** The summary's sentences, in the order they read, one element per line. */
    static List<String> messagesIn(Dialog dialog) {
        return new ComponentQuery<>(Div.class).withClassName("elicit-result-message")
                .from(dialog).all().stream().map(Div::getText).toList();
    }

    /** The summary's sub-headings ("Records installed: 128", "Errors:"). */
    static List<String> headingsIn(Dialog dialog) {
        return new ComponentQuery<>(Div.class).withClassName("elicit-result-heading")
                .from(dialog).all().stream().map(Div::getText).toList();
    }

    /** The indented detail lines beneath a heading, each its own list item. */
    static List<String> detailsIn(Dialog dialog) {
        return new ComponentQuery<>(ListItem.class).from(dialog).all()
                .stream().map(ListItem::getText).toList();
    }

    /** The detail lines the dialog marked as errors. */
    static List<String> errorsIn(Dialog dialog) {
        return new ComponentQuery<>(ListItem.class).withClassName("elicit-result-error")
                .from(dialog).all().stream().map(ListItem::getText).toList();
    }
}
