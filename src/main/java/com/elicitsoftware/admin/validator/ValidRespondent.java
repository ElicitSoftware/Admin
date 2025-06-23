package com.elicitsoftware.admin.validator;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Custom validation annotation for ensuring survey respondents have valid contact information.
 * 
 * <p>This constraint annotation validates that a {@link com.elicitsoftware.model.Subject} 
 * (survey respondent) has at least one valid contact method available for survey 
 * communications. The validation ensures that respondents can be reached for survey 
 * invitations, reminders, and follow-up communications.</p>
 * 
 * <p><strong>Validation Rules:</strong></p>
 * <ul>
 *   <li><strong>Email Required OR Phone Required:</strong> At least one contact method must be present</li>
 *   <li><strong>Non-blank Values:</strong> Contact information must not be null or blank</li>
 *   <li><strong>Both Allowed:</strong> Having both email and phone is valid and preferred</li>
 *   <li><strong>Neither Fails:</strong> Having neither email nor phone will cause validation failure</li>
 * </ul>
 * 
 * <p><strong>Supported Contact Methods:</strong></p>
 * <ul>
 *   <li><strong>Email:</strong> Valid email address for electronic communications</li>
 *   <li><strong>Phone:</strong> Phone number for SMS or voice communications (legacy)</li>
 * </ul>
 * 
 * <p><strong>Usage Examples:</strong></p>
 * <pre>{@code
 * @Entity
 * @ValidRespondent
 * public class Subject extends PanacheEntityBase {
 *     private String email;
 *     private String phone;
 *     // ... other fields
 * }
 * 
 * // Valid cases:
 * Subject respondent1 = new Subject();
 * respondent1.setEmail("user@example.com");        // Email only - VALID
 * 
 * Subject respondent2 = new Subject();
 * respondent2.setPhone("555-1234");               // Phone only - VALID
 * 
 * Subject respondent3 = new Subject();
 * respondent3.setEmail("user@example.com");
 * respondent3.setPhone("555-1234");               // Both - VALID
 * 
 * // Invalid case:
 * Subject respondent4 = new Subject();            // Neither - INVALID
 * }</pre>
 * 
 * <p><strong>Integration with Bean Validation:</strong></p>
 * <ul>
 *   <li>Works seamlessly with Jakarta Bean Validation framework</li>
 *   <li>Can be combined with other validation annotations</li>
 *   <li>Supports validation groups for conditional validation</li>
 *   <li>Provides custom error messages for failed validation</li>
 * </ul>
 * 
 * <p><strong>Implementation Details:</strong></p>
 * <ul>
 *   <li><strong>Validator:</strong> {@link RespondentValidator} implements the validation logic</li>
 *   <li><strong>Target:</strong> Applied at the class/type level (not individual fields)</li>
 *   <li><strong>Runtime:</strong> Validation occurs at runtime during entity processing</li>
 *   <li><strong>Default Message:</strong> "Must have a valid email, phone or both"</li>
 * </ul>
 * 
 * <p><strong>Future Considerations:</strong></p>
 * <ul>
 *   <li>May be simplified to require only email addresses (SMS no longer used)</li>
 *   <li>Could be replaced with standard JPA validation annotations</li>
 *   <li>Entire validator package may be deprecated in future versions</li>
 * </ul>
 * 
 * <p><strong>Error Handling:</strong></p>
 * <p>When validation fails, the constraint violation will contain the default message
 * or a custom message if specified. The violation can be processed by validation
 * frameworks to provide user-friendly error feedback.</p>
 * 
 * @author Elicit Software
 * @version 1.0
 * @since 1.0
 * @see RespondentValidator
 * @see com.elicitsoftware.model.Subject
 * @see Constraint
 * @see jakarta.validation.Valid
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({
        ElementType.TYPE
})
@Constraint(validatedBy = RespondentValidator.class)
public @interface ValidRespondent {

    /**
     * The validation error message to display when the constraint is violated.
     * 
     * <p>This message is returned when a Subject fails the contact validation,
     * meaning it has neither a valid email address nor a valid phone number.
     * The message can be customized when applying the annotation.</p>
     * 
     * <p><strong>Default Message:</strong> "Must have a valid email, phone or both"</p>
     * 
     * <p><strong>Customization Example:</strong></p>
     * <pre>{@code
     * @ValidRespondent(message = "Please provide either an email address or phone number")
     * public class Subject extends PanacheEntityBase {
     *     // ... class implementation
     * }
     * }</pre>
     * 
     * <p><strong>Message Interpolation:</strong></p>
     * <p>The message supports Bean Validation message interpolation features,
     * allowing for internationalization and parameter substitution if needed.</p>
     * 
     * @return the validation error message template
     */
    String message() default "Must have a valid email, phone or both";

    /**
     * Payload for metadata clients to associate arbitrary data with constraint violations.
     * 
     * <p>This attribute allows validation clients to associate additional metadata
     * with constraint violations. It's typically used by validation frameworks
     * to carry additional information about the validation context or to trigger
     * specific behavior when violations occur.</p>
     * 
     * <p><strong>Common Use Cases:</strong></p>
     * <ul>
     *   <li><strong>Severity Levels:</strong> Mark violations as warnings vs errors</li>
     *   <li><strong>Client Metadata:</strong> Provide UI-specific information</li>
     *   <li><strong>Processing Hints:</strong> Guide post-validation processing</li>
     * </ul>
     * 
     * <p><strong>Usage Example:</strong></p>
     * <pre>{@code
     * // Define custom payload
     * public class Severity {
     *     public interface Warning extends Payload {}
     *     public interface Error extends Payload {}
     * }
     * 
     * // Use with annotation
     * @ValidRespondent(payload = {Severity.Warning.class})
     * public class Subject extends PanacheEntityBase {
     *     // ... class implementation
     * }
     * }</pre>
     * 
     * @return array of payload classes for metadata association
     */
    Class<? extends Payload>[] payload() default {};

    /**
     * Validation groups for conditional validation scenarios.
     * 
     * <p>Groups allow for conditional validation where different validation rules
     * apply in different contexts. This is useful when the same entity needs
     * different validation behavior in different business scenarios.</p>
     * 
     * <p><strong>Group Scenarios:</strong></p>
     * <ul>
     *   <li><strong>Creation vs Update:</strong> Different rules for new vs existing entities</li>
     *   <li><strong>User Roles:</strong> Different validation based on user permissions</li>
     *   <li><strong>Business Context:</strong> Different rules for different business processes</li>
     * </ul>
     * 
     * <p><strong>Usage Example:</strong></p>
     * <pre>{@code
     * // Define validation groups
     * public interface CreateValidation {}
     * public interface UpdateValidation {}
     * 
     * // Apply conditional validation
     * @ValidRespondent(groups = {CreateValidation.class})
     * public class Subject extends PanacheEntityBase {
     *     // Only validate contact info during creation
     * }
     * 
     * // Trigger specific group validation
     * Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
     * Set<ConstraintViolation<Subject>> violations = 
     *     validator.validate(subject, CreateValidation.class);
     * }</pre>
     * 
     * <p><strong>Default Behavior:</strong></p>
     * <p>When no groups are specified, the constraint applies to the default
     * validation group, which is triggered during standard entity validation.</p>
     * 
     * @return array of validation group classes for conditional validation
     */
    Class<?>[] groups() default {};

}

