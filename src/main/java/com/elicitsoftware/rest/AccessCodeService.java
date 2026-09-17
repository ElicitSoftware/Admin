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

import com.elicitsoftware.admin.upload.MultipartBody;
import com.elicitsoftware.exception.AccessCodeGenerationError;
import com.elicitsoftware.model.*;
import com.elicitsoftware.request.AddRequest;
import com.elicitsoftware.response.AddResponse;
import com.elicitsoftware.response.AddResponseStatus;
import com.elicitsoftware.service.CsvImportService;
import com.elicitsoftware.util.LogMasking;
import com.elicitsoftware.util.RandomString;
import io.quarkus.logging.Log;
import io.quarkus.narayana.jta.QuarkusTransaction;
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
 * AccessCodeService registers survey subjects and issues their access codes.
 * <p>
 * This REST resource (Bearer-authenticated, under {@code /api/secured}) creates subjects,
 * generates a unique access code for each new respondent, and imports subjects from CSV.
 * The access code is the credential the respondent enters to reach the survey.
 *
 * @author Elicit Software
 * @since 1.0.0
 */
@Path("/secured")
@ApplicationScoped
public class AccessCodeService {

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

    /** How many candidate access codes {@link #generateAccessCode(int)} tries before giving up. */
    static final int MAX_ACCESS_CODE_ATTEMPTS = 4;

    /**
     * Initializes the AccessCodeService with a secure random access code generator.
     * <p>
     * Sets up the service with a random string generator that uses
     * easily distinguishable characters (avoiding similar-looking characters
     * like 0/O and 1/l) to create 9-character access codes.
     */
    public AccessCodeService() {
        super();
        String easy = RandomString.digits + "BCDFGHJKLMNPQRSTVWXZbcdfghjkmnpqrstvwxz2456789";
        generator = new RandomString(9, new SecureRandom(), easy);
    }

    /**
     * Adds a new subject to the survey system and generates an access code.
     * <p>
     * Creates a new subject record based on the provided request data,
     * generates a unique access code, and returns the response
     * containing the subject's status, including the access code.
     *
     * @param request the subject registration request containing demographic data
     * @return AddResponse containing the subject's status (with its access code) or an error message
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
                    Respondent respondent = generateAccessCode(request.surveyId);
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

        } catch (AccessCodeGenerationError e) {
            response.setError(e.getMessage());
        }
        return response;
    }

    /**
     * Adds multiple subjects to the survey system in bulk and generates access codes.
     * <p>
     * Processes an array of subject registration requests, creating subject records
     * and generating a unique access code for each valid subject. This method
     * provides bulk processing capabilities with individual error handling for each subject.
     *
     * <p><strong>Processing Logic:</strong></p>
     * <ul>
     *   <li><strong>Exclusion Check:</strong> Validates each XID against the exclusion list</li>
     *   <li><strong>Duplicate Detection:</strong> Checks for existing subjects before creation</li>
     *   <li><strong>Access Code Generation:</strong> Creates unique access codes for new subjects</li>
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
    public AddResponse putSubjects(List<AddRequest> requests) {
        AddResponse response = new AddResponse();

        // Each request gets its own transaction (requiringNew()) rather than sharing one
        // @Transactional boundary across the whole batch. A persistence failure on request N
        // used to be able to mark the shared transaction rollback-only, silently discarding
        // already-"successful" requests N+1..end at commit time even though the response
        // reported them as saved. With per-item transactions, a failure on request N is
        // isolated to request N's own commit; request N+1 still gets a fresh transaction.
        for (AddRequest request : requests) {
            AddResponseStatus addStatus;
            try {
                addStatus = QuarkusTransaction.requiringNew().call(() -> processSubjectRequest(request));
            } catch (Exception e) {
                // The per-item transaction failed to commit (e.g. a constraint violation
                // surfaced only at flush/commit time). Isolated to this item only.
                Status errorStatus = new Status();
                addStatus = new AddResponseStatus(errorStatus, "Unexpected error processing " + request.xid + ": " + e.getMessage());
            }
            response.addStatus(addStatus);
        }

        return response;
    }

    /**
     * Processes a single subject registration request within the caller's transaction.
     * <p>
     * Known/expected failure modes ({@link AccessCodeGenerationError}) are handled here and
     * turned into an error status. Any other, unexpected exception (e.g. a persistence
     * failure) is intentionally left to propagate so the per-item transaction boundary
     * in {@link #putSubjects(List)} rolls back exactly this request.
     *
     * @param request the subject registration request
     * @return the status for this single request
     */
    private AddResponseStatus processSubjectRequest(AddRequest request) {
        try {
            // Check if this xid and department should be excluded
            boolean isExcluded = ExcludedXid.isExcluded(request.xid, request.departmentId);
            if (isExcluded) {
                Status status = new Status();
                return new AddResponseStatus(status, "Excluded Subject: " + request.xid);
            }

            // Check if they are an existing respondent
            Status status = Status.findByXidAndDepartmentId(request.xid, request.departmentId);
            if (status == null) {
                // Create new subject
                Respondent respondent = generateAccessCode(request.surveyId);
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
                return new AddResponseStatus(status, "New Subject: " + request.xid);
            } else {
                return new AddResponseStatus(status, "Existing Subject: " + request.xid);
            }
        } catch (AccessCodeGenerationError e) {
            Status errorStatus = new Status();
            return new AddResponseStatus(errorStatus, "Error processing " + request.xid + ": " + e.getMessage());
        }
    }

    /**
     * Generates a unique access code for a survey.
     * <p>
     * Creates a new respondent record with an access code that is unique within the specified
     * survey. If access code generation fails after multiple attempts, throws an
     * exception to prevent infinite loops.
     *
     * @param surveyId the ID of the survey to generate an access code for
     * @return Respondent object containing the generated access code
     * @throws AccessCodeGenerationError if unable to generate a unique access code after multiple attempts
     */
    public Respondent generateAccessCode(int surveyId) {
        String accessCode = null;
        Respondent respondent = null;
        int tries = MAX_ACCESS_CODE_ATTEMPTS;
        Survey survey = Survey.findById(surveyId);
        try {
            while (tries > 0) {
                accessCode = generator.nextString();
                respondent = Respondent.findBySurveyAndAccessCode(surveyId, accessCode);
                if (respondent == null) {
                    respondent = new Respondent();
                    respondent.survey = survey;
                    respondent.accessCode = accessCode;
                    respondent.active = true;
                    return respondent;
                } else {
                    Log.info("Duplicate access code " + LogMasking.maskAccessCode(accessCode));
                }
                tries--;
            }
        } catch (Exception e) {
            //Pass along the error message.
            throw new AccessCodeGenerationError(e.getMessage());
        }
        //The tries worked but we couldn't find a unique access code. This should never happen.
        throw new AccessCodeGenerationError("Unable to generate a unique access code");
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
     * Returns a test message to confirm that the AccessCodeService is
     * accessible and functioning properly.
     *
     * @return a test message string
     */
    @Path("/test")
    @GET
    @PermitAll
    public String test() {
        return "access code service test";
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
    @RolesAllowed("elicit_admin")
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
