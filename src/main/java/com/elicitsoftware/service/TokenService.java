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
import com.elicitsoftware.exception.TokenGenerationError;
import com.elicitsoftware.model.*;
import com.elicitsoftware.request.AddRequest;
import com.elicitsoftware.response.AddResponse;
import com.elicitsoftware.response.AddResponseStatus;
import com.elicitsoftware.service.CsvImportService;
import com.elicitsoftware.util.RandomString;
import io.quarkus.logging.Log;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import org.eclipse.microprofile.jwt.JsonWebToken;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.UriInfo;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

/**
 * TokenService provides token-based authentication and subject management for surveys.
 * <p>
 * This REST service handles secure token generation, subject registration,
 * and authentication operations for the survey system. It provides endpoints
 * for adding subjects and retrieving authentication tokens.
 *
 * @author Elicit Software
 * @since 1.0.0
 */
@Path("/secured")
@ApplicationScoped
public class TokenService {

    /**
     * CSV import service for processing participant data from uploaded files.
     * <p>
     * This service handles the core business logic for parsing CSV files,
     * validating participant data, and creating participant records in the database.
     */
    @Inject
    CsvImportService csvImportService;

    /**
     * Security identity for accessing authenticated user information and roles.
     */
    @Inject
    SecurityIdentity securityIdentity;

    /**
     * JSON Web Token for accessing token claims and metadata.
     */
    @Inject
    JsonWebToken jwt;

    @Context
    private UriInfo uriInfo;
    private RandomString generator = null;

    /**
     * Initializes the TokenService with a secure random token generator.
     * <p>
     * Sets up the service with a random string generator that uses
     * easily distinguishable characters (avoiding similar-looking characters
     * like 0/O and 1/l) to create 9-character authentication tokens.
     */
    public TokenService() {
        super();
        String easy = RandomString.digits + "BCDFGHJKLMNPQRSTVWXZbcdfghjkmnpqrstvwxz2456789";
        generator = new RandomString(9, new SecureRandom(), easy);
    }

    /**
     * Adds a new subject to the survey system and generates an authentication token.
     * <p>
     * Creates a new subject record based on the provided request data,
     * generates a secure authentication token, and returns the response
     * containing the subject ID and token for survey access.
     *
     * @param request the subject registration request containing demographic data
     * @return AddResponse containing the new subject ID, authentication token, or error message
     */
    @Path("/add/subject")
    @POST
    @RolesAllowed({"elicit_importer", "elicit_admin", "elicit_user"})
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Transactional
    public AddResponse putSubject(AddRequest request) {
        AddResponse response = new AddResponse();
        AddResponseStatus addStatus;
        try {
            // Check if this xid and department should be exluded.
            boolean isExluded = ExcludedXid.isExcluded(request.xid, request.departmentId);
            if (isExluded) {
                Status status = new Status();
                addStatus = new AddResponseStatus(status, "Exluded Subject");
            } else {
                // Check if they are an existing respondent.
                Status status = Status.findByXidAndDepartmentId(request.xid, request.departmentId);
                if (status == null) {
                    Respondent respondent = getToken(request.surveyId);
                    Subject subject = new Subject(request.xid, request.surveyId, request.departmentId, request.firstName, request.lastName, request.middleName, request.dob, request.email, request.phone);
                    subject.setRespondent(respondent);
                    subject.persistAndFlush();
                    ArrayList<Message> messages = Message.createMessagesForSubject(subject);
                    for (Message message : messages) {
                        message.persistAndFlush();
                    }
                    status = Status.findByXidAndDepartmentId(request.xid, request.departmentId);
                    addStatus = new AddResponseStatus(status, "New Subject");
                } else {
                    addStatus = new AddResponseStatus(status, "Existing Subject");
                }
            }
            response.addStatus(addStatus);

        } catch (TokenGenerationError e) {
            response.setError(e.getMessage());
        }
        return response;
    }

    /**
     * Adds multiple subjects to the survey system in bulk and generates authentication tokens.
     * <p>
     * Processes an array of subject registration requests, creating subject records
     * and generating secure authentication tokens for each valid subject. This method
     * provides bulk processing capabilities with individual error handling for each subject.
     *
     * <p><strong>Processing Logic:</strong></p>
     * <ul>
     *   <li><strong>Exclusion Check:</strong> Validates each XID against the exclusion list</li>
     *   <li><strong>Duplicate Detection:</strong> Checks for existing subjects before creation</li>
     *   <li><strong>Token Generation:</strong> Creates unique tokens for new subjects</li>
     *   <li><strong>Message Creation:</strong> Generates communication messages for each subject</li>
     *   <li><strong>Individual Handling:</strong> Each subject is processed independently</li>
     * </ul>
     *
     * <p><strong>Response Structure:</strong></p>
     * <ul>
     *   <li>Contains an array of {@link AddResponseStatus} objects</li>
     *   <li>Each status corresponds to one input subject request</li>
     *   <li>Includes success/failure status and descriptive messages</li>
     *   <li>Failed subjects don't prevent processing of other subjects</li>
     * </ul>
     *
     * @param requests array of subject registration requests containing demographic data
     * @return AddResponse containing status for each subject (success, exclusion, existing, or error)
     */
    @Path("/add/subjects")
    @POST
    @RolesAllowed({"elicit_importer", "elicit_admin", "elicit_user"})
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Transactional
    public AddResponse putSubjects(List<AddRequest> requests) {
        AddResponse response = new AddResponse();

        // Process each subject request individually
        for (AddRequest request : requests) {
            AddResponseStatus addStatus;
            try {
                // Check if this xid and department should be excluded
                boolean isExcluded = ExcludedXid.isExcluded(request.xid, request.departmentId);
                if (isExcluded) {
                    Status status = new Status();
                    addStatus = new AddResponseStatus(status, "Excluded Subject: " + request.xid);
                } else {
                    // Check if they are an existing respondent
                    Status status = Status.findByXidAndDepartmentId(request.xid, request.departmentId);
                    if (status == null) {
                        // Create new subject
                        Respondent respondent = getToken(request.surveyId);
                        Subject subject = new Subject(request.xid, request.surveyId, request.departmentId,
                                                    request.firstName, request.lastName, request.middleName,
                                                    request.dob, request.email, request.phone);
                        subject.setRespondent(respondent);
                        subject.persistAndFlush();

                        // Create messages for the new subject
                        ArrayList<Message> messages = Message.createMessagesForSubject(subject);
                        for (Message message : messages) {
                            message.persistAndFlush();
                        }

                        status = Status.findByXidAndDepartmentId(request.xid, request.departmentId);
                        addStatus = new AddResponseStatus(status, "New Subject: " + request.xid);
                    } else {
                        addStatus = new AddResponseStatus(status, "Existing Subject: " + request.xid);
                    }
                }
            } catch (TokenGenerationError e) {
                // Create error status for this individual subject
                Status errorStatus = new Status();
                addStatus = new AddResponseStatus(errorStatus, "Error processing " + request.xid + ": " + e.getMessage());
            } catch (Exception e) {
                // Handle any other unexpected errors for this subject
                Status errorStatus = new Status();
                addStatus = new AddResponseStatus(errorStatus, "Unexpected error processing " + request.xid + ": " + e.getMessage());
            }

            response.addStatus(addStatus);
        }

        return response;
    }

    /**
     * Generates and retrieves a unique authentication token for a survey.
     * <p>
     * Creates a new respondent record with a unique token for the specified
     * survey. If token generation fails after multiple attempts, throws an
     * exception to prevent infinite loops.
     *
     * @param surveyId the ID of the survey to generate a token for
     * @return Respondent object containing the generated token
     * @throws TokenGenerationError if unable to generate a unique token after multiple attempts
     */
    public Respondent getToken(int surveyId) {
        String token = null;
        Respondent respondent = null;
        int tries = 4; // Lets only try this three times.
        Survey survey = Survey.findById(surveyId);
        try {
            while (tries > 0) {
                token = generator.nextString();
                respondent = Respondent.findBySurveyAndToken(surveyId, token);
                if (respondent == null) {
                    respondent = new Respondent();
                    respondent.survey = survey;
                    respondent.token = token;
                    respondent.active = true;
                    return respondent;
                } else {
                    Log.info("Duplicate token " + token);
                }
                tries++;
            }
        } catch (Exception e) {
            //Pass along the error message.
            throw new TokenGenerationError(e.getMessage());
        }
        //The tries worked but we couldn't find a unique token. This should never happen.
        throw new TokenGenerationError("Unable to generate a unique token");
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
    @Path("/add/csv")
    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @RolesAllowed("elicit_importer")
    @Transactional
    public AddResponse importCsv(MultipartBody multipartBody) {
        Log.info("Upload CSV file received");
        try {
            // Validate that a file was provided
            if (multipartBody.file == null) {
                AddResponse errorResponse = new AddResponse();
                errorResponse.setError("No CSV file provided in the 'file' field");
                return errorResponse;
            }
            // Process the CSV import
            AddResponse response = csvImportService.importSubjects(multipartBody.file);
            return response;

        } catch (Exception e) {
            // Handle validation errors and other exceptions
            AddResponse errorResponse = new AddResponse();
            errorResponse.setError(e.getMessage());
            return errorResponse;
        }
    }
    /**
     * Simple test endpoint to verify service availability.
     * <p>
     * Returns a test message to confirm that the TokenService is
     * accessible and functioning properly.
     *
     * @return a test message string
     */
    @Path("/test")
    @GET
    @PermitAll
    public String test() {
        return "token test";
    }

    /**
     * Returns the current user's roles and security information.
     * <p>
     * Provides detailed diagnostic information about the JWT token including:
     * <ul>
     *   <li>Principal name and authentication status</li>
     *   <li>Roles extracted by Quarkus Security</li>
     *   <li>Token type (user vs service principal)</li>
     *   <li>Raw token claims for debugging</li>
     * </ul>
     *
     * @return a string containing comprehensive security and token information
     */
    @Path("/roles")
    @GET
    @RolesAllowed({"elicit_importer", "elicit_admin", "elicit_user"})
    public String roles() {
        StringBuilder sb = new StringBuilder();
        
        // Check for principal
        if (securityIdentity.getPrincipal() == null) {
            sb.append("No principal found\n");
            return sb.toString();
        }

        // Basic identity information
        sb.append("=== Security Identity ===\n");
        sb.append("Principal Name: ").append(securityIdentity.getPrincipal().getName()).append("\n");
        sb.append("Is Anonymous: ").append(securityIdentity.isAnonymous()).append("\n");
        sb.append("Roles from SecurityIdentity: ").append(securityIdentity.getRoles()).append("\n\n");

        // JWT Token information
        try {
            if (jwt != null) {
                sb.append("=== JWT Token Information ===\n");
                
                // Check if JWT was actually parsed
                try {
                    String tokenName = jwt.getName();
                    sb.append("Token Name: ").append(tokenName != null ? tokenName : "null (JWT not parsed)").append("\n");
                } catch (Exception e) {
                    sb.append("Token Name: Error - ").append(e.getMessage()).append("\n");
                }
                
                try {
                    String subject = jwt.getSubject();
                    sb.append("Subject (sub): ").append(subject != null ? subject : "null").append("\n");
                } catch (Exception e) {
                    sb.append("Subject (sub): Error - ").append(e.getMessage()).append("\n");
                }
                
                try {
                    String issuer = jwt.getIssuer();
                    sb.append("Issuer (iss): ").append(issuer != null ? issuer : "null").append("\n");
                } catch (Exception e) {
                    sb.append("Issuer (iss): Error - ").append(e.getMessage()).append("\n");
                }
                
                // If all basic claims are null, the JWT wasn't parsed
                if (jwt.getName() == null && jwt.getSubject() == null && jwt.getIssuer() == null) {
                    sb.append("\n⚠️  JWT object exists but has no claims - token validation likely failed\n");
                    sb.append("Possible issues:\n");
                    sb.append("  1. Token signature validation failed\n");
                    sb.append("  2. Token issuer (iss) not trusted in quarkus.oidc.token.issuer configuration\n");
                    sb.append("  3. Token audience (aud) doesn't match quarkus.oidc.client-id\n");
                    sb.append("  4. Token expired (check exp claim)\n");
                    sb.append("  5. OIDC configuration missing or incorrect\n");
                    sb.append("  6. JWKS endpoint unreachable for signature verification\n\n");
                }
                
                // Try to get claim names safely
                sb.append("=== Attempting to read claims ===\n");
                try {
                    if (jwt.getClaimNames() != null && !jwt.getClaimNames().isEmpty()) {
                        sb.append("Found ").append(jwt.getClaimNames().size()).append(" claims:\n");
                        for (String claimName : jwt.getClaimNames()) {
                            try {
                                Object claimValue = jwt.getClaim(claimName);
                                sb.append("  ").append(claimName).append(": ").append(String.valueOf(claimValue)).append("\n");
                            } catch (Exception e) {
                                sb.append("  ").append(claimName).append(": Error reading - ").append(e.getMessage()).append("\n");
                            }
                        }
                    } else {
                        sb.append("getClaimNames() returned null or empty - JWT was not successfully parsed\n");
                    }
                } catch (Exception e) {
                    sb.append("Error iterating claims: ").append(e.getMessage()).append("\n");
                }
            } else {
                sb.append("\n=== JWT Token ===\n");
                sb.append("JWT is null - token may not be properly injected\n");
                sb.append("This usually means no Bearer token was provided in the Authorization header\n");
            }
        } catch (Exception e) {
            sb.append("\n=== JWT Error ===\n");
            sb.append("Unexpected error accessing JWT: ").append(e.getClass().getName()).append(": ").append(e.getMessage()).append("\n");
            if (e.getCause() != null) {
                sb.append("Caused by: ").append(e.getCause().getClass().getName()).append(": ").append(e.getCause().getMessage()).append("\n");
            }
        }

        return sb.toString();
    }
}
