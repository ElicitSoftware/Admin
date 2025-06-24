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
import jakarta.json.bind.annotation.JsonbTransient;
import jakarta.persistence.*;

/**
 * JPA entity representing a report definition for survey data visualization and analysis.
 *
 * <p>The ReportDefinition entity models configurable reports that can be generated
 * for survey data. Each report is associated with a specific survey and contains
 * metadata about how the report should be displayed and accessed within the
 * Elicit survey system.</p>
 *
 * <p><strong>Key Features:</strong></p>
 * <ul>
 *   <li><strong>Survey Association:</strong> Each report is tied to a specific survey</li>
 *   <li><strong>URL-based Access:</strong> Reports are accessed via configured URLs</li>
 *   <li><strong>Display Ordering:</strong> Reports can be ordered for consistent presentation</li>
 *   <li><strong>Flexible Configuration:</strong> Name and description provide metadata</li>
 * </ul>
 *
 * <p><strong>Database Mapping:</strong></p>
 * <ul>
 *   <li><strong>Table:</strong> {@code survey.reports}</li>
 *   <li><strong>Primary Key:</strong> Auto-generated sequence-based ID</li>
 *   <li><strong>Foreign Key:</strong> {@code survey_id} references {@link Survey}</li>
 * </ul>
 *
 * <p><strong>Usage Examples:</strong></p>
 * <pre>{@code
 * // Create a new report definition
 * ReportDefinition report = new ReportDefinition();
 * report.survey = surveyInstance;
 * report.name = "Response Summary";
 * report.description = "Summary of all survey responses";
 * report.url = "/reports/summary";
 * report.displayOrder = 1;
 * report.persist();
 *
 * // Find reports for a survey
 * List<ReportDefinition> reports = ReportDefinition.find("survey", surveyInstance).list();
 *
 * // Find reports ordered by display order
 * List<ReportDefinition> orderedReports = ReportDefinition.find("ORDER BY displayOrder").list();
 * }</pre>
 *
 * @author Elicit Software
 * @version 1.0
 * @see Survey
 * @see PanacheEntityBase
 */
@Entity
@Table(name = "reports", schema = "survey")
public class ReportDefinition extends PanacheEntityBase {

    /**
     * Unique identifier for the report definition.
     *
     * <p>Auto-generated primary key using a database sequence. This ID uniquely
     * identifies each report definition within the system.</p>
     *
     * <p><strong>Generation Strategy:</strong></p>
     * <ul>
     *   <li><strong>Sequence Name:</strong> {@code survey.reports_seq}</li>
     *   <li><strong>Allocation Size:</strong> 1 (no batch allocation)</li>
     *   <li><strong>Database Type:</strong> INTEGER</li>
     * </ul>
     */
    @Id
    @SequenceGenerator(name = "REPORT_ID_GENERATOR", schema = "survey", sequenceName = "reports_seq", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "REPORT_ID_GENERATOR")
    @Column(name = "id", unique = true, nullable = false)
    public Integer id;

    /**
     * The survey this report definition belongs to.
     *
     * <p>Establishes the relationship between the report and the survey whose
     * data it will display. This relationship ensures that reports are properly
     * scoped to their associated survey data.</p>
     *
     * <p><strong>JSON Serialization:</strong> This field is excluded from JSON
     * serialization using {@code @JsonbTransient} to prevent circular references
     * when serializing survey data that includes report definitions.</p>
     *
     * @see Survey
     */
    @JsonbTransient
    @ManyToOne()
    @JoinColumn(name = "survey_id", nullable = false)
    public Survey survey;

    /**
     * Display name for the report.
     *
     * <p>A human-readable name that identifies the report in user interfaces.
     * This name is typically displayed in report menus, dashboards, and
     * navigation elements.</p>
     *
     * <p><strong>Examples:</strong></p>
     * <ul>
     *   <li>"Response Summary"</li>
     *   <li>"Demographic Analysis"</li>
     *   <li>"Completion Rates"</li>
     *   <li>"Data Export"</li>
     * </ul>
     */
    @Column(name = "name")
    public String name;

    /**
     * Detailed description of the report's purpose and content.
     *
     * <p>Provides additional context about what the report contains and how
     * it should be used. This description may be displayed in tooltips,
     * help text, or detailed report listings.</p>
     *
     * <p><strong>Best Practices:</strong></p>
     * <ul>
     *   <li>Keep descriptions concise but informative</li>
     *   <li>Explain what data the report shows</li>
     *   <li>Mention any special features or limitations</li>
     * </ul>
     */
    @Column(name = "description")
    public String description;

    /**
     * URL path or endpoint for accessing the report.
     *
     * <p>Specifies where the report can be accessed within the application.
     * This can be a relative path within the Elicit system or an absolute
     * URL to an external reporting service.</p>
     *
     * <p><strong>URL Types:</strong></p>
     * <ul>
     *   <li><strong>Relative:</strong> {@code /reports/summary/123}</li>
     *   <li><strong>Absolute:</strong> {@code https://reports.example.com/survey/123}</li>
     *   <li><strong>API Endpoint:</strong> {@code /api/reports/export/csv}</li>
     * </ul>
     */
    @Column(name = "url")
    public String url;

    /**
     * Display order for sorting reports in user interfaces.
     *
     * <p>Determines the order in which reports appear in menus, dashboards,
     * and other listing interfaces. Lower numbers appear first, allowing
     * administrators to control the presentation order of reports.</p>
     *
     * <p><strong>Ordering Guidelines:</strong></p>
     * <ul>
     *   <li><strong>1-10:</strong> Primary/most important reports</li>
     *   <li><strong>11-50:</strong> Secondary reports</li>
     *   <li><strong>51+:</strong> Specialized or administrative reports</li>
     * </ul>
     */
    @Column(name = "display_order")
    public int displayOrder;

    /**
     * Default constructor for JPA.
     *
     * <p>Creates a new ReportDefinition instance. Required by JPA for entity instantiation.</p>
     */
    public ReportDefinition() {
        super();
    }
}
