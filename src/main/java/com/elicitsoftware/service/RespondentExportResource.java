package com.elicitsoftware.service;

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

import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * REST endpoint that exports respondent data as a safe import file.
 */
@Path("/secured/export")
@ApplicationScoped
public class RespondentExportResource {

    /**
     * Default constructor for CDI.
     */
    public RespondentExportResource() {
        // CDI managed bean
    }

    @Inject
    RespondentExportService respondentExportService;

    /**
     * Export respondent data as a custom format file. Preferred query parameter is "id".
     *
     * @param id respondent id
     * @param respondentIdAlias optional alias query parameter: respondent_id
     * @return Export file as a downloadable text file
     */
    @GET
    @RolesAllowed("elicit_admin")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public Response exportRespondent(@QueryParam("id") Integer id,
                                     @QueryParam("respondent_id") Integer respondentIdAlias) {
        Integer respondentId = id != null ? id : respondentIdAlias;
        if (respondentId == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Missing required query parameter: id")
                    .type(MediaType.TEXT_PLAIN)
                    .build();
        }

        try {
            String exportData = respondentExportService.exportRespondent(respondentId);
            return Response.ok(exportData.getBytes(java.nio.charset.StandardCharsets.UTF_8))
                    .header("Content-Disposition", "attachment; filename=\"respondent_" + respondentId + "_export.elicit\"")
                    .type(MediaType.APPLICATION_OCTET_STREAM)
                    .build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(e.getMessage())
                    .type(MediaType.TEXT_PLAIN)
                    .build();
        }
    }
}
