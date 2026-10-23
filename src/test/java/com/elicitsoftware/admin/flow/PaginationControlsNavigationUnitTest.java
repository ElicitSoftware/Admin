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
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.select.SelectVariant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UC-002: Search and Monitor Subject Progress — paging through results with the buttons and the
 * page-size picker of {@link PaginationControls}, in a plain browserless UI. Complements
 * {@link PaginationControlsTest}, which covers the arithmetic and the styling classes without a UI.
 */
class PaginationControlsNavigationUnitTest extends PlainSmallViewTestSupport {

    private PaginationControls controls;
    private final AtomicInteger changes = new AtomicInteger();

    @BeforeEach
    void build() {
        controls = new PaginationControls();
        UI.getCurrent().add(controls);
        controls.onPageChanged(changes::incrementAndGet);
        controls.recalculatePageCount(45);
        changes.set(0);
    }

    private static Stream<Component> subtree(Component root) {
        return Stream.concat(Stream.of(root), root.getChildren().flatMap(PaginationControlsNavigationUnitTest::subtree));
    }

    private Button button(String labelKey) {
        String label = t(labelKey);
        return subtree(controls).filter(Button.class::isInstance).map(Button.class::cast)
                .filter(b -> b.getAriaLabel().filter(label::equals).isPresent()).findFirst().orElseThrow();
    }

    @SuppressWarnings("unchecked")
    private Select<Integer> pageSize() {
        return subtree(controls).filter(Select.class::isInstance).map(c -> (Select<Integer>) c).findFirst().orElseThrow();
    }

    private Span pageLabel() {
        return subtree(controls).filter(Span.class::isInstance).map(Span.class::cast)
                .filter(sp -> sp.hasClassName("pagination-page-label")).findFirst().orElseThrow();
    }

    private void assertEnabled(boolean first, boolean previous, boolean next, boolean last) {
        assertEquals(List.of(first, previous, next, last), List.of(
                button("paginationControls.firstPage").isEnabled(), button("paginationControls.previousPage").isEnabled(),
                button("paginationControls.nextPage").isEnabled(), button("paginationControls.lastPage").isEnabled()));
    }

    @Test
    void given_controls_when_built_then_layoutPickerAndButtonsAreConfigured() {
        assertEquals(FlexComponent.Alignment.CENTER, controls.getDefaultVerticalComponentAlignment());
        assertEquals("100%", controls.getWidth());
        assertEquals("0.3rem", controls.getElement().getStyle().get("gap"));

        Select<Integer> select = pageSize();
        assertEquals(10, select.getValue());
        assertTrue(select.getThemeNames().contains(SelectVariant.LUMO_SMALL.getVariantName()));
        assertTrue(select.hasClassName("pagination-page-size"));
        assertEquals("4.8rem", select.getWidth());
        assertEquals(java.util.Optional.of("page-size-label"), select.getAriaLabelledBy());
        Span label = subtree(controls).filter(Span.class::isInstance).map(Span.class::cast)
                .filter(sp -> sp.getId().filter("page-size-label"::equals).isPresent()).findFirst().orElseThrow();
        assertEquals(t("paginationControls.pageSize"), label.getText());
        HorizontalLayout picker = (HorizontalLayout) select.getParent().orElseThrow();
        assertFalse(picker.isSpacing());
        assertTrue(picker.getThemeList().contains("spacing-s"));

        Button next = button("paginationControls.nextPage");
        assertTrue(next.getThemeNames().contains(ButtonVariant.LUMO_ICON.getVariantName()));
        assertTrue(next.getThemeNames().contains(ButtonVariant.LUMO_SMALL.getVariantName()));
        assertEquals(t("paginationControls.pageOf", 1, 5), pageLabel().getText());
        assertEnabled(false, false, true, true);
    }

    @Test
    void given_secondToLastPage_when_reached_then_everyButtonIsEnabled() {
        test(button("paginationControls.lastPage")).click();
        test(button("paginationControls.previousPage")).click();

        assertEquals(t("paginationControls.pageOf", 4, 5), pageLabel().getText());
        assertEnabled(true, true, true, true);
    }

    @Test
    void given_laterPage_when_firstClicked_then_firstPageIsShown() {
        test(button("paginationControls.lastPage")).click();

        test(button("paginationControls.firstPage")).click();

        assertEquals(0, controls.calculateOffset());
        assertEquals(t("paginationControls.pageOf", 1, 5), pageLabel().getText());
        assertEnabled(false, false, true, true);
    }

    @Test
    void given_firstPage_when_nextClicked_then_secondPageIsShownAndListenersAreTold() {
        test(button("paginationControls.nextPage")).click();

        assertEquals(10, controls.calculateOffset());
        assertEquals(1, changes.get());
        assertTrue(button("paginationControls.previousPage").isEnabled());
        assertTrue(button("paginationControls.firstPage").isEnabled());
    }

    @Test
    void given_thirdPage_when_previousClicked_then_secondPageIsShown() {
        test(button("paginationControls.nextPage")).click();
        test(button("paginationControls.nextPage")).click();

        test(button("paginationControls.previousPage")).click();

        assertEquals(10, controls.calculateOffset());
        assertEquals(3, changes.get());
    }

    @Test
    void given_lastPage_when_reached_then_forwardButtonsAreDisabled() {
        test(button("paginationControls.lastPage")).click();

        assertEquals(40, controls.calculateOffset());
        assertEquals(t("paginationControls.pageOf", 5, 5), pageLabel().getText());
        assertEnabled(true, true, false, false);
    }

    @Test
    void given_lastPage_when_pageSizeRaised_then_pageIsClampedToTheNewLastPage() {
        test(button("paginationControls.lastPage")).click();

        pageSize().setValue(25);

        assertEquals(25, controls.getPageSize());
        assertEquals(25, controls.calculateOffset(), "45 items at 25 per page: the last page is the second");
        assertEquals(t("paginationControls.pageOf", 2, 2), pageLabel().getText());
        assertEquals(List.of(5, 10, 15, 25, 50, 100), pageSize().getListDataView().getItems().toList());
    }
}
