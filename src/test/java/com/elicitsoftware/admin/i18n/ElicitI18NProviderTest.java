package com.elicitsoftware.admin.i18n;

/*-
 * ***LICENSE_START***
 * Elicit Admin
 * %%
 * Copyright (C) 2025 The Regents of the University of Michigan - Rogel Cancer Center
 * %%
 * PolyForm Noncommercial License 1.0.0
 * <https://polyformproject.org/licenses/noncommercial/1.0.0>
 * ***LICENSE_END***
 */

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

/**
 * UC-026 BR-082: the provider's classpath-only resolution (Admin#81 removed the filesystem
 * mount and local-directory tiers; {@code vaadin-i18n/translations[_tag].properties} on the
 * classpath is now the only source). {@code bundledLocales} is package-private (the BrandUtilTest
 * pattern) so it can be set directly without CDI. The English bundle is authored in
 * {@code src/main/resources}; the Arabic and Spanish (es-419) bundles are received from a
 * translator under {@code i18n/} and packaged onto the classpath by the {@code <resource>} block
 * in {@code pom.xml} — that packaging step is what {@link #bundledLocalesAndPackaging_offerRealTranslations()}
 * exists to catch if it ever breaks.
 */
class ElicitI18NProviderTest {

    private static final Locale ES_419 = Locale.forLanguageTag("es-419");
    private static final Locale AR = Locale.forLanguageTag("ar");
    private static final Locale AR_EG = Locale.forLanguageTag("ar-EG");
    private static final Locale FR = Locale.FRENCH;

    private static ElicitI18NProvider provider(String bundledLocales) {
        ElicitI18NProvider p = new ElicitI18NProvider();
        p.bundledLocales = bundledLocales;
        p.pseudoLocaleEnabled = false;
        return p;
    }

    @Test
    void unknownLanguage_fallsBackToEnglish() {
        // "fr" has no bundle at all on the classpath: exact tag misses, language-only misses
        // (same tag), so this exercises the final tier landing on English.
        ElicitI18NProvider p = provider("en");

        assertEquals("Save", p.getTranslation("common.save", FR));
    }

    @Test
    void countryLocale_fallsBackToLanguageBundle() {
        // "ar-EG" has no bundle of its own; it must fall back to translations_ar.properties
        // rather than skipping straight to English.
        ElicitI18NProvider p = provider("en,ar");

        assertEquals("حفظ", p.getTranslation("common.save", AR_EG));
    }

    @Test
    void unknownKey_rendersVisibleMarker() {
        ElicitI18NProvider p = provider("en,ar");

        assertEquals("!no.such.key!", p.getTranslation("no.such.key", Locale.ENGLISH));
        assertEquals("!no.such.key!", p.getTranslation("no.such.key", AR));
    }

    @Test
    void parameters_areFormatted_onlyWhenGiven() {
        ElicitI18NProvider p = provider("en");

        assertEquals("Duplicate entry: a subject with the external ID 9911 already exists for this department.",
                p.getTranslation("registerView.error.duplicateXid", Locale.ENGLISH, "9911"));
        assertEquals("Database error: {0}", p.getTranslation("registerView.error.database", Locale.ENGLISH),
                "no params given: the value is returned as-is, not run through MessageFormat");
    }

    @Test
    void getAllTranslations_mergesLanguageOverEnglish() {
        ElicitI18NProvider p = provider("en,ar");

        assertEquals("Register subjects", p.getAllTranslations(Locale.ENGLISH).get("registerView.pageTitle"),
                "the default locale itself gets no overlay");
        assertEquals("تسجيل المشاركين", p.getAllTranslations(AR).get("registerView.pageTitle"),
                "a non-default locale overlays its bundle onto the English base");
    }

    @Test
    void pseudoLocale_marksEveryKnownKey() {
        ElicitI18NProvider p = provider("en");
        p.pseudoLocaleEnabled = true;

        assertTrue(p.getProvidedLocales().contains(ElicitI18NProvider.PSEUDO_LOCALE));
        assertEquals("⟦common.save⟧", p.getTranslation("common.save", ElicitI18NProvider.PSEUDO_LOCALE));
        assertEquals("!no.such.key!", p.getTranslation("no.such.key", ElicitI18NProvider.PSEUDO_LOCALE));
        assertTrue(p.getAllTranslations(ElicitI18NProvider.PSEUDO_LOCALE).values().stream()
                .allMatch(v -> v.startsWith("⟦")));
    }

    @Test
    void bundledLocalesAndPackaging_offerRealTranslations() {
        // With no filesystem tier left, this is the only thing standing between a broken pom.xml
        // <resource> mapping and a silent English-only release: it reads the real packaged
        // translations_ar.properties / translations_es_419.properties, not a fixture.
        ElicitI18NProvider p = provider("en,ar,es-419");

        List<Locale> locales = p.getProvidedLocales();
        assertEquals(Locale.ENGLISH, locales.get(0), "English sorts first");
        assertTrue(locales.contains(AR), "ar must be offered: " + locales);
        assertTrue(locales.contains(ES_419), "es-419 must be offered: " + locales);

        assertEquals("تسجيل المشاركين", p.getTranslation("registerView.pageTitle", AR));
        assertEquals("Registrar sujetos", p.getTranslation("registerView.pageTitle", ES_419));
    }

    @Test
    void bundledLocales_narrowsOfferedLocales_butNotWhatIsLoadable() {
        // A site narrows i18n.bundled.locales to hide a language from the selector; the bundle
        // stays in the image either way (classpath resources cannot be deleted per-deployment).
        ElicitI18NProvider p = provider("en,ar,es-419");
        assertTrue(p.getProvidedLocales().contains(AR), "sanity: starts wide");

        p.bundledLocales = "en";
        p.clearCache(); // getProvidedLocales() caches in providedLocales; must clear to re-discover

        List<Locale> locales = p.getProvidedLocales();
        assertEquals(List.of(Locale.ENGLISH), locales, "narrowed site offers only English: " + locales);
        assertEquals("تسجيل المشاركين", p.getTranslation("registerView.pageTitle", AR),
                "the ar bundle is still on the classpath -- narrowing only trims what is offered");
    }
}
