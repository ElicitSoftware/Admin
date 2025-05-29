package com.elicitsoftware.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "USERS", schema = "survey")
public class User extends PanacheEntityBase {

    @Id
    @SequenceGenerator(name = "USERS_ID_GENERATOR", schema = "survey", sequenceName = "USERS_SEQ", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "USERS_ID_GENERATOR")
    @Column(unique = true, nullable = false, precision = 20)
    public long id;

    @Column(name = "USERNAME")
    public String username;

    @Column(name = "FIRST_NAME")
    public String firstName;

    @Column(name = "LAST_NAME")
    public String lastName;

    @Column(name = "ACTIVE")
    public boolean active;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "USER_SURVEYS", schema = "survey", joinColumns = {
            @JoinColumn(name = "USER_ID")
    }, inverseJoinColumns = {
            @JoinColumn(name = "SURVEY_ID")
    })
    public Set<Survey> surveys;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "user_departments", schema = "survey", joinColumns = {
            @JoinColumn(name = "user_id")
    }, inverseJoinColumns = {
            @JoinColumn(name = "department_id")
    })
    @OrderBy(value = "name")
    public Set<Department> departments;

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
