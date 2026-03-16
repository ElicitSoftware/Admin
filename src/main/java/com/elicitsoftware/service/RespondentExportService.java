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

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Exports respondent, answer, dependent, subject, message, and PSA rows as an importable data file.
 * <p>
 * The export format is a custom text format designed for safe import via parameterized queries:
 * <pre>
 * # ELICIT_EXPORT_V1
 * # respondent_id: 123
 * # timezone: America/Detroit
 * # generated: 2026-03-13T20:50:38.106350589-04:00
 * respondents: survey_id|token|logins|created_dt|first_access_dt
 * answers: survey_id|step|step_instance|...|created_dt|saved_dt
 * dependents: upstream_display_key|downstream_display_key|relationship_id|deleted
 * subjects: subject_index|xid|firstname|lastname|...|created_dt
 * messages: subject_index|message_type|mime_type|...|created_dt|sent_dt
 * respondent_psa: post_survey_action_id|tries|status|error_msg|created_dt|uploaded_dt
 * </pre>
 * <p>
 * All timestamps are exported in ISO-8601 format with the database's local timezone offset
 * (e.g., {@code 2026-03-13T20:50:38.106-04:00}).
 * <p>
 * Field delimiter: | (pipe)<br>
 * Escape sequences: \| for literal pipe, \\ for literal backslash, \n for newline
 *
 * @see RespondentImportService
 */
@ApplicationScoped
@SuppressWarnings("java:S1118") // CDI managed bean
public class RespondentExportService {

    /**
     * Default constructor for CDI.
     */
    public RespondentExportService() {
        // CDI managed bean
    }

    private static final String FORMAT_VERSION = "ELICIT_EXPORT_V1";
    private static final String FIELD_DELIMITER = "|";

    @Inject
    EntityManager em;

    /**
     * Generates an export file for a single respondent in the custom Elicit format.
     * <p>
     * Queries the database timezone and formats all timestamps with the local timezone offset
     * for accurate import to databases configured with the same timezone.
     *
     * @param respondentId source respondent identifier
     * @return export data as a String that can be safely imported via {@link RespondentImportService}
     * @throws IllegalArgumentException if respondent is not found
     */
    @Transactional
    public String exportRespondent(Integer respondentId) {
        // Get database timezone for consistent timestamp formatting
        String dbTimezone = (String) em.createNativeQuery("SELECT current_setting('TIMEZONE')").getSingleResult();
        ZoneId zoneId = ZoneId.of(dbTimezone);
        
        Object[] respondent = getRespondent(respondentId);
        if (respondent == null) {
            throw new IllegalArgumentException("Respondent not found: " + respondentId);
        }

        List<Object[]> answers = getAnswers(respondentId);
        List<Object[]> dependents = getDependents(respondentId);
        List<Object[]> subjects = getSubjects(respondentId);
        List<Object[]> messages = getMessages(respondentId);
        List<Object[]> respondentPsa = getRespondentPsa(respondentId);

        // Build subject ID to index mapping for message references
        Map<Object, Integer> subjectIdToIndex = new LinkedHashMap<>();
        for (int i = 0; i < subjects.size(); i++) {
            subjectIdToIndex.put(subjects.get(i)[0], i);
        }

        StringBuilder out = new StringBuilder();
        
        // Header
        out.append("# ").append(FORMAT_VERSION).append("\n");
        out.append("# respondent_id: ").append(respondentId).append("\n");
        out.append("# survey_id: ").append(respondent[1]).append("\n");
        out.append("# token: ").append(respondent[2]).append("\n");
        out.append("# answers: ").append(answers.size()).append("\n");
        out.append("# dependents: ").append(dependents.size()).append("\n");
        out.append("# subjects: ").append(subjects.size()).append("\n");
        out.append("# messages: ").append(messages.size()).append("\n");
        out.append("# respondent_psa: ").append(respondentPsa.size()).append("\n");
        out.append("# timezone: ").append(dbTimezone).append("\n");
        out.append("# generated: ").append(OffsetDateTime.now(zoneId).toString()).append("\n");
        out.append("\n");

        // Respondent record
        // Fields: survey_id, token, logins, created_dt, first_access_dt
        out.append("respondents: ");
        out.append(escapeField(respondent[1]));  // survey_id
        out.append(FIELD_DELIMITER).append(escapeField(respondent[2]));  // token
        out.append(FIELD_DELIMITER).append(escapeField(respondent[4]));  // logins
        out.append(FIELD_DELIMITER).append(formatTimestamp(respondent[5], zoneId));  // created_dt
        out.append(FIELD_DELIMITER).append(formatTimestamp(respondent[6], zoneId));  // first_access_dt
        out.append("\n");

        // Answer records
        // Fields: survey_id, step, step_instance, section, section_instance, question_display_order,
        //         question_instance, section_question_id, question_id, display_key, display_text,
        //         text_value, deleted, created_dt, saved_dt
        for (Object[] answer : answers) {
            out.append("answers: ");
            out.append(escapeField(answer[1]));  // survey_id
            out.append(FIELD_DELIMITER).append(escapeField(answer[2]));  // step
            out.append(FIELD_DELIMITER).append(escapeField(answer[3]));  // step_instance
            out.append(FIELD_DELIMITER).append(escapeField(answer[4]));  // section
            out.append(FIELD_DELIMITER).append(escapeField(answer[5]));  // section_instance
            out.append(FIELD_DELIMITER).append(escapeField(answer[6]));  // question_display_order
            out.append(FIELD_DELIMITER).append(escapeField(answer[7]));  // question_instance
            out.append(FIELD_DELIMITER).append(escapeField(answer[8]));  // section_question_id
            out.append(FIELD_DELIMITER).append(escapeField(answer[9]));  // question_id
            out.append(FIELD_DELIMITER).append(escapeField(answer[10]));  // display_key
            out.append(FIELD_DELIMITER).append(escapeField(answer[11]));  // display_text
            out.append(FIELD_DELIMITER).append(escapeField(answer[12]));  // text_value
            out.append(FIELD_DELIMITER).append(escapeField(answer[13]));  // deleted
            out.append(FIELD_DELIMITER).append(formatTimestamp(answer[14], zoneId));  // created_dt
            out.append(FIELD_DELIMITER).append(formatTimestamp(answer[15], zoneId));  // saved_dt
            out.append("\n");
        }

        // Dependent records
        // Fields: upstream_display_key, downstream_display_key, relationship_id, deleted
        for (Object[] dependent : dependents) {
            out.append("dependents: ");
            out.append(escapeField(dependent[5]));  // upstream_display_key
            out.append(FIELD_DELIMITER).append(escapeField(dependent[6]));  // downstream_display_key
            out.append(FIELD_DELIMITER).append(escapeField(dependent[3]));  // relationship_id
            out.append(FIELD_DELIMITER).append(escapeField(dependent[4]));  // deleted
            out.append("\n");
        }

        // Subject records
        // Fields: subject_index, xid, firstname, lastname, middlename, dob, email, phone,
        //         department_id, survey_id, created_dt
        for (int i = 0; i < subjects.size(); i++) {
            Object[] subject = subjects.get(i);
            out.append("subjects: ");
            out.append(i);  // subject_index for message linking
            out.append(FIELD_DELIMITER).append(escapeField(subject[1]));  // xid
            out.append(FIELD_DELIMITER).append(escapeField(subject[2]));  // firstname
            out.append(FIELD_DELIMITER).append(escapeField(subject[3]));  // lastname
            out.append(FIELD_DELIMITER).append(escapeField(subject[4]));  // middlename
            out.append(FIELD_DELIMITER).append(formatDate(subject[5]));  // dob
            out.append(FIELD_DELIMITER).append(escapeField(subject[6]));  // email
            out.append(FIELD_DELIMITER).append(escapeField(subject[7]));  // phone
            out.append(FIELD_DELIMITER).append(escapeField(subject[8]));  // department_id
            out.append(FIELD_DELIMITER).append(escapeField(subject[9]));  // survey_id
            out.append(FIELD_DELIMITER).append(formatTimestamp(subject[10], zoneId));  // created_dt
            out.append("\n");
        }

        // Message records
        // Fields: subject_index, message_type, mime_type, subjectline, body, created_dt, sent_dt
        for (Object[] message : messages) {
            Integer subjectIndex = subjectIdToIndex.get(message[1]);
            if (subjectIndex == null) {
                continue;  // Skip orphaned messages
            }
            out.append("messages: ");
            out.append(subjectIndex);  // subject_index
            out.append(FIELD_DELIMITER).append(escapeField(message[2]));  // message_type
            out.append(FIELD_DELIMITER).append(escapeField(message[3]));  // mime_type
            out.append(FIELD_DELIMITER).append(escapeField(message[4]));  // subjectline
            out.append(FIELD_DELIMITER).append(escapeField(message[5]));  // body
            out.append(FIELD_DELIMITER).append(formatTimestamp(message[6], zoneId));  // created_dt
            out.append(FIELD_DELIMITER).append(formatTimestamp(message[7], zoneId));  // sent_dt
            out.append("\n");
        }

        // Respondent PSA records
        // Fields: post_survey_action_id, tries, status, error_msg, created_dt, uploaded_dt
        for (Object[] psa : respondentPsa) {
            out.append("respondent_psa: ");
            out.append(escapeField(psa[2]));  // post_survey_action_id
            out.append(FIELD_DELIMITER).append(escapeField(psa[3]));  // tries
            out.append(FIELD_DELIMITER).append(escapeField(psa[4]));  // status
            out.append(FIELD_DELIMITER).append(escapeField(psa[5]));  // error_msg
            out.append(FIELD_DELIMITER).append(formatTimestamp(psa[6], zoneId));  // created_dt
            out.append(FIELD_DELIMITER).append(formatTimestamp(psa[7], zoneId));  // uploaded_dt
            out.append("\n");
        }

        return out.toString();
    }

    /**
     * Escapes a field value for the export format.
     * Handles: null, pipe delimiter, backslash, newline
     */
    private String escapeField(Object value) {
        if (value == null) {
            return "";
        }
        String str = value.toString();
        // Escape backslashes first, then pipes, then newlines
        str = str.replace("\\", "\\\\");
        str = str.replace("|", "\\|");
        str = str.replace("\n", "\\n");
        str = str.replace("\r", "\\r");
        return str;
    }

    private Object[] getRespondent(Integer respondentId) {
        String querySql = """
                SELECT id, survey_id, token, active, logins,
                       created_dt, first_access_dt, finalized_dt
                FROM survey.respondents
                WHERE id = :respondentId
                """;
        Query query = em.createNativeQuery(querySql);
        query.setParameter("respondentId", respondentId);

        List<Object[]> results = toObjectArrayRows(query.getResultList(), "respondent");
        return results.isEmpty() ? null : results.get(0);
    }

    private List<Object[]> getAnswers(Integer respondentId) {
        String querySql = """
                SELECT id, survey_id, step, step_instance, section, section_instance,
                       question_display_order, question_instance, section_question_id, question_id,
                       display_key, display_text, text_value, deleted, created_dt, saved_dt
                FROM survey.answers
                WHERE respondent_id = :respondentId
                ORDER BY id
                """;
        Query query = em.createNativeQuery(querySql);
        query.setParameter("respondentId", respondentId);

        List<Object[]> results = toObjectArrayRows(query.getResultList(), "answers");
        return results;
    }

    private List<Object[]> getDependents(Integer respondentId) {
        String querySql = """
                SELECT d.id, d.upstream_id, d.downstream_id, d.relationship_id, d.deleted,
                       ua.display_key as upstream_display_key,
                       da.display_key as downstream_display_key
                FROM survey.dependents d
                JOIN survey.answers ua ON d.upstream_id = ua.id
                JOIN survey.answers da ON d.downstream_id = da.id
                WHERE d.respondent_id = :respondentId
                ORDER BY d.id
                """;
        Query query = em.createNativeQuery(querySql);
        query.setParameter("respondentId", respondentId);

        List<Object[]> results = toObjectArrayRows(query.getResultList(), "dependents");
        return results;
    }

    private List<Object[]> getSubjects(Integer respondentId) {
        String querySql = """
                SELECT id, xid, firstname, lastname, middlename, dob,
                       email, phone, department_id, survey_id, created_dt
                FROM survey.subjects
                WHERE respondent_id = :respondentId
                ORDER BY id
                """;
        Query query = em.createNativeQuery(querySql);
        query.setParameter("respondentId", respondentId);

        List<Object[]> results = toObjectArrayRows(query.getResultList(), "subjects");
        return results;
    }

    private List<Object[]> getMessages(Integer respondentId) {
        String querySql = """
                SELECT m.id, m.subject_id, m.message_type, m.mime_type,
                       m.subjectline, m.body, m.created_dt, m.sent_dt
                FROM survey.messages m
                JOIN survey.subjects s ON s.id = m.subject_id
                WHERE s.respondent_id = :respondentId
                ORDER BY m.id
                """;
        Query query = em.createNativeQuery(querySql);
        query.setParameter("respondentId", respondentId);

        List<Object[]> results = toObjectArrayRows(query.getResultList(), "messages");
        return results;
    }

    private List<Object[]> getRespondentPsa(Integer respondentId) {
        String querySql = """
                SELECT id, respondent_id, post_survey_action_id, tries,
                       status, error_msg, created_dt, uploaded_dt
                FROM survey.respondent_psa
                WHERE respondent_id = :respondentId
                ORDER BY id
                """;
        Query query = em.createNativeQuery(querySql);
        query.setParameter("respondentId", respondentId);

        List<Object[]> rows = toObjectArrayRows(query.getResultList(), "respondent_psa");
        return rows;
    }

    private List<Object[]> toObjectArrayRows(List<?> rawRows, String queryName) {
        List<Object[]> rows = new ArrayList<>(rawRows.size());
        for (Object row : rawRows) {
            if (!(row instanceof Object[] columns)) {
                throw new IllegalStateException("Unexpected row type for " + queryName + ": " +
                        (row == null ? "null" : row.getClass().getName()));
            }
            rows.add(columns);
        }
        return rows;
    }

    private String formatDate(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof java.sql.Date sqlDate) {
            return sqlDate.toString();
        }
        if (value instanceof Date date) {
            return new java.sql.Date(date.getTime()).toString();
        }
        return escapeField(value);
    }

    private String formatTimestamp(Object value, ZoneId zoneId) {
        if (value == null) {
            return OffsetDateTime.now(zoneId).toString();
        }
        if (value instanceof OffsetDateTime offsetDateTime) {
            return offsetDateTime.atZoneSameInstant(zoneId).toOffsetDateTime().toString();
        }
        if (value instanceof Instant instant) {
            return instant.atZone(zoneId).toOffsetDateTime().toString();
        }
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toInstant().atZone(zoneId).toOffsetDateTime().toString();
        }
        throw new IllegalArgumentException("Unexpected timestamp type: " + value.getClass().getName());
    }

}
