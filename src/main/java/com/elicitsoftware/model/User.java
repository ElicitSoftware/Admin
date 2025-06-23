package com.elicitsoftware.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

import java.util.Set;

/**
 * JPA entity representing system users in the Elicit survey administration platform.
 * 
 * <p>This entity models administrative users who have access to the Elicit Admin application.
 * Users are authenticated through OpenID Connect (OIDC) and authorized based on their role
 * assignments and department affiliations. The entity manages both personal information
 * and organizational relationships.</p>
 * 
 * <p><strong>User Management Features:</strong></p>
 * <ul>
 *   <li><strong>Authentication Integration:</strong> Links with OIDC user identities</li>
 *   <li><strong>Department Assignment:</strong> Many-to-many relationship with departments</li>
 *   <li><strong>Survey Access:</strong> Many-to-many relationship with surveys</li>
 *   <li><strong>Active Status:</strong> Enable/disable user access</li>
 * </ul>
 * 
 * <p><strong>Authorization Model:</strong></p>
 * <ul>
 *   <li><strong>OIDC Roles:</strong> "elicit_admin" and "elicit_user" roles managed in OIDC provider</li>
 *   <li><strong>Department Access:</strong> Users can access data for their assigned departments</li>
 *   <li><strong>Survey Permissions:</strong> Users can manage surveys they're assigned to</li>
 *   <li><strong>Active Status:</strong> Inactive users cannot access the system</li>
 * </ul>
 * 
 * <p><strong>Database Relationships:</strong></p>
 * <ul>
 *   <li><strong>Departments:</strong> Many-to-many via {@code user_departments} table</li>
 *   <li><strong>Surveys:</strong> Many-to-many via {@code USER_SURVEYS} table</li>
 *   <li><strong>Eager Loading:</strong> Both relationships use eager fetching for performance</li>
 * </ul>
 * 
 * <p><strong>Usage Examples:</strong></p>
 * <pre>{@code
 * // Create a new user
 * User user = new User();
 * user.setUsername("john.doe@hospital.com");
 * user.setFirstName("John");
 * user.setLastName("Doe");
 * user.setActive(true);
 * user.persist();
 * 
 * // Find user by username
 * User user = User.find("username", "john.doe@hospital.com").firstResult();
 * 
 * // Get user's departments
 * Set<Department> userDepts = user.getDepartments();
 * 
 * // Check if user is active
 * if (user.isActive()) {
 *     // Allow access
 * }
 * }</pre>
 * 
 * <p><strong>Security Considerations:</strong></p>
 * <ul>
 *   <li><strong>OIDC Integration:</strong> Username should match OIDC subject identifier</li>
 *   <li><strong>Data Segregation:</strong> Department assignments control data access</li>
 *   <li><strong>Active Status:</strong> Provides quick way to disable access without deletion</li>
 *   <li><strong>Role Management:</strong> Actual permissions managed in OIDC provider</li>
 * </ul>
 * 
 * @author Elicit Software
 * @version 1.0
 * @since 1.0
 * @see Department
 * @see Survey
 * @see PanacheEntityBase
 */
@Entity
@Table(name = "USERS", schema = "survey")
public class User extends PanacheEntityBase {

    /**
     * Unique identifier for the user.
     * 
     * <p>Auto-generated primary key using a database sequence. This ID uniquely
     * identifies each user within the system and is used for establishing
     * relationships with departments and surveys.</p>
     */
    @Id
    @SequenceGenerator(name = "USERS_ID_GENERATOR", schema = "survey", sequenceName = "USERS_SEQ", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "USERS_ID_GENERATOR")
    @Column(unique = true, nullable = false, precision = 20)
    private long id;

    /**
     * Username for authentication and identification.
     * 
     * <p>This field typically contains the user's email address or OIDC subject identifier.
     * It must match the identifier used in the OpenID Connect authentication provider
     * to ensure proper authentication integration.</p>
     * 
     * <p><strong>Format:</strong> Usually email address (e.g., "john.doe@hospital.com")</p>
     * <p><strong>Security:</strong> Must match OIDC principal name for authentication</p>
     */
    @Column(name = "USERNAME")
    private String username;

    /**
     * User's first name for display and personalization purposes.
     * 
     * <p>Used in user interfaces, reports, and communications to provide
     * a personalized experience. This information may be synchronized
     * from the OIDC provider or maintained separately.</p>
     */
    @Column(name = "FIRST_NAME")
    private String firstName;

    /**
     * User's last name for display and identification purposes.
     * 
     * <p>Combined with first name for full user identification in
     * administrative interfaces and reports.</p>
     */
    @Column(name = "LAST_NAME")
    private String lastName;

    /**
     * Flag indicating whether the user account is active and can access the system.
     * 
     * <p>When set to {@code false}, the user is effectively disabled and should
     * not be able to access the system, even if they have valid OIDC credentials.
     * This provides a way to quickly disable access without deleting the user record.</p>
     * 
     * <p><strong>Default:</strong> Should be {@code true} for new users</p>
     * <p><strong>Security:</strong> Should be checked during authentication</p>
     */
    @Column(name = "ACTIVE")
    private boolean active;

    /**
     * Set of surveys that this user has access to manage.
     * 
     * <p>Many-to-many relationship defining which surveys the user can view,
     * edit, and manage. This relationship is eagerly loaded for performance
     * when checking survey permissions.</p>
     * 
     * <p><strong>Join Table:</strong> {@code survey.USER_SURVEYS}</p>
     * <p><strong>Access Control:</strong> Determines which surveys appear in user's interface</p>
     */
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "USER_SURVEYS", schema = "survey", joinColumns = {
            @JoinColumn(name = "USER_ID")
    }, inverseJoinColumns = {
            @JoinColumn(name = "SURVEY_ID")
    })
    private Set<Survey> surveys;

    /**
     * Set of departments that this user is assigned to.
     * 
     * <p>Many-to-many relationship defining which departments the user can access
     * and manage. This relationship controls data segregation and determines which
     * survey data and administrative functions the user can access.</p>
     * 
     * <p><strong>Join Table:</strong> {@code survey.user_departments}</p>
     * <p><strong>Ordering:</strong> Departments are ordered by name for consistent display</p>
     * <p><strong>Access Control:</strong> Primary mechanism for data segregation</p>
     */
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "user_departments", schema = "survey", joinColumns = {
            @JoinColumn(name = "user_id")
    }, inverseJoinColumns = {
            @JoinColumn(name = "department_id")
    })
    @OrderBy(value = "name")
    private Set<Department> departments;

    /**
     * Gets the unique identifier for this user.
     * 
     * @return the user ID
     */
    public long getId() {
        return id;
    }

    /**
     * Sets the unique identifier for this user.
     * 
     * @param id the user ID to set
     */
    public void setId(long id) {
        this.id = id;
    }

    /**
     * Gets the username for authentication and identification.
     * 
     * @return the username, typically an email address
     */
    public String getUsername() {
        return username;
    }

    /**
     * Sets the username for authentication and identification.
     * 
     * @param username the username to set, should match OIDC identifier
     */
    public void setUsername(String username) {
        this.username = username;
    }

    /**
     * Gets the user's first name.
     * 
     * @return the first name
     */
    public String getFirstName() {
        return firstName;
    }

    /**
     * Sets the user's first name.
     * 
     * @param firstName the first name to set
     */
    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    /**
     * Gets the user's last name.
     * 
     * @return the last name
     */
    public String getLastName() {
        return lastName;
    }

    /**
     * Sets the user's last name.
     * 
     * @param lastName the last name to set
     */
    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    /**
     * Checks if the user account is active.
     * 
     * @return true if the user is active and can access the system, false otherwise
     */
    public boolean isActive() {
        return active;
    }

    /**
     * Sets the active status of the user account.
     * 
     * @param active true to enable access, false to disable
     */
    public void setActive(boolean active) {
        this.active = active;
    }

    /**
     * Gets the set of surveys this user can manage.
     * 
     * @return the surveys assigned to this user
     */
    public Set<Survey> getSurveys() {
        return surveys;
    }

    /**
     * Sets the surveys this user can manage.
     * 
     * @param surveys the surveys to assign to this user
     */
    public void setSurveys(Set<Survey> surveys) {
        this.surveys = surveys;
    }

    /**
     * Gets the set of departments this user is assigned to.
     * 
     * @return the departments this user can access
     */
    public Set<Department> getDepartments() {
        return departments;
    }

    /**
     * Sets the departments this user is assigned to.
     * 
     * @param departments the departments to assign to this user
     */
    public void setDepartments(Set<Department> departments) {
        this.departments = departments;
    }

    /**
     * Gets the ID of the department at the specified position.
     * 
     * <p>This transient method provides positional access to department IDs
     * for cases where the first assigned department ID is needed. Returns
     * a default value of 1 if no departments are assigned.</p>
     * 
     * @param position the position (1-based) of the department
     * @return the department ID at the specified position, or 1 if not found
     */
    @Transient
    public long getDepartmentId(int position) {
        if (departments.size() > 0) {
            Department[] depArray = this.departments.toArray(new Department[departments.size()]);
            return depArray[position - 1].id;
        }
        return 1;
    }

    /**
     * Gets the department object at the specified position.
     * 
     * <p>This transient method provides positional access to department objects
     * for cases where the first assigned department is needed. Returns null
     * if no departments are assigned or the position is invalid.</p>
     * 
     * @param position the position (1-based) of the department
     * @return the department at the specified position, or null if not found
     */
    @Transient
    public Department getDepartment(int position) {
        if (departments.size() > 0) {
            Department[] depArray = this.departments.toArray(new Department[departments.size()]);
            return depArray[position - 1];
        }
        return null;
    }
}
