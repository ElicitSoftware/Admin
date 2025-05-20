package com.elicitsoftware.service;

import com.elicitsoftware.exception.TokenGenerationError;
import com.elicitsoftware.model.Respondent;
import com.elicitsoftware.model.Survey;
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
import jakarta.ws.rs.core.UriInfo;

import java.security.SecureRandom;
import java.util.Date;

@Path("/secured")
@ApplicationScoped
public class TokenService {

    @Context
    private UriInfo uriInfo;

    private RandomString generator = null;

    @Inject
    TransactionService transactionService;

    public TokenService() {
        super();
        String easy = RandomString.digits + "BCDFGHJKLMNPQRSTVWXZbcdfghjkmnpqrstvwxz2456789";
        generator = new RandomString(9, new SecureRandom(), easy);
    }

    @Path("/add")
    @POST
    @RolesAllowed("tokenservice-user")
    @Produces("application/json")
    @Consumes("application/json")
    @Transactional
    public AddResponse putToken(AddRequest req) {
        AddResponse response = new AddResponse();
        try {
            Respondent respondent = getToken(req.getSurveyId());
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
//                    respondent.persist();
//                    respondent = transactionService.saveRespondent(respondent);
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

    public Respondent deactivate(long respondentId) {
        Respondent user = Respondent.findById(respondentId);
        if (user.active) {
            user.active = false;
            if (user.finalizedDt == null) {
                user.finalizedDt = new Date();
            }
            user.persistAndFlush();
        }
        return user;
    }

    @Path("/test")
    @GET
    public String test() {
        return "token test";
    }
}
