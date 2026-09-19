package com.elicitsoftware.rest;

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

import com.elicitsoftware.analytics.AnalyticsConfig;
import com.elicitsoftware.analytics.SupersetGuestTokenService;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.Map;

/**
 * Issues Superset guest tokens to the embedded Analytics view (UC-020, BR-081). Called by the
 * browser-side embedding script on load and again whenever a token nears expiry (UC-020 A3);
 * only holders of {@code elicit_analytics} may call it, and only while analytics is configured.
 */
@Path("/secured/analytics/guest-token")
@ApplicationScoped
public class AnalyticsResource {

    @Inject
    AnalyticsConfig config;

    @Inject
    SupersetGuestTokenService guestTokenService;

    @Inject
    SecurityIdentity identity;

    /** Creates the resource; dependencies are injected by CDI. */
    public AnalyticsResource() {
    }

    /**
     * Mints a fresh guest token for the calling analyst.
     *
     * @return {@code {"token": "...", "expiresInSeconds": n}}, or 404 when analytics is not
     *         configured for this deployment (UC-020 A1)
     */
    @GET
    @RolesAllowed("elicit_analytics")
    @Produces(MediaType.APPLICATION_JSON)
    public Response guestToken() {
        if (!config.isEnabled()) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(Map.of("error", "Analytics is not configured for this deployment"))
                    .build();
        }
        String token = guestTokenService.mint(identity.getPrincipal().getName());
        return Response.ok(Map.of("token", token, "expiresInSeconds", config.getGuestTokenTtlSeconds()))
                .header("Cache-Control", "no-store")
                .build();
    }
}
