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

/**
 * JPA entity representing organizational departments in the Elicit survey system.
 *
 * <p>This entity models the departmental structure within organizations that use
 * the Elicit survey platform. Departments serve as organizational units for
 * grouping users, managing access control, and organizing survey data collection
 * activities.</p>
 *
 * <p><strong>Key Functions:</strong></p>
 * <ul>
 *   <li><strong>User Organization:</strong> Groups users by departmental affiliation</li>
 *   <li><strong>Access Control:</strong> Enables department-based data segregation</li>
 *   <li><strong>Survey Management:</strong> Associates surveys with specific departments</li>
 *   <li><strong>Message Templating:</strong> Provides default messaging templates</li>
 * </ul>
 *
 * <p><strong>Database Mapping:</strong></p>
 * <ul>
 *   <li><strong>Table:</strong> {@code survey.departments}</li>
 *   <li><strong>Primary Key:</strong> Auto-generated sequence-based ID</li>
 *   <li><strong>Unique Constraints:</strong> Department codes should be unique per organization</li>
 * </ul>
 *
 * <p><strong>Relationships:</strong></p>
 * <ul>
 *   <li><strong>Users:</strong> One-to-many relationship with {@link User} entities</li>
 *   <li><strong>Subjects:</strong> One-to-many relationship with {@link Subject} entities</li>
 *   <li><strong>Surveys:</strong> One-to-many relationship with {@link Survey} entities</li>
 *   <li><strong>Message Templates:</strong> References to {@link MessageTemplate} entities</li>
 * </ul>
 *
 * <p><strong>Usage Examples:</strong></p>
 * <pre>{@code
 * // Create a new department
 * Department dept = new Department();
 * dept.name = "Cardiology";
 * dept.code = "CARD";
 * dept.defaultMessageId = "welcome-template-001";
 * dept.persist();
 *
 * // Find departments by name
 * List<Department> departments = Department.find("name", "Cardiology").list();
 *
 * // Get all departments
 * List<Department> allDepartments = Department.listAll();
 * }</pre>
 *
 * <p><strong>Business Rules:</strong></p>
 * <ul>
 *   <li><strong>Name Uniqueness:</strong> Department names should be unique within an organization</li>
 *   <li><strong>Code Standards:</strong> Department codes should follow organizational conventions</li>
 *   <li><strong>Default Messages:</strong> Each department should have appropriate default message templates</li>
 * </ul>
 *
 * @author Elicit Software
 * @version 1.0
 * @since 1.0
 * @see User
 * @see Subject
 * @see Survey
 * @see MessageTemplate
 * @see PanacheEntityBase
 */
@Entity
@Table(name = "departments", schema = "survey")
public class Department extends PanacheEntityBase {

    /**
     * Unique identifier for the department.
     *
     * <p>Auto-generated primary key using a database sequence. This ID uniquely
     * identifies each department within the system and is used for establishing
     * relationships with other entities.</p>
     *
     * <p><strong>Generation Strategy:</strong></p>
     * <ul>
     *   <li><strong>Sequence Name:</strong> {@code survey.departments_seq}</li>
     *   <li><strong>Allocation Size:</strong> 1 (no batch allocation)</li>
     *   <li><strong>Database Type:</strong> BIGINT (precision 20)</li>
     * </ul>
     */
    @Id
    @SequenceGenerator(name = "departments_id_generator", schema = "survey", sequenceName = "departments_seq", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "departments_id_generator")
    @Column(unique = true, nullable = false, precision = 20)
    public long id;

    /**
     * Human-readable name of the department.
     *
     * <p>The full display name of the department as it should appear in user interfaces
     * and reports. This name should be descriptive and meaningful to users within the
     * organization.</p>
     *
     * <p><strong>Examples:</strong> "Cardiology", "Emergency Medicine", "Human Resources"</p>
     *
     * <p><strong>Usage Guidelines:</strong></p>
     * <ul>
     *   <li>Should be unique within the organization</li>
     *   <li>Used for display purposes in UI components</li>
     *   <li>Should follow organizational naming conventions</li>
     * </ul>
     */
    @Column(name = "name")
    public String name;

    /**
     * Short code or abbreviation for the department.
     *
     * <p>A brief, standardized code that can be used for quick identification,
     * reporting, and integration with external systems. This code should be
     * concise and follow organizational standards.</p>
     *
     * <p><strong>Examples:</strong> "CARD", "EM", "HR", "IT"</p>
     *
     * <p><strong>Usage Guidelines:</strong></p>
     * <ul>
     *   <li>Should be unique and standardized</li>
     *   <li>Typically 2-8 characters in length</li>
     *   <li>Used for integration and reporting purposes</li>
     *   <li>Should avoid special characters for compatibility</li>
     * </ul>
     */
    @Column(name = "code")
    public String code;

    /**
     * Reference to the default message template for this department.
     *
     * <p>Identifier for the default {@link MessageTemplate} that should be used
     * for communications related to this department. This template serves as the
     * starting point for survey invitations, reminders, and other departmental
     * communications.</p>
     *
     * <p><strong>Purpose:</strong></p>
     * <ul>
     *   <li>Provides consistent messaging for the department</li>
     *   <li>Reduces setup time for new surveys</li>
     *   <li>Ensures brand and tone consistency</li>
     *   <li>Can be customized per department's needs</li>
     * </ul>
     *
     * <p><strong>Related Operations:</strong></p>
     * <ul>
     *   <li>Used when creating new surveys for the department</li>
     *   <li>Applied to survey invitation templates</li>
     *   <li>Can be overridden on a per-survey basis</li>
     * </ul>
     *
     * @see MessageTemplate
     */
    @Column(name = "default_message_id")
    public String defaultMessageId;

    /**
     * Default constructor for JPA entity instantiation.
     * <p>
     * Creates a new Department instance with default values. This constructor
     * is required by JPA for entity instantiation and is used by the
     * persistence framework during query execution and relationship loading.
     */
    public Department() {
        // Default constructor for JPA
    }

    /**
     * Returns the display name of the department.
     *
     * <p>Accessor method for the department's human-readable name. This method
     * provides a consistent way to retrieve the department name for display
     * in user interfaces, reports, and logging.</p>
     *
     * <p><strong>Usage:</strong></p>
     * <pre>{@code
     * Department dept = Department.findById(1L);
     * String displayName = dept.getName();
     * logger.info("Processing department: " + displayName);
     * }</pre>
     *
     * @return the department name, may be null if not set
     */
    public String getName() {
        return name;
    }
}
