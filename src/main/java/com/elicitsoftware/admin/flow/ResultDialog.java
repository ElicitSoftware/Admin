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

import com.vaadin.flow.component.ModalityMode;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.ListItem;
import com.vaadin.flow.component.html.UnorderedList;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

import java.util.List;

/**
 * The acknowledgement dialog the upload views show once an operation has run: a title, the
 * outcome in lines, and a Close button.
 * <p>
 * The summary is laid out as structure — one block element per line, detail lines in a real
 * {@code <ul>} — rather than as one newline-separated string. An earlier version handed the
 * whole summary to a single {@code Span} and relied on {@code LumoUtility.Whitespace.PRE_WRAP}
 * to render the newlines, but Admin's utility classes are inert: {@code styles.css} imports
 * {@code lumo/lumo-utility.css}, which is not there, and the Lumo utility classes need the Lumo
 * theme in any case, while this application renders with Aura. Every line therefore collapsed
 * into one run-on paragraph and the success/error coloring was lost with it. The layout here
 * survives a missing stylesheet because block elements and list indentation are native HTML
 * behaviour, and the few visual affordances are set on the components themselves through Lumo
 * custom properties that {@code styles.css} maps onto the brand's colors.
 */
class ResultDialog extends Dialog {

    /** What a summary line is, which decides how it is rendered. */
    enum Kind {
        /** A sentence about the outcome. */
        MESSAGE,
        /** A sentence introducing the detail lines that follow it. */
        HEADING,
        /** One per-table count or similar detail, rendered as a list item. */
        DETAIL,
        /** One error, rendered as a list item in the error color. */
        ERROR
    }

    /** One line of a result summary. */
    record Line(String text, Kind kind) {

        static Line message(String text) {
            return new Line(text, Kind.MESSAGE);
        }

        static Line heading(String text) {
            return new Line(text, Kind.HEADING);
        }

        static Line detail(String text) {
            return new Line(text, Kind.DETAIL);
        }

        static Line error(String text) {
            return new Line(text, Kind.ERROR);
        }
    }

    /**
     * A group of lines that belong together — what a blank line used to separate. Sections are
     * spaced apart from each other; the lines inside one are not.
     */
    record Section(List<Line> lines) {

        Section {
            lines = List.copyOf(lines);
        }

        static Section of(Line... lines) {
            return new Section(List.of(lines));
        }
    }

    /**
     * Shows a summary made of sections.
     *
     * @param title the dialog's header title
     * @param sections the summary, in the order it should read
     * @param isError whether the operation failed, which decides the coloring
     */
    ResultDialog(String title, List<Section> sections, boolean isError) {
        setHeaderTitle(title);

        Div summary = new Div();
        summary.addClassName("elicit-result-summary");
        summary.getStyle().set("text-align", "start");

        boolean first = true;
        for (Section section : sections) {
            Div group = renderSection(section, isError);
            if (!first) {
                // Aura's own spacing token: --lumo-space-* does not resolve in Admin (styles.css
                // defines only the color and font-family --lumo-* properties), so a declaration
                // written against it is as inert as the utility classes #85 removed.
                group.getStyle().set("margin-top", "var(--vaadin-gap-m, 12px)");
            }
            first = false;
            summary.add(group);
        }

        Button closeButton = new Button(getTranslation("common.close"), evt -> close());
        closeButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        VerticalLayout dialogLayout = new VerticalLayout(summary, closeButton);
        dialogLayout.setAlignItems(FlexComponent.Alignment.CENTER);
        dialogLayout.setSpacing(true);

        add(dialogLayout);
        setModality(ModalityMode.STRICT);
        setDraggable(false);
        setResizable(true);
        setWidth("600px");
        setMaxWidth("90vw");
    }

    /**
     * Shows a single message — the shape an unexpected exception leaves.
     *
     * @param title the dialog's header title
     * @param message the one line to show
     * @param isError whether the operation failed, which decides the coloring
     */
    ResultDialog(String title, String message, boolean isError) {
        this(title, List.of(Section.of(Line.message(message == null ? "" : message))), isError);
    }

    /**
     * Lays one section out, collapsing each run of detail or error lines into a single list so
     * they indent under the sentence that introduces them.
     */
    private static Div renderSection(Section section, boolean isError) {
        Div group = new Div();
        group.addClassName("elicit-result-section");

        UnorderedList list = null;
        for (Line line : section.lines()) {
            if (line.kind() == Kind.DETAIL || line.kind() == Kind.ERROR) {
                if (list == null) {
                    list = new UnorderedList();
                    list.addClassName("elicit-result-items");
                    // Native <ul> margins would space the list away from its heading.
                    list.getStyle().set("margin-block", "0");
                    group.add(list);
                }
                ListItem item = new ListItem(line.text());
                if (line.kind() == Kind.ERROR) {
                    item.addClassName("elicit-result-error");
                    item.getStyle().set("color", "var(--lumo-error-text-color, hsl(3, 85%, 48%))");
                }
                list.add(item);
            } else {
                list = null;
                group.add(renderSentence(line, isError));
            }
        }
        return group;
    }

    /**
     * The sentences carry the success or failure color; the detail lines stay in body text, so
     * a long list of counts remains readable.
     */
    private static Div renderSentence(Line line, boolean isError) {
        Div sentence = new Div(line.text());
        sentence.addClassName(line.kind() == Kind.HEADING
                ? "elicit-result-heading"
                : "elicit-result-message");
        sentence.getStyle().set("color", isError
                ? "var(--lumo-error-text-color, hsl(3, 85%, 48%))"
                : "var(--lumo-success-text-color, hsl(145, 72%, 30%))");
        if (line.kind() == Kind.HEADING) {
            sentence.getStyle().set("font-weight", "600");
        }
        return sentence;
    }
}
