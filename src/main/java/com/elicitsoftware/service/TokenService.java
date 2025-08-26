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

import com.elicitsoftware.exception.TokenGenerationError;
import com.elicitsoftware.model.Message;
import com.elicitsoftware.model.Respondent;
import com.elicitsoftware.model.Subject;
import com.elicitsoftware.model.Survey;
import com.elicitsoftware.request.AddRequest;
import com.elicitsoftware.response.AddResponse;
import com.elicitsoftware.util.RandomString;
import io.quarkus.logging.Log;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.UriInfo;

import java.security.SecureRandom;
import java.util.ArrayList;

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
        try {
            Respondent respondent = getToken(request.surveyId);
            Subject subject = new Subject(request.xid, request.surveyId, request.departmentId, request.firstName, request.lastName, request.middleName, request.dob, request.email, request.phone);
            subject.setRespondent(respondent);
            subject.persistAndFlush();
            ArrayList<Message> messages = Message.createMessagesForSubject(subject);
            for (Message message : messages) {
                message.persistAndFlush();
            }
            response.setRespondentId(respondent.id);
            response.setToken(respondent.token);
            return response;
        } catch (TokenGenerationError e) {
            response.setError(e.getMessage());
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
     * Simple test endpoint to verify service availability.
     * <p>
     * Returns a test message to confirm that the TokenService is
     * accessible and functioning properly.
     *
     * @return a test message string
     */
    @Path("/test")
    @GET
    public String test() {
        return "token test";
    }
}
