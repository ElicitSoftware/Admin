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
import io.quarkus.panache.common.Parameters;
import jakarta.persistence.*;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 * JPA entity representing survey respondents and their participation tracking in the Elicit system.
 *
 * <p>This entity tracks the participation lifecycle of individuals responding to surveys,
 * including access tracking, completion status, and session management. It serves as the
 * technical counterpart to the {@link Subject} entity, focusing on response tracking
 * rather than personal information.</p>
 *
 * <p><strong>Key Tracking Features:</strong></p>
 * <ul>
 *   <li><strong>Session Management:</strong> Token-based access control and session tracking</li>
 *   <li><strong>Participation Timeline:</strong> Creation, first access, and completion timestamps</li>
 *   <li><strong>Access Analytics:</strong> Login count and elapsed time calculations</li>
 *   <li><strong>Status Management:</strong> Active/inactive status for participation control</li>
 * </ul>
 *
 * <p><strong>Participation Lifecycle:</strong></p>
 * <ol>
 *   <li><strong>Creation:</strong> Respondent record created when survey is assigned</li>
 *   <li><strong>First Access:</strong> Timestamp recorded when respondent first accesses survey</li>
 *   <li><strong>Active Participation:</strong> Multiple logins and responses tracked</li>
 *   <li><strong>Completion:</strong> Finalization timestamp recorded when survey is completed</li>
 * </ol>
 *
 * <p><strong>Token-Based Access:</strong></p>
 * <ul>
 *   <li><strong>Unique Tokens:</strong> Each respondent has a unique access token</li>
 *   <li><strong>Security:</strong> Tokens provide secure, anonymous access to surveys</li>
 *   <li><strong>Session Tracking:</strong> Tokens enable tracking without requiring registration</li>
 *   <li><strong>Active Status:</strong> Only active respondents can access surveys</li>
 * </ul>
 *
 * <p><strong>Named Queries:</strong></p>
 * <ul>
 *   <li><strong>findBySurveyAndToken:</strong> Find respondent by survey ID and access token</li>
 *   <li><strong>findActiveByToken:</strong> Find active respondent by token (ordered by survey ID)</li>
 * </ul>
 *
 * <p><strong>Analytics and Reporting:</strong></p>
 * <ul>
 *   <li><strong>Completion Rates:</strong> Track survey completion statistics</li>
 *   <li><strong>Engagement Metrics:</strong> Monitor login frequency and session duration</li>
 *   <li><strong>Access Patterns:</strong> Analyze how respondents interact with surveys</li>
 *   <li><strong>Time Analysis:</strong> Calculate time spent on survey completion</li>
 * </ul>
 *
 * <p><strong>Usage Examples:</strong></p>
 * <pre>{@code
 * // Find respondent by survey and token
 * Respondent respondent = Respondent.findBySurveyAndToken(123, "abc123token");
 *
 * // Check if respondent is active
 * if (respondent.active) {
 *     // Allow survey access
 * }
 *
 * // Calculate completion time
 * String elapsedTime = respondent.getElapsedTime();
 *
 * // Track login
 * respondent.logins++;
 * respondent.persist();
 * }</pre>
 *
 * @author Elicit Software
 * @version 1.0
 * @since 1.0
 * @see Subject
 * @see Survey
 * @see PanacheEntityBase
 */
@Entity
@Table(name = "respondents", schema = "survey")
@NamedQueries({
        @NamedQuery(name = "Respondent.findBySurveyAndToken", query = "SELECT R FROM Respondent R where R.survey.id = :survey_id and R.token = :token"),
        @NamedQuery(name = "Respondent.findActiveByToken", query = "SELECT R FROM Respondent R where R.token = :token and R.active = true order by R.survey.id")
})
public class Respondent extends PanacheEntityBase {

    /** Date formatter for elapsed time display. */
    private static SimpleDateFormat sdf = new SimpleDateFormat("HH:MM:SS");

    /**
     * Unique identifier for the respondent.
     *
     * <p>Auto-generated primary key using a database sequence. This ID uniquely
     * identifies each respondent within the system and is used for tracking
     * survey participation and response data.</p>
     *
     * <p><strong>Generation Strategy:</strong></p>
     * <ul>
     *   <li><strong>Sequence Name:</strong> {@code survey.respondents_seq}</li>
     *   <li><strong>Allocation Size:</strong> 1 (no batch allocation)</li>
     *   <li><strong>Database Type:</strong> INTEGER</li>
     * </ul>
     */
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "RESPONDENT_ID_GENERATOR")
    @SequenceGenerator(name = "RESPONDENT_ID_GENERATOR", schema = "survey", sequenceName = "respondents_seq", allocationSize = 1)
    @Column(name = "id", unique = true, nullable = false, precision = 20)
    public Integer id;

    /**
     * Timestamp when the respondent record was created.
     *
     * <p>Automatically set to the current timestamp when a new respondent instance
     * is created. This marks the beginning of the respondent's participation
     * lifecycle and is used for tracking survey assignment timing.</p>
     *
     * <p><strong>Usage:</strong></p>
     * <ul>
     *   <li><strong>Audit Trail:</strong> Track when survey invitations were created</li>
     *   <li><strong>Analytics:</strong> Measure time from invitation to participation</li>
     *   <li><strong>Cleanup:</strong> Identify old, inactive respondent records</li>
     * </ul>
     */
    @Column(name = "created_dt")
    public Date createdDt = new Date();

    /**
     * Timestamp of the respondent's first access to the survey.
     *
     * <p>Records when the respondent first accessed their survey using their
     * unique token. This marks the transition from "invited" to "active"
     * participation and is used for engagement analytics.</p>
     *
     * <p><strong>States:</strong></p>
     * <ul>
     *   <li><strong>null:</strong> Respondent has not yet accessed the survey</li>
     *   <li><strong>timestamp:</strong> Respondent first accessed survey at this time</li>
     * </ul>
     */
    @Column(name = "first_access_dt")
    public Date firstAccessDt;

    /**
     * Timestamp when the respondent completed/finalized their survey.
     *
     * <p>Records when the respondent submitted their final survey response.
     * Combined with {@link #firstAccessDt}, this enables calculation of
     * survey completion time and participation analytics.</p>
     *
     * <p><strong>States:</strong></p>
     * <ul>
     *   <li><strong>null:</strong> Survey is still in progress or abandoned</li>
     *   <li><strong>timestamp:</strong> Survey was completed at this time</li>
     * </ul>
     */
    @Column(name = "finalized_dt")
    public Date finalizedDt;

    /**
     * Whether the respondent is currently active and can access the survey.
     *
     * <p>Controls survey access permissions. Only active respondents can
     * access their assigned surveys. This flag can be used to temporarily
     * disable access or permanently exclude respondents from participation.</p>
     *
     * <p><strong>Use Cases:</strong></p>
     * <ul>
     *   <li><strong>Access Control:</strong> Disable access for specific respondents</li>
     *   <li><strong>Data Quality:</strong> Exclude test or invalid responses</li>
     *   <li><strong>Survey Management:</strong> Control survey participation timing</li>
     * </ul>
     */
    @Column(name = "active")
    public boolean active;

    /**
     * Number of times the respondent has logged into the survey.
     *
     * <p>Tracks respondent engagement by counting survey access sessions.
     * This metric helps understand participation patterns and survey
     * completion behavior.</p>
     *
     * <p><strong>Analytics Uses:</strong></p>
     * <ul>
     *   <li><strong>Engagement:</strong> Measure respondent interaction levels</li>
     *   <li><strong>Completion Patterns:</strong> Identify single vs. multi-session completions</li>
     *   <li><strong>User Experience:</strong> Detect potential usability issues</li>
     * </ul>
     */
    @Column(name = "logins")
    public int logins;

    /**
     * The survey this respondent is assigned to participate in.
     *
     * <p>Establishes the relationship between the respondent and their assigned
     * survey. This relationship ensures that respondents can only access their
     * designated survey and enables proper data collection scoping.</p>
     *
     * <p><strong>Relationship Properties:</strong></p>
     * <ul>
     *   <li><strong>Cardinality:</strong> Many respondents to one survey</li>
     *   <li><strong>Constraint:</strong> Non-nullable (every respondent must have a survey)</li>
     *   <li><strong>Access Control:</strong> Used to validate survey access permissions</li>
     * </ul>
     *
     * @see Survey
     */
    @ManyToOne
    @JoinColumn(name = "survey_id", nullable = false)
    public Survey survey;

    /**
     * Unique access token for the respondent.
     *
     * <p>Provides secure, anonymous access to the assigned survey without
     * requiring user registration or authentication. The token serves as
     * both identifier and access credential for survey participation.</p>
     *
     * <p><strong>Security Features:</strong></p>
     * <ul>
     *   <li><strong>Uniqueness:</strong> Each token is globally unique across all surveys</li>
     *   <li><strong>Anonymity:</strong> Enables participation without personal credentials</li>
     *   <li><strong>Access Control:</strong> Required for all survey interactions</li>
     *   <li><strong>Session Management:</strong> Links survey sessions to specific respondents</li>
     * </ul>
     */
    @Column(name = "token")
    public String token;

    /**
     * Default constructor for JPA.
     *
     * <p>Creates a new Respondent instance with default values. The {@link #createdDt}
     * field is automatically set to the current timestamp.</p>
     */
    public Respondent() {
        super();
    }

    /**
     * Finds a respondent by survey ID and access token.
     *
     * <p>Uses the named query "Respondent.findBySurveyAndToken" to locate a specific
     * respondent within a survey using their unique access token. This method
     * is commonly used for survey access validation and session management.</p>
     *
     * <p><strong>Query Parameters:</strong></p>
     * <ul>
     *   <li><strong>survey_id:</strong> The ID of the survey to search within</li>
     *   <li><strong>token:</strong> The respondent's unique access token</li>
     * </ul>
     *
     * @param survey_id the survey ID to search within
     * @param token the respondent's access token
     * @return the matching Respondent, or null if not found
     *
     * @see NamedQuery
     */
    @Transient
    public static Respondent findBySurveyAndToken(Integer survey_id, String token) {
        return find("#Respondent.findBySurveyAndToken", Parameters.with("survey_id", survey_id).and("token", token)).firstResult();
    }

    /**
     * Finds an active respondent by access token across all surveys.
     *
     * <p>Uses the named query "Respondent.findActiveByToken" to locate an active
     * respondent by their token, regardless of which survey they're assigned to.
     * Results are ordered by survey ID to ensure consistent behavior when a
     * token might exist across multiple surveys.</p>
     *
     * <p><strong>Query Criteria:</strong></p>
     * <ul>
     *   <li><strong>Token Match:</strong> Exact match on the provided token</li>
     *   <li><strong>Active Status:</strong> Only returns respondents where active = true</li>
     *   <li><strong>Ordering:</strong> Results ordered by survey ID for consistency</li>
     * </ul>
     *
     * @param token the respondent's access token
     * @return the first matching active Respondent, or null if not found
     *
     * @see NamedQuery
     */
    @Transient
    public static Respondent findActiveByToken(String token) {
        return find("#Respondent.findActiveByToken", Parameters.with("token", token)).firstResult();
    }

    /**
     * Calculates and formats the elapsed time between first access and completion.
     *
     * <p>Computes the duration between when the respondent first accessed the
     * survey ({@link #firstAccessDt}) and when they completed it ({@link #finalizedDt}).
     * The result is formatted as "HH:MM:SS" for easy reading and display.</p>
     *
     * <p><strong>Calculation Logic:</strong></p>
     * <ol>
     *   <li>Checks that both firstAccessDt and finalizedDt are not null</li>
     *   <li>Calculates the difference in milliseconds</li>
     *   <li>Converts to hours, minutes, and seconds</li>
     *   <li>Formats as zero-padded "HH:MM:SS" string</li>
     * </ol>
     *
     * <p><strong>Return Values:</strong></p>
     * <ul>
     *   <li><strong>"HH:MM:SS":</strong> Formatted elapsed time if both dates are available</li>
     *   <li><strong>"Not calculated":</strong> If either date is missing</li>
     * </ul>
     *
     * <p><strong>Usage Examples:</strong></p>
     * <ul>
     *   <li><strong>Analytics:</strong> Survey completion time analysis</li>
     *   <li><strong>Reporting:</strong> Participant engagement metrics</li>
     *   <li><strong>Quality Control:</strong> Identify unusually quick/slow completions</li>
     * </ul>
     *
     * @return formatted elapsed time string or "Not calculated" if dates are missing
     */
    @Transient
    public String getElapsedTime() {
        if (firstAccessDt != null && finalizedDt != null) {
            long elapsedMiliseconds = finalizedDt.getTime() - firstAccessDt.getTime();
            return String.format("%02d:%02d:%02d",
                    TimeUnit.MILLISECONDS.toHours(elapsedMiliseconds),
                    TimeUnit.MILLISECONDS.toMinutes(elapsedMiliseconds)
                            - TimeUnit.HOURS.toMinutes(TimeUnit.MILLISECONDS
                            .toHours(elapsedMiliseconds)),
                    TimeUnit.MILLISECONDS.toSeconds(elapsedMiliseconds)
                            - TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS
                            .toMinutes(elapsedMiliseconds)));

        }
        return "Not calculated";
    }
}

