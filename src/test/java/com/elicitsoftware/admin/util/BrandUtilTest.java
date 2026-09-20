package com.elicitsoftware.admin.util;

/*-
 * ***LICENSE_START***
 * Elicit Survey
 * %%
 * Copyright (C) 2025 The Regents of the University of Michigan - Rogel Cancer Center
 * %%
 * PolyForm Noncommercial License 1.0.0
 * <https://polyformproject.org/licenses/noncommercial/1.0.0>
 * ***LICENSE_END***
 */

import com.elicitsoftware.admin.util.BrandUtil.BrandInfo;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link BrandUtil} title and logo-path helpers.
 *
 * <p>Traceability: UC-001 (Authenticate and Access the Admin Console) — once a
 * user reaches the console, the shell displays a brand-specific application
 * title and logo. These tests cover the deterministic presentation helpers
 * without touching the filesystem-based brand detection.</p>
 */
class BrandUtilTest {

    private static final String CSS = "brand-css";

    private BrandInfo embeddedBrand(String logoPath) {
        return new BrandInfo("default-brand", "Elicit", logoPath, CSS);
    }

    private BrandInfo externalBrand(String displayName, String logoPath) {
        return new BrandInfo("acme", displayName, logoPath, CSS);
    }

    /** UC-001: the embedded/default brand renders as "Elicit &lt;appType&gt;". */
    @Test
    void defaultBrandTitleUsesElicitPrefix() {
        BrandUtil util = new BrandUtil();
        assertEquals("Elicit Admin", util.getApplicationTitle(embeddedBrand("brand/images/logo.png"), "Admin"));
        assertEquals("Elicit Survey", util.getApplicationTitle(embeddedBrand("brand/images/logo.png"), "Survey"));
    }

    /** UC-001: an external brand renders as "&lt;displayName&gt; &lt;appType&gt;". */
    @Test
    void externalBrandTitleUsesDisplayName() {
        BrandUtil util = new BrandUtil();
        assertEquals("Acme Health Admin",
                util.getApplicationTitle(externalBrand("Acme Health", "brand/images/logo.png"), "Admin"));
    }

    /** UC-001: an embedded logo path is served under the /api/ prefix. */
    @Test
    void embeddedLogoPathIsServedUnderApi() throws Exception {
        BrandUtil util = new BrandUtil();
        setBrandFileSystemPath(util, "/brand");
        assertEquals("/api/brand/images/HorizontalLogo.png",
                util.getLogoResourcePath(embeddedBrand("brand/images/HorizontalLogo.png")));
    }

    /** UC-001: an external (filesystem) logo path resolves to the shared brand handler URL. */
    @Test
    void externalLogoPathIsServedByBrandHandler() throws Exception {
        BrandUtil util = new BrandUtil();
        setBrandFileSystemPath(util, "/brand");
        BrandInfo external = externalBrand("Acme Health", "/brand/images/AcmeLogo.png");
        assertEquals("/api/brand/images/HorizontalLogo.png", util.getLogoResourcePath(external));
    }

    private void setBrandFileSystemPath(BrandUtil util, String value) throws Exception {
        Field field = BrandUtil.class.getDeclaredField("brandFileSystemPath");
        field.setAccessible(true);
        field.set(util, value);
    }

    /** UC-020 BR-087: the brand's "localized" block supplies per-language display names. */
    @Test
    void localizedBlock_resolvesTagThenLanguageVariantThenBase() throws Exception {
        String json = "{\"name\": \"Health Test\", \"organization\": \"Health Test Organization\", "
                + "\"localized\": {\"es-419\": {\"organization\": \"Organizaci\u00f3n de prueba\"}, "
                + "\"ar\": {\"name\": \"\u0645\u0646\u0638\u0645\u0629\"}}}";
        Map<String, String> names = BrandUtil.extractLocalizedNames(
                new com.fasterxml.jackson.databind.ObjectMapper().readTree(json));
        BrandInfo info = new BrandInfo("health-test", "Health Test Organization", "logo.png", CSS, names);

        assertEquals("Organizaci\u00f3n de prueba", info.getDisplayName(Locale.forLanguageTag("es-419")));
        assertEquals("Organizaci\u00f3n de prueba", info.getDisplayName(Locale.forLanguageTag("es-GT")), "same-language variant");
        assertEquals("\u0645\u0646\u0638\u0645\u0629", info.getDisplayName(Locale.forLanguageTag("ar")), "name used when no organization variant");
        assertEquals("Health Test Organization", info.getDisplayName(Locale.FRENCH), "base name when the language has no variant");
        assertEquals("Health Test Organization", info.getDisplayName(null));
    }

    /** UC-020 BR-087: brands without a localized block behave exactly as before. */
    @Test
    void withoutLocalizedBlock_localeLookupReturnsBase() throws Exception {
        Map<String, String> names = BrandUtil.extractLocalizedNames(
                new com.fasterxml.jackson.databind.ObjectMapper().readTree("{\"name\": \"Plain\"}"));
        assertTrue(names.isEmpty());
        assertEquals("Elicit", embeddedBrand("logo.png").getDisplayName(Locale.forLanguageTag("ar")));
        assertTrue(embeddedBrand("logo.png").isDefaultBrand());
    }
}
