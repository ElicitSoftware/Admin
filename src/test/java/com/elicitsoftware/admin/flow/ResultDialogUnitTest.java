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

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.ModalityMode;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.ListItem;
import com.vaadin.flow.component.html.UnorderedList;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UC-012: Import Respondent Data and UC-018: Apply Survey Definition — the outcome dialog both
 * screens open, {@link ResultDialog}, rendered in a plain browserless UI: sentences, headings and
 * detail lists laid out as structure, error lines marked, success and failure coloured apart.
 */
class ResultDialogUnitTest extends PlainSmallViewTestSupport {

    private static final String ERROR_COLOR = "var(--lumo-error-text-color, hsl(3, 85%, 48%))";
    private static final String SUCCESS_COLOR = "var(--lumo-success-text-color, hsl(145, 72%, 30%))";

    private static VerticalLayout layout(ResultDialog dialog) {
        return (VerticalLayout) dialog.getChildren().findFirst().orElseThrow();
    }

    private static Div summary(ResultDialog dialog) {
        return (Div) layout(dialog).getComponentAt(0);
    }

    private static List<Div> groups(ResultDialog dialog) {
        return summary(dialog).getChildren().map(Div.class::cast).toList();
    }

    @Test
    void given_lineFactories_when_called_then_eachCarriesItsKind() {
        assertEquals(new ResultDialog.Line("a", ResultDialog.Kind.MESSAGE), ResultDialog.Line.message("a"));
        assertEquals(new ResultDialog.Line("b", ResultDialog.Kind.HEADING), ResultDialog.Line.heading("b"));
        assertEquals(new ResultDialog.Line("c", ResultDialog.Kind.DETAIL), ResultDialog.Line.detail("c"));
        assertEquals(new ResultDialog.Line("d", ResultDialog.Kind.ERROR), ResultDialog.Line.error("d"));
    }

    @Test
    void given_mutableList_when_sectionBuilt_then_itKeepsAnUnmodifiableCopy() {
        List<ResultDialog.Line> lines = new ArrayList<>(List.of(ResultDialog.Line.message("x")));
        ResultDialog.Section section = new ResultDialog.Section(lines);
        lines.add(ResultDialog.Line.message("y"));

        assertEquals(1, section.lines().size());
        assertThrows(UnsupportedOperationException.class, () -> section.lines().add(ResultDialog.Line.message("z")));
        assertEquals(List.of(ResultDialog.Line.detail("p")), ResultDialog.Section.of(ResultDialog.Line.detail("p")).lines());
    }

    @Test
    void given_successMessage_when_built_then_dialogIsStrictAndTheSentenceIsGreen() {
        ResultDialog dialog = new ResultDialog("Done", "All good", false);

        assertEquals("Done", dialog.getHeaderTitle());
        assertEquals(ModalityMode.STRICT, dialog.getModality());
        assertFalse(dialog.isDraggable());
        assertTrue(dialog.isResizable());
        assertEquals("600px", dialog.getWidth());
        assertEquals("90vw", dialog.getMaxWidth());

        VerticalLayout layout = layout(dialog);
        assertEquals(FlexComponent.Alignment.CENTER, layout.getAlignItems());
        assertTrue(layout.isSpacing());
        Div summary = summary(dialog);
        assertTrue(summary.hasClassName("elicit-result-summary"));
        assertEquals("start", summary.getStyle().get("text-align"));
        Button close = (Button) layout.getComponentAt(1);
        assertEquals(t("common.close"), close.getText());
        assertTrue(close.getThemeNames().contains(ButtonVariant.LUMO_PRIMARY.getVariantName()));

        List<Div> groups = groups(dialog);
        assertEquals(1, groups.size());
        assertTrue(groups.get(0).hasClassName("elicit-result-section"));
        assertNull(groups.get(0).getStyle().get("margin-top"), "the first section is not spaced from above");
        Div sentence = (Div) groups.get(0).getComponentAt(0);
        assertEquals("All good", sentence.getText());
        assertTrue(sentence.hasClassName("elicit-result-message"));
        assertEquals(SUCCESS_COLOR, sentence.getStyle().get("color"));
        assertNull(sentence.getStyle().get("font-weight"));
    }

    @Test
    void given_nullMessage_when_built_then_anEmptySentenceIsShown() {
        ResultDialog dialog = new ResultDialog("Failed", (String) null, true);

        Div sentence = (Div) groups(dialog).get(0).getComponentAt(0);
        assertEquals("", sentence.getText());
        assertEquals(ERROR_COLOR, sentence.getStyle().get("color"));
    }

    @Test
    void given_mixedSections_when_built_then_headingsDetailsAndErrorsAreLaidOutInOrder() {
        ResultDialog dialog = new ResultDialog("Imported", List.of(
                ResultDialog.Section.of(ResultDialog.Line.message("Imported 3")),
                new ResultDialog.Section(List.of(
                        ResultDialog.Line.heading("Counts"),
                        ResultDialog.Line.detail("a: 1"),
                        ResultDialog.Line.error("bad"),
                        ResultDialog.Line.message("between"),
                        ResultDialog.Line.detail("b: 2")))), false);

        List<Div> groups = groups(dialog);
        assertEquals(2, groups.size());
        assertEquals("var(--vaadin-gap-m, 12px)", groups.get(1).getStyle().get("margin-top"));

        List<Component> parts = groups.get(1).getChildren().toList();
        assertEquals(4, parts.size(), "heading, list, sentence, a new list");
        Div heading = (Div) parts.get(0);
        assertEquals("Counts", heading.getText());
        assertTrue(heading.hasClassName("elicit-result-heading"));
        assertFalse(heading.hasClassName("elicit-result-message"));
        assertEquals("600", heading.getStyle().get("font-weight"));
        assertEquals(SUCCESS_COLOR, heading.getStyle().get("color"));

        UnorderedList first = assertInstanceOf(UnorderedList.class, parts.get(1));
        assertTrue(first.hasClassName("elicit-result-items"));
        assertEquals("0", first.getStyle().get("margin-block"));
        List<ListItem> items = first.getChildren().map(ListItem.class::cast).toList();
        assertEquals(2, items.size(), "consecutive detail and error lines share one list");
        assertEquals("a: 1", items.get(0).getText());
        assertFalse(items.get(0).hasClassName("elicit-result-error"));
        assertNull(items.get(0).getStyle().get("color"));
        assertEquals("bad", items.get(1).getText());
        assertTrue(items.get(1).hasClassName("elicit-result-error"));
        assertEquals(ERROR_COLOR, items.get(1).getStyle().get("color"));

        assertEquals("between", ((Div) parts.get(2)).getText());
        UnorderedList second = assertInstanceOf(UnorderedList.class, parts.get(3));
        assertEquals(List.of("b: 2"), second.getChildren().map(c -> ((ListItem) c).getText()).toList());
    }

    @Test
    void given_errorDialog_when_headingRendered_then_itIsRed() {
        ResultDialog dialog = new ResultDialog("Failed",
                List.of(ResultDialog.Section.of(ResultDialog.Line.heading("Errors:"))), true);

        Div heading = (Div) groups(dialog).get(0).getComponentAt(0);
        assertEquals(ERROR_COLOR, heading.getStyle().get("color"));
    }

    @Test
    void given_openDialog_when_closeClicked_then_itCloses() {
        ResultDialog dialog = new ResultDialog("Done", "ok", false);
        dialog.open();
        roundTrip();

        test((Button) layout(dialog).getComponentAt(1)).click();

        assertFalse(dialog.isOpened());
    }
}
