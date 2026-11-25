package com.elicitsoftware.model;

/*-
 * ***LICENSE_START***
 * Elicit Admin
 * %%
 * Copyright (C) 2025 - 2026 Elicit Software LLC
 * %%
 * PolyForm Noncommercial License 1.0.0
 * <https://polyformproject.org/licenses/noncommercial/1.0.0>
 * ***LICENSE_END***
 */

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.json.bind.annotation.JsonbTransient;
import jakarta.persistence.*;

/**
 * One post-survey action of a survey, a row of {@code survey.post_survey_actions}: an address the
 * Survey application calls after a respondent finishes the survey, in {@code executionOrder} among
 * the survey's actions. Admin imports and updates these with the survey definition and probes their
 * addresses in its connection checks; it does not call them.
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

    /**
     * Compares this post-survey action to another based on primary key equality.
     * <p>
     * {@code id} is a boxed, nullable {@code Integer} (unset before the entity is
     * persisted), so comparison and hashing use {@link java.util.Objects} null-safe
     * helpers rather than primitive equality. Consistent {@code equals}/{@code hashCode}
     * matters here because {@code Survey.postSurveyActions} is a JPA-managed
     * {@code Set<PostSurveyAction>}.
     *
     * @param o the object to compare with
     * @return {@code true} if the other object is a {@code PostSurveyAction} with the same id
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PostSurveyAction other)) {
            return false;
        }
        return java.util.Objects.equals(id, other.id);
    }

    /**
     * Returns a hash code derived from the primary key, consistent with {@link #equals(Object)}.
     *
     * @return the hash code for this post-survey action
     */
    @Override
    public int hashCode() {
        return java.util.Objects.hashCode(id);
    }
}
