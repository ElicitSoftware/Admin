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

import com.elicitsoftware.admin.i18n.Translations;
import com.elicitsoftware.admin.upload.MultipartBody;
import com.elicitsoftware.exception.AccessCodeGenerationError;
import com.elicitsoftware.model.*;
import com.elicitsoftware.request.AddRequest;
import com.elicitsoftware.response.AddResponse;
import com.elicitsoftware.response.AddResponseStatus;
import com.elicitsoftware.security.DepartmentScope;
import com.elicitsoftware.security.ElicitRoles;
import com.elicitsoftware.service.CsvImportException;
import com.elicitsoftware.service.CsvImportService;
import com.elicitsoftware.service.CsvSurveyMissingException;
import com.elicitsoftware.service.DepartmentService;
import com.elicitsoftware.util.LogMasking;
import com.elicitsoftware.util.RandomString;
import io.quarkus.logging.Log;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.annotation.security.RolesAllowed;
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
import java.util.UUID;

/**
 * AccessCodeService registers survey subjects and issues their access codes.
 * <p>
 * This REST resource (Bearer-authenticated, under {@code /api/secured}) creates subjects,
 * generates a unique access code for each new respondent, and imports subjects from CSV.
 * The access code is the credential the respondent enters to reach the survey.
 *
 * @author Elicit Software
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

    /** The surveys each department registers onto (UC-006 BR-172, UC-010 BR-177). */
    @Inject
    DepartmentService departmentService;

    /** Decides which departments the caller may register subjects in (UC-010 BR-137). */
    @Inject
    DepartmentScope departmentScope;

    @Context
    private UriInfo uriInfo;
    private RandomString generator = null;

    /**
     * The characters an access code is drawn from (UC-015 BR-050): upper-case consonants,
     * lower-case consonants without {@code l}, and the digits that are not mistaken for a letter.
     * Each appears once, so each is equally likely at every position.
     */
    static final String ACCESS_CODE_ALPHABET = RandomString.upper + "bcdfghjkmnpqrstvwxz" + RandomString.digits;

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
        generator = new RandomString(9, new SecureRandom(), ACCESS_CODE_ALPHABET);
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
    @RolesAllowed(ElicitRoles.IMPORTER)
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Transactional
    public AddResponse putSubject(AddRequest request) {
        // UC-010 BR-034, BR-170: the API is for import clients only.
        departmentScope.requireImportClient();
        return registerSubject(request);
    }

    /**
     * Registers one subject for the current principal, in a department its record is assigned
     * to. This is what {@link #putSubject(AddRequest)} does for an import client and what a row
     * of a CSV file comes to, on the API and on the Register page alike; it carries no role of
     * its own, so a user's upload on the Register page runs under the user's role and console
     * user record (UC-003 A6) and is not a use of the registration API.
     *
     * @param request the subject registration request
     * @return the subject's status (with its access code when this call created it) or an error
     * @throws jakarta.ws.rs.ForbiddenException when the department is not one of the principal's
     */
    @Transactional
    public AddResponse registerSubject(AddRequest request) {
        // UC-010 BR-137: before the exclusion and duplicate checks, so a refusal says nothing
        // about the department's subjects.
        departmentScope.require(request.departmentId);
        // UC-010 BR-177: refused alike when the department does not register onto the survey.
        if (!offersSurvey(request.departmentId, request.surveyId)) {
            throw new ForbiddenException();
        }
        AddResponse response = new AddResponse();
        AddResponseStatus addStatus;
        try {
            // Check if this xid and department should be excluded.
            boolean isExcluded = ExcludedXid.isExcluded(request.xid, request.departmentId);
            if (isExcluded) {
                Status status = new Status();
                addStatus = new AddResponseStatus(status, "Excluded Subject");
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
                    addStatus = new AddResponseStatus(outcomeOnly(status), "Existing Subject");
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
    @RolesAllowed(ElicitRoles.IMPORTER)
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public AddResponse putSubjects(List<AddRequest> requests) {
        // UC-010 BR-034, BR-170: the API is for import clients only.
        departmentScope.requireImportClient();
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
                addStatus = new AddResponseStatus(errorStatus, "Unexpected error processing " + request.xid + " " + logged(e));
            }
            response.addStatus(addStatus);
        }

        return response;
    }

    /**
     * What an answer says of a subject that already exists (UC-010 BR-164): the external ID and
     * the department, which the caller sent, and no stored field. The caller did not create
     * this subject, so its name, contact data, status and access code are not the caller's to
     * be handed.
     *
     * @param existing the stored subject's status row
     * @return a status carrying only the external ID and the department
     */
    static Status outcomeOnly(Status existing) {
        Status outcome = new Status();
        outcome.setXid(existing.getXid());
        outcome.setDepartmentId(existing.getDepartmentId());
        // Its two dates are the moment of this answer, as in the empty status of an excluded
        // record; the stored subject's dates are not copied.
        return outcome;
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
        // UC-010 BR-137: a record for a department the caller is not assigned to is answered
        // before anything about that department is looked up; the rest of the batch goes on.
        if (!departmentScope.permits(request.departmentId)) {
            return new AddResponseStatus(new Status(), "Not permitted: " + request.xid);
        }
        // UC-010 BR-177, A8: nor onto a survey the department does not register onto.
        if (!offersSurvey(request.departmentId, request.surveyId)) {
            return new AddResponseStatus(new Status(), "Not permitted: " + request.xid);
        }
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
                return new AddResponseStatus(outcomeOnly(status), "Existing Subject: " + request.xid);
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
            // UC-010 BR-139: the caller is told that it failed and under which reference; the
            // cause stays in the log.
            throw new AccessCodeGenerationError(logged(e));
        }
        //The tries worked but we couldn't find a unique access code. This should never happen.
        throw new AccessCodeGenerationError(Translations.get("accessCode.noUniqueCode"));
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
     * surveyId,departmentId,xid,firstName,lastName,middleName,dob,email,phone
     * 1,1,EXT001,John,Doe,Michael,1990-01-15,john.doe@email.com,123-456-7890
     * 1,2,EXT002,Jane,Smith,,1985-03-22,jane.smith@email.com,555-123-4567
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
     * @see CsvImportService#importSubjects(java.io.InputStream)
     * @see MultipartBody
     */
    @Path("/add/csv")
    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @RolesAllowed(ElicitRoles.IMPORTER)
    @Transactional
    public AddResponse importCsv(MultipartBody multipartBody) {
        // UC-010 BR-034, BR-170: the API is for import clients only. Outside the try below,
        // which would turn the refusal into an answer.
        departmentScope.requireImportClient();
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

        } catch (CsvSurveyMissingException | CsvImportException e) {
            // The rejected lines, each with what the caller has to correct; or the whole file,
            // when a row names no survey (BR-178).
            AddResponse errorResponse = new AddResponse();
            errorResponse.setError(e.getMessage());
            return errorResponse;
        } catch (Exception e) {
            AddResponse errorResponse = new AddResponse();
            errorResponse.setError(logged(e));
            return errorResponse;
        }
    }

    /**
     * Whether the caller may register subjects in a department (UC-010 BR-137). The CSV import
     * asks before it hands a line to {@link #registerSubject(AddRequest)}, so a refused line is
     * reported by its number and the other lines go on.
     *
     * @param departmentId the department a record names
     * @return true when the caller may register into it
     */
    public boolean permitsDepartment(long departmentId) {
        return departmentScope.permits(departmentId);
    }

    /**
     * Whether a subject in this department may be registered onto this survey (UC-006 BR-172,
     * UC-010 BR-177). The CSV import asks it for each line, as it asks
     * {@link #permitsDepartment(long)}.
     *
     * @param departmentId the department a record names
     * @param surveyId     the survey it names
     * @return true when the department lists the survey
     */
    public boolean offersSurvey(long departmentId, long surveyId) {
        return departmentService.offersSurvey(departmentId, surveyId);
    }

    /**
     * Logs an unexpected failure and returns the fixed phrase the caller receives in its place
     * (UC-010 BR-139): the phrase carries a reference that is on the log line, and nothing of
     * the failure itself.
     */
    static String logged(Exception e) {
        String reference = UUID.randomUUID().toString().substring(0, 8);
        Log.errorf(e, "Subject registration failed (ref %s)", reference);
        return "Error (ref " + reference + ")";
    }
}
