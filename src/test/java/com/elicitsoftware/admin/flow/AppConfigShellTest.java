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

import com.vaadin.flow.component.page.Inline;
import com.vaadin.flow.server.AppShellSettings;
import com.vaadin.flow.server.VaadinRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UC-023: Diagnose Branding (BR-091) and UC-026 BR-087 — the page shell the brand produces: the
 * brand-info meta tag (in the reader's language), the favicon, and the brand stylesheets inlined
 * in cascade order from the mounted directory, the local directory or the embedded default.
 * The settings record what is added instead of rendering it, and both brand directories are
 * temporary directories of the test's making.
 */
class AppConfigShellTest {

    /** Records what the shell is given, in order. */
    private static final class RecordingSettings extends AppShellSettings {
        final List<String> added = new ArrayList<>();
        final List<String> inlined = new ArrayList<>();
        final Map<String, String> meta = new java.util.LinkedHashMap<>();
        VaadinRequest request;

        @Override
        public VaadinRequest getRequest() {
            return request;
        }

        @Override
        public void addMetaTag(String name, String content) {
            meta.put(name, content);
        }

        @Override
        public void addLink(String rel, String href) {
            added.add("link " + rel + " " + href);
        }

        @Override
        public void addFavIcon(String rel, String href, String sizes) {
            added.add("favicon " + rel + " " + href + " " + sizes);
        }

        @Override
        public void addInlineWithContents(String contents, Inline.Wrapping type) {
            added.add("inline " + type);
            inlined.add(contents);
        }
    }

    @TempDir
    Path temp;

    private Path external;
    private Path local;
    private AppConfig config;
    private RecordingSettings settings;

    @BeforeEach
    void setUp() {
        external = temp.resolve("mounted");
        local = temp.resolve("local");
        config = new AppConfig();
        config.brandFileSystemPath = external.toString();
        config.brandLocalPath = local.toString();
        config.init();
        settings = new RecordingSettings();
    }

    private static void write(Path file, String content) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    private static String embedded(String path) throws IOException {
        try (InputStream in = AppConfigShellTest.class.getResourceAsStream("/META-INF/brand/" + path)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static VaadinRequest requestIn(Locale locale) {
        return (VaadinRequest) Proxy.newProxyInstance(VaadinRequest.class.getClassLoader(),
                new Class<?>[]{VaadinRequest.class}, (p, method, args) -> {
                    if (method.getName().equals("getLocale")) {
                        return locale;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    @Test
    void given_noBrandDirectory_when_configurePage_then_theEmbeddedDefaultInline() throws IOException {
        config.configurePage(settings);

        assertEquals(Map.of("brand-info", "Default Theme (no brand directory found)"), settings.meta);
        assertEquals(List.of("link shortcut icon /api/brand/images/favicon.ico",
                "favicon icon /api/brand/images/favicon.ico 32x32",
                "inline STYLESHEET", "inline STYLESHEET", "inline STYLESHEET"), settings.added);
        assertEquals(List.of(embedded("colors/brand-colors.css"), embedded("typography/brand-typography.css"),
                embedded("theme.css")), settings.inlined);
    }

    @Test
    void given_aFullMount_when_configurePage_then_itsMetadataFaviconAndStylesheetsInCascadeOrder() throws IOException {
        write(external.resolve("brand-config.json"), "{\"name\": \"Acme\", \"version\": \"3\", \"organization\": \"Acme Org\","
                + " \"description\": \"Clinic brand\", \"favicon\": \"acme.png\"}");
        write(external.resolve("colors/brand-colors.css"), "/* colors */");
        write(external.resolve("typography/brand-typography.css"), "/* typography */");
        write(external.resolve("theme.css"), "/* theme */");
        write(local.resolve("theme.css"), "/* local theme */");

        config.configurePage(settings);

        assertEquals("External Brand: Acme (v3) - Acme Org - Clinic brand [from brand-config.json]", settings.meta.get("brand-info"));
        assertEquals(List.of("link shortcut icon /api/brand/images/acme.png", "favicon icon /api/brand/images/acme.png 32x32",
                "inline STYLESHEET", "inline STYLESHEET", "inline STYLESHEET"), settings.added);
        assertEquals(List.of("/* colors */", "/* typography */", "/* theme */"), settings.inlined,
                "the mounted directory wins over the local one");
    }

    @Test
    void given_aLocalizedBrandAndASpanishRequest_when_configurePage_then_theMetaTagIsInSpanish() throws IOException {
        write(external.resolve("brand-info.json"), "{\"brand\": {\"name\": \"Acme\", \"organization\": \"Acme Org\","
                + " \"localized\": {\"es\": {\"name\": \"Acme ES\", \"organization\": \"Org ES\", \"description\": \"Marca\"}}}}");
        settings.request = requestIn(Locale.forLanguageTag("es-419"));

        config.configurePage(settings);

        assertEquals("External Brand: Acme ES (vunknown) - Org ES - Marca [from brand-info.json]", settings.meta.get("brand-info"));
    }

    @Test
    void given_aLocalDirectoryOnly_when_configurePage_then_itsStylesheetsAndTheEmbeddedRest() throws IOException {
        write(local.resolve("brand-info.json"), "{\"name\": \"Local\"}");
        write(local.resolve("colors/brand-colors.css"), "/* local colors */");

        config.configurePage(settings);

        String info = settings.meta.get("brand-info");
        assertTrue(info.startsWith("Default Brand: Local (vunknown) -") && info.endsWith(" [from brand-info.json]"), info);
        assertEquals(List.of("/* local colors */", embedded("typography/brand-typography.css"), embedded("theme.css")),
                settings.inlined);
        assertTrue(settings.added.contains("link shortcut icon /api/brand/images/favicon.ico"), settings.added.toString());
    }

    @Test
    void given_aMountWithoutMetadata_when_configurePage_then_theBrandFilesAreListed() throws IOException {
        write(external.resolve("colors/brand-colors.css"), "a");
        write(external.resolve("theme.css"), "b");

        config.configurePage(settings);

        assertEquals("External Brand Files: colors theme", settings.meta.get("brand-info"));
    }

    @Test
    void given_aLocalDirectoryWithMetadataWithoutAName_when_configurePage_then_itsFilesAreListed() throws IOException {
        write(local.resolve("brand-config.json"), "{\"version\": \"1\"}");
        write(local.resolve("typography/brand-typography.css"), "t");

        config.configurePage(settings);

        assertEquals("Default Brand Files: typography", settings.meta.get("brand-info"));
    }

    @Test
    void given_metadataThatCannotBeRead_when_configurePage_then_theMetaTagSaysSo() throws IOException {
        Files.createDirectories(external.resolve("brand-info.json"));

        config.configurePage(settings);

        assertTrue(settings.meta.get("brand-info").startsWith("External Brand Directory Detected (error reading metadata: "),
                settings.meta.get("brand-info"));
    }

    @Test
    void given_mountedStylesheetsThatCannotBeRead_when_configurePage_then_linkedThroughTheHandlerInstead() throws IOException {
        Files.createDirectories(external.resolve("colors/brand-colors.css"));
        write(local.resolve("colors/brand-colors.css"), "c");
        Files.createDirectories(external.resolve("typography/brand-typography.css"));
        write(local.resolve("typography/brand-typography.css"), "t");
        Files.createDirectories(external.resolve("theme.css"));

        config.configurePage(settings);

        assertEquals(List.of("link stylesheet /api/brand/colors/brand-colors.css",
                        "link stylesheet /api/brand/typography/brand-typography.css", "link stylesheet /api/brand/theme.css"),
                settings.added.stream().filter(a -> a.startsWith("link stylesheet")).toList());
        assertTrue(settings.inlined.isEmpty(), settings.inlined.toString());
    }

    @Test
    void given_unreadableMountedStylesheetsWithoutLocalCopies_when_configurePage_then_onlyTheThemeIsLinked() throws IOException {
        Files.createDirectories(external.resolve("colors/brand-colors.css"));
        Files.createDirectories(external.resolve("typography/brand-typography.css"));
        Files.createDirectories(external.resolve("theme.css"));

        config.configurePage(settings);

        assertEquals(List.of("link stylesheet /api/brand/theme.css"),
                settings.added.stream().filter(a -> a.startsWith("link stylesheet")).toList());
    }

    @Test
    void given_onlyALocalThemeThatCannotBeRead_when_configurePage_then_theThemeIsLinked() throws IOException {
        Files.createDirectories(external);
        Files.createDirectories(local.resolve("theme.css"));

        config.configurePage(settings);

        assertTrue(settings.added.contains("link stylesheet /api/brand/theme.css"), settings.added.toString());
    }

    @Test
    void given_aMountedConfigWithoutAFavicon_when_configurePage_then_theEmbeddedFavicon() throws IOException {
        write(external.resolve("brand-config.json"), "{\"name\": \"Acme\"}");

        config.configurePage(settings);

        assertEquals("link shortcut icon /api/brand/images/favicon.ico", settings.added.getFirst());
    }

    @Test
    void given_aLocalizedBlock_when_read_then_exactTagThenLanguageThenNothing() {
        String json = "{\"localized\": {\"ES-419\": {\"name\": \"Exacto\"}, \"es\": {\"name\": \"Idioma\", \"description\": null},"
                + " \"ar\": {\"organization\": \"Org\"}}}";

        assertEquals(Map.of("name", "Exacto"), AppConfig.localizedBrandText(json, Locale.forLanguageTag("es-419")));
        assertEquals(Map.of("name", "Idioma"), AppConfig.localizedBrandText(json, Locale.forLanguageTag("es-MX")));
        assertEquals(Map.of("organization", "Org"), AppConfig.localizedBrandText(json, Locale.forLanguageTag("ar")));
        assertEquals(Map.of(), AppConfig.localizedBrandText(json, Locale.FRENCH));
        assertEquals(Map.of("name", "Nested"), AppConfig.localizedBrandText(
                "{\"brand\": {\"localized\": {\"fr\": {\"name\": \"Nested\"}}}}", Locale.FRENCH));
        assertEquals(Map.of("name", "Top"), AppConfig.localizedBrandText(
                "{\"brand\": {\"name\": \"x\"}, /* comment */ \"localized\": {\"fr\": {\"name\": \"Top\"}}}", Locale.FRENCH));
    }

    @Test
    void given_noUsableLocalizedBlock_when_read_then_empty() {
        assertEquals(Map.of(), AppConfig.localizedBrandText(null, Locale.FRENCH));
        assertEquals(Map.of(), AppConfig.localizedBrandText("{}", null));
        assertEquals(Map.of(), AppConfig.localizedBrandText("{\"localized\": \"fr\"}", Locale.FRENCH));
        assertEquals(Map.of(), AppConfig.localizedBrandText("{ broken", Locale.FRENCH));
    }
}
