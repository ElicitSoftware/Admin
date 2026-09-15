package com.elicitsoftware.service;

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

import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.transaction.Transactional;

import java.util.UUID;

/**
 * Writes one audit row per survey definition import/update attempt to {@code survey.survey_log}.
 * <p>
 * Deliberately a separate CDI bean (not a private method on the calling service) because
 * {@code REQUIRES_NEW} only takes effect through the CDI proxy, which self-invocation within the
 * same class bypasses.
 * <p>
 * {@link #logSuccess} joins the caller's own transaction: the survey row it references and the
 * log row commit together, atomically, so there is no visibility problem. {@link #logFailure}
 * runs in its own {@link Transactional.TxType#REQUIRES_NEW} transaction so the log entry survives
 * even though the caller's transaction is about to roll back — a rejected duplicate-key import or
 * a mismatched-key update must stay on the record, not just successes. That independence is also
 * exactly why {@code surveyId} passed to {@code logFailure} must never reference a row created
 * within the very same (rolling-back) attempt: a separate, already-running transaction can never
 * see an uncommitted row from another transaction, so the FK to {@code survey.surveys} would
 * violate immediately. It is only safe when the row is known to have existed before this attempt
 * began (e.g. Update's pre-existing target survey) — never for Import, which always creates a
 * brand-new survey within the same transaction that is now failing.
 */
@ApplicationScoped
public class SurveyLogService {

    /**
     * Default constructor for CDI.
     */
    public SurveyLogService() {
        // CDI managed bean
    }

    @Inject
    EntityManager em;

    @Inject
    SecurityIdentity identity;

    /**
     * Records a successful import or update attempt, in the caller's own transaction.
     *
     * @param surveyId destination survey id
     * @param surveyKey the file's declared survey key, or {@code null} if absent
     * @param action {@code "IMPORT"} or {@code "UPDATE"}
     * @param fileName the uploaded file's name, or {@code null} if not available
     * @param summary a short human-readable summary (e.g. per-table counts)
     */
    @Transactional
    public void logSuccess(Long surveyId, UUID surveyKey, String action, String fileName, String summary) {
        insert(surveyId, surveyKey, action, true, fileName, summary, null);
    }

    /**
     * Records a failed import or update attempt, in its own transaction so the entry survives the
     * caller's rollback.
     *
     * @param surveyId destination survey id, or {@code null} if the attempt never resolved one,
     *     or if the row it would reference was itself created (and is now being rolled back)
     *     within this same failed attempt
     * @param surveyKey the file's declared survey key, or {@code null} if absent
     * @param action {@code "IMPORT"} or {@code "UPDATE"}
     * @param fileName the uploaded file's name, or {@code null} if not available
     * @param errorMessage the failure reason
     */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void logFailure(Long surveyId, UUID surveyKey, String action, String fileName, String errorMessage) {
        insert(surveyId, surveyKey, action, false, fileName, null, errorMessage);
    }

    private void insert(Long surveyId, UUID surveyKey, String action, boolean success,
            String fileName, String summary, String errorMessage) {
        Query seqQuery = em.createNativeQuery("SELECT nextval('survey.survey_log_seq')");
        Long newId = ((Number) seqQuery.getSingleResult()).longValue();

        Query query = em.createNativeQuery("""
                INSERT INTO survey.survey_log
                    (id, survey_id, survey_key, action, outcome, performed_by, file_name, summary, error_message)
                VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9)
                """);
        query.setParameter(1, newId);
        query.setParameter(2, surveyId);
        query.setParameter(3, surveyKey);
        query.setParameter(4, action);
        query.setParameter(5, success ? "SUCCESS" : "FAILURE");
        query.setParameter(6, identity.getPrincipal().getName());
        query.setParameter(7, fileName);
        query.setParameter(8, summary);
        query.setParameter(9, errorMessage);
        query.executeUpdate();
    }
}
