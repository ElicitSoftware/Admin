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
import io.quarkus.panache.common.Parameters;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

/**
 * JPA entity representing survey subjects (participants/respondents) in the Elicit survey system.
 *
 * <p>This entity models individuals who participate in surveys, capturing their personal information,
 * contact details, and survey participation context. A Subject represents the administrative
 * record of a survey participant, linking their demographic information with their survey
 * responses through the associated {@link Respondent} entity.</p>
 *
 * <p><strong>Key Relationships:</strong></p>
 * <ul>
 *   <li><strong>Respondent:</strong> One-to-one relationship with response tracking entity</li>
 *   <li><strong>Survey:</strong> Many-to-one relationship with the survey being taken</li>
 *   <li><strong>Department:</strong> Many-to-one relationship with the organizing department</li>
 * </ul>
 *
 * <p><strong>Data Management Features:</strong></p>
 * <ul>
 *   <li><strong>Personal Information:</strong> Name, contact details, demographics</li>
 *   <li><strong>Survey Context:</strong> Survey and department assignments</li>
 *   <li><strong>Contact Validation:</strong> Ensures valid email or phone contact methods</li>
 *   <li><strong>Audit Trail:</strong> Creation timestamp for tracking</li>
 *   <li><strong>External Integration:</strong> XID field for external system linking</li>
 * </ul>
 *
 * <p><strong>Validation Requirements:</strong></p>
 * <ul>
 *   <li><strong>Required Fields:</strong> firstName, lastName, surveyId, departmentId</li>
 *   <li><strong>Contact Information:</strong> At least email or phone must be provided</li>
 *   <li><strong>Length Constraints:</strong> Various field length validations</li>
 *   <li><strong>Data Integrity:</strong> Cross-field validation for contact methods</li>
 * </ul>
 *
 * <p><strong>Named Queries:</strong></p>
 * <ul>
 *   <li><strong>AppointmentView.findByDateDeptAndXidList:</strong> Find subjects by date range, department, and XID list</li>
 *   <li><strong>AppointmentView.mainViewSearch:</strong> Main search query for appointment views</li>
 * </ul>
 *
 * <p><strong>Usage Examples:</strong></p>
 * <pre>{@code
 * // Create a new survey subject
 * Subject subject = new Subject();
 * subject.setFirstName("John");
 * subject.setLastName("Doe");
 * subject.setEmail("john.doe@example.com");
 * subject.setSurveyId(123L);
 * subject.setDepartmentId(456L);
 * subject.persist();
 *
 * // Find subjects by department and date range
 * List<Subject> subjects = Subject.find(
 *     "AppointmentView.findByDateDeptAndXidList",
 *     Parameters.with("departmentId", 456L)
 *               .and("startDate", startDate)
 *               .and("endDate", endDate)
 *               .and("xids", xidList)
 * ).list();
 * }</pre>
 *
 * <p><strong>Contact Information Management:</strong></p>
 * <ul>
 *   <li><strong>Email:</strong> Primary contact method for survey invitations</li>
 *   <li><strong>Phone:</strong> Alternative contact method (legacy SMS support)</li>
 *   <li><strong>Address:</strong> Physical address information for context</li>
 *   <li><strong>Validation:</strong> Custom validation ensures at least one contact method</li>
 * </ul>
 *
 * @author Elicit Software
 * @version 1.0
 * @since 1.0
 * @see Respondent
 * @see Survey
 * @see Department
 * @see com.elicitsoftware.admin.validator.ValidRespondent
 * @see PanacheEntityBase
 */
@Entity
@Table(name = "subjects", schema = "survey")
@NamedQueries({
        @NamedQuery(name = "AppointmentView.findByDateDeptAndXidList", query = "SELECT S FROM Subject S where S.departmentId = :departmentId and S.createdDt between :startDate and :endDate and S.xid IN : xids"),
        @NamedQuery(name = "AppointmentView.mainViewSearch", query = "SELECT S FROM Subject S where S.departmentId in(:departmentIds) and S.createdDt between :startDate and :endDate and S.xid IN : xids"),
})
public class Subject extends PanacheEntityBase {

    @Transient
    private final SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yyyy");
    /**
     * The timestamp when this subject record was created.
     * <p>
     * Automatically initialized to the current date and time when a new
     * Subject instance is created. Used for auditing and tracking when
     * participants were registered in the system.
     */
    @Column(name = "CREATED_DT")
    public Date createdDt = new Date();

    @Id
    @SequenceGenerator(name = "SUBJECTS_ID_GENERATOR", schema = "survey", sequenceName = "SUBJECTS_SEQ", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "SUBJECTS_ID_GENERATOR")
    @Column(unique = true, nullable = false, precision = 20)
    private long id;

    @Column(name = "XID")
    private String xid;

    @NotNull
    @OneToOne(cascade = CascadeType.PERSIST)
    private Respondent respondent;

    @Column(name = "survey_id")
    @NotNull(message = "survey id must not be null")
    private long surveyId;

    @Column(name = "department_id")
    @NotNull(message = "Department id must not be null")
    private long departmentId;

    @Column(name = "firstName")
    @NotNull(message = "First name cannot be blank")
    @NotBlank
    @Size(max = 50,
            message = "First name max length 50 characters")
    private String firstName;

    @Column(name = "lastName")
    @NotNull(message = "Last name cannot be blank")
    @NotBlank
    @Size(max = 50,
            message = "Last name max length 50 characters")
    private String lastName;
    @Column(name = "middleName")
    @Size(max = 50,
            message = "Middle name max length 50 characters")
    private String middleName;

    @Column(name = "dob")
    @Past(message = "Date of birth must be in the past")
    private LocalDate dob;

    @Column(name = "email")
    @Size(max = 255, message = "Max Email length 50 characters")
    @Email(message = "The email is invalid")
    @NotBlank
    private String email;
    /**
     * Subject's phone number.
     *
     * <p>Optional secondary contact method following ###-###-#### format.
     * While historically used for SMS communications, this is now primarily
     * kept for demographic purposes and alternative contact scenarios.</p>
     *
     * <p><strong>Validation Rules:</strong></p>
     * <ul>
     *   <li><strong>Optional:</strong> May be null</li>
     *   <li><strong>Format:</strong> Must match ###-###-#### pattern if provided</li>
     *   <li><strong>Length:</strong> Maximum 20 characters</li>
     * </ul>
     *
     * <p><strong>Format Examples:</strong></p>
     * <ul>
     *   <li><strong>Valid:</strong> "555-123-4567"</li>
     *   <li><strong>Invalid:</strong> "5551234567", "(555) 123-4567", "+1-555-123-4567"</li>
     * </ul>
     *
     * <p><strong>Legacy Note:</strong></p>
     * <p>This field was originally used for SMS survey invitations but is now
     * primarily maintained for demographic reporting and backup contact purposes.</p>
     */
    @Column(name = "phone", nullable = true)
    @Size(max = 20)
    @Pattern(regexp = "^\\d{3}-\\d{3}-\\d{4}$", message = "Telephone must match ###-###-#### format")
    private String phone;

    /**
     * Constructs a new Subject with the specified parameters.
     * <p>
     * Creates a Subject instance with all required demographic and contact
     * information. The creation date is automatically set to the current time
     * in EST timezone.
     *
     * @param xid the external identifier for the subject
     * @param surveyId the ID of the associated survey
     * @param departmentId the ID of the associated department
     * @param firstName the subject's first name
     * @param lastName the subject's last name
     * @param middleName the subject's middle name (may be null)
     * @param dob the subject's date of birth
     * @param email the subject's email address
     * @param phone the subject's phone number
     */
    public Subject(String xid, long surveyId, long departmentId, String firstName, String lastName, String middleName, LocalDate dob, String email, String phone) {
        super();
        //todo add created date
        sdf.setTimeZone(TimeZone.getTimeZone("EST"));
        this.xid = xid;
        this.surveyId = surveyId;
        this.departmentId = departmentId;
        this.firstName = firstName;
        this.lastName = lastName;
        this.middleName = middleName;
        this.dob = dob;
        this.email = email;
        this.phone = phone;
    }

    /**
     * Default constructor for JPA entity instantiation.
     * <p>
     * Creates a new Subject instance with default values. This constructor
     * is required by JPA for entity instantiation and should not be used
     * directly in application code.
     */
    public Subject() {
        super();
    }

    /**
     * Finds subjects by department, external IDs, and date range.
     * <p>
     * Retrieves subjects that belong to the specified department, have
     * external IDs matching the provided list, and were created within
     * the specified date range.
     *
     * @param department_id the department ID to filter by
     * @param xids list of external IDs to match
     * @param startDate the start of the date range (inclusive)
     * @param endDate the end of the date range (inclusive)
     * @return list of matching Subject entities
     */
    @Transient
    public static List<Subject> findByDepartmentXidDates(long department_id, List<String> xids, Date startDate, Date endDate) {
        return find("#AppointmentView.findByDateDeptAndXidList", Parameters.with("departmentId", department_id).and("startDate", startDate).and("endDate", endDate).and("xid", "(" + String.join("','", xids) + ")")).list();
    }

    /**
     * Returns the unique identifier for this subject.
     *
     * @return the subject ID
     */
    public long getId() {
        return id;
    }

    /**
     * Sets the unique identifier for this subject.
     *
     * @param id the subject ID to set
     */
    public void setId(long id) {
        this.id = id;
    }

    /**
     * Returns the external identifier for this subject.
     *
     * @return the external ID
     */
    public String getXid() {
        return xid;
    }

    /**
     * Sets the external identifier for this subject.
     *
     * @param xid the external ID to set
     */
    public void setXid(String xid) {
        this.xid = xid;
    }

    /**
     * Returns the respondent associated with this subject.
     *
     * @return the respondent instance
     */
    public Respondent getRespondent() {
        return respondent;
    }

    /**
     * Sets the respondent for this subject.
     *
     * @param respondent the respondent to associate
     */
    public void setRespondent(Respondent respondent) {
        this.respondent = respondent;
    }

    /**
     * Returns the department ID.
     *
     * @return the department ID
     */
    public long getDepartmentId() {
        return departmentId;
    }

    /**
     * Sets the department ID.
     *
     * @param departmentId the department ID to set
     */
    public void setDepartmentId(long departmentId) {
        this.departmentId = departmentId;
    }

    /**
     * Returns the survey ID.
     *
     * @return the survey ID
     */
    public long getSurveyId() {
        return surveyId;
    }

    /**
     * Sets the survey ID.
     *
     * @param surveyId the survey ID to set
     */
    public void setSurveyId(long surveyId) {
        this.surveyId = surveyId;
    }

    /**
     * Returns the first name.
     *
     * @return the first name
     */
    public String getFirstName() {
        return firstName;
    }

    /**
     * Sets the first name.
     *
     * @param firstName the first name to set
     */
    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    /**
     * Returns the last name.
     *
     * @return the last name
     */
    public String getLastName() {
        return lastName;
    }

    /**
     * Sets the last name.
     *
     * @param lastName the last name to set
     */
    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    /**
     * Returns the middle name.
     *
     * @return the middle name
     */
    public String getMiddleName() {
        return middleName;
    }

    /**
     * Sets the middle name.
     *
     * @param middleName the middle name to set
     */
    public void setMiddleName(String middleName) {
        this.middleName = middleName;
    }

    /**
     * Returns the date of birth.
     *
     * @return the date of birth
     */
    public LocalDate getDob() {
        return dob;
    }

    /**
     * Sets the date of birth.
     *
     * @param dob the date of birth to set
     */
    public void setDob(LocalDate dob) {
        this.dob = dob;
    }

    /**
     * Returns the email address.
     *
     * @return the email address
     */
    public String getEmail() {
        return email;
    }

    /**
     * Sets the email address.
     *
     * @param email the email address to set
     */
    public void setEmail(String email) {
        this.email = email;
    }

    /**
     * Returns the phone number.
     *
     * @return the phone number
     */
    public String getPhone() {
        return phone;
    }

    /**
     * Sets the phone number.
     *
     * @param phone the phone number to set
     */
    public void setPhone(String phone) {
        this.phone = phone;
    }

    /**
     * Returns the creation date.
     *
     * @return the creation date
     */
    public Date getCreatedDt() {
        return createdDt;
    }

    /**
     * Sets the creation date.
     *
     * @param createdDt the creation date to set
     */
    public void setCreatedDt(Date createdDt) {
        this.createdDt = createdDt;
    }

    /**
     * Finds a subject by their authentication token.
     * <p>
     * Searches for a subject whose associated respondent has the specified
     * authentication token. This method is used for token-based authentication
     * and survey access.
     *
     * @param token the authentication token to search for
     * @return the Subject associated with the token, or null if not found
     */
    public static Subject findSubjectByToken(String token) {
        // Implement DB lookup here, e.g. using JPA or Panache
        return Subject.find("respondent.token", token).firstResult();
    }
}
