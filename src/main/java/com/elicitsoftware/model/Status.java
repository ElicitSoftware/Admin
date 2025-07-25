package com.elicitsoftware.model;

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

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * JPA entity representing status tracking for survey participants in the Elicit system.
 *
 * <p>This entity provides a comprehensive status tracking mechanism for survey participants,
 * capturing personal information, contact details, and survey participation status.
 * It serves as a consolidated view for tracking and reporting on survey progress
 * and participant engagement.</p>
 *
 * <p><strong>Key Features:</strong></p>
 * <ul>
 *   <li><strong>Participant Identification:</strong> XID linking and personal information</li>
 *   <li><strong>Survey Association:</strong> Links to specific survey instances via survey ID</li>
 *   <li><strong>Respondent Linking:</strong> Associates with respondent records for comprehensive tracking</li>
 *   <li><strong>Contact Management:</strong> Email and phone contact information</li>
 *   <li><strong>Status Tracking:</strong> Comprehensive participation status monitoring</li>
 *   <li><strong>Department Integration:</strong> Organizational unit tracking and reporting</li>
 * </ul>
 *
 * <p><strong>Database Mapping:</strong></p>
 * <ul>
 *   <li><strong>Table:</strong> {@code survey.status}</li>
 *   <li><strong>Primary Key:</strong> Manual ID assignment (not auto-generated)</li>
 *   <li><strong>External Integration:</strong> XID field for external system correlation</li>
 *   <li><strong>Relationships:</strong> Links to survey, respondent, and department entities</li>
 * </ul>
 *
 * <p><strong>Use Cases:</strong></p>
 * <ul>
 *   <li><strong>Progress Tracking:</strong> Monitor survey completion status across participants</li>
 *   <li><strong>Reporting:</strong> Generate participation and completion reports by department</li>
 *   <li><strong>Contact Management:</strong> Manage participant communication and follow-up</li>
 *   <li><strong>Data Integration:</strong> Interface with external systems via XID mapping</li>
 *   <li><strong>Access Control:</strong> Token-based secure survey access management</li>
 * </ul>
 *
 * <p><strong>Security Considerations:</strong></p>
 * <ul>
 *   <li><strong>Token Authentication:</strong> Secure, anonymous survey access via unique tokens</li>
 *   <li><strong>PII Protection:</strong> Personal information handling with proper database constraints</li>
 *   <li><strong>Date Tracking:</strong> Comprehensive audit trail with creation and finalization timestamps</li>
 * </ul>
 *
 * @author Elicit Software
 * @version 1.0
 * @since 1.0
 * @see io.quarkus.hibernate.orm.panache.PanacheEntityBase
 * @see jakarta.persistence.Entity
 * @see jakarta.persistence.Table
 */
@Entity
@Table(name = "status", schema = "survey")
public class Status extends PanacheEntityBase {

    /** Date formatter for displaying creation dates in MM/dd/yyyy format. */
    @Transient
    private final static SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yyyy");

    /**
     * Unique identifier for the status record.
     *
     * <p>Manually assigned primary key that uniquely identifies each status
     * record. Unlike other entities, this ID is not auto-generated and must
     * be explicitly set when creating new status records.</p>
     */
    @Id
    @Column(name = "id", unique = true, nullable = false, precision = 20)
    private long id;

    /**
     * Identifier of the respondent associated with this status record.
     *
     * <p>Links this status record to a specific respondent entity, enabling
     * cross-referencing between status tracking and detailed respondent information.
     * This relationship supports comprehensive participant management and reporting.</p>
     */
    @Column(name = "respondent_id")
    private long respondentId;

    /**
     * External identifier for cross-system integration.
     *
     * <p>Links this status record to external systems or databases.
     * The XID serves as a correlation key for data synchronization
     * and integration with other survey or participant management systems.</p>
     */
    @Column(name = "XID")
    private String xid;

    /**
     * Identifier of the survey this status record is associated with.
     *
     * <p>Links the status record to a specific survey instance.
     * This allows tracking of participant status across multiple
     * surveys and enables survey-specific reporting and analytics.</p>
     */
    @Column(name = "survey_id")
    private long surveyId;

    /**
     * Participant's first name.
     *
     * <p>Personal identifier used for communication and reporting.
     * This field enables personalized survey communications and
     * participant identification in administrative interfaces.</p>
     */
    @Column(name = "firstName")
    private String firstName;

    /**
     * Participant's last name.
     *
     * <p>Primary surname used for participant identification
     * and alphabetical sorting in reports and administrative views.</p>
     */
    @Column(name = "lastName")
    private String lastName;

    /**
     * Participant's middle name.
     *
     * <p>Optional middle name or initial for complete name recording.
     * May be null or empty if not provided during registration.</p>
     */
    @Column(name = "middleName")
    private String middleName;

    /**
     * Participant's date of birth.
     *
     * <p>Used for demographic analysis and age-based survey targeting.
     * Stored as a date-only value (no time component) for privacy
     * and demographic reporting purposes.</p>
     */
    @Column(name = "dob")
    @Temporal(TemporalType.DATE)
    private Date dob;

    /**
     * Participant's email address.
     *
     * <p>Primary contact method for survey invitations and communications.
     * This email address is used for sending survey links, reminders,
     * and completion notifications.</p>
     */
    @Column(name = "email")
    private String email;

    /**
     * Participant's phone number.
     *
     * <p>Optional secondary contact method. May be used for alternative
     * communication channels or demographic analysis. This field is
     * nullable as phone contact is not always required.</p>
     */
    @Column(name = "phone", nullable = true)
    private String phone;

    /**
     * Name of the department this participant belongs to.
     *
     * <p>Departmental affiliation for organizational reporting and
     * access control. Used for grouping participants by organizational
     * units and generating department-specific reports.</p>
     */
    @Column(name = "department_name")
    private String departmentName;

    /**
     * Identifier of the department this participant belongs to.
     *
     * <p>Numeric reference to the participant's department for
     * database relationships and efficient querying. Used in
     * conjunction with departmentName for organizational tracking.</p>
     */
    @Column(name = "department_id")
    private long department_id;

    /**
     * Unique access token for survey participation.
     *
     * <p>Secure token that provides anonymous access to the assigned
     * survey. This token enables survey participation without requiring
     * user registration while maintaining response tracking capabilities.</p>
     */
    @Column(name = "token")
    private String token;

    /**
     * Current participation status of the participant.
     *
     * <p>Tracks the participant's progress through the survey lifecycle.
     * Common values include "invited", "started", "completed", "expired",
     * or other status indicators defined by the survey workflow.</p>
     */
    @Column(name = "status")
    private String status;

    /**
     * Timestamp when the status record was created.
     *
     * <p>Automatically set to the current timestamp when a new status
     * record is instantiated. This marks when the participant was
     * first registered or invited to participate in the survey.</p>
     */
    @Column(name = "CREATED_DT")
    @Temporal(TemporalType.TIMESTAMP)
    public Date createdDt = new Date();

    /**
     * Timestamp when the participant finalized their survey response.
     *
     * <p>Records when the participant completed their survey submission.
     * Initially set to the current timestamp but should be updated to
     * the actual completion time when the survey is finalized.</p>
     */
    @Column(name = "finalized_dt")
    @Temporal(TemporalType.TIMESTAMP)
    public Date finalizedDt = new Date();

    /**
     * Default constructor for JPA entity instantiation.
     *
     * <p>Creates a new Status instance with default timestamps. Both {@link #createdDt}
     * and {@link #finalizedDt} are set to the current timestamp. This constructor is
     * primarily used by JPA for entity instantiation and should be followed by proper
     * field initialization before persistence.</p>
     *
     * <p><strong>Note:</strong> The {@link #finalizedDt} timestamp should be updated
     * to the actual completion time when the survey is finalized, rather than keeping
     * the instantiation timestamp.</p>
     */
    public Status() {
        super();
    }

    /**
     * Gets the unique identifier for this status record.
     *
     * @return the status record ID
     */
    public long getId() {
        return id;
    }

    /**
     * Sets the unique identifier for this status record.
     *
     * @param id the status record ID to set
     */
    public void setId(long id) {
        this.id = id;
    }

    /**
     * Gets the respondent identifier associated with this status record.
     *
     * @return the respondent ID
     * @since 1.0
     */
    public long getRespondentId() {
        return respondentId;
    }

    /**
     * Sets the respondent identifier associated with this status record.
     *
     * @param respondentId the respondent ID to set
     * @since 1.0
     */
    public void setRespondentId(long respondentId) {
        this.respondentId = respondentId;
    }

    /**
     * Gets the external system identifier.
     *
     * @return the XID (external identifier)
     */
    public String getXid() {
        return xid;
    }

    /**
     * Sets the external system identifier.
     *
     * @param xid the XID (external identifier) to set
     */
    public void setXid(String xid) {
        this.xid = xid;
    }

    /**
     * Gets the survey identifier this status is associated with.
     *
     * @return the survey ID
     */
    public long getSurveyId() {
        return surveyId;
    }

    /**
     * Sets the survey identifier this status is associated with.
     *
     * @param surveyId the survey ID to set
     */
    public void setSurveyId(long surveyId) {
        this.surveyId = surveyId;
    }

    /**
     * Gets the participant's first name.
     *
     * @return the first name
     */
    public String getFirstName() {
        return firstName;
    }

    /**
     * Sets the participant's first name.
     *
     * @param firstName the first name to set
     */
    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    /**
     * Gets the participant's last name.
     *
     * @return the last name
     */
    public String getLastName() {
        return lastName;
    }

    /**
     * Sets the participant's last name.
     *
     * @param lastName the last name to set
     */
    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    /**
     * Gets the participant's middle name.
     *
     * @return the middle name, may be null
     */
    public String getMiddleName() {
        return middleName;
    }

    /**
     * Sets the participant's middle name.
     *
     * @param middleName the middle name to set, may be null
     */
    public void setMiddleName(String middleName) {
        this.middleName = middleName;
    }

    /**
     * Gets the participant's date of birth.
     *
     * @return the date of birth
     */
    public Date getDob() {
        return dob;
    }

    /**
     * Sets the participant's date of birth.
     *
     * @param dob the date of birth to set
     */
    public void setDob(Date dob) {
        this.dob = dob;
    }

    /**
     * Gets the participant's email address.
     *
     * @return the email address
     */
    public String getEmail() {
        return email;
    }

    /**
     * Sets the participant's email address.
     *
     * @param email the email address to set
     */
    public void setEmail(String email) {
        this.email = email;
    }

    /**
     * Gets the participant's phone number.
     *
     * @return the phone number, may be null
     */
    public String getPhone() {
        return phone;
    }

    /**
     * Sets the participant's phone number.
     *
     * @param phone the phone number to set, may be null
     */
    public void setPhone(String phone) {
        this.phone = phone;
    }

    /**
     * Gets the current participation status.
     *
     * @return the participation status
     */
    public String getStatus() {
        return status;
    }

    /**
     * Sets the current participation status.
     *
     * @param status the participation status to set
     */
    public void setStatus(String status) {
        this.status = status;
    }

    /**
     * Gets the participant's unique access token.
     *
     * @return the access token
     */
    public String getToken() {
        return token;
    }

    /**
     * Sets the participant's unique access token.
     *
     * @param token the access token to set
     */
    public void setToken(String token) {
        this.token = token;
    }

    /**
     * Gets the department name this participant belongs to.
     *
     * @return the department name
     * @see #getDepartmentId()
     */
    public String getDepartmentName() {
        return departmentName;
    }

    /**
     * Sets the department name this participant belongs to.
     *
     * @param departmentName the department name to set
     * @see #setDepartmentId(long)
     */
    public void setDepartmentName(String departmentName) {
        this.departmentName = departmentName;
    }

    /**
     * Gets the department identifier this participant belongs to.
     *
     * @return the department ID
     * @since 1.0
     * @see #getDepartmentName()
     */
    public long getDepartmentId() {
        return department_id;
    }

    /**
     * Sets the department identifier this participant belongs to.
     *
     * @param departmentId the department ID to set
     * @since 1.0
     * @see #setDepartmentName(String)
     */
    public void setDepartmentId(long departmentId) {
        this.department_id = departmentId;
    }

    /**
     * Gets the timestamp when this status record was created.
     *
     * @return the creation timestamp
     * @see #getCreated()
     */
    public Date getCreatedDt() {
        return createdDt;
    }

    /**
     * Sets the timestamp when this status record was created.
     *
     * @param createdDt the creation timestamp to set
     * @see #getCreated()
     */
    public void setCreatedDt(Date createdDt) {
        this.createdDt = createdDt;
    }

    /**
     * Gets the creation date formatted as MM/dd/yyyy.
     *
     * <p>Provides a user-friendly formatted version of the creation
     * timestamp for display in reports and user interfaces. The format
     * is locale-independent and consistent across the application.</p>
     *
     * @return the creation date formatted as MM/dd/yyyy string, or formatted
     *         representation of current date if createdDt is null
     * @see #getCreatedDt()
     */
    public String getCreated(){
        return sdf.format(createdDt);
    }

    /**
     * Gets the timestamp when the participant finalized their survey.
     *
     * <p>This timestamp represents when the participant completed and submitted
     * their survey response. Initially set to the creation timestamp but should
     * be updated to reflect the actual completion time.</p>
     *
     * @return the finalization timestamp
     */
    public Date getFinalizedDt() {
        return finalizedDt;
    }

    /**
     * Sets the timestamp when the participant finalized their survey.
     *
     * <p>This should be called when the participant completes their survey
     * to accurately track completion times for reporting and analytics.</p>
     *
     * @param finalizedDt the finalization timestamp to set
     */
    public void setFinalizedDt(Date finalizedDt) {
        this.finalizedDt = finalizedDt;
    }
}
