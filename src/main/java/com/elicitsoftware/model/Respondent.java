package com.elicitsoftware.model;


import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import io.quarkus.panache.common.Parameters;
import jakarta.persistence.*;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 * The persistent class for the RESPONDENT database table.
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

