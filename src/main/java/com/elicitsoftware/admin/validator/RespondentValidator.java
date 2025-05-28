package com.elicitsoftware.admin.validator;

import com.elicitsoftware.model.Subject;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class RespondentValidator implements ConstraintValidator<ValidRespondent, Subject> {
    @Override
    public boolean isValid(Subject pat, ConstraintValidatorContext context) {
        //TODO no that we only have Respondents and email no SMS we can require email address and use JPA validation.
        //This should remove this entire package.

        // They need to provide a contact method. Either mobile, email or both.
        return (pat.getEmail() != null && !pat.getEmail().isBlank()) || (pat.getPhone() != null && !pat.getPhone().isBlank());
    }
}
