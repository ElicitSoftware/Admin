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

import jakarta.validation.ConstraintViolation;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * A result wrapper class for validation operations in the Elicit Admin application.
 * 
 * <p>This class encapsulates the outcome of validation processes, providing both
 * success/failure status and associated messages. It serves as a standardized
 * response format for validation operations, making it easy to handle both
 * successful validations and constraint violations.</p>
 * 
 * <p><strong>Key Features:</strong></p>
 * <ul>
 *   <li><strong>Success Tracking:</strong> Boolean flag indicating validation success or failure</li>
 *   <li><strong>Message Aggregation:</strong> Combines multiple constraint violation messages</li>
 *   <li><strong>Immutable Design:</strong> Once created, the result cannot be modified</li>
 *   <li><strong>Stream Processing:</strong> Efficiently processes collections of violations</li>
 * </ul>
 * 
 * <p><strong>Usage Patterns:</strong></p>
 * <ul>
 *   <li><strong>Success Result:</strong> Created with a success message</li>
 *   <li><strong>Failure Result:</strong> Created from constraint violations with auto-generated message</li>
 * </ul>
 * 
 * <p><strong>Usage Examples:</strong></p>
 * <pre>{@code
 * // Success case
 * Result success = new Result("Validation passed successfully");
 * 
 * // Failure case with violations
 * Set<ConstraintViolation<MyEntity>> violations = validator.validate(entity);
 * Result failure = new Result(violations);
 * 
 * // Checking results
 * if (result.isSuccess()) {
 *     // Handle success
 *     System.out.println(result.getMessage());
 * } else {
 *     // Handle validation errors
 *     System.err.println("Validation failed: " + result.getMessage());
 * }
 * }</pre>
 * 
 * <p><strong>Message Format:</strong></p>
 * <ul>
 *   <li><strong>Success messages:</strong> Custom message provided by caller</li>
 *   <li><strong>Failure messages:</strong> Comma-separated list of constraint violation messages</li>
 * </ul>
 * 
 * @author Elicit Software
 * @version 1.0
 * @since 1.0
 * @see ConstraintViolation
 * @see jakarta.validation.Validator
 */
public class Result {

    /** The validation result message, either success message or aggregated error messages. */
    private final String message;
    
    /** Flag indicating whether the validation was successful (true) or failed (false). */
    private final boolean success;

    /**
     * Creates a successful validation result with the specified message.
     * 
     * <p>This constructor is used when validation passes successfully and you want
     * to provide a custom success message. The result will have {@code success = true}
     * and the provided message.</p>
     * 
     * <p><strong>Usage Example:</strong></p>
     * <pre>{@code
     * Result result = new Result("User registration completed successfully");
     * assert result.isSuccess() == true;
     * assert result.getMessage().equals("User registration completed successfully");
     * }</pre>
     * 
     * @param message the success message to associate with this result
     * @throws NullPointerException if message is null (behavior depends on usage)
     */
    public Result(String message) {
        this.success = true;
        this.message = message;
    }

    /**
     * Creates a failed validation result from a set of constraint violations.
     * 
     * <p>This constructor is used when validation fails and you have a collection
     * of constraint violations. The result will have {@code success = false} and
     * a message that combines all violation messages into a single comma-separated string.</p>
     * 
     * <p><strong>Message Generation:</strong></p>
     * <ul>
     *   <li>Extracts the message from each {@link ConstraintViolation}</li>
     *   <li>Joins all messages with ", " (comma and space) separator</li>
     *   <li>Preserves the order as provided by the Set's iterator</li>
     * </ul>
     * 
     * <p><strong>Usage Example:</strong></p>
     * <pre>{@code
     * Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
     * Set<ConstraintViolation<User>> violations = validator.validate(user);
     * 
     * if (!violations.isEmpty()) {
     *     Result result = new Result(violations);
     *     assert result.isSuccess() == false;
     *     // result.getMessage() might be: "Email is required, First name must not be blank"
     * }
     * }</pre>
     * 
     * <p><strong>Edge Cases:</strong></p>
     * <ul>
     *   <li><strong>Empty Set:</strong> Results in an empty message string</li>
     *   <li><strong>Null Violations:</strong> May cause NullPointerException during stream processing</li>
     *   <li><strong>Null Messages:</strong> Individual null messages are converted to "null" strings</li>
     * </ul>
     * 
     * @param violations the set of constraint violations to process into an error message
     * @throws NullPointerException if violations set is null or contains null elements during processing
     * @see ConstraintViolation#getMessage()
     */
    public Result(Set<? extends ConstraintViolation<?>> violations) {
        this.success = false;
        this.message = violations.stream()
                .map(cv -> cv.getMessage())
                .collect(Collectors.joining(", "));
    }

    /**
     * Returns the message associated with this validation result.
     * 
     * <p>The message content depends on how the result was created:</p>
     * <ul>
     *   <li><strong>Success Result:</strong> Returns the custom success message provided to the constructor</li>
     *   <li><strong>Failure Result:</strong> Returns a comma-separated list of constraint violation messages</li>
     * </ul>
     * 
     * <p><strong>Message Examples:</strong></p>
     * <ul>
     *   <li>Success: {@code "User created successfully"}</li>
     *   <li>Failure: {@code "Email is required, First name must not be blank, Age must be positive"}</li>
     *   <li>Empty violations: {@code ""} (empty string)</li>
     * </ul>
     * 
     * @return the validation result message, never null but may be empty
     */
    public String getMessage() {
        return message;
    }

    /**
     * Returns whether this validation result represents a successful operation.
     * 
     * <p>The success flag is determined by which constructor was used:</p>
     * <ul>
     *   <li><strong>{@code true}:</strong> Result created with {@link #Result(String)} constructor</li>
     *   <li><strong>{@code false}:</strong> Result created with {@link #Result(Set)} constructor</li>
     * </ul>
     * 
     * <p><strong>Usage Pattern:</strong></p>
     * <pre>{@code
     * Result result = performValidation();
     * 
     * if (result.isSuccess()) {
     *     // Handle successful validation
     *     logInfo(result.getMessage());
     *     proceedWithOperation();
     * } else {
     *     // Handle validation failures
     *     logError("Validation failed: " + result.getMessage());
     *     showErrorsToUser(result.getMessage());
     * }
     * }</pre>
     * 
     * @return {@code true} if the validation was successful, {@code false} if validation failed
     */
    public boolean isSuccess() {
        return success;
    }

}
