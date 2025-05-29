package com.elicitsoftware.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import io.quarkus.panache.common.Parameters;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

@Entity
@Table(name = "subjects", schema = "survey")
@NamedQueries({
        @NamedQuery(name = "AppointmentView.findByDateDeptAndXidList", query = "SELECT S FROM Subject S where S.departmentId = :departmentId and S.createdDt between :startDate and :endDate and S.xid IN : xids"),
        @NamedQuery(name = "AppointmentView.mainViewSearch", query = "SELECT S FROM Subject S where S.departmentId in(:departmentIds) and S.createdDt between :startDate and :endDate and S.xid IN : xids"),
})
public class Subject extends PanacheEntityBase {

    @Transient
    private final SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yyyy");
    @Temporal(TemporalType.TIMESTAMP)
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
    @Temporal(TemporalType.DATE)
    private LocalDate dob;
    @Column(name = "email")
    @Size(max = 255, message = "Max Email length 50 characters")
    @Email(message = "The email is invalid")
    @NotBlank
    private String email;
    @Column(name = "phone", nullable = true)
    @Size(max = 20)
    @Pattern(regexp = "^\\d{3}-\\d{3}-\\d{4}$", message = "Telephone must match ###-###-#### format")
    private String phone;

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

    public Subject() {
        super();
    }

    @Transient
    public static List<Subject> findByDepartmentXidDates(long department_id, List<String> xids, Date startDate, Date endDate) {
        return find("#AppointmentView.findByDateDeptAndXidList", Parameters.with("departmentId", department_id).and("startDate", startDate).and("endDate", endDate).and("xid", "(" + String.join("','", xids) + ")")).list();
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

    public Respondent getRespondent() {
        return respondent;
    }

    public void setRespondent(Respondent respondent) {
        this.respondent = respondent;
    }

    public long getDepartmentId() {
        return departmentId;
    }

    public void setDepartmentId(long departmentId) {
        this.departmentId = departmentId;
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

    public LocalDate getDob() {
        return dob;
    }

    public void setDob(LocalDate dob) {
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

    public Date getCreatedDt() {
        return createdDt;
    }

    public void setCreatedDt(Date createdDt) {
        this.createdDt = createdDt;
    }

    public static Subject findSubjectByToken(String token) {
        // Implement DB lookup here, e.g. using JPA or Panache
        return Subject.find("respondent.token", token).firstResult();
    }
}
