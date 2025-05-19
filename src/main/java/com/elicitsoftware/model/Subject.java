package com.elicitsoftware.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Parameters;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;

import java.text.SimpleDateFormat;
import java.util.*;

@Entity
@Table(name = "subjects", schema = "survey")
@NamedQueries({
        @NamedQuery(name = "AppointmentView.findByDateDeptAndXidList", query = "SELECT S FROM Subject S where S.departmentId = :departmentId and S.createdDt between :startDate and :endDate and S.xid IN : xids"),
})

public class Subject extends PanacheEntityBase {

    @Transient
    private final static SimpleDateFormat dayFormat = new SimpleDateFormat("MM/dd/yyyy");

    @Id
    @SequenceGenerator(name = "SUBJECTS_ID_GENERATOR", schema = "survey", sequenceName = "SUBJECTS_SEQ", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "SUBJECTS_ID_GENERATOR")
    @Column(unique = true, nullable = false, precision = 20)
    private long id;

    @Column(name = "XID")
    private String xid;

    @Column(name = "survey_id")
    @NotNull(message = "survey id must not be null")
    private long surveyId;

    @Column(name = "department_id")
    @NotNull(message = "Department id must not be null")
    private long departmentId;

    @Column(name = "firstName")
    @NotNull(message = "First name cannot be blank")
    @Size(max = 50,
            message = "First name max length 50 characters")
    private String firstName;

    @Column(name = "lastName")
    @NotNull(message = "Last name cannot be blank")
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
    private Date dob;

    @Column(name = "email")
    @Size(max = 255, message = "Max Email length 50 characters")
    @Email(message = "The email is invalid")
    private String email;

    @Column(name = "phone", nullable = true)
    @Size(max = 20)
    @Pattern(regexp = "^\\d{3}-\\d{3}-\\d{4}$", message = "Telephone must match ###-###-#### format")
    private String phone;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "CREATED_DT")
    public Date createdDt = new Date();

    @Transient
    private final SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yyyy");

    public Subject(String xid, long surveyId, long departmentId, String firstName, String lastName, String middleName, Date dob, String email, String phone) {
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

    public long getDepartmentId() {
        return departmentId;
    }

    public void setDepartmentId(long departmentId) {
        this.departmentId = departmentId;
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

    public void setMobile(String phone) {
        this.phone = phone;
    }

    @Transient
    public static List<Subject> findByDepartmentAndMRNSAndDates(long department_id, List<String> mrns, Date startDate, Date endDate) {
        return find("#AppointmentView.findByDateDeptAndXidList", Parameters.with("departmentId", department_id).and("startDate", startDate).and("endDate", endDate).and("mrns", "(" + String.join("','", mrns) + ")")).list();
    }

//    @Transient
//    public List<UploadRequest> getUpdateRequest(Department dep) {
//        ArrayList<UploadRequest> requests = new ArrayList<UploadRequest>();
//
//        String[] commonValues = new String[UploadRequest.CSVColumn.values().length];
//        commonValues[UploadRequest.CSVColumn.xid.ordinal()] = this.getXid();
//        commonValues[UploadRequest.CSVColumn.FirstName.ordinal()] = this.firstName;
//        commonValues[UploadRequest.CSVColumn.LastName.ordinal()] = this.lastName;
//        if (this.middleName != null) {
//            commonValues[UploadRequest.CSVColumn.MiddleName.ordinal()] = this.middleName;
//        }
//        if (this.dob != null) {
//            commonValues[UploadRequest.CSVColumn.DOB.ordinal()] = dayFormat.format(this.dob);
//        }
//        if (this.email != null) {
//            commonValues[UploadRequest.CSVColumn.Email.ordinal()] = this.email;
//        }
//        if (this.mobile != null) {
//            commonValues[UploadRequest.CSVColumn.CellPhone.ordinal()] = this.mobile;
//        }
//        return requests;
//    }
//
//    @Transient
//    public static long searchLength(ListRequest listRequest) {
//        try {
//            HashMap<String, Object> params = new HashMap<String, Object>();
//            String query = getQueryString(listRequest, params);
//
//            long returnLength = Subject.find(query, params).page(Page.of(listRequest.page, listRequest.pageSize)).count();
//
//            return returnLength;
//        } catch (Exception e) {
//            System.out.println(e);
//            return 0;
//        }
//    }
//
//    @Transient
//    public static List<Subject> search(ListRequest listRequest) {
//        try {
//            HashMap<String, Object> params = new HashMap<String, Object>();
//            String query = getQueryString(listRequest, params);
//            query = query + " order by A.dateTime";
//
//            List<Subject> returnList = Subject.find(query, params).page(Page.of(listRequest.page, listRequest.pageSize)).list();
//
//            if (returnList == null) {
//                returnList = new ArrayList<Subject>();
//            }
//
//            return returnList;
//        } catch (Exception e) {
//            System.out.println(e);
//            return new ArrayList<Subject>();
//        }
//    }
//
//    private static String getQueryString(ListRequest listRequest, HashMap<String, Object> params) {
//        String query = "";
//        if (listRequest.departmentId == -1) {
//            User user = User.find("username", listRequest.username).firstResult();
//            if (user != null) {
//                for (Department dept : user.departments) {
//                    query = query + dept.id + ",";
//                }
//                query = query.substring(0, query.length() - 1);
//            }
//
//            query = " SELECT A FROM AppointmentView A where A.departmentId in (" + query + ")";
//        } else {
//            query = " SELECT A FROM AppointmentView A where A.departmentId = :dept";
//            params.put("dept", (long) listRequest.departmentId);
//        }
//
//        if (listRequest.startdate != null && !listRequest.startdate.isBlank()
//                && listRequest.enddate != null && !listRequest.enddate.isBlank()) {
//            query = query + " and A.dateTime between :startDate and :endDate";
//            params.put("startDate", listRequest.getStartDate());
//            params.put("endDate", listRequest.getEndDate());
//        } else if (listRequest.startdate != null && !listRequest.startdate.isBlank()
//                && (listRequest.enddate == null || listRequest.enddate.isBlank())) {
//            query = query + " and A.dateTime > :startDate";
//            params.put("startDate", listRequest.getStartDate());
//        } else if ((listRequest.startdate == null || listRequest.startdate.isBlank())
//                && (listRequest.enddate != null && !listRequest.enddate.isBlank())) {
//            query = query + " and A.dateTime < :endDate";
//            params.put("endDate", listRequest.getEndDate());
//        }
//
//        if (listRequest.token != null && !listRequest.token.isBlank()) {
//            query = query + " and lower(A.token) like :token";
//            params.put("token", "%" + listRequest.token.toLowerCase() + "%");
//        }
//
//        if (listRequest.firstname != null && !listRequest.firstname.isBlank()) {
//            query = query + " and lower(A.firstname) like :firstname";
//            params.put("firstname", "%" + listRequest.firstname.toLowerCase() + "%");
//        }
//        if (listRequest.lastname != null && !listRequest.lastname.isBlank()) {
//            query = query + " and lower(A.lastname) like :lastname";
//            params.put("lastname", "%" + listRequest.lastname.toLowerCase() + "%");
//        }
//        if (listRequest.mrn != null && !listRequest.mrn.isBlank()) {
//            query = query + " and lower(A.mrn) like :mrn";
//            params.put("mrn", "%" + listRequest.mrn.toLowerCase() + "%");
//        }
//
//        if (listRequest.email != null && !listRequest.email.isBlank()) {
//            query = query + " and lower(A.email) like :email";
//            params.put("email", "%" + listRequest.email.toLowerCase() + "%");
//        }
//        if (listRequest.phone != null && !listRequest.phone.isBlank()) {
//            query = query + " and A.mobile like :phone";
//            params.put("phone", "%" + listRequest.phone + "%");
//        }
//        return query;
//    }
//
}
