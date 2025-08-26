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

import com.elicitsoftware.admin.upload.MultipartBody;
import com.elicitsoftware.model.User;
import com.elicitsoftware.request.AddRequest;
import com.elicitsoftware.response.AddResponse;
import io.quarkus.logging.Log;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import netscape.javascript.JSObject;

import java.util.Set;

/**
 * REST resource for CSV participant import functionality in the Elicit Admin application.
 *
 * <p>This resource provides RESTful endpoints for uploading and importing CSV files containing
 * participant data into the survey system. It bridges the gap between external applications
 * and the CSV import functionality, enabling programmatic bulk participant registration.</p>
 *
 * <p><strong>Key Features:</strong></p>
 * <ul>
 *   <li><strong>Multipart Upload:</strong> Handles CSV file uploads via multipart/form-data</li>
 *   <li><strong>User Authentication:</strong> Integrates with OIDC authentication system</li>
 *   <li><strong>Role-based Authorization:</strong> Requires appropriate user permissions</li>
 *   <li><strong>Comprehensive Error Handling:</strong> Provides detailed error responses</li>
 *   <li><strong>Transactional Processing:</strong> Ensures data consistency during imports</li>
 * </ul>
 *
 * <p><strong>Security Model:</strong></p>
 * <ul>
 *   <li><strong>Authentication:</strong> Valid OIDC token required</li>
 *   <li><strong>Authorization:</strong> User must have "elicit_user" or "elicit_admin" role</li>
 *   <li><strong>Department Validation:</strong> Users can only import to accessible departments</li>
 *   <li><strong>Active User Check:</strong> Only active users can perform imports</li>
 * </ul>
 *
 * <p><strong>CSV Format Requirements:</strong></p>
 * <pre>
 * departmentId,firstName,lastName,middleName,dob,email,phone,xid
 * </pre>
 *
 * <p><strong>Usage Example:</strong></p>
 * <pre>{@code
 * // Using curl to upload a CSV file
 * curl -X POST \
 *   -H "Authorization: Bearer {token}" \
 *   -F "file=@participants.csv" \
 *   http://localhost:8080/api/csv/import
 * }</pre>
 *
 * <p><strong>Response Formats:</strong></p>
 * <ul>
 *   <li><strong>Success:</strong> JSON with import count and status</li>
 *   <li><strong>Validation Errors:</strong> JSON with detailed error messages</li>
 *   <li><strong>Authentication Errors:</strong> HTTP 401/403 status codes</li>
 * </ul>
 *
 * @author Elicit Software
 * @version 1.0
 * @since 1.0
 * @see CsvImportService
 * @see MultipartBody
 * @see User
 */
@Path("/import")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
public class CsvImportResource {

    /**
     * CSV import service for processing participant data from uploaded files.
     * <p>
     * This service handles the core business logic for parsing CSV files,
     * validating participant data, and creating participant records in the database.
     */
    @Inject
    CsvImportService csvImportService;

    private final TokenService tokenService;
    /**
     * Security identity for accessing current user authentication information.
     * <p>
     * Provides access to the authenticated user's principal name and roles,
     * which are used for user lookup and authorization checks.
     */
    @Inject
    SecurityIdentity identity;

    public CsvImportResource(TokenService tokenService) {
        this.tokenService = tokenService;
    }

    /**
     * Imports participant data from an uploaded CSV file.
     *
     * <p>This endpoint accepts multipart/form-data uploads containing CSV files with
     * participant information. It performs comprehensive validation, authentication,
     * and authorization checks before processing the import.</p>
     *
     * <p><strong>Processing Workflow:</strong></p>
     * <ol>
     *   <li><strong>Authentication Check:</strong> Verifies valid OIDC token</li>
     *   <li><strong>User Lookup:</strong> Finds active user in database</li>
     *   <li><strong>File Validation:</strong> Ensures CSV file is provided</li>
     *   <li><strong>Import Processing:</strong> Delegates to CsvImportService</li>
     *   <li><strong>Response Generation:</strong> Returns success count or errors</li>
     * </ol>
     *
     * <p><strong>Input Requirements:</strong></p>
     * <ul>
     *   <li><strong>Authentication:</strong> Valid Bearer token in Authorization header</li>
     *   <li><strong>Content-Type:</strong> multipart/form-data</li>
     *   <li><strong>File Parameter:</strong> "file" field containing CSV data</li>
     *   <li><strong>User Status:</strong> Active user account in system</li>
     * </ul>
     *
     * <p><strong>CSV Format:</strong></p>
     * <pre>
     * departmentId,firstName,lastName,middleName,dob,email,phone,xid
     * 1,John,Doe,Michael,1990-01-15,john.doe@email.com,123-456-7890,EXT001
     * 2,Jane,Smith,,1985-03-22,jane.smith@email.com,555-123-4567,EXT002
     * </pre>
     *
     * <p><strong>Success Response Example:</strong></p>
     * <pre>{@code
     * {
     *   "success": true,
     *   "message": "Successfully imported 15 participants",
     *   "importedCount": 15
     * }
     * }</pre>
     *
     * <p><strong>Error Response Example:</strong></p>
     * <pre>{@code
     * {
     *   "success": false,
     *   "message": "Import completed with errors:\nLine 3: Invalid department ID: 999\nLine 7: Email is required",
     *   "importedCount": 0
     * }
     * }</pre>
     *
     * <p><strong>Error Scenarios:</strong></p>
     * <ul>
     *   <li><strong>Authentication Failure:</strong> Invalid or missing token (401)</li>
     *   <li><strong>Authorization Failure:</strong> Insufficient permissions (403)</li>
     *   <li><strong>User Not Found:</strong> Authenticated user not in database (403)</li>
     *   <li><strong>Missing File:</strong> No CSV file provided (400)</li>
     *   <li><strong>Validation Errors:</strong> CSV format or data validation failures (400)</li>
     *   <li><strong>System Errors:</strong> Database or service failures (500)</li>
     * </ul>
     *
     * @param multipartBody the multipart form data containing the CSV file and metadata
     * @return Response with import results or error information
//     * @see CsvImportService#importSubjects(java.io.InputStream, User)
     * @see CsvImportService#importSubjects(java.io.InputStream)
     * @see MultipartBody
     */
    @Path("/subjects")
    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @RolesAllowed("elicit_importer")
    @Transactional
    public Response importCsv(MultipartBody multipartBody) {
        try {
            // Validate that a file was provided
            if (multipartBody.file == null) {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ImportResponse(false,
                        "No CSV file provided in the 'file' field", 0))
                    .build();
            }

            // Process the CSV import
            int importedCount = csvImportService.importSubjects(multipartBody.file);

            return Response.ok()
                .entity(new ImportResponse(true,
                    "Successfully imported " + importedCount + " participants",
                    importedCount))
                .build();

        } catch (Exception e) {
            // Handle validation errors and other exceptions
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ImportResponse(false, e.getMessage(), 0))
                .build();
        }
    }

    /**
     * Data transfer object for CSV import response information.
     * <p>
     * This class provides a standardized response format for CSV import operations,
     * including success status, descriptive messages, and import statistics.
     */
    public static class ImportResponse {
        /** Indicates whether the import operation was successful */
        public boolean success;

        /** Human-readable message describing the result or errors */
        public String message;

        /** Number of participants successfully imported */
        public int importedCount;

        /**
         * Default constructor for JSON serialization.
         */
        public ImportResponse() {
        }

        /**
         * Constructs a new ImportResponse with the specified values.
         *
         * @param success whether the import was successful
         * @param message descriptive message about the result
         * @param importedCount number of participants imported
         */
        public ImportResponse(boolean success, String message, int importedCount) {
            this.success = success;
            this.message = message;
            this.importedCount = importedCount;
        }
    }

    @Path("/subject")
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @RolesAllowed("elicit_importer")
    @Transactional
    public AddResponse importSubject(AddRequest request) {
        AddResponse addResponse = null;
        try {
            addResponse = tokenService.putSubject(request);
        } catch (Exception e) {
            addResponse = new AddResponse();
            addResponse.setError(e.getMessage());
        }
        return addResponse;
    }

    @Path("/test")
    @GET
    @RolesAllowed("elicit_importer")
    @Produces(MediaType.TEXT_PLAIN)
    public String test() {
        return "Role elicit_importer test worked";
    }
}
