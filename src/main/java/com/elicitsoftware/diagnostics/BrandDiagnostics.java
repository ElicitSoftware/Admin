package com.elicitsoftware.diagnostics;

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

import com.elicitsoftware.admin.util.BrandUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Reports which brand directory resolved and where each brand asset came from (UC-023).
 * <p>
 * Mirrors the three-tier resolution {@code AppConfig} and {@link BrandUtil} perform when the
 * pages render: the mounted directory ({@code brand.file.system.path}), then the local
 * directory ({@code brand.local.path}), then the embedded default under
 * {@code META-INF/brand}. Every asset is reported on its own (BR-091) because a partial
 * mount is the case that is otherwise invisible.
 */
@ApplicationScoped
public class BrandDiagnostics {

    /** Where an asset resolved from. */
    public enum Source {
        /** The mounted brand directory. */
        EXTERNAL,
        /** The local brand directory. */
        LOCAL,
        /** The default packaged with the application. */
        EMBEDDED,
        /** Nowhere; the page renders without it. */
        ABSENT,
        /** A file exists but could not be read (UC-023 A2). */
        UNREADABLE
    }

    /**
     * One brand asset.
     *
     * @param path   the path relative to the brand directory
     * @param role   what the asset is for
     * @param source where it resolved from
     * @param detail the resolved location, or the read failure
     */
    public record AssetReport(String path, String role, Source source, String detail) {
    }

    /**
     * The whole brand picture.
     *
     * @param configuredPath    {@code brand.file.system.path}
     * @param localPath         {@code brand.local.path}
     * @param externalExists    whether the configured directory exists
     * @param localExists       whether the local directory exists
     * @param metadataFile      the metadata file the resolved directory carries, or null
     * @param brandName         the brand name from the metadata, or null
     * @param organization      the organization from the metadata, or null
     * @param version           the brand version from the metadata, or null
     * @param inUse             the brand the application resolved and cached
     * @param assets            every expected asset and its source
     */
    public record BrandReport(String configuredPath, String localPath, boolean externalExists, boolean localExists,
                              String metadataFile, String brandName, String organization, String version,
                              BrandUtil.BrandInfo inUse, List<AssetReport> assets) {

        /** A one-line summary for the overview (UC-020 step 3). */
        public String summary() {
            if (externalExists) {
                return "Mounted brand" + (brandName != null ? ": " + brandName : "") + " at " + configuredPath;
            }
            if (localExists) {
                return "Local brand directory " + localPath;
            }
            return "Embedded default theme; no brand directory at " + configuredPath;
        }
    }

    private record Expected(String path, String role) {
    }

    static final List<Expected> EXPECTED = List.of(
            new Expected("colors/brand-colors.css", "colour stylesheet"),
            new Expected("typography/brand-typography.css", "typography stylesheet"),
            new Expected("theme.css", "theme stylesheet"),
            new Expected("images/HorizontalLogo.png", "horizontal logo"),
            new Expected("images/icon-white.png", "header icon"),
            new Expected("images/favicon.ico", "favicon"));

    @ConfigProperty(name = "brand.file.system.path", defaultValue = "/brand")
    String brandFileSystemPath;

    @ConfigProperty(name = "brand.local.path", defaultValue = "brand")
    String brandLocalPath;

    @Inject
    BrandUtil brandUtil;

    public BrandDiagnostics() {
        // CDI managed bean
    }

    public BrandReport report() {
        Path external = Paths.get(brandFileSystemPath);
        Path local = Paths.get(brandLocalPath);
        boolean externalExists = Files.isDirectory(external);
        boolean localExists = Files.isDirectory(local);
        Path resolved = externalExists ? external : localExists ? local : null;

        String metadataFile = null;
        String name = null;
        String organization = null;
        String version = null;
        if (resolved != null) {
            for (String candidate : List.of("brand-info.json", "brand-config.json")) {
                Path file = resolved.resolve(candidate);
                if (Files.isRegularFile(file)) {
                    metadataFile = candidate;
                    try {
                        JsonNode json = new ObjectMapper().readTree(Files.readString(file));
                        name = text(json, "name");
                        organization = text(json, "organization");
                        version = text(json, "version");
                    } catch (IOException e) {
                        name = null;
                    }
                    break;
                }
            }
        }

        List<AssetReport> assets = new ArrayList<>();
        for (Expected expected : EXPECTED) {
            assets.add(resolve(expected, external, local));
        }
        return new BrandReport(brandFileSystemPath, brandLocalPath, externalExists, localExists, metadataFile,
                name, organization, version, brandUtil.detectCurrentBrand(), assets);
    }

    /** Discards the cached brand so the next request re-resolves it (BR-092). */
    public void reload() {
        brandUtil.clearCache();
    }

    private AssetReport resolve(Expected expected, Path external, Path local) {
        Path externalFile = external.resolve(expected.path());
        if (Files.exists(externalFile)) {
            return readable(expected, Source.EXTERNAL, externalFile);
        }
        Path localFile = local.resolve(expected.path());
        if (Files.exists(localFile)) {
            return readable(expected, Source.LOCAL, localFile);
        }
        try (InputStream embedded = getClass().getResourceAsStream("/META-INF/brand/" + expected.path())) {
            if (embedded != null) {
                return new AssetReport(expected.path(), expected.role(), Source.EMBEDDED, "META-INF/brand/" + expected.path());
            }
        } catch (IOException e) {
            return new AssetReport(expected.path(), expected.role(), Source.UNREADABLE, e.getMessage());
        }
        return new AssetReport(expected.path(), expected.role(), Source.ABSENT, "not found in any location");
    }

    private static AssetReport readable(Expected expected, Source source, Path file) {
        if (Files.isReadable(file) && Files.isRegularFile(file)) {
            return new AssetReport(expected.path(), expected.role(), source, file.toAbsolutePath().toString());
        }
        return new AssetReport(expected.path(), expected.role(), Source.UNREADABLE,
                file.toAbsolutePath() + " exists but cannot be read");
    }

    private static String text(JsonNode json, String field) {
        JsonNode node = json.get(field);
        if (node == null && json.has("brand")) {
            node = json.get("brand").get(field);
        }
        return node == null || node.isNull() ? null : node.asText();
    }
}
