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

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;

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
    @JsonProperty("subjects")
    private ArrayList<AddResponseStatus> subjects = new ArrayList<>();

    /**
     * Error message describing any failures that occurred during participant creation.
     * <p>
     * This field contains human-readable error descriptions when the
     * participant registration fails. It should be null for successful
     * operations.
     */
    @JsonProperty("error")
    private ArrayList<String> errors = new ArrayList<>();

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
    public ArrayList<String> getErrors() {
        return errors;
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
        this.errors.add(error);
    }

    /**
     * Gets the list of subjects/statuses associated with this response.
     *
     * @return the list of AddResponseStatus objects
     */
    public ArrayList<AddResponseStatus> getSubjects() {
        return subjects;
    }

    /**
     * Sets the list of subjects/statuses for this response.
     *
     * @param subjects the list of AddResponseStatus objects to set
     */
    public void setSubjects(ArrayList<AddResponseStatus> subjects) {
        this.subjects = subjects;
    }

    public void addStatus(AddResponseStatus status) {
        this.subjects.add(status);
    }

    /**
     * Returns a string representation of this AddResponse object.
     * <p>
     * The string representation includes:
     * - Number of subjects/statuses
     * - Number of errors
     * - Summary of success/failure status
     * <p>
     * This method is useful for logging, debugging, and general object inspection.
     *
     * @return A string representation of this AddResponse
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("AddResponse {\n");
        
        // Subjects information
        sb.append("  subjects: ");
        if (subjects == null || subjects.isEmpty()) {
            sb.append("none\n");
        } else {
            sb.append(subjects.size()).append(" item(s)\n");
            for (int i = 0; i < subjects.size(); i++) {
                sb.append("    [").append(i).append("] ").append(subjects.get(i)).append("\n");
            }
        }
        
        // Errors information
        sb.append("  errors: ");
        if (errors == null || errors.isEmpty()) {
            sb.append("none\n");
        } else {
            sb.append(errors.size()).append(" item(s)\n");
            for (int i = 0; i < errors.size(); i++) {
                sb.append("    [").append(i).append("] \"").append(errors.get(i)).append("\"\n");
            }
        }
        
        // Status summary
        sb.append("  hasErrors: ").append(errors != null && !errors.isEmpty()).append("\n");
        sb.append("}");
        
        return sb.toString();
    }
}
