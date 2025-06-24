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
 * The PostSurveyAction class represents an entity in the "post_survey_actions" table
 * within the "survey" schema. This entity defines actions to be executed after a survey
 * is completed.
 * <p>
 * Attributes:
 * - id: The unique identifier for the post-survey action.
 * - survey: The associated survey to which this action belongs, represented as a
 * many-to-one relationship with the Survey entity.
 * - name: The name of the post-survey action.
 * - description: A textual description of the action.
 * - url: The URL associated with the action, which may point to further resources
 * or actions.
 * - executionOrder: The order in which this action should be executed, relative to
 * other actions associated with the same survey.
 */
/**
 * PostSurveyAction represents actions to be executed after a survey is completed.
 * <p>
 * This entity defines post-completion actions that can be automatically triggered
 * when a survey respondent finishes their survey. Actions can include redirects
 * to external URLs, triggering notifications, or executing custom business logic.
 * <p>
 * Actions are associated with specific surveys and can be ordered for sequential
 * execution using the executionOrder field.
 *
 * @author Elicit Software
 * @since 1.0.0
 */
@Entity
@Table(name = "post_survey_actions", schema = "survey")
public class PostSurveyAction extends PanacheEntityBase {

    /**
     * Unique identifier for the post-survey action.
     * <p>
     * Auto-generated primary key that uniquely identifies each action
     * within the system. Used for database relationships and references.
     */
    @Id
    @SequenceGenerator(name = "ACTION_ID_GENERATOR", schema = "survey", sequenceName = "post_survey_actions_seq", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ACTION_ID_GENERATOR")
    @Column(name = "id", unique = true, nullable = false)
    public Integer id;

    /**
     * The survey associated with this post-completion action.
     * <p>
     * Many-to-one relationship linking this action to a specific survey.
     * When the survey is completed, all associated actions will be evaluated
     * for execution based on their order and conditions.
     */
    @JsonbTransient
    @ManyToOne()
    @JoinColumn(name = "survey_id", nullable = false)
    public Survey survey;

    /**
     * Human-readable name for the action.
     * <p>
     * A descriptive name that identifies the purpose of this action.
     * Used in administrative interfaces and logging for easy identification.
     */
    @Column(name = "name")
    public String name;

    /**
     * Detailed description of what the action does.
     * <p>
     * Provides additional context about the action's purpose, behavior,
     * and any special considerations for its execution. Used for documentation
     * and administrative purposes.
     */
    @Column(name = "description")
    public String description;

    /**
     * URL to redirect or call as part of the action.
     * <p>
     * The target URL for redirect actions or webhook calls. Can be an absolute
     * URL for external services or a relative path for internal redirects.
     * May include placeholders for dynamic parameter substitution.
     */
    @Column(name = "url")
    public String url;

    /**
     * Order in which this action should be executed relative to other actions.
     * <p>
     * Defines the execution sequence when multiple actions are associated
     * with the same survey. Lower numbers execute first. Actions with the
     * same order may execute in any sequence.
     */
    @Column(name = "execution_order")
    public Integer executionOrder;

    /**
     * Default constructor for JPA entity instantiation.
     * <p>
     * Creates a new PostSurveyAction instance with default values. This constructor
     * is required by JPA for entity instantiation and is used by the
     * persistence framework during query execution and relationship loading.
     */
    public PostSurveyAction() {
        // Default constructor for JPA
    }

}
