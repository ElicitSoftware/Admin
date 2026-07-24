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

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
