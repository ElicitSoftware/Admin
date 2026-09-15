package com.elicitsoftware.rest;

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

import com.elicitsoftware.admin.upload.MultipartBody;
import com.elicitsoftware.service.SurveyDefinitionApplyService;
import io.quarkus.logging.Log;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.io.IOException;
import java.util.UUID;

/**
 * REST endpoint that applies a survey definition file without the caller having to know whether
 * the survey already exists here — the file's {@code survey_key} decides, routing to create
 * (UC-014) or update (UC-017).
 * <p>
 * Prefer this over calling {@link SurveyDefinitionImportResource} or
 * {@link SurveyDefinitionUpdateResource} directly when distributing one authored file to several
 * deployments: whether it lands as a create or an update differs per site, and that is exactly
 * what this endpoint works out. The two explicit endpoints remain for the case where an operator
 * wants to assert which one should happen and have it fail if they're wrong.
 *
 * @see SurveyDefinitionApplyService
 */
@Path("/secured/survey/apply")
@ApplicationScoped
public class SurveyDefinitionApplyResource {

    /**
     * Default constructor for CDI.
     */
    public SurveyDefinitionApplyResource() {
        // CDI managed bean
    }

    @Inject
    SurveyDefinitionApplyService applyService;

    /**
     * Applies the uploaded definition file, selecting create or update by its {@code survey_key}.
     *
     * @param multipartBody multipart form data: the file in the "file" field
     * @return JSON reporting which action was taken and the underlying service's own result
     */
    @POST
    @RolesAllowed("elicit_admin")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    public Response applySurvey(MultipartBody multipartBody) {
        if (multipartBody.file == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ApplyResponse(false, null, "No file provided in the 'file' field", null))
                    .build();
        }

        try {
            byte[] data = multipartBody.file.readAllBytes();
            SurveyDefinitionApplyService.ApplyResult result = applyService.apply(data, multipartBody.fileName);

            ApplyResponse response = new ApplyResponse(result.success(), result.action().name(),
                    result.message(), result.detail());

            return result.success()
                    ? Response.ok(response).build()
                    : Response.status(Response.Status.BAD_REQUEST).entity(response).build();

        } catch (IOException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ApplyResponse(false, null, "Failed to read the uploaded file", null))
                    .build();
        } catch (Exception e) {
            String correlationId = UUID.randomUUID().toString();
            Log.errorf(e, "Survey definition apply failed [correlationId=%s]", correlationId);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ApplyResponse(false, null,
                            "Apply failed due to an unexpected error. Reference: " + correlationId, null))
                    .build();
        }
    }

    /**
     * Response DTO reporting the routing decision alongside the underlying result.
     *
     * @param success whether the file was applied
     * @param action {@code IMPORT}, {@code UPDATE}, or {@code REJECTED}
     * @param message a human-readable summary or the failure reason
     * @param detail the import or update service's own result object, or {@code null}
     */
    public record ApplyResponse(boolean success, String action, String message, Object detail) {
    }
}
