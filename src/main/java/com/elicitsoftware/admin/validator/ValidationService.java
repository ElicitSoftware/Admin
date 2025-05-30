package com.elicitsoftware.admin.validator;

import com.elicitsoftware.model.Subject;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;

@ApplicationScoped
public class ValidationService {

    @Transactional
    public String validate(@Valid Subject respondent) {
        try {
            validate(respondent);
            respondent.persist();
        } catch (Exception e) {
            return e.getMessage();
        }
        return "success";
    }
}