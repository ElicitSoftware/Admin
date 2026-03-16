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

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Exports respondent, answer, dependent, subject, message, and PSA rows as an importable SQL script.
 */
@ApplicationScoped
public class RespondentExportService {

    /**
     * Creates the respondent export service.
     */
    public RespondentExportService() {
        // Required explicit constructor for Javadoc.
    }

    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSSX");

    @Inject
    EntityManager em;

    /**
     * Generates an SQL export script for a single respondent.
     *
     * @param respondentId source respondent identifier
     * @return SQL script that can be executed on a matching target survey schema
     */
    @Transactional
    public String exportRespondentAsSql(Integer respondentId) {
        Object[] respondent = getRespondent(respondentId);
        if (respondent == null) {
            throw new IllegalArgumentException("Respondent not found: " + respondentId);
        }

        List<Object[]> answers = getAnswers(respondentId);
        List<Object[]> dependents = getDependents(respondentId);
        List<Object[]> subjects = getSubjects(respondentId);
        List<Object[]> messages = getMessages(respondentId);
        List<Object[]> respondentPsa = getRespondentPsa(respondentId);

        StringBuilder sql = new StringBuilder();
        sql.append("-- ============================================\n");
        sql.append("-- Export of Respondent ID: ").append(respondentId).append("\n");
        sql.append("-- Survey ID: ").append(respondent[1]).append("\n");
        sql.append("-- Token: ").append(respondent[2]).append("\n");
        sql.append("-- Answers: ").append(answers.size()).append("\n");
        sql.append("-- Dependents: ").append(dependents.size()).append("\n");
        sql.append("-- Subjects: ").append(subjects.size()).append("\n");
        sql.append("-- Messages: ").append(messages.size()).append("\n");
        sql.append("-- Respondent PSA: ").append(respondentPsa.size()).append("\n");
        sql.append("-- Generated: ").append(OffsetDateTime.now().format(TIMESTAMP_FORMAT)).append("\n");
        sql.append("-- ============================================\n\n");

        sql.append("BEGIN;\n\n");
        sql.append(generateRespondentSql(respondent));
        sql.append(generateAnswersSql(answers));
        sql.append(generateDependentsSql(dependents));
        sql.append(generateSubjectsAndMessagesSql(subjects, messages));
        sql.append(generateRespondentPsaSql(respondentPsa));

        sql.append("-- ============================================\n");
        sql.append("-- Verification\n");
        sql.append("-- ============================================\n");
        sql.append("SELECT 'Respondent ID:' AS info, currval('survey.respondents_seq') AS value;\n");
        sql.append("SELECT 'Answers imported:' AS info, COUNT(*) AS value FROM survey.answers ");
        sql.append("WHERE respondent_id = currval('survey.respondents_seq');\n");
        sql.append("SELECT 'Dependents imported:' AS info, COUNT(*) AS value FROM survey.dependents ");
        sql.append("WHERE respondent_id = currval('survey.respondents_seq');\n\n");
        sql.append("SELECT 'Subjects imported:' AS info, COUNT(*) AS value FROM survey.subjects ");
        sql.append("WHERE respondent_id = currval('survey.respondents_seq');\n");
        sql.append("SELECT 'Messages imported:' AS info, COUNT(*) AS value FROM survey.messages m ");
        sql.append("JOIN survey.subjects s ON s.id = m.subject_id ");
        sql.append("WHERE s.respondent_id = currval('survey.respondents_seq');\n");
        if (!respondentPsa.isEmpty()) {
            sql.append("SELECT 'Respondent PSA imported:' AS info, COUNT(*) AS value FROM survey.respondent_psa ");
            sql.append("WHERE respondent_id = currval('survey.respondents_seq');\n");
        }
        sql.append("\n");
        sql.append("COMMIT;\n");

        return sql.toString();
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

    private String generateRespondentSql(Object[] respondent) {
        StringBuilder sql = new StringBuilder();
        sql.append("-- ============================================\n");
        sql.append("-- Insert Respondent\n");
        sql.append("-- ============================================\n");
        sql.append("INSERT INTO survey.respondents (\n");
        sql.append("    id, survey_id, token, active, logins, created_dt, first_access_dt, finalized_dt\n");
        sql.append(") VALUES (\n");
        sql.append("    nextval('survey.respondents_seq'),\n");
        sql.append("    ").append(nullOrValue(respondent[1])).append(",  -- survey_id\n");
        sql.append("    ").append(escapeString((String) respondent[2])).append(",  -- token\n");
        sql.append("    true,  -- active (force active so imported respondent can log in)\n");
        sql.append("    ").append(nullOrValue(respondent[4])).append(",  -- logins\n");
        sql.append("    ").append(formatTimestamp(respondent[5])).append(",  -- created_dt\n");
        sql.append("    ").append(formatTimestamp(respondent[6])).append(",  -- first_access_dt\n");
        sql.append("    NULL   -- finalized_dt (allow manual finish to trigger ETL)\n");
        sql.append(");\n\n");
        return sql.toString();
    }

    private String generateAnswersSql(List<Object[]> answers) {
        if (answers.isEmpty()) {
            return "-- No answers to export\n\n";
        }

        StringBuilder sql = new StringBuilder();
        sql.append("-- ============================================\n");
        sql.append("-- Insert Answers (").append(answers.size()).append(" total)\n");
        sql.append("-- ============================================\n");

        for (Object[] answer : answers) {
            sql.append("INSERT INTO survey.answers (\n");
            sql.append("    id, survey_id, respondent_id, step, step_instance, section, section_instance,\n");
            sql.append("    question_display_order, question_instance, section_question_id, question_id,\n");
            sql.append("    display_key, display_text, text_value, deleted, created_dt, saved_dt\n");
            sql.append(") VALUES (\n");
            sql.append("    nextval('survey.answers_seq'),\n");
            sql.append("    ").append(nullOrValue(answer[1])).append(",  -- survey_id\n");
            sql.append("    currval('survey.respondents_seq'),  -- respondent_id\n");
            sql.append("    ").append(nullOrValue(answer[2])).append(",  -- step\n");
            sql.append("    ").append(nullOrValue(answer[3])).append(",  -- step_instance\n");
            sql.append("    ").append(nullOrValue(answer[4])).append(",  -- section\n");
            sql.append("    ").append(nullOrValue(answer[5])).append(",  -- section_instance\n");
            sql.append("    ").append(nullOrValue(answer[6])).append(",  -- question_display_order\n");
            sql.append("    ").append(nullOrValue(answer[7])).append(",  -- question_instance\n");
            sql.append("    ").append(nullOrValue(answer[8])).append(",  -- section_question_id\n");
            sql.append("    ").append(nullOrValue(answer[9])).append(",  -- question_id\n");
            sql.append("    ").append(escapeString((String) answer[10])).append(",  -- display_key\n");
            sql.append("    ").append(escapeString((String) answer[11])).append(",  -- display_text\n");
            sql.append("    ").append(escapeString((String) answer[12])).append(",  -- text_value\n");
            sql.append("    ").append(booleanOrNull(answer[13])).append(",  -- deleted\n");
            sql.append("    ").append(formatTimestamp(answer[14])).append(",  -- created_dt\n");
            sql.append("    ").append(formatTimestamp(answer[15])).append("   -- saved_dt\n");
            sql.append(");\n");
        }

        sql.append("\n");
        return sql.toString();
    }

    private String generateDependentsSql(List<Object[]> dependents) {
        if (dependents.isEmpty()) {
            return "-- No dependents to export\n\n";
        }

        StringBuilder sql = new StringBuilder();
        sql.append("-- ============================================\n");
        sql.append("-- Insert Dependents (").append(dependents.size()).append(" total)\n");
        sql.append("-- Uses subqueries to look up answer IDs by display_key\n");
        sql.append("-- ============================================\n");

        for (Object[] dependent : dependents) {
            String upstreamDisplayKey = (String) dependent[5];
            String downstreamDisplayKey = (String) dependent[6];

            sql.append("INSERT INTO survey.dependents (\n");
            sql.append("    id, respondent_id, upstream_id, downstream_id, relationship_id, deleted\n");
            sql.append(") VALUES (\n");
            sql.append("    nextval('survey.dependents_seq'),\n");
            sql.append("    currval('survey.respondents_seq'),\n");
            sql.append("    (SELECT id FROM survey.answers WHERE respondent_id = currval('survey.respondents_seq') ");
            sql.append("AND display_key = ").append(escapeString(upstreamDisplayKey)).append("),\n");
            sql.append("    (SELECT id FROM survey.answers WHERE respondent_id = currval('survey.respondents_seq') ");
            sql.append("AND display_key = ").append(escapeString(downstreamDisplayKey)).append("),\n");
            sql.append("    ").append(nullOrValue(dependent[3])).append(",  -- relationship_id\n");
            sql.append("    ").append(booleanOrNull(dependent[4])).append("   -- deleted\n");
            sql.append(");\n");
        }

        sql.append("\n");
        return sql.toString();
    }

    private String generateSubjectsAndMessagesSql(List<Object[]> subjects, List<Object[]> messages) {
        Map<Object, List<Object[]>> messagesBySubjectId = new LinkedHashMap<>();
        for (Object[] message : messages) {
            Object sourceSubjectId = message[1];
            messagesBySubjectId.computeIfAbsent(sourceSubjectId, key -> new ArrayList<>()).add(message);
        }

        StringBuilder sql = new StringBuilder();

        if (subjects.isEmpty()) {
            sql.append("-- No subjects to export\n\n");
            if (!messages.isEmpty()) {
                sql.append("-- Messages skipped because no subject rows were exported\n\n");
            }
            return sql.toString();
        }

        sql.append("-- ============================================\n");
        sql.append("-- Insert Subjects (").append(subjects.size()).append(" total)\n");
        sql.append("-- Insert related messages using currval('survey.subjects_seq')\n");
        sql.append("-- ============================================\n");

        for (Object[] subject : subjects) {
            Object sourceSubjectId = subject[0];
            List<Object[]> subjectMessages = messagesBySubjectId.getOrDefault(sourceSubjectId, List.of());

            sql.append("INSERT INTO survey.subjects (\n");
            sql.append("    id, xid, firstname, lastname, middlename, dob,\n");
            sql.append("    email, phone, department_id, survey_id, respondent_id, created_dt\n");
            sql.append(") VALUES (\n");
            sql.append("    nextval('survey.subjects_seq'),\n");
            sql.append("    ").append(escapeString((String) subject[1])).append(",\n");
            sql.append("    ").append(escapeString((String) subject[2])).append(",\n");
            sql.append("    ").append(escapeString((String) subject[3])).append(",\n");
            sql.append("    ").append(escapeString((String) subject[4])).append(",\n");
            sql.append("    ").append(formatDate(subject[5])).append(",\n");
            sql.append("    ").append(escapeString((String) subject[6])).append(",\n");
            sql.append("    ").append(escapeString((String) subject[7])).append(",\n");
            sql.append("    ").append(nullOrValue(subject[8])).append(",\n");
            sql.append("    ").append(nullOrValue(subject[9])).append(",\n");
            sql.append("    currval('survey.respondents_seq'),\n");
            sql.append("    ").append(formatTimestamp(subject[10])).append("\n");
            sql.append(");\n");

            for (Object[] message : subjectMessages) {
                sql.append("INSERT INTO survey.messages (\n");
                sql.append("    id, subject_id, message_type, mime_type, subjectline, body, created_dt, sent_dt\n");
                sql.append(") VALUES (\n");
                sql.append("    nextval('survey.messages_seq'),\n");
                sql.append("    currval('survey.subjects_seq'),\n");
                sql.append("    ").append(nullOrValue(message[2])).append(",\n");
                sql.append("    ").append(escapeString((String) message[3])).append(",\n");
                sql.append("    ").append(escapeString((String) message[4])).append(",\n");
                sql.append("    ").append(escapeString((String) message[5])).append(",\n");
                sql.append("    ").append(formatTimestamp(message[6])).append(",\n");
                sql.append("    ").append(formatTimestamp(message[7])).append("\n");
                sql.append(");\n");
            }
        }

        sql.append("\n");
        return sql.toString();
    }

    private String generateRespondentPsaSql(List<Object[]> respondentPsaRows) {
        if (respondentPsaRows.isEmpty()) {
            return "-- No respondent_psa rows to export\n\n";
        }

        StringBuilder sql = new StringBuilder();
        sql.append("-- ============================================\n");
        sql.append("-- Insert respondent_psa (").append(respondentPsaRows.size()).append(" total)\n");
        sql.append("-- ============================================\n");

        for (Object[] psa : respondentPsaRows) {
            sql.append("INSERT INTO survey.respondent_psa (\n");
            sql.append("    id, respondent_id, post_survey_action_id, tries,\n");
            sql.append("    status, error_msg, created_dt, uploaded_dt\n");
            sql.append(") VALUES (\n");
            sql.append("    nextval('survey.respondent_psa_seq'),\n");
            sql.append("    currval('survey.respondents_seq'),\n");
            sql.append("    ").append(nullOrValue(psa[2])).append(",\n");
            sql.append("    ").append(nullOrValue(psa[3])).append(",\n");
            sql.append("    ").append(escapeString((String) psa[4])).append(",\n");
            sql.append("    ").append(escapeString((String) psa[5])).append(",\n");
            sql.append("    ").append(formatTimestamp(psa[6])).append(",\n");
            sql.append("    ").append(formatTimestamp(psa[7])).append("\n");
            sql.append(");\n");
        }

        sql.append("\n");
        return sql.toString();
    }

    private String formatDate(Object value) {
        if (value == null) {
            return "NULL";
        }
        if (value instanceof java.sql.Date sqlDate) {
            return "'" + sqlDate + "'";
        }
        if (value instanceof Date date) {
            return "'" + new java.sql.Date(date.getTime()) + "'";
        }
        return escapeString(value.toString());
    }

    private String escapeString(String value) {
        if (value == null) {
            return "NULL";
        }
        return "'" + value.replace("'", "''") + "'";
    }

    private String nullOrValue(Object value) {
        return value == null ? "NULL" : value.toString();
    }

    private String booleanOrNull(Object value) {
        if (value == null) {
            return "NULL";
        }
        if (value instanceof Boolean bool) {
            return bool.toString();
        }
        String text = value.toString();
        if ("t".equalsIgnoreCase(text)) {
            return "true";
        }
        if ("f".equalsIgnoreCase(text)) {
            return "false";
        }
        return text;
    }

    private String formatTimestamp(Object value) {
        if (value == null) {
            return "NULL";
        }
        if (value instanceof OffsetDateTime offsetDateTime) {
            return "'" + offsetDateTime.format(TIMESTAMP_FORMAT) + "'";
        }
        if (value instanceof Date date) {
            return "'" + new java.sql.Timestamp(date.getTime()) + "'";
        }
        return "'" + value.toString() + "'";
    }

}
