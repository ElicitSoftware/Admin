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

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.TestTransaction;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * US2: Verifies that exporting a respondent and re-importing it preserves all
 * answer fields, counts, and null values correctly.
 *
 * Each test runs in a @TestTransaction that rolls back after completion so no
 * test-data leaks between test methods.
 */
@QuarkusTest
@QuarkusTestResource(PostgreSQLTestResource.class)
class RespondentRoundTripTest {

    @Inject
    RespondentExportService exportService;

    @Inject
    RespondentImportService importService;

    @Inject
    EntityManager em;

    // -----------------------------------------------------------------------
    // Fixture helper
    // -----------------------------------------------------------------------

    /**
     * Inserts a minimal survey structure + one respondent + three answers.
     * Answer 1: text_value = null
     * Answer 2: deleted = true, text_value = "normal"
     * Answer 3: text_value contains a pipe character "val|ue"
     *
     * @return the respondent id
     */
    private long seedFixture() {
        // Survey
        Number surveyId = (Number) em.createNativeQuery(
                "SELECT nextval('survey.surveys_seq')").getSingleResult();
        em.createNativeQuery(
                "INSERT INTO survey.surveys (id, name, display_order, title, description, initial_display_key, post_survey_url) " +
                "VALUES (?1, ?2, 1, 'RT Respondent Survey', null, 'S1', null)")
                .setParameter(1, surveyId)
                .setParameter(2, "RT Respondent Survey " + surveyId)
                .executeUpdate();

        // Step
        Number stepId = (Number) em.createNativeQuery(
                "SELECT nextval('survey.steps_seq')").getSingleResult();
        em.createNativeQuery(
                "INSERT INTO survey.steps (id, survey_id, display_order, name, dimension_name, description) " +
                "VALUES (?1, ?2, 1, 'Step 1', null, null)")
                .setParameter(1, stepId)
                .setParameter(2, surveyId)
                .executeUpdate();

        // Section
        Number sectionId = (Number) em.createNativeQuery(
                "SELECT nextval('survey.sections_seq')").getSingleResult();
        em.createNativeQuery(
                "INSERT INTO survey.sections (id, survey_id, display_order, name, dimension_name, description) " +
                "VALUES (?1, ?2, 1, 'Section 1', null, null)")
                .setParameter(1, sectionId)
                .setParameter(2, surveyId)
                .executeUpdate();

        // Question 1
        Number q1Id = (Number) em.createNativeQuery(
                "SELECT nextval('survey.questions_seq')").getSingleResult();
        em.createNativeQuery(
                "INSERT INTO survey.questions (id, survey_id, type_id, text, short_text, tool_tip, required, " +
                "min_value, max_value, validation_text, select_group_id, mask, placeholder, default_value, variant) " +
                "VALUES (?1, ?2, 1, 'Q1', null, null, false, null, null, null, null, null, null, null, null)")
                .setParameter(1, q1Id)
                .setParameter(2, surveyId)
                .executeUpdate();

        // Question 2
        Number q2Id = (Number) em.createNativeQuery(
                "SELECT nextval('survey.questions_seq')").getSingleResult();
        em.createNativeQuery(
                "INSERT INTO survey.questions (id, survey_id, type_id, text, short_text, tool_tip, required, " +
                "min_value, max_value, validation_text, select_group_id, mask, placeholder, default_value, variant) " +
                "VALUES (?1, ?2, 1, 'Q2', null, null, false, null, null, null, null, null, null, null, null)")
                .setParameter(1, q2Id)
                .setParameter(2, surveyId)
                .executeUpdate();

        // Question 3
        Number q3Id = (Number) em.createNativeQuery(
                "SELECT nextval('survey.questions_seq')").getSingleResult();
        em.createNativeQuery(
                "INSERT INTO survey.questions (id, survey_id, type_id, text, short_text, tool_tip, required, " +
                "min_value, max_value, validation_text, select_group_id, mask, placeholder, default_value, variant) " +
                "VALUES (?1, ?2, 1, 'Q3', null, null, false, null, null, null, null, null, null, null, null)")
                .setParameter(1, q3Id)
                .setParameter(2, surveyId)
                .executeUpdate();

        // Steps-Sections join
        Number ssId = (Number) em.createNativeQuery(
                "SELECT nextval('survey.steps_sections_seq')").getSingleResult();
        em.createNativeQuery(
                "INSERT INTO survey.steps_sections (id, survey_id, step_id, step_display_order, section_id, section_display_order, display_key) " +
                "VALUES (?1, ?2, ?3, 1, ?4, 1, 'SS1')")
                .setParameter(1, ssId)
                .setParameter(2, surveyId)
                .setParameter(3, stepId)
                .setParameter(4, sectionId)
                .executeUpdate();

        // Sections-Questions joins
        Number sq1Id = (Number) em.createNativeQuery(
                "SELECT nextval('survey.sections_questions_seq')").getSingleResult();
        em.createNativeQuery(
                "INSERT INTO survey.sections_questions (id, survey_id, question_id, section_id, display_order) " +
                "VALUES (?1, ?2, ?3, ?4, 1)")
                .setParameter(1, sq1Id)
                .setParameter(2, surveyId)
                .setParameter(3, q1Id)
                .setParameter(4, sectionId)
                .executeUpdate();

        Number sq2Id = (Number) em.createNativeQuery(
                "SELECT nextval('survey.sections_questions_seq')").getSingleResult();
        em.createNativeQuery(
                "INSERT INTO survey.sections_questions (id, survey_id, question_id, section_id, display_order) " +
                "VALUES (?1, ?2, ?3, ?4, 2)")
                .setParameter(1, sq2Id)
                .setParameter(2, surveyId)
                .setParameter(3, q2Id)
                .setParameter(4, sectionId)
                .executeUpdate();

        Number sq3Id = (Number) em.createNativeQuery(
                "SELECT nextval('survey.sections_questions_seq')").getSingleResult();
        em.createNativeQuery(
                "INSERT INTO survey.sections_questions (id, survey_id, question_id, section_id, display_order) " +
                "VALUES (?1, ?2, ?3, ?4, 3)")
                .setParameter(1, sq3Id)
                .setParameter(2, surveyId)
                .setParameter(3, q3Id)
                .setParameter(4, sectionId)
                .executeUpdate();

        // Respondent
        Number respondentId = (Number) em.createNativeQuery(
                "SELECT nextval('survey.respondents_seq')").getSingleResult();
        em.createNativeQuery(
                "INSERT INTO survey.respondents (id, survey_id, token, active, logins, created_dt, first_access_dt, finalized_dt) " +
                "VALUES (?1, ?2, 'tok-rt', true, 0, now(), null, null)")
                .setParameter(1, respondentId)
                .setParameter(2, surveyId)
                .executeUpdate();

        // Answer 1: text_value = null (deleted = false)
        em.createNativeQuery(
                "INSERT INTO survey.answers (id, respondent_id, survey_id, step, step_instance, section, section_instance, " +
                "question_display_order, question_instance, section_question_id, question_id, " +
                "display_key, display_text, text_value, deleted, created_dt, saved_dt) " +
                "VALUES (nextval('survey.answers_seq'), ?1, ?2, 1, 1, 1, 1, 1, 1, ?3, ?4, 'DK-NULL', 'Q1 text', null, false, now(), null)")
                .setParameter(1, respondentId)
                .setParameter(2, surveyId)
                .setParameter(3, sq1Id)
                .setParameter(4, q1Id)
                .executeUpdate();

        // Answer 2: deleted = true, text_value = "normal"
        em.createNativeQuery(
                "INSERT INTO survey.answers (id, respondent_id, survey_id, step, step_instance, section, section_instance, " +
                "question_display_order, question_instance, section_question_id, question_id, " +
                "display_key, display_text, text_value, deleted, created_dt, saved_dt) " +
                "VALUES (nextval('survey.answers_seq'), ?1, ?2, 1, 1, 1, 1, 2, 1, ?3, ?4, 'DK-DEL', 'Q2 text', 'normal', true, now(), null)")
                .setParameter(1, respondentId)
                .setParameter(2, surveyId)
                .setParameter(3, sq2Id)
                .setParameter(4, q2Id)
                .executeUpdate();

        // Answer 3: text_value contains a pipe character
        em.createNativeQuery(
                "INSERT INTO survey.answers (id, respondent_id, survey_id, step, step_instance, section, section_instance, " +
                "question_display_order, question_instance, section_question_id, question_id, " +
                "display_key, display_text, text_value, deleted, created_dt, saved_dt) " +
                "VALUES (nextval('survey.answers_seq'), ?1, ?2, 1, 1, 1, 1, 3, 1, ?3, ?4, 'DK-PIPE', 'Q3 text', 'val|ue', false, now(), null)")
                .setParameter(1, respondentId)
                .setParameter(2, surveyId)
                .setParameter(3, sq3Id)
                .setParameter(4, q3Id)
                .executeUpdate();

        return respondentId.longValue();
    }

    // T012: imported answer count matches fixture
    @Test
    @TestTransaction
    void respondentRoundTripPreservesAnswerCount() {
        long respondentId = seedFixture();

        String exported = exportService.exportRespondent((int) respondentId);
        RespondentImportService.ImportResult result = importService.importFromFile(
                new ByteArrayInputStream(exported.getBytes(StandardCharsets.UTF_8)));

        assertNotNull(result);
        assertTrue(result.isSuccess(), () -> "Import failed: " + result.getErrors());
        assertEquals(3, result.getCounts().get("answers"));
    }

    // T013: text_value and deleted flag are preserved after round-trip
    @Test
    @TestTransaction
    void respondentRoundTripPreservesTextValueAndDeletedFlag() {
        long respondentId = seedFixture();

        String exported = exportService.exportRespondent((int) respondentId);
        RespondentImportService.ImportResult result = importService.importFromFile(
                new ByteArrayInputStream(exported.getBytes(StandardCharsets.UTF_8)));
        assertTrue(result.isSuccess(), () -> "Import failed: " + result.getErrors());

        Long importedRespondentId = ((Number) em.createNativeQuery(
                "SELECT MAX(id) FROM survey.respondents").getSingleResult()).longValue();

        // DK-DEL: deleted = true, text_value = "normal"
        Object[] delRow = (Object[]) em.createNativeQuery(
                "SELECT text_value, deleted FROM survey.answers " +
                "WHERE respondent_id = ?1 AND display_key = 'DK-DEL'")
                .setParameter(1, importedRespondentId)
                .getSingleResult();
        assertEquals("normal", delRow[0]);
        assertEquals(Boolean.TRUE, delRow[1]);

        // DK-PIPE: deleted = false, text_value = "val|ue"
        Object[] pipeRow = (Object[]) em.createNativeQuery(
                "SELECT text_value, deleted FROM survey.answers " +
                "WHERE respondent_id = ?1 AND display_key = 'DK-PIPE'")
                .setParameter(1, importedRespondentId)
                .getSingleResult();
        assertEquals("val|ue", pipeRow[0]);
        assertEquals(Boolean.FALSE, pipeRow[1]);
    }

    // T014: first line of the respondent export is the version header
    @Test
    @TestTransaction
    void headerVersionLineIsElicitExportV1() {
        long respondentId = seedFixture();

        String exported = exportService.exportRespondent((int) respondentId);
        String[] lines = exported.split("\n");

        assertEquals("# ELICIT_EXPORT_V1", lines[0]);
    }

    // T015: null text_value survives round-trip as null (not empty string)
    @Test
    @TestTransaction
    void nullAnswerValueRoundTripsAsNull() {
        long respondentId = seedFixture();

        String exported = exportService.exportRespondent((int) respondentId);
        RespondentImportService.ImportResult result = importService.importFromFile(
                new ByteArrayInputStream(exported.getBytes(StandardCharsets.UTF_8)));
        assertTrue(result.isSuccess(), () -> "Import failed: " + result.getErrors());

        Long importedRespondentId = ((Number) em.createNativeQuery(
                "SELECT MAX(id) FROM survey.respondents").getSingleResult()).longValue();

        Object textValue = em.createNativeQuery(
                "SELECT text_value FROM survey.answers " +
                "WHERE respondent_id = ?1 AND display_key = 'DK-NULL'")
                .setParameter(1, importedRespondentId)
                .getSingleResult();

        assertNull(textValue, "null text_value must round-trip as null, not empty string");
    }

    // T015b: subjects and messages round-trip through export→import
    @Test
    @TestTransaction
    void subjectsAndMessagesRoundTrip() {
        long respondentId = seedFixtureWithSubjectAndMessage();

        String exported = exportService.exportRespondent((int) respondentId);
        assertTrue(exported.contains("subjects:"), "Export should contain subject line");
        assertTrue(exported.contains("messages:"), "Export should contain message line");

        RespondentImportService.ImportResult result = importService.importFromFile(
                new ByteArrayInputStream(exported.getBytes(StandardCharsets.UTF_8)));
        assertTrue(result.isSuccess(), () -> "Import failed: " + result.getErrors());
        assertEquals(1, result.getCounts().get("subjects"), "Should import 1 subject");
        assertEquals(1, result.getCounts().get("messages"), "Should import 1 message");
    }

    /**
     * Inserts a minimal survey + respondent + one answer + one subject + one message.
     * Uses department id=1 and message_type id=1 seeded by V0.0.0 stub SQL.
     */
    private long seedFixtureWithSubjectAndMessage() {
        // Survey
        Number surveyId = (Number) em.createNativeQuery(
                "SELECT nextval('survey.surveys_seq')").getSingleResult();
        em.createNativeQuery(
                "INSERT INTO survey.surveys (id, name, display_order, title, description, initial_display_key, post_survey_url) " +
                "VALUES (?1, ?2, 1, 'Subj Survey', null, 'S1', null)")
                .setParameter(1, surveyId)
                .setParameter(2, "Subj Survey " + surveyId)
                .executeUpdate();

        // Minimal step/section/question/sections_questions (needed by answers)
        Number stepId = (Number) em.createNativeQuery(
                "SELECT nextval('survey.steps_seq')").getSingleResult();
        em.createNativeQuery(
                "INSERT INTO survey.steps (id, survey_id, display_order, name, dimension_name, description) " +
                "VALUES (?1, ?2, 1, 'Step', null, null)")
                .setParameter(1, stepId).setParameter(2, surveyId).executeUpdate();

        Number sectionId = (Number) em.createNativeQuery(
                "SELECT nextval('survey.sections_seq')").getSingleResult();
        em.createNativeQuery(
                "INSERT INTO survey.sections (id, survey_id, display_order, name, dimension_name, description) " +
                "VALUES (?1, ?2, 1, 'Section', null, null)")
                .setParameter(1, sectionId).setParameter(2, surveyId).executeUpdate();

        Number qId = (Number) em.createNativeQuery(
                "SELECT nextval('survey.questions_seq')").getSingleResult();
        em.createNativeQuery(
                "INSERT INTO survey.questions (id, survey_id, type_id, text, short_text, tool_tip, required, " +
                "min_value, max_value, validation_text, select_group_id, mask, placeholder, default_value, variant) " +
                "VALUES (?1, ?2, 1, 'Q', null, null, false, null, null, null, null, null, null, null, null)")
                .setParameter(1, qId).setParameter(2, surveyId).executeUpdate();

        Number ssId = (Number) em.createNativeQuery(
                "SELECT nextval('survey.steps_sections_seq')").getSingleResult();
        em.createNativeQuery(
                "INSERT INTO survey.steps_sections (id, survey_id, step_id, step_display_order, section_id, section_display_order, display_key) " +
                "VALUES (?1, ?2, ?3, 1, ?4, 1, 'SS1')")
                .setParameter(1, ssId).setParameter(2, surveyId)
                .setParameter(3, stepId).setParameter(4, sectionId).executeUpdate();

        Number sqId = (Number) em.createNativeQuery(
                "SELECT nextval('survey.sections_questions_seq')").getSingleResult();
        em.createNativeQuery(
                "INSERT INTO survey.sections_questions (id, survey_id, question_id, section_id, display_order) " +
                "VALUES (?1, ?2, ?3, ?4, 1)")
                .setParameter(1, sqId).setParameter(2, surveyId)
                .setParameter(3, qId).setParameter(4, sectionId).executeUpdate();

        // Respondent
        Number respondentId = (Number) em.createNativeQuery(
                "SELECT nextval('survey.respondents_seq')").getSingleResult();
        em.createNativeQuery(
                "INSERT INTO survey.respondents (id, survey_id, token, active, logins, created_dt, first_access_dt, finalized_dt) " +
                "VALUES (?1, ?2, 'tok-subj', true, 0, now(), null, null)")
                .setParameter(1, respondentId).setParameter(2, surveyId).executeUpdate();

        // Answer
        em.createNativeQuery(
                "INSERT INTO survey.answers (id, respondent_id, survey_id, step, step_instance, section, section_instance, " +
                "question_display_order, question_instance, section_question_id, question_id, " +
                "display_key, display_text, text_value, deleted, created_dt, saved_dt) " +
                "VALUES (nextval('survey.answers_seq'), ?1, ?2, 1, 1, 1, 1, 1, 1, ?3, ?4, 'DK-SUBJ', 'Q text', 'answer', false, now(), null)")
                .setParameter(1, respondentId).setParameter(2, surveyId)
                .setParameter(3, sqId).setParameter(4, qId).executeUpdate();

        // Subject (department_id=1 and survey_id from above).
        // Use xid=null so the round-trip re-import doesn't hit the (xid,department_id) unique constraint
        // (PostgreSQL treats NULL as distinct in unique indexes, so two null-xid rows are allowed).
        Number subjectId = (Number) em.createNativeQuery(
                "SELECT nextval('survey.subjects_seq')").getSingleResult();
        em.createNativeQuery(
                "INSERT INTO survey.subjects (id, xid, firstname, lastname, middlename, dob, email, phone, department_id, survey_id, respondent_id, created_dt) " +
                "VALUES (?1, null, 'First', 'Last', null, null, 'first@example.com', null, 1, ?2, ?3, now())")
                .setParameter(1, subjectId).setParameter(2, surveyId).setParameter(3, respondentId).executeUpdate();

        // Message (message_type=1 seeded by V0.0.0 stub)
        em.createNativeQuery(
                "INSERT INTO survey.messages (id, subject_id, message_type, mime_type, subjectline, body, created_dt, sent_dt) " +
                "VALUES (nextval('survey.messages_seq'), ?1, 1, 'text/html', 'Hello', 'Body text', now(), null)")
                .setParameter(1, subjectId).executeUpdate();

        return respondentId.longValue();
    }
}
