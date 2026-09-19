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

import com.elicitsoftware.model.User;
import io.smallrye.jwt.algorithm.SignatureAlgorithm;
import io.smallrye.jwt.build.Jwt;
import io.smallrye.jwt.build.JwtClaimsBuilder;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Mints Superset guest tokens for the embedded Analytics dashboard (UC-020, BR-081).
 *
 * <p>The token mirrors what Superset's own {@code create_guest_access_token} produces:
 * {@code user}, {@code resources}, {@code rls_rules}, {@code iat}, {@code exp}, optional
 * {@code aud}, and {@code type: "guest"}, signed HS256 with the secret shared through
 * {@link AnalyticsConfig}. It names exactly the configured dashboard, carries no row-level
 * security rules, and expires after the configured lifetime.</p>
 */
@ApplicationScoped
public class SupersetGuestTokenService {

    @Inject
    AnalyticsConfig config;

    /** Creates the service; configuration is injected by CDI. */
    public SupersetGuestTokenService() {
    }

    /**
     * Signs a guest token for the given console user.
     *
     * @param username the authenticated principal's name
     * @return the compact JWT
     * @throws IllegalStateException if analytics is not configured (UC-020 A1)
     */
    public String mint(String username) {
        if (!config.isEnabled()) {
            throw new IllegalStateException("Analytics is not configured for this deployment");
        }
        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(config.getGuestTokenTtlSeconds());

        User user = User.find("username", username).firstResult();
        Map<String, String> guestUser = Map.of(
                "username", username,
                "first_name", user != null && user.getFirstName() != null ? user.getFirstName() : "",
                "last_name", user != null && user.getLastName() != null ? user.getLastName() : "");

        JwtClaimsBuilder claims = Jwt.claims()
                .claim("user", guestUser)
                .claim("resources", List.of(Map.of("type", "dashboard", "id", config.getDashboardId().orElseThrow())))
                .claim("rls_rules", List.of())
                .claim("type", "guest")
                .issuedAt(now.getEpochSecond())
                .expiresAt(expiry.getEpochSecond());
        config.getGuestTokenAudience().ifPresent(claims::audience);

        return claims.jws()
                .algorithm(SignatureAlgorithm.HS256)
                .signWithSecret(config.getGuestTokenSecret().orElseThrow());
    }
}
