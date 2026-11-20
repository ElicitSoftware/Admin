package com.elicitsoftware.security;

/*-
 * ***LICENSE_START***
 * Elicit Admin
 * %%
 * Copyright (C) 2025 - 2026 The Regents of the University of Michigan - Rogel Cancer Center
 * %%
 * PolyForm Noncommercial License 1.0.0
 * <https://polyformproject.org/licenses/noncommercial/1.0.0>
 * ***LICENSE_END***
 */

import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * The deployment's service token (NFR-024): the shared secret Elicit's services authenticate
 * their calls to each other with. The console is a caller only. It sends the token to Survey's
 * {@code /api} paths (UC-018 BR-159) and to a report service whose origin this site's
 * configuration names as trusted (UC-018 BR-160), and to nothing else.
 * <p>
 * The value is read here and nowhere else, and it leaves this class only as the value of an
 * {@code Authorization} header. It is never logged, rendered, put in an address or included in
 * a message: everything else is told only whether a token is configured (NFR-011).
 * {@link #toString()} does not carry it.
 * <p>
 * At startup (UC-018 BR-162): a token that is set but shorter than {@link #MINIMUM_LENGTH}
 * characters fails the start, as does a malformed entry of the trusted origins; no token logs
 * one warning and leaves the console not ready ({@link ServiceTokenHealthCheck}); the
 * development value logs a warning.
 */
@ApplicationScoped
public class ServiceToken {

    /** The environment variable the packaged configuration reads the token from. */
    public static final String VARIABLE = "ELICIT_SERVICE_TOKEN";

    /** The environment variable the packaged configuration reads the trusted origins from. */
    public static final String TRUSTED_ORIGINS_VARIABLE = "ELICIT_SERVICE_TRUSTED_ORIGINS";

    /** The shortest token accepted. */
    public static final int MINIMUM_LENGTH = 32;

    /**
     * The value the platform's compose files and the development profile carry, so that a
     * checkout runs with no setup. It is public knowledge and protects nothing at a real site.
     */
    public static final String DEVELOPMENT_VALUE = "elicit-dev-service-token-not-for-a-real-site";

    /** What a caller reports, in the log, when it has no token to send. */
    public static final String NOT_CONFIGURED = "service token not configured";

    /**
     * Thrown instead of making a call that needs the token when none is configured (UC-018
     * BR-161). The message is fixed and carries nothing of the configuration.
     */
    public static final class NotConfiguredException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        NotConfiguredException() {
            super(NOT_CONFIGURED);
        }
    }

    /**
     * Where a request goes, as far as the token is concerned (UC-018 BR-160): scheme and host
     * in lower case and the port, 80 or 443 when the address writes none.
     *
     * @param scheme {@code http} or {@code https}
     * @param host   the host, lower case
     * @param port   the effective port
     */
    public record Origin(String scheme, String host, int port) {

        /**
         * The origin of an address, or empty when the address can never receive the token: it
         * has user information, no host, or a scheme other than {@code http} and {@code https}.
         * Only the parts {@link URI} parsed are read; the address is never compared as text.
         *
         * @param address the address, or null
         * @return the address's origin, or empty for null or an address that has none
         */
        public static Optional<Origin> of(URI address) {
            if (address == null || address.getScheme() == null || address.getHost() == null
                    || address.getRawUserInfo() != null) {
                return Optional.empty();
            }
            String scheme = address.getScheme().toLowerCase(Locale.ROOT);
            int defaultPort;
            if (scheme.equals("http")) {
                defaultPort = 80;
            } else if (scheme.equals("https")) {
                defaultPort = 443;
            } else {
                return Optional.empty();
            }
            String host = address.getHost().toLowerCase(Locale.ROOT);
            if (host.isEmpty()) {
                return Optional.empty();
            }
            int port = address.getPort() < 0 ? defaultPort : address.getPort();
            return Optional.of(new Origin(scheme, host, port));
        }

        @Override
        public String toString() {
            return scheme + "://" + host + ":" + port;
        }
    }

    private final String token;
    private final String trustedOriginsSetting;
    private final String surveyUrl;
    private volatile Set<Origin> trusted;

    /**
     * Reads the service token, the trusted origins and the Survey address from configuration.
     * A token that is blank after trimming counts as not configured.
     *
     * @param configured     {@code elicit.service.token}, if set
     * @param trustedOrigins {@code elicit.service.trusted-origins}, if set
     * @param surveyUrl      {@code elicit.survey.url}, whose origin is always trusted
     */
    public ServiceToken(@ConfigProperty(name = "elicit.service.token") Optional<String> configured,
                        @ConfigProperty(name = "elicit.service.trusted-origins") Optional<String> trustedOrigins,
                        @ConfigProperty(name = "elicit.survey.url", defaultValue = "http://survey:8080") String surveyUrl) {
        this.token = configured.map(String::trim).filter(v -> !v.isEmpty()).orElse(null);
        this.trustedOriginsSetting = trustedOrigins.orElse("");
        this.surveyUrl = surveyUrl;
    }

    void onStart(@Observes StartupEvent event) {
        validate();
    }

    /**
     * Checks the token and the trusted origins as the application starts.
     *
     * @throws IllegalStateException when a token is set but too short (the message names the
     *                               variable and the length required, never the value or its
     *                               length), or when an entry of the trusted origins is not an
     *                               origin (the message names the variable and the entry's
     *                               position, never the entry)
     */
    void validate() {
        trustedOrigins();
        if (token == null) {
            Log.warn(VARIABLE + " is not set. The console reports not ready, and will not ask Survey to rebuild"
                    + " or rename a reporting schema or request a report from a trusted service, until a"
                    + " service token is supplied.");
            return;
        }
        if (token.length() < MINIMUM_LENGTH) {
            throw new IllegalStateException(VARIABLE + " is too short: a service token must be at least "
                    + MINIMUM_LENGTH + " characters. Generate one with: openssl rand -hex 32");
        }
        if (isDevelopmentValue()) {
            Log.warn(VARIABLE + " is the well-known development value. Replace it on every Elicit service"
                    + " before this console is used at a real site.");
        }
    }

    /**
     * Whether a token is configured.
     *
     * @return true when a non-blank token is configured
     */
    public boolean isConfigured() {
        return token != null;
    }

    /**
     * Whether the configured token is the development value.
     *
     * @return true when the token equals the well-known development value
     */
    public boolean isDevelopmentValue() {
        return DEVELOPMENT_VALUE.equals(token);
    }

    /**
     * The {@code Authorization} header value for a call to an address this site's own
     * configuration names: Survey's {@code /api} paths under {@code elicit.survey.url} (UC-018
     * BR-159). Never for an address that came from a survey definition; that is
     * {@link #authorizationFor(URI)}.
     *
     * @return the header value, {@code Bearer} followed by the token
     * @throws NotConfiguredException when no token is configured: the call must not be made
     */
    public String authorization() {
        if (token == null) {
            throw new NotConfiguredException();
        }
        return "Bearer " + token;
    }

    /**
     * The {@code Authorization} header value for a request to {@code destination}, or empty when
     * that address must not receive the token (UC-018 BR-160). The destination is the final
     * address, after any resolution; its origin must equal a trusted one.
     *
     * @param destination the address the request is about to be sent to
     * @return the header value, or empty for a destination that is not trusted
     * @throws NotConfiguredException when the destination is trusted and no token is configured:
     *                                the call must not be made (UC-018 BR-161)
     */
    public Optional<String> authorizationFor(URI destination) {
        if (!isTrusted(destination)) {
            return Optional.empty();
        }
        return Optional.of(authorization());
    }

    /**
     * Whether the token may be sent to this address: its origin equals a trusted one.
     *
     * @param destination the address the request is about to be sent to, or null
     * @return true when the address has an origin and it is trusted
     */
    public boolean isTrusted(URI destination) {
        return Origin.of(destination).filter(trustedOrigins()::contains).isPresent();
    }

    /**
     * The trusted origins: the origin of {@code elicit.survey.url} and each entry of
     * {@code elicit.service.trusted-origins}.
     */
    Set<Origin> trustedOrigins() {
        Set<Origin> origins = trusted;
        if (origins == null) {
            origins = new LinkedHashSet<>();
            surveyOrigin(surveyUrl).ifPresent(origins::add);
            origins.addAll(parseTrustedOrigins(trustedOriginsSetting));
            trusted = origins = Set.copyOf(origins);
        }
        return origins;
    }

    /** The origin of the Survey address, which may carry a path; empty when it has no origin. */
    private static Optional<Origin> surveyOrigin(String surveyUrl) {
        if (surveyUrl == null || surveyUrl.isBlank()) {
            return Optional.empty();
        }
        try {
            return Origin.of(new URI(surveyUrl.trim()));
        } catch (URISyntaxException e) {
            return Optional.empty();
        }
    }

    /**
     * Reads the comma-separated trusted origins. Each entry is a scheme, a host and optionally a
     * port, and nothing more.
     *
     * @throws IllegalStateException for an entry that is not {@code http} or {@code https}, has
     *                               no host, or carries user information, a path, a query or a
     *                               fragment
     */
    static Set<Origin> parseTrustedOrigins(String setting) {
        Set<Origin> origins = new LinkedHashSet<>();
        if (setting == null || setting.isBlank()) {
            return origins;
        }
        String[] entries = setting.split(",");
        for (int i = 0; i < entries.length; i++) {
            String entry = entries[i].trim();
            if (entry.isEmpty()) {
                continue;
            }
            URI uri;
            try {
                uri = new URI(entry);
            } catch (URISyntaxException e) {
                throw malformed(i, "it is not an address");
            }
            if (uri.getRawUserInfo() != null) {
                throw malformed(i, "it carries user information");
            }
            if (uri.getRawPath() != null && !uri.getRawPath().isEmpty()) {
                throw malformed(i, "it carries a path");
            }
            if (uri.getRawQuery() != null) {
                throw malformed(i, "it carries a query");
            }
            if (uri.getRawFragment() != null) {
                throw malformed(i, "it carries a fragment");
            }
            Optional<Origin> origin = Origin.of(uri);
            if (origin.isEmpty()) {
                throw malformed(i, "it needs http or https and a host");
            }
            origins.add(origin.get());
        }
        return origins;
    }

    /** The entry itself is not repeated: an address with user information may hold a password. */
    private static IllegalStateException malformed(int index, String why) {
        return new IllegalStateException(TRUSTED_ORIGINS_VARIABLE + ": entry " + (index + 1) + " is not an origin ("
                + why + "). Write each entry as scheme://host or scheme://host:port, separated by commas.");
    }

    @Override
    public String toString() {
        return "ServiceToken[" + (token == null ? "not set" : "set") + "]";
    }
}
