package com.elicitsoftware.model;


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

    private static SimpleDateFormat sdf = new SimpleDateFormat("HH:MM:SS");

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "RESPONDENT_ID_GENERATOR")
    @SequenceGenerator(name = "RESPONDENT_ID_GENERATOR", schema = "survey", sequenceName = "respondents_seq", allocationSize = 1)
    @Column(name = "id", unique = true, nullable = false, precision = 20)
    public Integer id;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "created_dt")
    public Date createdDt = new Date();

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "first_access_dt")
    public Date firstAccessDt;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "finalized_dt")
    public Date finalizedDt;

    @Column(name = "active")
    public boolean active;

    // The number of times they logged in.
    @Column(name = "logins")
    public int logins;

    // uni-directional many-to-one association to ActionType
    @ManyToOne
    @JoinColumn(name = "survey_id", nullable = false)
    public Survey survey;

    @Column(name = "token")
    public String token;

    @Transient
    public static Respondent findBySurveyAndToken(Integer survey_id, String token) {
        return find("#Respondent.findBySurveyAndToken", Parameters.with("survey_id", survey_id).and("token", token)).firstResult();
    }

    @Transient
    public static Respondent findActiveByToken(String token) {
        return find("#Respondent.findActiveByToken", Parameters.with("token", token)).firstResult();
    }

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

