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

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;

import java.util.Optional;

/**
 * The effective mail settings, with the password reported as present or absent only
 * (UC-024 step 2, BR-094).
 */
@ApplicationScoped
public class MailerDiagnostics {

    /**
     * Effective mailer configuration.
     *
     * @param from            the sender address, or null when unset (UC-024 A2)
     * @param host            the relay host
     * @param port            the relay port
     * @param tls             whether TLS is on
     * @param startTls        the STARTTLS setting, as configured
     * @param authMethods     the authentication methods, or null when unset
     * @param usernamePresent whether a username is configured
     * @param passwordPresent whether a password is configured
     * @param mock            whether the mailer is mocked (tests and some dev setups)
     */
    public record MailerReport(String from, String host, int port, boolean tls, String startTls, String authMethods,
                               boolean usernamePresent, boolean passwordPresent, boolean mock) {

        public boolean canSend() {
            return from != null && !from.isBlank();
        }

        public String summary() {
            if (!canSend()) {
                return "No sender address configured (quarkus.mailer.from)";
            }
            return "From " + from + " via " + host + ":" + port + (tls ? " with TLS" : "") + (mock ? " (mocked)" : "");
        }
    }

    public MailerDiagnostics() {
        // CDI managed bean
    }

    public MailerReport report() {
        Config config = ConfigProvider.getConfig();
        return new MailerReport(
                value(config, "quarkus.mailer.from").orElse(null),
                value(config, "quarkus.mailer.host").orElse("localhost"),
                config.getOptionalValue("quarkus.mailer.port", Integer.class).orElse(25),
                config.getOptionalValue("quarkus.mailer.tls", Boolean.class).orElse(false),
                value(config, "quarkus.mailer.start-tls").orElse("OPTIONAL"),
                value(config, "quarkus.mailer.auth-methods").orElse(null),
                value(config, "quarkus.mailer.username").isPresent(),
                value(config, "quarkus.mailer.password").isPresent(),
                config.getOptionalValue("quarkus.mailer.mock", Boolean.class).orElse(false));
    }

    private static Optional<String> value(Config config, String property) {
        try {
            return config.getOptionalValue(property, String.class).filter(v -> !v.isBlank());
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }
}
