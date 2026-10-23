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

import com.elicitsoftware.diagnostics.MailerDiagnostics.MailerReport;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UC-024: Diagnose Email, step 2 and A2 (BR-094) — the effective mail settings read from the
 * configuration, the credentials reported as present or absent only, and the one-line summary.
 * The configuration is an {@link IsolatedConfig} of the test's own making.
 */
class MailerDiagnosticsReportTest {

    private static MailerReport report(Map<String, String> properties) {
        return IsolatedConfig.with(properties, () -> new MailerDiagnostics().report());
    }

    @Test
    void given_aFullConfiguration_when_report_then_everySettingAndOnlyThePresenceOfCredentials() {
        MailerReport report = report(Map.of(
                "quarkus.mailer.from", "survey@example.org",
                "quarkus.mailer.host", "smtp.example.org",
                "quarkus.mailer.port", "587",
                "quarkus.mailer.tls", "true",
                "quarkus.mailer.start-tls", "REQUIRED",
                "quarkus.mailer.auth-methods", "PLAIN LOGIN",
                "quarkus.mailer.username", "relay",
                "quarkus.mailer.password", "secret",
                "quarkus.mailer.mock", "true"));

        assertEquals(new MailerReport("survey@example.org", "smtp.example.org", 587, true, "REQUIRED", "PLAIN LOGIN",
                true, true, true), report);
        assertFalse(report.toString().contains("secret"), "BR-094: the password is never reported");
    }

    @Test
    void given_nothingConfigured_when_report_then_theDefaults() {
        assertEquals(new MailerReport(null, "localhost", 25, false, "OPTIONAL", null, false, false, false), report(Map.of()));
    }

    @Test
    void given_blankOrUnexpandableValues_when_report_then_treatedAsUnset() {
        MailerReport report = report(Map.of(
                "quarkus.mailer.from", "  ",
                "quarkus.mailer.host", "",
                "quarkus.mailer.username", "${NO_SUCH_ELICIT_TEST_VARIABLE}",
                "quarkus.mailer.password", " "));

        assertEquals(new MailerReport(null, "localhost", 25, false, "OPTIONAL", null, false, false, false), report);
    }

    @Test
    void given_noSender_when_summary_then_itSaysSo() {
        MailerReport none = new MailerReport(null, "h", 25, false, "OPTIONAL", null, false, false, false);
        MailerReport blank = new MailerReport(" ", "h", 25, false, "OPTIONAL", null, false, false, false);

        assertFalse(none.canSend());
        assertFalse(blank.canSend());
        assertEquals("No sender address configured (quarkus.mailer.from)", none.summary());
        assertEquals(none.summary(), blank.summary());
    }

    @Test
    void given_aSender_when_summary_then_relayTlsAndMockAreNamed() {
        MailerReport plain = new MailerReport("a@b.org", "relay", 25, false, "OPTIONAL", null, false, false, false);
        MailerReport tls = new MailerReport("a@b.org", "relay", 465, true, "OPTIONAL", null, false, false, false);
        MailerReport mocked = new MailerReport("a@b.org", "relay", 25, false, "OPTIONAL", null, false, false, true);

        assertTrue(plain.canSend());
        assertEquals("From a@b.org via relay:25", plain.summary());
        assertEquals("From a@b.org via relay:465 with TLS", tls.summary());
        assertEquals("From a@b.org via relay:25 (mocked)", mocked.summary());
    }
}
