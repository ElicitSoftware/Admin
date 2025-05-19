package com.elicitsoftware.admin.validator;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({
        ElementType.TYPE
})
@Constraint(validatedBy = RespondentValidator.class)
public @interface ValidRespondent {

    String message() default "Must have a valid email, phone or both";

    Class<? extends Payload>[] payload() default {};

    Class<?>[] groups() default {};

}

