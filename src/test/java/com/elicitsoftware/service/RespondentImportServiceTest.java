package com.elicitsoftware.service;

/*-
 * ***LICENSE_START***
 * Elicit Survey
 * %%
 * Copyright (C) 2025 - 2026 The Regents of the University of Michigan - Rogel Cancer Center
 * %%
 * PolyForm Noncommercial License 1.0.0
 * <https://polyformproject.org/licenses/noncommercial/1.0.0>
 * ***LICENSE_END***
 */

import com.elicitsoftware.model.Department;
import com.elicitsoftware.model.Message;
import com.elicitsoftware.model.MessageType;
import com.elicitsoftware.model.PostSurveyAction;
import com.elicitsoftware.model.Respondent;
import com.elicitsoftware.model.Subject;
import com.elicitsoftware.model.Survey;
import com.elicitsoftware.test.PostgresTestResource;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Booted round-trip and error-path tests for {@link RespondentExportService} and
 * {@link RespondentImportService}.
 *
 * <p>Traceability: UC-011 (Export Respondent Data) and UC-012 (Import Respondent Data). Both
 * services had zero test coverage before this class. The round-trip test also seeds the six
 * cross-module tables ({@code answers}, {@code dependents}, {@code respondent_psa}, and the
 * minimal {@code steps}/{@code sections}/{@code questions}/{@code sections_questions}/
 * {@code relationships} chain those FK to) that {@code src/test/resources/db/test/
 * V0.0.0.1__TEST_BOOTSTRAP.sql} now defines with real columns instead of one-column stubs.</p>
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class RespondentImportServiceTest {

    @Inject
    RespondentExportService respondentExportService;

    @Inject
    RespondentImportService respondentImportService;

    @Inject
    EntityManager em;

    /** Holds the seeded values a round trip needs to assert against post-import. */
    private record RespondentFixture(int respondentId, String accessCode, String firstName, String email,
                                      String answerKey1, String answerKey2, String messageSubject,
                                      String messageBody, String psaStatus) {
    }

    private InputStream toStream(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }

    private long nextVal(String sequence) {
        return ((Number) em.createNativeQuery("SELECT nextval('" + sequence + "')").getSingleResult()).longValue();
    }

    private long queryLong(String sql, Object param) {
        return ((Number) em.createNativeQuery(sql).setParameter(1, param).getSingleResult()).longValue();
    }

    private long insertStep(int surveyId, int displayOrder, String name) {
        long id = nextVal("survey.steps_seq");
        em.createNativeQuery("INSERT INTO survey.steps (id, survey_id, display_order, name, dimension_name) "
                        + "VALUES (?1, ?2, ?3, ?4, 'D')")
                .setParameter(1, id).setParameter(2, surveyId).setParameter(3, displayOrder).setParameter(4, name)
                .executeUpdate();
        return queryLong("SELECT step_id FROM survey.steps WHERE id = ?1", id);
    }

    private long insertSection(int surveyId, int displayOrder, String name) {
        long id = nextVal("survey.sections_seq");
        em.createNativeQuery("INSERT INTO survey.sections (id, survey_id, display_order, name, dimension_name) "
                        + "VALUES (?1, ?2, ?3, ?4, 'D')")
                .setParameter(1, id).setParameter(2, surveyId).setParameter(3, displayOrder).setParameter(4, name)
                .executeUpdate();
        return queryLong("SELECT section_id FROM survey.sections WHERE id = ?1", id);
    }

    private long insertQuestion(int surveyId, String text) {
        long id = nextVal("survey.questions_seq");
        em.createNativeQuery("INSERT INTO survey.questions (id, survey_id, type_id, text) VALUES (?1, ?2, 1, ?3)")
                .setParameter(1, id).setParameter(2, surveyId).setParameter(3, text)
                .executeUpdate();
        return queryLong("SELECT question_id FROM survey.questions WHERE id = ?1", id);
    }

    private long insertSectionQuestion(int surveyId, long questionId, long sectionId, int displayOrder) {
        long id = nextVal("survey.sections_questions_seq");
        em.createNativeQuery("INSERT INTO survey.sections_questions (id, survey_id, question_id, section_id, display_order) "
                        + "VALUES (?1, ?2, ?3, ?4, ?5)")
                .setParameter(1, id).setParameter(2, surveyId).setParameter(3, questionId)
                .setParameter(4, sectionId).setParameter(5, displayOrder)
                .executeUpdate();
        return queryLong("SELECT sections_question_id FROM survey.sections_questions WHERE id = ?1", id);
    }

    /** Minimal relationship: only the required upstream_sq_id/operator/action - downstream_* left
     *  null, which satisfies relationships' downstream_ck CHECK (a NULL sum is not FALSE). */
    private long insertRelationship(int surveyId, long upstreamSqId) {
        long id = nextVal("survey.relationships_seq");
        em.createNativeQuery("INSERT INTO survey.relationships (id, survey_id, upstream_sq_id, operator_id, action_id) "
                        + "VALUES (?1, ?2, ?3, 1, 1)")
                .setParameter(1, id).setParameter(2, surveyId).setParameter(3, upstreamSqId)
                .executeUpdate();
        return id;
    }

    private long insertAnswer(int surveyId, int respondentId, long stepId, String displayKey, String displayText) {
        long id = nextVal("survey.answers_seq");
        em.createNativeQuery("INSERT INTO survey.answers (id, survey_id, respondent_id, step, display_key, display_text) "
                        + "VALUES (?1, ?2, ?3, ?4, ?5, ?6)")
                .setParameter(1, id).setParameter(2, surveyId).setParameter(3, respondentId)
                .setParameter(4, stepId).setParameter(5, displayKey).setParameter(6, displayText)
                .executeUpdate();
        return id;
    }

    private void insertDependent(int respondentId, long upstreamAnswerId, long downstreamAnswerId, long relationshipId) {
        long id = nextVal("survey.dependents_seq");
        em.createNativeQuery("INSERT INTO survey.dependents (id, respondent_id, upstream_id, downstream_id, relationship_id) "
                        + "VALUES (?1, ?2, ?3, ?4, ?5)")
                .setParameter(1, id).setParameter(2, respondentId).setParameter(3, upstreamAnswerId)
                .setParameter(4, downstreamAnswerId).setParameter(5, relationshipId)
                .executeUpdate();
    }

    /**
     * Import re-inserts the exported subject under the same (xid, department_id) the original
     * still occupies - {@code survey.subjects} has a UNIQUE(xid, department_id) constraint
     * (Admin's own V0.0.1 migration), so a same-database round trip must free that slot first,
     * same as a real restore-into-an-empty-target scenario would start from an empty table.
     */
    private void deleteSubjectAndMessages(int respondentId) {
        em.createNativeQuery("DELETE FROM survey.messages WHERE subject_id IN "
                        + "(SELECT id FROM survey.subjects WHERE respondent_id = ?1)")
                .setParameter(1, respondentId).executeUpdate();
        em.createNativeQuery("DELETE FROM survey.subjects WHERE respondent_id = ?1")
                .setParameter(1, respondentId).executeUpdate();
    }

    private void insertRespondentPsa(int respondentId, int postSurveyActionId, String status) {
        long id = nextVal("survey.respondent_psa_seq");
        em.createNativeQuery("INSERT INTO survey.respondent_psa (id, respondent_id, post_survey_action_id, status) "
                        + "VALUES (?1, ?2, ?3, ?4)")
                .setParameter(1, id).setParameter(2, respondentId).setParameter(3, postSurveyActionId).setParameter(4, status)
                .executeUpdate();
    }

    /**
     * Seeds a full respondent tree covering all six record types the export/import format
     * carries: respondent, subject, message, two linked answers (via a dependent), and a
     * respondent_psa row.
     */
    private RespondentFixture persistRespondentTree(String accessCode) {
        Survey survey = Survey.findById(1L);
        assertNotNull(survey, "test bootstrap should have seeded survey id=1");
        int surveyId = survey.id;

        Department department = new Department();
        department.name = "RX Dept " + accessCode;
        department.code = "RX-" + accessCode;
        department.defaultMessageId = "1";
        department.fromEmail = "rx-import@example.org";
        department.persist();

        Respondent respondent = new Respondent();
        respondent.survey = survey;
        respondent.accessCode = accessCode;
        respondent.active = true;
        respondent.logins = 2;
        respondent.persist();
        int respondentId = respondent.id;

        Subject subject = new Subject("XID-" + accessCode, surveyId, department.id,
                "First-" + accessCode, "Last-" + accessCode, null, LocalDate.of(1990, 1, 15),
                "rx-" + accessCode.toLowerCase() + "@example.org", null);
        subject.setRespondent(respondent);
        subject.persistAndFlush();

        MessageType messageType = new MessageType();
        messageType.setName("Type " + accessCode);
        messageType.persist();

        Message message = new Message(subject, messageType, "Subject " + accessCode, "Body " + accessCode);
        message.persist();

        PostSurveyAction psa = new PostSurveyAction();
        psa.survey = survey;
        psa.name = "PSA " + accessCode;
        psa.persistAndFlush();

        long stepId = insertStep(surveyId, 1, "Step " + accessCode);
        long sectionId = insertSection(surveyId, 1, "Section " + accessCode);
        long questionId = insertQuestion(surveyId, "Question " + accessCode + "?");
        long sqId = insertSectionQuestion(surveyId, questionId, sectionId, 1);
        long relationshipId = insertRelationship(surveyId, sqId);

        String key1 = "A1-" + accessCode;
        String key2 = "A2-" + accessCode;
        long answer1Id = insertAnswer(surveyId, respondentId, stepId, key1, "Answer text 1 " + accessCode);
        long answer2Id = insertAnswer(surveyId, respondentId, stepId, key2, "Answer text 2 " + accessCode);
        insertDependent(respondentId, answer1Id, answer2Id, relationshipId);

        insertRespondentPsa(respondentId, psa.id, "PENDING");

        return new RespondentFixture(respondentId, accessCode, subject.getFirstName(), subject.getEmail(),
                key1, key2, message.subjectLine, message.body, "PENDING");
    }

    /**
     * UC-011/UC-012: exporting a full respondent tree and importing it back creates a brand-new
     * respondent (new IDs throughout) whose data matches the original, including the dependent
     * link resolved by display_key and the respondent_psa row - which pins down that
     * {@code insertRespondentPsa}'s {@code currval('survey.respondents_seq')} lookup resolves to
     * the just-imported respondent, not a stale one, for a normal single-file import.
     */
    @Test
    @TestTransaction
    void exportThenImportRoundTripsAllRecordTypes() {
        RespondentFixture fixture = persistRespondentTree("RT1");

        String exported = respondentExportService.exportRespondent(fixture.respondentId());
        assertTrue(exported.contains("# ELICIT_EXPORT_V1"));
        assertTrue(exported.contains("respondents: "));
        assertTrue(exported.contains("answers: "));
        assertTrue(exported.contains("dependents: "));
        assertTrue(exported.contains("subjects: "));
        assertTrue(exported.contains("messages: "));
        assertTrue(exported.contains("respondent_psa: "));

        deleteSubjectAndMessages(fixture.respondentId());

        RespondentImportService.ImportResult result = respondentImportService.importFromFile(toStream(exported));

        assertTrue(result.isSuccess(), () -> "import errors: " + result.getErrors());
        assertEquals(1, result.getCounts().get("respondents"));
        assertEquals(2, result.getCounts().get("answers"));
        assertEquals(1, result.getCounts().get("dependents"));
        assertEquals(1, result.getCounts().get("subjects"));
        assertEquals(1, result.getCounts().get("messages"));
        assertEquals(1, result.getCounts().get("respondent_psa"));

        long newRespondentId = queryLong(
                "SELECT id FROM survey.respondents WHERE access_code = ?1 ORDER BY id DESC LIMIT 1", fixture.accessCode());
        assertNotEquals((long) fixture.respondentId(), newRespondentId,
                "import should create a new respondent, not reuse the original");

        Object[] newSubject = (Object[]) em.createNativeQuery(
                        "SELECT firstname, email FROM survey.subjects WHERE respondent_id = ?1")
                .setParameter(1, newRespondentId).getSingleResult();
        assertEquals(fixture.firstName(), newSubject[0]);
        assertEquals(fixture.email(), newSubject[1]);

        List<?> newAnswerKeys = em.createNativeQuery(
                        "SELECT display_key FROM survey.answers WHERE respondent_id = ?1 ORDER BY display_key")
                .setParameter(1, newRespondentId).getResultList();
        assertEquals(List.of(fixture.answerKey1(), fixture.answerKey2()), newAnswerKeys);

        long dependentCount = queryLong(
                "SELECT count(*) FROM survey.dependents WHERE respondent_id = ?1", newRespondentId);
        assertEquals(1, dependentCount);

        Object[] newMessage = (Object[]) em.createNativeQuery(
                        "SELECT subjectline, body FROM survey.messages m "
                                + "JOIN survey.subjects s ON s.id = m.subject_id WHERE s.respondent_id = ?1")
                .setParameter(1, newRespondentId).getSingleResult();
        assertEquals(fixture.messageSubject(), newMessage[0]);
        assertEquals(fixture.messageBody(), newMessage[1]);

        Object[] newPsa = (Object[]) em.createNativeQuery(
                        "SELECT respondent_id, status FROM survey.respondent_psa WHERE respondent_id = ?1")
                .setParameter(1, newRespondentId).getSingleResult();
        assertEquals(newRespondentId, ((Number) newPsa[0]).longValue(),
                "insertRespondentPsa's currval() lookup must resolve to the just-imported respondent");
        assertEquals(fixture.psaStatus(), newPsa[1]);
    }

    /** UC-011: exporting a nonexistent respondent id fails fast rather than returning an empty file. */
    @Test
    @TestTransaction
    void exportRespondentNotFoundThrows() {
        assertThrows(IllegalArgumentException.class, () -> respondentExportService.exportRespondent(999999));
    }

    /** UC-012: a file missing the format-version header returns a failed result, not an exception. */
    @Test
    @TestTransaction
    void malformedHeaderReturnsFailedResultWithoutThrowing() {
        String content = "respondents: 1|tok|0|2026-01-01T00:00:00-05:00|\n";

        RespondentImportService.ImportResult result = respondentImportService.importFromFile(toStream(content));

        assertFalse(result.isSuccess());
        assertTrue(result.getErrors().get(0).contains("valid format header"), result.getErrors().toString());
    }

    /** UC-012: an unrecognized table name is collected as an error but does not abort the import. */
    @Test
    @TestTransaction
    void unknownTableNameIsCollectedAsErrorWithoutThrowing() {
        String content = "# ELICIT_EXPORT_V1\n\nfoobar: 1|2|3\n";

        RespondentImportService.ImportResult result = respondentImportService.importFromFile(toStream(content));

        assertFalse(result.isSuccess());
        assertTrue(result.getErrors().get(0).contains("Unknown table: foobar"), result.getErrors().toString());
    }

    /** UC-012: too few fields on a data line throws, wrapping the field-count IllegalArgumentException. */
    @Test
    @TestTransaction
    void shortFieldCountThrowsRuntimeException() {
        String content = "# ELICIT_EXPORT_V1\n\nrespondents: 1|tok\n";

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> respondentImportService.importFromFile(toStream(content)));

        assertTrue(ex.getMessage().contains("Respondent requires 5 fields"), ex.getMessage());
    }

    /** UC-012: an answers line before any respondents line is skipped with an error, not thrown. */
    @Test
    @TestTransaction
    void answerBeforeRespondentIsSkippedWithErrorNotThrown() {
        String content = "# ELICIT_EXPORT_V1\n\nanswers: 1|2|3\n";

        RespondentImportService.ImportResult result = respondentImportService.importFromFile(toStream(content));

        assertFalse(result.isSuccess());
        assertEquals(0, result.getCounts().get("answers"));
        assertTrue(result.getErrors().get(0).contains("Cannot insert answer before respondent"), result.getErrors().toString());
    }
}
