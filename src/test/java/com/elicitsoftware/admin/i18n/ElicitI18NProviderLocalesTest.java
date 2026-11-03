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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UC-026: Use the Console in My Language (BR-082, BR-083) — the languages a site offers are
 * English first and then the mounted languages the offered list keeps, in a stable order; a
 * malformed tag offers nothing, a narrowed list takes effect once the cache is cleared, and a
 * regional locale's whole bundle falls back through its language to English. Complements
 * {@code ElicitI18NProviderTest}.
 */
class ElicitI18NProviderLocalesTest {

    private static final Locale SPANISH = Locale.forLanguageTag("es-419");
    private static final Locale ARABIC = Locale.forLanguageTag("ar");

    /** The curated mount, narrowed by {@code offered} ({@code null} offers every mounted language). */
    private static ElicitI18NProvider provider(String offered, boolean pseudo) {
        ElicitI18NProvider p = new ElicitI18NProvider();
        p.fileSystemPath = ElicitI18NProviderTest.CURATED;
        p.offeredLocales = Optional.ofNullable(offered);
        p.pseudoLocaleEnabled = pseudo;
        return p;
    }

    @Test
    void given_offeredTagsInAnyOrder_when_providedLocales_then_englishFirstThenByTag() {
        ElicitI18NProvider p = provider("es-419, ar,  ,x-private,en", true);

        assertEquals(List.of(Locale.ENGLISH, ARABIC, SPANISH, ElicitI18NProvider.PSEUDO_LOCALE), p.getProvidedLocales());
        assertEquals(Locale.ENGLISH, p.getDefaultLocale());
    }

    @Test
    void given_nothingMounted_when_providedLocales_then_englishAlone(@TempDir Path empty) {
        ElicitI18NProvider p = provider(null, false);
        p.fileSystemPath = empty.toString();

        assertEquals(List.of(Locale.ENGLISH), p.getProvidedLocales());
    }

    @Test
    void given_noOfferedList_when_providedLocales_then_everyMountedLanguage() {
        assertEquals(List.of(Locale.ENGLISH, ARABIC, SPANISH), provider(null, false).getProvidedLocales());
        assertEquals(List.of(Locale.ENGLISH, ARABIC, SPANISH), provider("", false).getProvidedLocales());
    }

    @Test
    void given_aNarrowedSite_when_isProvided_then_onlyTheOfferedLocales() {
        ElicitI18NProvider p = provider("en,ar", false);

        assertTrue(p.isProvided(ARABIC));
        assertTrue(p.isProvided(Locale.ENGLISH));
        assertFalse(p.isProvided(SPANISH), "BR-083: a withheld language is not offered");
    }

    @Test
    void given_aChangedDeclaration_when_cacheCleared_then_theNewListIsOffered() {
        ElicitI18NProvider p = provider("en", false);
        assertEquals(List.of(Locale.ENGLISH), p.getProvidedLocales());
        p.offeredLocales = Optional.of("en,ar");

        assertEquals(List.of(Locale.ENGLISH), p.getProvidedLocales(), "the list is cached");
        p.clearCache();
        assertEquals(List.of(Locale.ENGLISH, ARABIC), p.getProvidedLocales());
    }

    @Test
    void given_aRegionalLocale_when_allTranslations_then_itsLanguageOverEnglish() {
        ElicitI18NProvider p = provider("en,ar", false);

        Map<String, String> egyptian = p.getAllTranslations(Locale.forLanguageTag("ar-EG"));
        Map<String, String> english = p.getAllTranslations(null);

        assertEquals("حفظ", egyptian.get("common.save"));
        assertEquals("Save", english.get("common.save"));
        assertEquals(english.keySet(), p.getAllTranslations(Locale.ENGLISH).keySet());
        assertTrue(egyptian.keySet().containsAll(english.keySet()), "BR-082: every English key is there");
    }

    @Test
    void given_aTextWithAnApostropheAndNoParameters_when_translated_then_returnedAsWritten() {
        ElicitI18NProvider p = provider("en", false);

        assertEquals("String (required) - the subject's first name",
                p.getTranslation("registerView.csvDoc.col.firstName", Locale.ENGLISH));
    }

    @Test
    void given_aNullKey_when_translated_then_empty() {
        assertEquals("", provider("en", false).getTranslation(null, Locale.ENGLISH));
    }
}
