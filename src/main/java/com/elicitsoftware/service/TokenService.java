package com.elicitsoftware.service;

import com.elicitsoftware.exception.TokenGenerationError;
import com.elicitsoftware.model.*;
import com.elicitsoftware.request.AddRequest;
import com.elicitsoftware.response.AddResponse;
import com.elicitsoftware.util.RandomString;
import io.quarkus.logging.Log;
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
import java.util.Date;

@Path("/secured")
@ApplicationScoped
public class TokenService {

    @Context
    private UriInfo uriInfo;
    private RandomString generator = null;

    public TokenService() {
        super();
        String easy = RandomString.digits + "BCDFGHJKLMNPQRSTVWXZbcdfghjkmnpqrstvwxz2456789";
        generator = new RandomString(9, new SecureRandom(), easy);
    }

    @Path("/add/subject")
    @POST
    @RolesAllowed({"elicit_token", "elicit_admin", "elicit_user"})
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
            for(Message message : messages) {
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

    @Path("/test")
    @GET
    public String test() {
        return "token test";
    }
}
