package com.elicitsoftware.admin.validator;

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

import com.elicitsoftware.model.Subject;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Custom validator implementation for the {@link ValidRespondent} constraint annotation.
 *
 * <p>This validator ensures that a {@link Subject} (respondent) has at least one valid
 * contact method available for survey communication. The validation logic requires
 * either a valid email address or phone number (or both) to be present and non-blank.</p>
 *
 * <p><strong>Validation Rules:</strong></p>
 * <ul>
 *   <li><strong>Email:</strong> Must be non-null and non-blank if phone is not provided</li>
 *   <li><strong>Phone:</strong> Must be non-null and non-blank if email is not provided</li>
 *   <li><strong>Both:</strong> Having both email and phone is valid and preferred</li>
 *   <li><strong>Neither:</strong> Having neither email nor phone will fail validation</li>
 * </ul>
 *
 * <p><strong>Current Implementation Notes:</strong></p>
 * <ul>
 *   <li>The validator currently accepts either email OR phone as sufficient</li>
 *   <li>No format validation is performed (email format, phone format)</li>
 *   <li>Only checks for presence and non-blank status</li>
 * </ul>
 *
 * <p><strong>Future Considerations:</strong></p>
 * <ul>
 *   <li>TODO: Consider requiring email address only (as SMS is no longer used)</li>
 *   <li>TODO: May be replaced with standard JPA validation annotations</li>
 *   <li>TODO: This entire validator package may be removed in future versions</li>
 * </ul>
 *
 * <p><strong>Usage Example:</strong></p>
 * <pre>{@code
 * @ValidRespondent
 * public class Subject extends PanacheEntityBase {
 *     private String email;
 *     private String phone;
 *     // ... other fields
 * }
 * }</pre>
 *
 * @author Elicit Software
 * @version 1.0
 * @since 1.0
 * @see ValidRespondent
 * @see Subject
 * @see ConstraintValidator
 */
public class RespondentValidator implements ConstraintValidator<ValidRespondent, Subject> {

    /**
     * Default constructor.
     *
     * <p>Creates a new RespondentValidator instance. This constructor is used by
     * the Jakarta Validation framework when creating validator instances for
     * constraint validation.</p>
     */
    public RespondentValidator() {
        // Default constructor for Jakarta Validation framework
    }

    /**
     * Validates that a Subject has at least one valid contact method (email or phone).
     *
     * <p>This method implements the core validation logic for the {@link ValidRespondent}
     * constraint. It ensures that survey respondents have at least one way to be contacted
     * for survey invitations and communications.</p>
     *
     * <p><strong>Validation Logic:</strong></p>
     * <ol>
     *   <li>Check if email is present and non-blank</li>
     *   <li>Check if phone is present and non-blank</li>
     *   <li>Return true if either condition is met</li>
     *   <li>Return false if both email and phone are null or blank</li>
     * </ol>
     *
     * <p><strong>Implementation Details:</strong></p>
     * <ul>
     *   <li>Uses logical OR - only one contact method is required</li>
     *   <li>Checks for both null values and blank strings</li>
     *   <li>Does not validate email format or phone number format</li>
     *   <li>Parameter name "pat" is legacy - represents a Subject/Patient/Respondent</li>
     * </ul>
     *
     * <p><strong>Future Enhancement Notes:</strong></p>
     * <ul>
     *   <li>TODO: Consider requiring only email (SMS no longer used)</li>
     *   <li>TODO: May be replaced with standard JPA validation</li>
     *   <li>TODO: Could add format validation for email addresses</li>
     * </ul>
     *
     * @param pat the Subject (respondent) to validate - parameter name is legacy
     * @param context the constraint validator context for adding custom error messages
     * @return {@code true} if the subject has at least one valid contact method (email or phone),
     *         {@code false} if both email and phone are null or blank
     *
     * @see Subject#getEmail()
     * @see Subject#getPhone()
     */
    @Override
    public boolean isValid(Subject pat, ConstraintValidatorContext context) {
        //TODO no that we only have Respondents and email no SMS we can require email address and use JPA validation.
        //This should remove this entire package.

        // They need to provide a contact method. Either mobile, email or both.
        return (pat.getEmail() != null && !pat.getEmail().isBlank()) || (pat.getPhone() != null && !pat.getPhone().isBlank());
    }
}
