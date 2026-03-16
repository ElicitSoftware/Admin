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
 * REST endpoint that exports a complete survey definition as a safe import file.
 */
@Path("/secured/survey/export")
@ApplicationScoped
public class SurveyDefinitionExportResource {

    /**
     * Default constructor for CDI.
     */
    public SurveyDefinitionExportResource() {
        // CDI managed bean
    }

    @Inject
    SurveyDefinitionExportService surveyDefinitionExportService;

    /**
     * Export a survey definition as a custom-format file. All survey definition tables
     * (surveys, select_groups, select_items, steps, sections, steps_sections, questions,
     * sections_questions, relationships, reports, post_survey_actions, ontology, metadata)
     * are included.
     *
     * @param id the survey id to export
     * @return export file as a downloadable attachment
     */
    @GET
    @RolesAllowed("elicit_admin")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public Response exportSurvey(@QueryParam("id") Integer id) {
        if (id == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Missing required query parameter: id")
                    .type(MediaType.TEXT_PLAIN)
                    .build();
        }

        try {
            String exportData = surveyDefinitionExportService.exportSurvey(id);
            return Response.ok(exportData.getBytes(java.nio.charset.StandardCharsets.UTF_8))
                    .header("Content-Disposition", "attachment; filename=\"survey_" + id + "_definition.elicit\"")
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
