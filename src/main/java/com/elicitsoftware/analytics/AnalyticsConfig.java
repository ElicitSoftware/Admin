package com.elicitsoftware.analytics;

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

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Optional;

/**
 * Deployment configuration for the Analytics feature (UC-020, BR-082): where Superset is, which
 * dashboard the console embeds, and the secret Admin signs guest tokens with.
 *
 * <p>The feature is {@linkplain #isEnabled() enabled} only when all three are present, so a
 * deployment without Superset simply has no Analytics item (UC-020 A1).</p>
 */
@ApplicationScoped
public class AnalyticsConfig {

    @ConfigProperty(name = "elicit.analytics.superset-url")
    Optional<String> supersetUrl;

    @ConfigProperty(name = "elicit.analytics.dashboard-id")
    Optional<String> dashboardId;

    @ConfigProperty(name = "elicit.analytics.guest-token-secret")
    Optional<String> guestTokenSecret;

    @ConfigProperty(name = "elicit.analytics.guest-token-audience")
    Optional<String> guestTokenAudience;

    @ConfigProperty(name = "elicit.analytics.guest-token-ttl-seconds", defaultValue = "300")
    long guestTokenTtlSeconds;

    /** Creates the bean; values are injected by CDI. */
    public AnalyticsConfig() {
    }

    /** Whether the Analytics feature is configured for this deployment. */
    public boolean isEnabled() {
        return present(supersetUrl) && present(dashboardId) && present(guestTokenSecret);
    }

    /** Browser-reachable Superset base URL without a trailing slash. */
    public Optional<String> getSupersetUrl() {
        return supersetUrl.filter(v -> !v.isBlank()).map(v -> v.endsWith("/") ? v.substring(0, v.length() - 1) : v);
    }

    /** Embedded dashboard id, as shown by Superset's "Embed dashboard" dialog. */
    public Optional<String> getDashboardId() {
        return dashboardId.filter(v -> !v.isBlank());
    }

    /** HS256 secret shared with Superset's {@code GUEST_TOKEN_JWT_SECRET}. */
    public Optional<String> getGuestTokenSecret() {
        return guestTokenSecret.filter(v -> !v.isBlank());
    }

    /** Audience claim to add, when Superset's {@code GUEST_TOKEN_JWT_AUDIENCE} is set. */
    public Optional<String> getGuestTokenAudience() {
        return guestTokenAudience.filter(v -> !v.isBlank());
    }

    /** Guest token lifetime in seconds (NFR-011: at most 300). */
    public long getGuestTokenTtlSeconds() {
        return guestTokenTtlSeconds;
    }

    private static boolean present(Optional<String> value) {
        return value.filter(v -> !v.isBlank()).isPresent();
    }
}
