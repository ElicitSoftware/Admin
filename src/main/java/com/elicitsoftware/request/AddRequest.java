package com.elicitsoftware.request;

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

import jakarta.json.bind.annotation.JsonbDateFormat;

import java.time.LocalDate;

/**
 * AddRequest represents a data transfer object for adding new participants or respondents to a survey.
 * <p>
 * This class encapsulates all the required information needed to register a new participant
 * in the Elicit Survey system. It serves as the request payload for participant registration
 * endpoints and includes personal information, survey association, and departmental affiliation.
 * <p>
 * The class is designed for JSON serialization/deserialization and follows RESTful API
 * conventions for data transfer. All fields are public for direct access and JSON binding
 * compatibility.
 * <p>
 * Key features:
 * - **Survey association**: Links participants to specific surveys
 * - **External ID support**: Allows external system identifier mapping
 * - **Department affiliation**: Associates participants with organizational units
 * - **Complete personal data**: Captures essential participant information
 * - **Date formatting**: Standardized date format for API consistency
 * - **JSON compatibility**: Direct serialization for REST API communication
 * <p>
 * Data validation considerations:
 * - Survey ID should reference an existing survey in the system
 * - Department ID should reference a valid department
 * - Email should be in valid email format
 * - Date of birth should be a reasonable date (not future dates)
 * - External ID (xid) should be unique within the survey context
 * <p>
 * Usage example:
 * <pre>
 * {@code
 * AddRequest request = new AddRequest();
 * request.surveyId = 12345;
 * request.xid = "EXT-ID-001";
 * request.departmentId = 42;
 * request.firstName = "John";
 * request.lastName = "Doe";
 * request.middleName = "Michael";
 * request.dob = LocalDate.of(1990, 5, 15);
 * request.email = "john.doe@example.com";
 * request.phone = "+1-555-123-4567";
 *
 * // Send to registration endpoint
 * Response response = participantService.addParticipant(request);
 * }
 * </pre>
 *
 * @see com.elicitsoftware.model.Survey
 * @see com.elicitsoftware.model.Department
 * @see com.elicitsoftware.model.Respondent
 * @since 1.0.0
 */
public class AddRequest {

    /**
     * Default constructor for AddRequest.
     *
     * <p>Creates a new AddRequest instance with all fields initialized to their
     * default values (null for objects, 0 for primitives). This constructor is
     * typically used when building request objects programmatically or when
     * deserializing from JSON payloads.</p>
     *
     * <p><strong>Usage:</strong></p>
     * <pre>{@code
     * AddRequest request = new AddRequest();
     * request.surveyId = 12345;
     * request.firstName = "John";
     * request.lastName = "Doe";
     * request.email = "john.doe@example.com";
     * // ... set other fields as needed
     * }</pre>
     *
     * <p>After construction, all fields should be explicitly set before
     * submitting the request to ensure proper participant registration.</p>
     */
    public AddRequest() {
        // Default constructor for JSON binding and programmatic creation
    }

    /**
     * The unique identifier of the survey this participant will be associated with.
     *
     * <p>This field establishes the relationship between the new participant and
     * a specific survey in the system. The survey ID must reference an existing,
     * active survey that the participant is eligible to participate in.</p>
     *
     * <p>The survey determines:</p>
     * <ul>
     *   <li>Available questionnaires and forms</li>
     *   <li>Data collection procedures</li>
     *   <li>Participant workflow and timeline</li>
     *   <li>Access permissions and restrictions</li>
     * </ul>
     */
    public int surveyId;

    /**
     * External identifier for cross-system participant tracking.
     * <p>
     * This field allows integration with external systems by providing a way
     * to maintain participant identity across different platforms. The external
     * ID (xid) should be unique within the context of the associated survey.
     * <p>
     * Common use cases:
     * - Medical record numbers from hospital systems
     * - Student IDs from educational institutions
     * - Employee IDs from organizational databases
     * - Research participant codes from study protocols
     * <p>
     * The xid enables bidirectional synchronization and prevents duplicate
     * participant entries when integrating with external data sources.
     */
    public String xid;

    /**
     * The unique identifier of the department this participant is affiliated with.
     * <p>
     * This field establishes the organizational context for the participant,
     * which may affect:
     * - Data access permissions and privacy settings
     * - Reporting and analytics groupings
     * - Workflow routing and approval processes
     * - Administrative oversight and management
     * <p>
     * The department ID must reference a valid, active department in the system.
     */
    public int departmentId;

    /**
     * The participant's first name.
     * <p>
     * This field captures the primary given name of the participant as it
     * should appear in communications, reports, and user interfaces. The
     * name is used for:
     * - Personalized communications and notifications
     * - User interface display and identification
     * - Report generation and data export
     * - Audit trails and system logging
     */
    public String firstName;

    /**
     * The participant's last name (surname/family name).
     * <p>
     * This field captures the family name or surname of the participant.
     * Combined with the first name, it provides the primary identification
     * for the participant in the system.
     */
    public String lastName;

    /**
     * The participant's middle name(s), if applicable.
     * <p>
     * This optional field captures any middle names or initials for the
     * participant. It provides more complete name representation and can
     * help distinguish between participants with similar first and last names.
     * <p>
     * This field may be null or empty if the participant has no middle name
     * or chooses not to provide it.
     */
    public String middleName;

    /**
     * The participant's date of birth.
     * <p>
     * This field captures the participant's birth date for age calculation,
     * demographic analysis, and eligibility verification. The date is formatted
     * as "yyyy-MM-dd" for JSON serialization consistency.
     * <p>
     * Important considerations:
     * - Used for age-based survey eligibility checks
     * - Required for demographic reporting and analysis
     * - May be subject to privacy regulations (HIPAA, GDPR)
     * - Should be validated to ensure reasonable date ranges
     * <p>
     * The date format ensures consistent parsing across different systems
     * and locales while maintaining ISO 8601 standard compliance.
     */
    @JsonbDateFormat("yyyy-MM-dd")
    public LocalDate dob;

    /**
     * The participant's email address for communications.
     * <p>
     * This field captures the primary email address for the participant,
     * which is used for:
     * - Survey invitations and reminders
     * - System notifications and updates
     * - Password reset and account recovery
     * - Report delivery and data sharing
     * <p>
     * The email address should be:
     * - Valid and properly formatted
     * - Accessible by the participant
     * - Unique within the survey context (if required)
     * - Compliant with organizational email policies
     */
    public String email;

    /**
     * The participant's phone number for contact purposes.
     * <p>
     * This field captures the primary phone number for the participant,
     * which may be used for:
     * - Alternative communication when email is unavailable
     * - Identity verification and security purposes
     * - Emergency contact procedures
     * - Multi-factor authentication processes
     * <p>
     * The phone number format should include country code and area code
     * for international compatibility. Example formats:
     * - "+1-555-123-4567" (US format)
     * - "+44-20-7946-0958" (UK format)
     * - "555-123-4567" (domestic format)
     */
    public String phone;
}
