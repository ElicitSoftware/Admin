package com.elicitsoftware.model;

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
 *   <li><strong>Survey Association:</strong> Links to specific survey instances</li>
 *   <li><strong>Contact Management:</strong> Email and phone contact information</li>
 *   <li><strong>Status Tracking:</strong> Comprehensive participation status monitoring</li>
 * </ul>
 * 
 * <p><strong>Database Mapping:</strong></p>
 * <ul>
 *   <li><strong>Table:</strong> {@code survey.status}</li>
 *   <li><strong>Primary Key:</strong> Manual ID assignment (not auto-generated)</li>
 *   <li><strong>External Integration:</strong> XID field for external system correlation</li>
 * </ul>
 * 
 * <p><strong>Use Cases:</strong></p>
 * <ul>
 *   <li><strong>Progress Tracking:</strong> Monitor survey completion status</li>
 *   <li><strong>Reporting:</strong> Generate participation and completion reports</li>
 *   <li><strong>Contact Management:</strong> Manage participant communication</li>
 *   <li><strong>Data Integration:</strong> Interface with external systems via XID</li>
 * </ul>
 * 
 * @author Elicit Software
 * @version 1.0
 * @since 1.0
 * @see Survey
 * @see Subject
 * @see Respondent
 * @see PanacheEntityBase
 */
@Entity
@Table(name = "status", schema = "survey")
public class Status extends PanacheEntityBase {

    @Transient
    private final static SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yyyy");
    @Id
    @Column(name = "id", unique = true, nullable = false, precision = 20)
    private long id;
    @Column(name = "XID")
    private String xid;
    @Column(name = "survey_id")
    private long surveyId;
    @Column(name = "firstName")
    private String firstName;
    @Column(name = "lastName")
    private String lastName;
    @Column(name = "middleName")
    private String middleName;
    @Column(name = "dob")
    @Temporal(TemporalType.DATE)
    private Date dob;
    @Column(name = "email")
    private String email;
    @Column(name = "phone", nullable = true)
    private String phone;
    @Column(name = "department_name")
    private String departmentName;
    @Column(name = "department_id")
    private long department_id;
    @Column(name = "token")
    private String token;
    @Column(name = "status")
    private String status;
    @Column(name = "CREATED_DT")
    @Temporal(TemporalType.TIMESTAMP)
    public Date createdDt = new Date();
    @Column(name = "finalized_dt")
    @Temporal(TemporalType.TIMESTAMP)
    public Date finalizedDt = new Date();

    public Status() {
        super();
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getXid() {
        return xid;
    }

    public void setXid(String xid) {
        this.xid = xid;
    }

    public long getSurveyId() {
        return surveyId;
    }

    public void setSurveyId(long surveyId) {
        this.surveyId = surveyId;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getMiddleName() {
        return middleName;
    }

    public void setMiddleName(String middleName) {
        this.middleName = middleName;
    }

    public Date getDob() {
        return dob;
    }

    public void setDob(Date dob) {
        this.dob = dob;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getDepartmentName() {
        return departmentName;
    }

    public void setDepartmentName(String departmentName) {
        this.departmentName = departmentName;
    }

    public Date getCreatedDt() {
        return createdDt;
    }

    public void setCreatedDt(Date createdDt) {
        this.createdDt = createdDt;
    }
    public String getCreated(){
        return sdf.format(createdDt);
    }

    public Date getFinalizedDt() {
        return finalizedDt;
    }

    public void setFinalizedDt(Date finalizedDt) {
        this.finalizedDt = finalizedDt;
    }
}
