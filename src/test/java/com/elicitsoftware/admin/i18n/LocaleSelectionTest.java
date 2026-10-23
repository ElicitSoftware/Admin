package com.elicitsoftware.admin.i18n;

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

import com.elicitsoftware.admin.i18n.PlainVaadin.PlainUi;
import com.elicitsoftware.admin.i18n.PlainVaadin.StubSession;
import com.vaadin.flow.component.UI;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * UC-026: Use the Console in My Language, steps 4 and 5 (BR-083, BR-084, BR-088) — resolving a
 * requested language against what the site offers, and remembering the administrator's choice in
 * the Vaadin session only while applying its texts and layout to the UI.
 * <p>
 * Plain: the UI and session are {@link PlainVaadin} stand-ins and the layout is a recording stub.
 */
class LocaleSelectionTest {

    private static final Locale SPANISH = Locale.forLanguageTag("es-419");
    private static final Locale ARABIC = Locale.forLanguageTag("ar");

    private LocaleSelection selection;
    private final List<Locale> laidOut = new ArrayList<>();
    private final List<UI> laidOutUis = new ArrayList<>();
    private PlainUi ui;

    @BeforeEach
    void setUp() {
        ElicitI18NProvider provider = new ElicitI18NProvider();
        provider.bundledLocales = "en,es-419,ar";
        selection = new LocaleSelection();
        selection.provider = provider;
        selection.layout = new LocaleLayout() {
            @Override
            public void apply(UI target, Locale locale) {
                laidOutUis.add(target);
                laidOut.add(locale);
            }
        };
        ui = PlainUi.of(Locale.ENGLISH, new StubSession());
    }

    @AfterEach
    void tearDown() {
        PlainVaadin.clear();
    }

    @Test
    void given_noTag_when_resolving_then_empty() {
        assertEquals(Optional.empty(), selection.resolve(null));
        assertEquals(Optional.empty(), selection.resolve(""));
        assertEquals(Optional.empty(), selection.resolve("   "));
    }

    @Test
    void given_aTagWithoutALanguage_when_resolving_then_empty() {
        assertEquals(Optional.empty(), selection.resolve("x-private"));
    }

    @Test
    void given_anOfferedTag_when_resolving_then_thatLocale() {
        assertEquals(Optional.of(SPANISH), selection.resolve(" es-419 "));
        assertEquals(Optional.of(ARABIC), selection.resolve("ar"));
        assertEquals(Optional.of(Locale.ENGLISH), selection.resolve("en"));
    }

    @Test
    void given_anotherRegionOfAnOfferedLanguage_when_resolving_then_theOfferedLocale() {
        assertEquals(Optional.of(SPANISH), selection.resolve("es-MX"));
        assertEquals(Optional.of(ARABIC), selection.resolve("ar-EG"));
    }

    @Test
    void given_aLanguageNotOffered_when_resolving_then_empty() {
        assertEquals(Optional.empty(), selection.resolve("fr"), "BR-083: a withheld language is unreachable");
    }

    @Test
    void given_aLanguage_when_applying_then_rememberedForTheSessionAndApplied() {
        selection.apply(ui, ARABIC);

        assertEquals(ARABIC, ui.getSession().getAttribute(LocaleSelection.SESSION_ATTRIBUTE));
        assertEquals(ARABIC, ui.getLocale());
        assertEquals(List.of(ARABIC), laidOut);
        assertSame(ui, laidOutUis.getFirst());
    }

    @Test
    void given_aUiWithoutSession_when_applying_then_appliedWithoutRemembering() {
        ui.session = null;

        selection.apply(ui, SPANISH);

        assertEquals(SPANISH, ui.getLocale());
        assertEquals(List.of(SPANISH), laidOut);
    }

    @Test
    void given_aSessionWithAChoice_when_stored_then_thatChoice() {
        StubSession session = new StubSession();
        session.setAttribute(LocaleSelection.SESSION_ATTRIBUTE, SPANISH);

        assertEquals(Optional.of(SPANISH), selection.stored(session));
    }

    @Test
    void given_noSessionOrNoChoice_when_stored_then_empty() {
        StubSession session = new StubSession();

        assertEquals(Optional.empty(), selection.stored(null));
        assertEquals(Optional.empty(), selection.stored(session));
        session.setAttribute(LocaleSelection.SESSION_ATTRIBUTE, "ar");
        assertEquals(Optional.empty(), selection.stored(session), "only a Locale counts as a choice");
    }
}
