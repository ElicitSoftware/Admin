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

import jakarta.enterprise.inject.Vetoed;

/**
 * The console's provider for a browserless test's mock Vaadin service (UC-026). Without an
 * {@code i18n.provider} setting Vaadin falls back to its own provider, which reads only the
 * classpath -- and the image carries English only, so every other language would silently render
 * in English. Vaadin builds this with its no-argument constructor, so it names the curated mount
 * itself rather than reading the {@code %test} profile: once a booted test has run in the same JVM,
 * the configuration a plain test sees no longer reliably carries that profile. Vetoed so a booted
 * test never sees a second {@code ElicitI18NProvider} bean.
 */
@Vetoed
public class ConfiguredI18NProvider extends ElicitI18NProvider {

    public ConfiguredI18NProvider() {
        fileSystemPath = ElicitI18NProviderTest.CURATED;
    }
}
