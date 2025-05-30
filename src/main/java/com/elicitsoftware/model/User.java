package com.elicitsoftware.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

import java.util.Set;

@Entity
@Table(name = "USERS", schema = "survey")
public class User extends PanacheEntityBase {

    @Id
    @SequenceGenerator(name = "USERS_ID_GENERATOR", schema = "survey", sequenceName = "USERS_SEQ", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "USERS_ID_GENERATOR")
    @Column(unique = true, nullable = false, precision = 20)
    private long id;

    @Column(name = "USERNAME")
    private String username;

    @Column(name = "FIRST_NAME")
    private String firstName;

    @Column(name = "LAST_NAME")
    private String lastName;

    @Column(name = "ACTIVE")
    private boolean active;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "USER_SURVEYS", schema = "survey", joinColumns = {
            @JoinColumn(name = "USER_ID")
    }, inverseJoinColumns = {
            @JoinColumn(name = "SURVEY_ID")
    })
    private Set<Survey> surveys;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "user_departments", schema = "survey", joinColumns = {
            @JoinColumn(name = "user_id")
    }, inverseJoinColumns = {
            @JoinColumn(name = "department_id")
    })
    @OrderBy(value = "name")
    private Set<Department> departments;

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
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

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Set<Survey> getSurveys() {
        return surveys;
    }

    public void setSurveys(Set<Survey> surveys) {
        this.surveys = surveys;
    }

    public Set<Department> getDepartments() {
        return departments;
    }

    public void setDepartments(Set<Department> departments) {
        this.departments = departments;
    }

    @Transient
    public long getDepartmentId(int position) {
        if (departments.size() > 0) {
            Department[] depArray = this.departments.toArray(new Department[departments.size()]);
            return depArray[position - 1].id;
        }
        return 1;
    }

    @Transient
    public Department getDepartment(int position) {
        if (departments.size() > 0) {
            Department[] depArray = this.departments.toArray(new Department[departments.size()]);
            return depArray[position - 1];
        }
        return null;
    }
}
