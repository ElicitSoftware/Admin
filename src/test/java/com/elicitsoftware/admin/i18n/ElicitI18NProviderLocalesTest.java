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

import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UC-026: Use the Console in My Language (BR-082, BR-083) — the languages a site offers are
 * English first and then the declared tags in a stable order, a malformed tag offers nothing, a
 * narrowed list takes effect once the cache is cleared, and a regional locale's whole bundle
 * falls back through its language to English. Complements {@code ElicitI18NProviderTest}.
 */
class ElicitI18NProviderLocalesTest {

    private static final Locale SPANISH = Locale.forLanguageTag("es-419");
    private static final Locale ARABIC = Locale.forLanguageTag("ar");

    private static ElicitI18NProvider provider(String bundledLocales, boolean pseudo) {
        ElicitI18NProvider p = new ElicitI18NProvider();
        p.bundledLocales = bundledLocales;
        p.pseudoLocaleEnabled = pseudo;
        return p;
    }

    @Test
    void given_declaredTagsInAnyOrder_when_providedLocales_then_englishFirstThenByTag() {
        ElicitI18NProvider p = provider("es-419, ar,  ,x-private,en", true);

        assertEquals(List.of(Locale.ENGLISH, ARABIC, SPANISH, ElicitI18NProvider.PSEUDO_LOCALE), p.getProvidedLocales());
        assertEquals(Locale.ENGLISH, p.getDefaultLocale());
    }

    @Test
    void given_noDeclaredTags_when_providedLocales_then_englishAlone() {
        assertEquals(List.of(Locale.ENGLISH), provider(null, false).getProvidedLocales());
        assertEquals(List.of(Locale.ENGLISH), provider("", false).getProvidedLocales());
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
        p.bundledLocales = "en,ar";

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
