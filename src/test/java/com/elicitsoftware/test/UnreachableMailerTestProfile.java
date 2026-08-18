package com.elicitsoftware.test;

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

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;

/**
 * Disables the mock mailer and points {@code quarkus.mailer.host}/{@code port} at a closed local
 * port so {@code ReactiveMailer.send(...)} fails with a real connection error (as opposed to the
 * mocked "always succeeds" sends the default test profile uses). Used by {@code EmailServiceTest}
 * (UC-004) to reproduce, without a live SMTP relay, the class of failure seen in production where
 * the mail server rejects the connection.
 */
public class UnreachableMailerTestProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
                "quarkus.mailer.mock", "false",
                "quarkus.mailer.host", "localhost",
                "quarkus.mailer.port", "1"
        );
    }
}
