package com.elicitsoftware.response;

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

/**
 * AddResponse represents the response payload returned after successfully adding a new participant to a survey.
 * <p>
 * This class serves as a data transfer object (DTO) that encapsulates the results of a participant
 * registration operation. It provides essential information needed by client applications to
 * continue the participant workflow, including unique identifiers and authentication tokens.
 * <p>
 * The response follows a standard pattern where:
 * - **Success cases** populate respondentId and token fields with valid data
 * - **Error cases** populate the error field with descriptive error messages
 * - **Partial success** may include some fields populated with others null
 * <p>
 * Key features:
 * - **Unique identification** via respondent ID for system tracking
 * - **Authentication token** for secure participant access
 * - **Error handling** with descriptive error messages
 * - **JSON serialization** compatibility for REST API responses
 * - **Immutable after creation** through controlled setter access
 * <p>
 * Response scenarios:
 * - **Successful registration**: respondentId and token populated, error is null
 * - **Validation failure**: error populated with validation details, other fields null
 * - **System error**: error populated with system error message, other fields null
 * - **Duplicate participant**: error indicates duplicate, may include existing respondentId
 * <p>
 * Usage example:
 * <pre>
 * {@code
 * // Server-side response creation
 * AddResponse response = new AddResponse();
 * if (participant != null) {
 *     response.setRespondentId(participant.getId());
 *     response.setToken(tokenService.generateToken(participant));
 * } else {
 *     response.setError("Failed to create participant: Invalid email format");
 * }
 *
 * // Client-side response handling
 * if (response.getError() == null) {
 *     // Success - redirect to survey with token
 *     String surveyUrl = "/survey?token=" + response.getToken();
 *     redirectTo(surveyUrl);
 * } else {
 *     // Error - display error message to user
 *     showErrorMessage(response.getError());
 * }
 * }
 * </pre>
 *
 * @see com.elicitsoftware.request.AddRequest
 * @see com.elicitsoftware.model.Respondent
 * @since 1.0.0
 */
public class AddResponse {
    /**
     * The unique system identifier assigned to the newly created respondent.
     * <p>
     * This field contains the database-generated unique ID for the participant
     * record. It serves as the primary key for all subsequent operations
     * related to this participant.
     */
    private int respondentId;

    /**
     * Authentication token for secure participant access to surveys.
     * <p>
     * This field contains a secure, time-limited token that allows the
     * participant to access their assigned surveys without traditional
     * username/password authentication.
     */
    private String token;

    /**
     * Error message describing any failures that occurred during participant creation.
     * <p>
     * This field contains human-readable error descriptions when the
     * participant registration fails. It should be null for successful
     * operations.
     */
    private String error;

    /**
     * Default constructor for creating an empty AddResponse instance.
     * <p>
     * Creates a new AddResponse with default values:
     * - respondentId: 0 (indicating no ID assigned)
     * - token: null (no authentication token)
     * - error: null (no error message)
     * <p>
     * This constructor is typically used by:
     * - JSON/XML serialization frameworks
     * - Service layer methods that populate fields programmatically
     * - Test scenarios requiring empty response objects
     */
    public AddResponse() {
        // Default constructor - fields initialized to default values
    }

    /**
     * Returns the unique identifier assigned to the newly created respondent.
     * <p>
     * This ID can be used for:
     * - Tracking participant progress and responses
     * - Linking survey data to participant records
     * - Administrative operations and reporting
     * - Cross-referencing with external systems
     * <p>
     * The ID is only populated when participant creation is successful.
     * Returns 0 or negative values may indicate creation failure.
     *
     * @return The respondent ID as a positive integer, or 0 if creation failed
     */
    public int getRespondentId() {
        return respondentId;
    }

    /**
     * Sets the unique identifier for the newly created respondent.
     * <p>
     * This method is typically called by service layer components after
     * successfully creating a new participant record in the database.
     * The ID should be a positive integer representing the primary key
     * of the participant record.
     *
     * @param respondentId The unique respondent identifier; should be positive
     */
    public void setRespondentId(int respondentId) {
        this.respondentId = respondentId;
    }

    /**
     * Returns the authentication token for secure participant access.
     * <p>
     * This token enables the participant to access their assigned surveys
     * without traditional login credentials. The token includes:
     * - Participant identification information
     * - Survey access permissions
     * - Expiration time for security
     * - Digital signature for integrity
     * <p>
     * Token usage:
     * - Include in survey URLs for direct access
     * - Store securely on client side if needed
     * - Use for API authentication in survey operations
     * - Validate before allowing survey participation
     * <p>
     * Security considerations:
     * - Tokens have limited lifetime to prevent abuse
     * - Should be transmitted over secure connections (HTTPS)
     * - May be invalidated if suspicious activity is detected
     * - Should not be logged or stored in plain text
     *
     * @return The authentication token as a secure string, or null if creation failed
     */
    public String getToken() {
        return token;
    }

    /**
     * Sets the authentication token for the newly created participant.
     * <p>
     * This method is called by security services after successful participant
     * creation to provide secure access credentials. The token should be
     * generated using cryptographically secure methods and include appropriate
     * expiration and permission settings.
     *
     * @param token The secure authentication token; may be null if token generation fails
     */
    public void setToken(String token) {
        this.token = token;
    }

    /**
     * Returns any error message that occurred during participant creation.
     * <p>
     * This method returns detailed error information when participant
     * registration fails. Error messages are designed to be:
     * - Human-readable for user display
     * - Specific enough for troubleshooting
     * - Safe for client-side display (no sensitive information)
     * - Actionable when possible
     * <p>
     * Common error scenarios:
     * - **Validation errors**: "Invalid email format", "Missing required field: firstName"
     * - **Business rule violations**: "Participant already exists in this survey"
     * - **System errors**: "Database connection failed", "Service temporarily unavailable"
     * - **Permission errors**: "Insufficient privileges to add participants"
     * <p>
     * A null return value indicates successful participant creation with no errors.
     *
     * @return Error message describing the failure, or null if operation was successful
     */
    public String getError() {
        return error;
    }

    /**
     * Sets the error message for failed participant creation operations.
     * <p>
     * This method is called by service layer components when participant
     * registration fails for any reason. The error message should be:
     * - Descriptive enough for user understanding
     * - Free of sensitive system information
     * - Actionable when possible (suggesting corrective steps)
     * - Consistent with application error messaging standards
     * <p>
     * Setting an error message typically indicates that respondentId and
     * token fields should remain null or unset.
     *
     * @param error The error message describing the failure; should be non-null for failed operations
     */
    public void setError(String error) {
        this.error = error;
    }
}
