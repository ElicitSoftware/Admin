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

import java.util.Set;

/**
 * JPA entity representing surveys in the Elicit survey management system.
 *
 * <p>This entity models survey definitions that can be deployed to collect feedback
 * from subjects/respondents. Each survey contains metadata for display, configuration
 * for data collection, and references to related entities such as reports and
 * post-survey actions.</p>
 *
 * <p><strong>Survey Features:</strong></p>
 * <ul>
 *   <li><strong>Metadata Management:</strong> Name, title, description for identification</li>
 *   <li><strong>Display Configuration:</strong> Ordering and presentation settings</li>
 *   <li><strong>Report Integration:</strong> Associated report definitions for data analysis</li>
 *   <li><strong>Post-Survey Actions:</strong> Automated actions after survey completion</li>
 *   <li><strong>URL Integration:</strong> External URLs for survey completion workflows</li>
 * </ul>
 *
 * <p><strong>Key Relationships:</strong></p>
 * <ul>
 *   <li><strong>ReportDefinition:</strong> One-to-many relationship with survey reports</li>
 *   <li><strong>PostSurveyAction:</strong> One-to-many relationship with completion actions</li>
 *   <li><strong>Subject:</strong> One-to-many relationship with survey participants</li>
 *   <li><strong>User:</strong> Many-to-many relationship with managing users</li>
 * </ul>
 *
 * <p><strong>Database Mapping:</strong></p>
 * <ul>
 *   <li><strong>Table:</strong> {@code survey.surveys}</li>
 *   <li><strong>Primary Key:</strong> Auto-generated sequence-based Integer ID</li>
 *   <li><strong>Ordering:</strong> Reports and actions are ordered by their respective order fields</li>
 * </ul>
 *
 * <p><strong>Usage Examples:</strong></p>
 * <pre>{@code
 * // Create a new survey
 * Survey survey = new Survey();
 * survey.name = "Patient Experience Survey";
 * survey.title = "Help Us Improve Your Experience";
 * survey.description = "Share your feedback about your recent visit";
 * survey.displayOrder = 1;
 * survey.persist();
 *
 * // Find surveys by name
 * List<Survey> surveys = Survey.find("name", "Patient Experience Survey").list();
 *
 * // Get all surveys ordered by display order
 * List<Survey> allSurveys = Survey.find("ORDER BY displayOrder ASC").list();
 * }</pre>
 *
 * <p><strong>License Information:</strong></p>
 * <p>This software is licensed under the PolyForm Noncommercial License 1.0.0.
 * Copyright (C) 2025 The Regents of the University of Michigan - Rogel Cancer Center.</p>
 *
 * @author Elicit Software
 * @version 1.0
 * @since 1.0
 * @see ReportDefinition
 * @see PostSurveyAction
 * @see Subject
 * @see User
 * @see PanacheEntityBase
 */
@Entity
@Table(name = "surveys", schema = "survey")
public class Survey extends PanacheEntityBase {

    /**
     * Unique identifier for the survey.
     *
     * <p>Auto-generated primary key using a database sequence. Uses Integer type
     * for compatibility with existing survey systems and external integrations.</p>
     */
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "SURVEY_ID_GENERATOR")
    @SequenceGenerator(name = "SURVEY_ID_GENERATOR", schema = "survey", sequenceName = "surveys_seq", allocationSize = 1)
    @Column(name = "id", unique = true, nullable = false)
    public Integer id;

    /**
     * Display order for presenting surveys in user interfaces.
     *
     * <p>Determines the order in which surveys appear in lists, menus, and
     * selection interfaces. Lower numbers appear first. This allows administrators
     * to control the priority and visibility of different surveys.</p>
     */
    @Column(name = "display_order", nullable = false, precision = 3)
    public Integer displayOrder;

    /**
     * Internal name identifier for the survey.
     *
     * <p>Short, system-friendly name used for internal references, logging,
     * and administrative purposes. Should be unique and descriptive for
     * easy identification in administrative interfaces.</p>
     */
    @Column(name = "name")
    public String name;

    /**
     * Public title displayed to survey participants.
     *
     * <p>Human-readable title shown to respondents when taking the survey.
     * Should be engaging, clear, and descriptive of the survey's purpose
     * to encourage participation and set appropriate expectations.</p>
     */
    @Column(name = "title")
    public String title;

    /**
     * Detailed description of the survey's purpose and content.
     *
     * <p>Comprehensive description that explains what the survey covers,
     * why it's important, and what participants can expect. Used in
     * administrative interfaces and potentially in survey invitations.</p>
     */
    @Column(name = "description")
    public String description;

    /**
     * Key used for initial display configuration of the survey.
     *
     * <p>Reference key that determines how the survey is initially presented
     * to participants. This may control layout, styling, or other presentation
     * aspects specific to the survey platform implementation.</p>
     */
    @Column(name = "initial_display_key")
    public String initialDisplayKey;

    /**
     * URL to redirect participants after survey completion.
     *
     * <p>External URL where participants are directed after successfully
     * completing the survey. This can be used for thank you pages, additional
     * resources, or integration with other systems in the participant workflow.</p>
     */
    @Column(name = "post_survey_url")
    public String postSurveyURL;

    /**
     * Set of report definitions associated with this survey.
     *
     * <p>One-to-many relationship with {@link ReportDefinition} entities that
     * define how survey data should be analyzed and presented. Reports are
     * ordered by their display order for consistent presentation.</p>
     *
     * <p><strong>Features:</strong></p>
     * <ul>
     *   <li>Eager loading for performance when accessing survey details</li>
     *   <li>Ordered by displayOrder ASC for consistent report presentation</li>
     *   <li>Allows multiple report types per survey</li>
     * </ul>
     */
    @OneToMany(mappedBy = "survey", fetch = FetchType.EAGER)
    @OrderBy("displayOrder ASC")
    public Set<ReportDefinition> reports;

    /**
     * Set of actions to execute after survey completion.
     *
     * <p>One-to-many relationship with {@link PostSurveyAction} entities that
     * define automated actions to be performed when a participant completes
     * the survey. Actions are executed in order of their execution order.</p>
     *
     * <p><strong>Common Actions:</strong></p>
     * <ul>
     *   <li>Send confirmation emails</li>
     *   <li>Update external systems</li>
     *   <li>Trigger analytics events</li>
     *   <li>Generate reports or notifications</li>
     * </ul>
     *
     * <p><strong>Features:</strong></p>
     * <ul>
     *   <li>Eager loading for immediate action processing</li>
     *   <li>Ordered by executionOrder ASC for proper sequence</li>
     *   <li>Supports multiple actions per survey completion</li>
     * </ul>
     */
    @OneToMany(mappedBy = "survey", fetch = FetchType.EAGER)
    @OrderBy("executionOrder ASC")
    public Set<PostSurveyAction> postSurveyActions;

    /**
     * Default constructor for JPA entity instantiation.
     * <p>
     * Creates a new Survey instance with default values. This constructor
     * is required by JPA for entity instantiation and is used by the
     * persistence framework during query execution and relationship loading.
     */
    public Survey() {
        // Default constructor for JPA
    }

}
