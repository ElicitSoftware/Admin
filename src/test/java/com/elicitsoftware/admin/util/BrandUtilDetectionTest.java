package com.elicitsoftware.admin.util;

/*-
 * ***LICENSE_START***
 * Elicit Admin
 * %%
 * Copyright (C) 2025 - 2026 Elicit Software LLC
 * %%
 * PolyForm Noncommercial License 1.0.0
 * <https://polyformproject.org/licenses/noncommercial/1.0.0>
 * ***LICENSE_END***
 */

import com.elicitsoftware.admin.util.BrandUtil.BrandInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UC-023: Diagnose Branding (BR-092) — the brand the console renders is detected from the
 * {@code brand-config.json} of the mounted directory, then of a classpath directory of the same
 * name, then the embedded default; the result is cached until it is cleared. The mounted
 * directory is a temporary directory of the test's making.
 */
class BrandUtilDetectionTest {

    @TempDir
    Path mount;

    private BrandUtil util;

    @BeforeEach
    void setUp() {
        util = new BrandUtil();
        util.brandFileSystemPath = mount.toString();
    }

    private void config(String json) throws IOException {
        Files.writeString(mount.resolve("brand-config.json"), json);
    }

    private BrandInfo detect(String json) throws IOException {
        config(json);
        util.clearCache();
        return util.detectCurrentBrand();
    }

    @Test
    void given_aMountedConfig_when_detect_then_itsNamesLogoAndKey() throws IOException {
        BrandInfo brand = detect("{\"name\": \"  Acme Health & Co.  \", \"organization\": \"Acme Organization\","
                + " \"logos\": {\"horizontal\": \"Wide.png\", \"primary\": \"Square.png\"},"
                + " \"localized\": {\"es-419\": {\"name\": \"Acme ES\"}}}");

        assertEquals("acme-health-co", brand.getBrandKey());
        assertEquals("acme-health-co", brand.getCssClass());
        assertEquals("Acme Organization", brand.getDisplayName(), "the organization is the display name");
        assertEquals("brand/images/Wide.png", brand.getLogoPath());
        assertEquals("Acme ES", brand.getDisplayName(Locale.forLanguageTag("es-419")));
        assertEquals("Acme Organization", brand.getDisplayName(Locale.FRENCH));
        assertEquals(false, brand.isDefaultBrand(), "a mounted brand is not the default brand");
    }

    @Test
    void given_aLanguageOnlyVariant_when_displayName_then_usedForEveryRegion() throws IOException {
        BrandInfo brand = detect("{\"name\": \"Acme\", \"localized\": {\"ES\": {\"name\": \"Acme Espa\u00f1ol\"}}}");

        assertEquals("Acme Espa\u00f1ol", brand.getDisplayName(Locale.forLanguageTag("es-MX")));
        assertEquals("Acme Espa\u00f1ol", brand.getDisplayName(Locale.forLanguageTag("es")));
    }

    @Test
    void given_noOrganization_when_detect_then_theNameIsShown() throws IOException {
        assertEquals("Acme", detect("{\"name\": \"Acme\"}").getDisplayName());
        assertEquals("Nested", detect("{\"brand\": {\"name\": \"Nested\"}}").getDisplayName());
        BrandInfo unnamed = detect("{\"brand\": {\"version\": \"1\"}}");
        assertEquals("External Brand", unnamed.getDisplayName());
        assertEquals("external-brand", unnamed.getBrandKey());
    }

    @Test
    void given_aNameOfHyphensAndSpaces_when_detect_then_theKeyIsCollapsedAndTrimmed() throws IOException {
        assertEquals("hello-world", detect("{\"name\": \"  --Hello   World--  \"}").getBrandKey());
        assertEquals("a-b", detect("{\"name\": \"A - B\"}").getBrandKey());
    }

    @Test
    void given_logosOfEachShape_when_detect_then_horizontalThenPrimaryThenTheFirstThenTheDefault() throws IOException {
        assertEquals("brand/images/Square.png", detect("{\"logos\": {\"icon\": \"I.png\", \"primary\": \"Square.png\"}}").getLogoPath());
        assertEquals("brand/images/I.png", detect("{\"logos\": {\"icon\": \"I.png\", \"other\": \"O.png\"}}").getLogoPath());
        assertEquals("brand/images/HorizontalLogo.png", detect("{\"logos\": {}}").getLogoPath());
        assertEquals("brand/images/HorizontalLogo.png", detect("{\"name\": \"x\"}").getLogoPath());
    }

    @Test
    void given_aDetectedBrand_when_detectedAgain_then_cachedUntilCleared() throws IOException {
        BrandInfo first = detect("{\"name\": \"First\"}");
        config("{\"name\": \"Second\"}");

        assertSame(first, util.detectCurrentBrand(), "the brand is cached");

        util.clearCache();
        BrandInfo second = util.detectCurrentBrand();
        assertNotSame(first, second);
        assertEquals("Second", second.getDisplayName());
    }

    @Test
    void given_anUnreadableConfig_when_detect_then_theEmbeddedDefault() throws IOException {
        BrandInfo brand = detect("{ not json");

        assertEquals("Elicit Software", brand.getDisplayName());
        assertEquals("brand/images/HorizontalLogo.png", brand.getLogoPath());
        assertEquals("Elicit Software", brand.getDisplayName(Locale.forLanguageTag("es-419")),
                "the embedded brand's localized organization");
    }

    @Test
    void given_noMountedConfig_when_detect_then_theEmbeddedDefault() {
        util.brandFileSystemPath = mount.resolve("absent").toString();

        BrandInfo brand = util.detectCurrentBrand();

        assertEquals("Elicit Software", brand.getDisplayName());
        assertEquals("brand/images/HorizontalLogo.png", brand.getLogoPath());
        assertSame(brand, util.detectCurrentBrand(), "the fallback is cached too");
    }

    @Test
    void given_aClasspathDirectoryOfTheConfiguredName_when_detect_then_itsConfig() {
        util.brandFileSystemPath = "META-INF/brand";

        BrandInfo brand = util.detectCurrentBrand();

        assertEquals("elicit-default-brand", brand.getBrandKey());
        assertEquals("Elicit Software", brand.getDisplayName());
    }

    private void image(String file) throws IOException {
        Files.createDirectories(mount.resolve("images"));
        Files.writeString(mount.resolve("images").resolve(file), "image");
    }

    /** Umbrella UC-014 BR-008: the header icon is the file logos.icon names, served by the brand handler. */
    @Test
    void given_aMountNamingItsIcon_when_iconResourcePath_then_thatFile() throws IOException {
        config("{\"name\": \"Acme\", \"logos\": {\"icon\": \"mark.svg\"}, \"favicon\": \"tab.svg\"}");
        image("mark.svg");
        image("tab.svg");

        assertEquals("/api/brand/images/mark.svg", util.getIconResourcePath(util.detectCurrentBrand()));
        assertEquals(new BrandUtil.LogoFile("mark.svg", true), util.getLogoFile(BrandUtil.Logo.ICON));
        assertEquals("/api/brand/images/tab.svg", util.getLogoUrl(BrandUtil.Logo.FAVICON));
        assertFalse(util.isIconMissingBesideFavicon());
        assertEquals(List.of(), util.getMissingLogoFiles());
    }

    /** Umbrella UC-014 BR-008: the lockup is the file logos.horizontal names. */
    @Test
    void given_aMountNamingItsLockup_when_logoResourcePath_then_thatFile() throws IOException {
        config("{\"name\": \"Acme\", \"logos\": {\"horizontal\": \"Wide.png\"}}");
        image("Wide.png");

        assertEquals("/api/brand/images/Wide.png", util.getLogoResourcePath(util.detectCurrentBrand()));
    }

    /** Umbrella UC-014 BR-008: with nothing mounted every logo is the default brand's file. */
    @Test
    void given_noMount_when_logos_then_theDefaultBrandsFiles() {
        assertEquals("/api/brand/images/HorizontalLogo.png", util.getLogoUrl(BrandUtil.Logo.HORIZONTAL));
        assertEquals(new BrandUtil.LogoFile("icon-white.png", false), util.getLogoFile(BrandUtil.Logo.ICON));
        assertEquals("/api/brand/images/favicon.ico", util.getLogoUrl(BrandUtil.Logo.FAVICON));
        assertFalse(util.isIconMissingBesideFavicon());
    }

    /** Umbrella UC-014 BR-009: a mounted favicon without a mounted icon leaves the default mark, and says so. */
    @Test
    void given_aMountedFaviconWithoutAnIcon_when_checked_then_theIconIsMissing() throws IOException {
        config("{\"name\": \"Acme\", \"favicon\": \"favicon.svg\"}");
        image("favicon.svg");

        assertEquals("/api/brand/images/icon-white.png", util.getIconResourcePath(util.detectCurrentBrand()));
        assertTrue(util.isIconMissingBesideFavicon());
    }

    /** Umbrella UC-014 BR-002: a file the mount holds under the default name counts as mounted. */
    @Test
    void given_aMountHoldingTheDefaultIconName_when_resolved_then_itIsMounted() throws IOException {
        config("{\"name\": \"Acme\", \"favicon\": \"favicon.svg\"}");
        image("favicon.svg");
        image("icon-white.png");

        assertEquals(new BrandUtil.LogoFile("icon-white.png", true), util.getLogoFile(BrandUtil.Logo.ICON));
        assertFalse(util.isIconMissingBesideFavicon());
    }

    /** Umbrella UC-014 BR-008: a named file the mount lacks falls back to the default and is reported. */
    @Test
    void given_aNamedIconTheMountLacks_when_resolved_then_theDefaultAndItIsReported() throws IOException {
        config("{\"name\": \"Acme\", \"logos\": {\"icon\": \"mark.svg\"}}");

        assertEquals(new BrandUtil.LogoFile("icon-white.png", false), util.getLogoFile(BrandUtil.Logo.ICON));
        assertEquals(List.of("images/mark.svg"), util.getMissingLogoFiles());
    }

    /** A configured name that climbs out of images/ is not a file of the brand. */
    @Test
    void given_aNameOutsideImages_when_resolved_then_itIsIgnored() throws IOException {
        config("{\"name\": \"Acme\", \"logos\": {\"icon\": \"../brand-config.json\"}}");

        assertEquals("/api/brand/images/icon-white.png", util.getIconResourcePath(util.detectCurrentBrand()));
    }

    /** Umbrella UC-014 BR-006: the logos are cached until the brand is cleared. */
    @Test
    void given_aChangedMount_when_notCleared_then_theCachedLogos() throws IOException {
        assertEquals("/api/brand/images/icon-white.png", util.getLogoUrl(BrandUtil.Logo.ICON));
        config("{\"name\": \"Acme\", \"logos\": {\"icon\": \"mark.svg\"}}");
        image("mark.svg");

        assertEquals("/api/brand/images/icon-white.png", util.getLogoUrl(BrandUtil.Logo.ICON));
        util.clearCache();
        assertEquals("/api/brand/images/mark.svg", util.getLogoUrl(BrandUtil.Logo.ICON));
    }
}
