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
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.RouterLink;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UC-019: Surface Missing Survey Definition and UC-021: Warn About Default Accounts — the
 * console notices built by {@link MissingSurveyNotice} and {@link DefaultAccountNotice}, in a
 * plain browserless UI. Covers the informational {@code role=status} announcement (BR-077) and
 * the remedy offered as a link only to those who can carry it out (BR-078).
 */
class ConsoleNoticesUnitTest extends PlainSmallViewTestSupport {

    private static List<Component> children(Component c) {
        return c.getChildren().toList();
    }

    @Test
    void given_administrator_when_bannerBuilt_then_itWarnsAndLinksToApplySurveyDefinition() {
        Component banner = MissingSurveyNotice.banner(true);

        assertEquals(MissingSurveyNotice.BANNER_ID, banner.getId().orElseThrow());
        assertTrue(banner.hasClassName("console-notice"));
        assertEquals("100%", banner.getElement().getStyle().get("width"));
        assertEquals("status", banner.getElement().getAttribute("role"));
        List<Component> parts = children(banner);
        assertEquals(3, parts.size());
        assertInstanceOf(Icon.class, parts.get(0));
        assertEquals(t("missingSurveyNotice.headline"), ((Span) parts.get(1)).getText());
        RouterLink remedy = assertInstanceOf(RouterLink.class, parts.get(2));
        assertEquals(t("missingSurveyNotice.adminRemedy"), remedy.getText());
        assertEquals("survey-apply", remedy.getHref());
    }

    @Test
    void given_nonAdministrator_when_bannerBuilt_then_remedyIsPlainText() {
        List<Component> parts = children(MissingSurveyNotice.banner(false));

        Span remedy = assertInstanceOf(Span.class, parts.get(2));
        assertEquals(t("missingSurveyNotice.nonAdminRemedy"), remedy.getText());
    }

    @Test
    void given_administrator_when_emptyStateBuilt_then_headlineAndLinkAreStacked() {
        VerticalLayout empty = (VerticalLayout) MissingSurveyNotice.emptyState(true);

        assertEquals(MissingSurveyNotice.EMPTY_STATE_ID, empty.getId().orElseThrow());
        assertTrue(empty.isPadding());
        assertFalse(empty.isSpacing());
        assertEquals("status", empty.getElement().getAttribute("role"));
        List<Component> parts = children(empty);
        assertEquals(2, parts.size());
        Span headline = (Span) parts.get(0);
        assertEquals(t("missingSurveyNotice.headline"), headline.getText());
        assertTrue(headline.hasClassName("console-empty-state-headline"));
        assertInstanceOf(RouterLink.class, parts.get(1));
    }

    @Test
    void given_nonAdministrator_when_emptyStateBuilt_then_remedyIsPlainText() {
        List<Component> parts = children(MissingSurveyNotice.emptyState(false));

        assertEquals(t("missingSurveyNotice.nonAdminRemedy"), ((Span) parts.get(1)).getText());
    }

    @Test
    void given_oneSeededAccount_when_bannerBuilt_then_itNamesTheAccountAndLinksToUsers() {
        Component banner = DefaultAccountNotice.banner(List.of("admin"));

        assertEquals(DefaultAccountNotice.BANNER_ID, banner.getId().orElseThrow());
        assertTrue(banner.hasClassName("console-notice"));
        assertEquals("100%", banner.getElement().getStyle().get("width"));
        assertEquals("status", banner.getElement().getAttribute("role"));
        List<Component> parts = children(banner);
        assertEquals(3, parts.size());
        assertInstanceOf(Icon.class, parts.get(0));
        assertEquals(t("defaultAccountNotice.instruction.one", "'admin'") + " ", ((Span) parts.get(1)).getText());
        RouterLink users = (RouterLink) parts.get(2);
        assertEquals(t("defaultAccountNotice.openUsers"), users.getText());
        assertEquals("users", users.getHref());
    }

    @Test
    void given_noAccounts_when_instructionAsked_then_itIsEmpty() {
        assertEquals("", DefaultAccountNotice.instruction(List.of()));
    }

    @Test
    void given_twoAccounts_when_instructionAsked_then_bothAreNamed() {
        assertEquals(t("defaultAccountNotice.instruction.many", t("common.listAnd", "'admin'", "'user'")),
                DefaultAccountNotice.instruction(List.of("admin", "user")));
    }
}
