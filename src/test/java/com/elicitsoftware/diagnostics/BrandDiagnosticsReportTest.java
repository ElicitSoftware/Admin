package com.elicitsoftware.diagnostics;

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

import com.elicitsoftware.admin.util.BrandUtil;
import com.elicitsoftware.diagnostics.BrandDiagnostics.AssetReport;
import com.elicitsoftware.diagnostics.BrandDiagnostics.BrandReport;
import com.elicitsoftware.diagnostics.BrandDiagnostics.Source;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UC-023: Diagnose Branding (BR-091, BR-092) — which brand directory resolved, the metadata it
 * carries, and where each expected asset came from, every asset on its own, so a partial mount
 * is visible. The two brand directories are temporary directories of the test's making; the
 * embedded tier is the default brand packaged under {@code META-INF/brand}.
 */
class BrandDiagnosticsReportTest {

    private static final List<String> ASSETS = List.of("colors/brand-colors.css", "typography/brand-typography.css",
            "theme.css", "images/HorizontalLogo.png", "images/icon-white.png", "images/favicon.ico");

    @TempDir
    Path temp;

    private Path external;
    private Path local;
    private final BrandUtil.BrandInfo inUse = new BrandUtil.BrandInfo("acme", "Acme", "logo.png", "acme");
    private int cleared;
    private BrandDiagnostics diagnostics;

    @BeforeEach
    void setUp() {
        external = temp.resolve("mounted");
        local = temp.resolve("local");
        diagnostics = new BrandDiagnostics();
        diagnostics.brandFileSystemPath = external.toString();
        diagnostics.brandLocalPath = local.toString();
        diagnostics.brandUtil = new BrandUtil() {
            @Override
            public BrandInfo detectCurrentBrand() {
                return inUse;
            }

            @Override
            public void clearCache() {
                cleared++;
            }
        };
        setBrandPath(diagnostics.brandUtil, external);
    }

    /** The logos are resolved by the real {@link BrandUtil} against the test's mount. */
    private static void setBrandPath(BrandUtil util, Path path) {
        try {
            Field field = BrandUtil.class.getDeclaredField("brandFileSystemPath");
            field.setAccessible(true);
            field.set(util, path.toString());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void write(Path file, String content) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    @Test
    void given_noBrandDirectory_when_report_then_everyAssetEmbedded() {
        BrandReport report = diagnostics.report();

        assertFalse(report.externalExists());
        assertFalse(report.localExists());
        assertEquals(external.toString(), report.configuredPath());
        assertEquals(local.toString(), report.localPath());
        assertNull(report.metadataFile());
        assertNull(report.brandName());
        assertNull(report.organization());
        assertNull(report.version());
        assertSame(inUse, report.inUse());
        assertEquals(ASSETS, report.assets().stream().map(AssetReport::path).toList());
        assertEquals(List.of("color stylesheet", "typography stylesheet", "theme stylesheet", "horizontal logo",
                "header icon", "favicon"), report.assets().stream().map(AssetReport::role).toList());
        for (AssetReport asset : report.assets()) {
            assertEquals(Source.EMBEDDED, asset.source(), asset.path());
            assertEquals("META-INF/brand/" + asset.path(), asset.detail());
        }
        assertEquals("Embedded default theme; no brand directory at " + external, report.summary());
    }

    @Test
    void given_aPartialMount_when_report_then_eachAssetFromWhereItResolved() throws IOException {
        write(external.resolve("colors/brand-colors.css"), ":root{}");
        write(local.resolve("colors/brand-colors.css"), ":root{}");
        write(local.resolve("theme.css"), "html{}");
        Files.createDirectories(external.resolve("images/favicon.ico"));
        write(external.resolve("brand-info.json"), "{\"brand\": {\"name\": \"Acme\", \"organization\": \"Acme Health\", \"version\": \"2.1\"}}");
        write(external.resolve("brand-config.json"), "{\"name\": \"Ignored\"}");

        BrandReport report = diagnostics.report();

        assertTrue(report.externalExists());
        assertTrue(report.localExists());
        assertEquals("brand-info.json", report.metadataFile(), "brand-info.json is read before brand-config.json");
        assertEquals("Acme", report.brandName());
        assertEquals("Acme Health", report.organization());
        assertEquals("2.1", report.version());
        List<AssetReport> assets = report.assets();
        assertEquals(new AssetReport("colors/brand-colors.css", "color stylesheet", Source.EXTERNAL,
                external.resolve("colors/brand-colors.css").toAbsolutePath().toString()), assets.get(0));
        assertEquals(Source.EMBEDDED, assets.get(1).source());
        assertEquals(new AssetReport("theme.css", "theme stylesheet", Source.LOCAL,
                local.resolve("theme.css").toAbsolutePath().toString()), assets.get(2));
        assertEquals(new AssetReport("images/favicon.ico", "favicon", Source.UNREADABLE,
                external.resolve("images/favicon.ico").toAbsolutePath() + " exists but cannot be read"), assets.get(5));
        assertEquals("Mounted brand: Acme at " + external, report.summary());
    }

    @Test
    void given_aMountWithTopLevelMetadataWithoutAName_when_report_then_mountedWithoutAName() throws IOException {
        write(external.resolve("brand-config.json"), "{\"organization\": \"Acme Health\", \"version\": null}");

        BrandReport report = diagnostics.report();

        assertEquals("brand-config.json", report.metadataFile());
        assertNull(report.brandName());
        assertEquals("Acme Health", report.organization());
        assertNull(report.version(), "a JSON null is no version");
        assertFalse(report.localExists());
        assertEquals("Mounted brand at " + external, report.summary());
    }

    @Test
    void given_onlyALocalDirectory_when_report_then_itsMetadataAndSummary() throws IOException {
        write(local.resolve("brand-config.json"), "{\"name\": \"Local Brand\"}");
        write(local.resolve("images/HorizontalLogo.png"), "png");

        BrandReport report = diagnostics.report();

        assertFalse(report.externalExists());
        assertTrue(report.localExists());
        assertEquals("Local Brand", report.brandName());
        assertEquals(Source.LOCAL, report.assets().get(3).source());
        assertEquals("Local brand directory " + local, report.summary());
    }

    @Test
    void given_unparseableMetadata_when_report_then_theFileIsNamedButNoNameRead() throws IOException {
        write(external.resolve("brand-info.json"), "{ not json");

        BrandReport report = diagnostics.report();

        assertEquals("brand-info.json", report.metadataFile());
        assertNull(report.brandName());
        assertNull(report.organization());
        assertNull(report.version());
    }

    @Test
    void given_aMetadataNameThatIsADirectory_when_report_then_theNextCandidateIsRead() throws IOException {
        Files.createDirectories(external.resolve("brand-info.json"));
        write(external.resolve("brand-config.json"), "{\"name\": \"Acme\"}");

        assertEquals("brand-config.json", diagnostics.report().metadataFile());
    }

    /** Umbrella UC-014 BR-008, UC-023 BR-184: the logos are looked for under their configured names. */
    @Test
    void given_aMountNamingItsLogos_when_report_then_theyAreReportedUnderThoseNames() throws IOException {
        write(external.resolve("brand-config.json"), "{\"name\": \"Acme\", \"logos\": {\"horizontal\": \"Wide.svg\","
                + " \"icon\": \"mark.svg\"}, \"favicon\": \"tab.svg\"}");
        write(external.resolve("images/Wide.svg"), "<svg/>");
        write(external.resolve("images/mark.svg"), "<svg/>");
        write(external.resolve("images/tab.svg"), "<svg/>");

        BrandReport report = diagnostics.report();

        List<AssetReport> logos = report.assets().subList(3, 6);
        assertEquals(List.of("images/Wide.svg", "images/mark.svg", "images/tab.svg"),
                logos.stream().map(AssetReport::path).toList());
        logos.forEach(logo -> assertEquals(Source.EXTERNAL, logo.source(), logo.path()));
        assertEquals("/api/brand/images/Wide.svg", report.logoUrl());
        assertEquals(List.of(), report.warnings());
    }

    /** Umbrella UC-014 BR-009, UC-023 BR-184: a favicon of its own with no icon of its own is a warning. */
    @Test
    void given_aMountedFaviconWithoutAnIcon_when_report_then_theDefaultIconAndAWarning() throws IOException {
        write(external.resolve("brand-config.json"), "{\"name\": \"Acme\", \"logos\": {\"horizontal\": \"Wide.png\"},"
                + " \"favicon\": \"favicon.svg\"}");
        write(external.resolve("images/Wide.png"), "png");
        write(external.resolve("images/favicon.svg"), "<svg/>");

        BrandReport report = diagnostics.report();

        assertEquals(new AssetReport("images/icon-white.png", "header icon", Source.EMBEDDED,
                "META-INF/brand/images/icon-white.png"), report.assets().get(4));
        assertEquals(1, report.warnings().size());
        assertTrue(report.warnings().get(0).contains("logos.icon"), report.warnings().get(0));
    }

    /** UC-023 BR-184: a logo the configuration names but the mount lacks is a warning, and falls back. */
    @Test
    void given_aNamedLogoTheMountLacks_when_report_then_theDefaultAndAWarning() throws IOException {
        write(external.resolve("brand-config.json"), "{\"name\": \"Acme\", \"logos\": {\"icon\": \"mark.svg\"}}");

        BrandReport report = diagnostics.report();

        assertEquals("images/icon-white.png", report.assets().get(4).path());
        assertEquals(List.of("brand-config.json names images/mark.svg, which the mounted brand does not hold."
                + " The file of the default brand is shown instead."), report.warnings());
    }

    /** UC-023: a mount that names no logos and supplies none warns about nothing. */
    @Test
    void given_aMountWithoutLogos_when_report_then_noWarnings() throws IOException {
        write(external.resolve("colors/brand-colors.css"), ":root{}");

        assertEquals(List.of(), diagnostics.report().warnings());
    }

    @Test
    void given_aReload_when_requested_then_theCachedBrandIsDiscarded() {
        diagnostics.reload();

        assertEquals(1, cleared);
    }
}
