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

import com.elicitsoftware.diagnostics.RequiredConfigCheck.LegacySetting;
import com.elicitsoftware.diagnostics.RequiredConfigCheck.RequiredSetting;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UC-020: View the System Overview, steps 4 and 5 (BR-082, NFR-011) — every setting the
 * deployment must supply is reported as present or absent, never by value, and a stale setting
 * still configured is named with its replacement. The configuration is an
 * {@link IsolatedConfig} of the test's own making.
 */
class RequiredConfigCheckPresenceTest {

    private final RequiredConfigCheck check = new RequiredConfigCheck();

    @Test
    void given_nothingConfigured_when_requiredSettings_then_everyOneAbsentWithItsPurpose() {
        List<RequiredSetting> settings = IsolatedConfig.with(Map.of(), check::requiredSettings);

        assertEquals(List.of(
                new RequiredSetting("quarkus.datasource.password", "ELICIT_DB_PASSWORD",
                        "password of the application database user", false),
                new RequiredSetting("quarkus.datasource.owner.password", "ELICIT_OWNER_DB_PASSWORD",
                        "password of the owner database user that runs migrations", false),
                new RequiredSetting("quarkus.oidc.credentials.secret", "OIDC_CLIENT_SECRET",
                        "client secret registered with the identity provider", false),
                new RequiredSetting("quarkus.mailer.from", "quarkus.mailer.from",
                        "sender address of every invitation and reminder", false)), settings);
    }

    @Test
    void given_someConfigured_when_requiredSettings_then_onlyNonBlankExpandableValuesArePresent() {
        List<RequiredSetting> settings = IsolatedConfig.with(Map.of(
                "quarkus.datasource.password", "s3cret",
                "quarkus.datasource.owner.password", "   ",
                "quarkus.oidc.credentials.secret", "${NO_SUCH_ELICIT_TEST_VARIABLE}",
                "quarkus.mailer.from", "survey@example.org"), check::requiredSettings);

        assertEquals(List.of(true, false, false, true), settings.stream().map(RequiredSetting::present).toList());
        assertTrue(settings.stream().noneMatch(s -> s.toString().contains("s3cret")), "NFR-011: never by value");
    }

    @Test
    void given_aConfigurationThatThrows_when_isPresent_then_absent() {
        org.eclipse.microprofile.config.Config failing = (org.eclipse.microprofile.config.Config) java.lang.reflect.Proxy
                .newProxyInstance(getClass().getClassLoader(), new Class<?>[]{org.eclipse.microprofile.config.Config.class},
                        (p, method, args) -> {
                            throw new java.util.NoSuchElementException("SRCFG00011: Could not expand value");
                        });

        assertEquals(false, RequiredConfigCheck.isPresent(failing, "quarkus.oidc.credentials.secret"));
    }

    @Test
    void given_aLegacySetting_when_legacySettingsInUse_then_itIsNamedWithItsReplacement() {
        assertEquals(List.of(new LegacySetting("token.autoRegister", "accessCode.autoRegister")),
                IsolatedConfig.with(Map.of("token.autoRegister", "true"), check::legacySettingsInUse));
        assertEquals(List.of(), IsolatedConfig.with(Map.of("token.autoRegister", ""), check::legacySettingsInUse));
        assertEquals(List.of(), IsolatedConfig.with(Map.of(), check::legacySettingsInUse));
    }
}
