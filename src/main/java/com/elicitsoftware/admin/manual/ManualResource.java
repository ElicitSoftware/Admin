package com.elicitsoftware.admin.manual;

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

import com.elicitsoftware.security.ElicitRoles;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Response;

import java.io.InputStream;

/**
 * Serves the packaged administrator's manual at {@code /api/manual} (UC-029 step 2).
 * <p>
 * The path is relative to {@link com.elicitsoftware.config.RestApplication}'s
 * {@code @ApplicationPath("/api")}, so the reader's new tab opens {@code /api/manual}.
 * <p>
 * One document serves both console roles (UC-029 BR-006): {@code elicit_admin} and
 * {@code elicit_user} alike receive it, and the manual marks the procedures only an
 * administrator may carry out rather than being published twice. A signed-in reader holding
 * neither role is refused as the console's views refuse them (UC-029 A4), and an unauthenticated
 * request never reaches this resource — the {@code authenticated} HTTP policy challenges it first.
 * <p>
 * A build that carries no manual answers 404, and the navigation does not offer the entry in the
 * first place (UC-029 A1).
 */
@Path("/manual")
public class ManualResource {

    @Inject
    AdminManual manual;

    public ManualResource() {
        // CDI managed bean
    }

    /**
     * Streams the packaged manual to the reader's browser.
     *
     * @return 200 with the PDF, or 404 when this build carries no manual (UC-029 A1)
     */
    @GET
    @Produces("application/pdf")
    @RolesAllowed({ElicitRoles.ADMIN, ElicitRoles.USER})
    public Response manual() {
        InputStream pdf = manual.open();
        if (pdf == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        // "inline" so the browser's own viewer opens it in the new tab; the reader can still
        // save or print it from there (UC-029 A2).
        return Response.ok(pdf)
                .header("Content-Disposition", "inline; filename=\"" + AdminManual.FILE_NAME + "\"")
                .build();
    }
}
