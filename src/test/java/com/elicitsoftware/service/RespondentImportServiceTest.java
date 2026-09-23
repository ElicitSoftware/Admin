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
import java.util.UUID;

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
 * <p>Traceability: UC-011 (Export Respondent Data) and UC-012 (Import Respondent Data). The
 * round-trip tests seed the six cross-module tables ({@code answers}, {@code dependents},
 * {@code respondent_psa}, and the minimal {@code steps}/{@code sections}/{@code questions}/
 * {@code sections_questions}/{@code relationships} chain those FK to) that
 * {@code src/test/resources/db/test/V0.0.0.1__TEST_BOOTSTRAP.sql} defines with real columns.</p>
 *
 * <p>The {@code ELICIT_EXPORT_V2} format carries no site-local ids: the cross-instance cases
 * below stand up a second survey that shares survey 1's element keys (the situation two Elicit
 * instances deploying the same {@code .elicit} lineage are in) and check every reference is
 * re-resolved by key and version (BR-099/BR-103), never copied.</p>
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
                                      String messageBody, String psaStatus, String psaName,
                                      String departmentCode, long questionId, long sectionQuestionId,
                                      long relationshipId, String stepName, String sectionName) {
    }

    private static final String SURVEY_1_KEY = "00000000-0000-0000-0000-000000000001";

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

    /** Inserts a question and returns its surrogate {@code id}; {@code key} null lets the bootstrap default a random one. */
    private long insertQuestion(int surveyId, String text, UUID key) {
        long id = nextVal("survey.questions_seq");
        String keyColumn = key == null ? "" : ", question_key";
        String keyValue = key == null ? "" : ", ?4";
        var query = em.createNativeQuery("INSERT INTO survey.questions (id, survey_id, type_id, text" + keyColumn + ") "
                        + "VALUES (?1, ?2, 1, ?3" + keyValue + ")")
                .setParameter(1, id).setParameter(2, surveyId).setParameter(3, text);
        if (key != null) {
            query.setParameter(4, key);
        }
        query.executeUpdate();
        return id;
    }

    /** Inserts a section-question (durable question/section ids looked up from the surrogates) and returns its surrogate id. */
    private long insertSectionQuestion(int surveyId, long questionId, long sectionId, int displayOrder, UUID key) {
        long id = nextVal("survey.sections_questions_seq");
        String keyColumn = key == null ? "" : ", sections_question_key";
        String keyValue = key == null ? "" : ", ?6";
        var query = em.createNativeQuery("INSERT INTO survey.sections_questions "
                        + "(id, survey_id, question_id, section_id, display_order" + keyColumn + ") "
                        + "VALUES (?1, ?2, (SELECT question_id FROM survey.questions WHERE id = ?3), ?4, ?5" + keyValue + ")")
                .setParameter(1, id).setParameter(2, surveyId).setParameter(3, questionId)
                .setParameter(4, sectionId).setParameter(5, displayOrder);
        if (key != null) {
            query.setParameter(6, key);
        }
        query.executeUpdate();
        return id;
    }

    /** Minimal relationship: only the required upstream_sq_id/operator/action - downstream_* left
     *  null, which satisfies relationships' downstream_ck CHECK (a NULL sum is not FALSE). */
    private long insertRelationship(int surveyId, long upstreamSqId, UUID key) {
        long id = nextVal("survey.relationships_seq");
        String keyColumn = key == null ? "" : ", relationship_key";
        String keyValue = key == null ? "" : ", ?4";
        var query = em.createNativeQuery("INSERT INTO survey.relationships (id, survey_id, upstream_sq_id, operator_id, action_id"
                        + keyColumn + ") VALUES (?1, ?2, (SELECT sections_question_id FROM survey.sections_questions WHERE id = ?3), 1, 1"
                        + keyValue + ")")
                .setParameter(1, id).setParameter(2, surveyId).setParameter(3, upstreamSqId);
        if (key != null) {
            query.setParameter(4, key);
        }
        query.executeUpdate();
        return id;
    }

    private UUID elementKey(String table, String keyColumn, long id) {
        Object key = em.createNativeQuery("SELECT " + keyColumn + " FROM survey." + table + " WHERE id = ?1")
                .setParameter(1, id).getSingleResult();
        return key instanceof UUID uuid ? uuid : UUID.fromString(key.toString());
    }

    /** Inserts an answer that references its question and section-question by surrogate id, as the Survey app does. */
    private long insertAnswer(int surveyId, int respondentId, long stepId, String displayKey, String displayText,
                              long questionId, long sectionQuestionId) {
        long id = nextVal("survey.answers_seq");
        em.createNativeQuery("INSERT INTO survey.answers (id, survey_id, respondent_id, step, display_key, display_text, "
                        + "question_id, section_question_id) VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8)")
                .setParameter(1, id).setParameter(2, surveyId).setParameter(3, respondentId)
                .setParameter(4, stepId).setParameter(5, displayKey).setParameter(6, displayText)
                .setParameter(7, questionId).setParameter(8, sectionQuestionId)
                .executeUpdate();
        return id;
    }

    /**
     * The V2 access-code collision check (BR-105) rejects a second respondent with the same
     * access code on the same survey, so a same-database round trip must first move the source
     * respondent off the code the file carries - exactly what a real transfer between two
     * instances never has to do.
     */
    private void renameSourceAccessCode(int respondentId) {
        em.createNativeQuery("UPDATE survey.respondents SET access_code = access_code || '-src' WHERE id = ?1")
                .setParameter(1, respondentId).executeUpdate();
    }

    private Object[] respondentState(long respondentId) {
        return (Object[]) em.createNativeQuery(
                        "SELECT active, first_access_dt, finalized_dt, logins, survey_id FROM survey.respondents WHERE id = ?1")
                .setParameter(1, respondentId).getSingleResult();
    }

    private long newestRespondentId(String accessCode) {
        return queryLong("SELECT id FROM survey.respondents WHERE access_code = ?1 ORDER BY id DESC LIMIT 1", accessCode);
    }

    /**
     * Stands up a second survey the way another instance that deployed the same {@code .elicit}
     * lineage would look: its own survey_key, and question / section-question / relationship
     * rows that reuse survey 1's element keys (at version 0) under fresh surrogate ids. Survey 1's
     * rows are then moved to fresh keys: (key, version) is UNIQUE in the Survey schema, so within
     * one database only the twin may carry the exported keys - the source lives on another instance.
     *
     * @return {@code {surveyId, surveyKey, questionId, sectionQuestionId, relationshipId}}
     */
    private Object[] persistSecondSurveySharingElementKeys(RespondentFixture fixture, String suffix) {
        long surveyId = nextVal("survey.surveys_seq");
        UUID surveyKey = UUID.randomUUID();
        em.createNativeQuery("INSERT INTO survey.surveys (id, survey_key, display_order, name, title, description) "
                        + "VALUES (?1, ?2, ?3, ?4, ?4, 'Second instance twin')")
                .setParameter(1, surveyId).setParameter(2, surveyKey).setParameter(3, (int) surveyId)
                .setParameter(4, "Twin Survey " + suffix)
                .executeUpdate();

        int sid = (int) surveyId;
        insertStep(sid, 1, fixture.stepName());
        long sectionId = insertSection(sid, 1, fixture.sectionName());
        long questionId = insertQuestion(sid, "Twin question " + suffix,
                elementKey("questions", "question_key", fixture.questionId()));
        long sqId = insertSectionQuestion(sid, questionId, sectionId, 1,
                elementKey("sections_questions", "sections_question_key", fixture.sectionQuestionId()));
        long relationshipId = insertRelationship(sid, sqId,
                elementKey("relationships", "relationship_key", fixture.relationshipId()));

        PostSurveyAction psa = new PostSurveyAction();
        psa.survey = Survey.findById((int) surveyId);
        psa.name = fixture.psaName();
        psa.persistAndFlush();

        rekey("questions", "question_key", fixture.questionId());
        rekey("sections_questions", "sections_question_key", fixture.sectionQuestionId());
        rekey("relationships", "relationship_key", fixture.relationshipId());

        return new Object[]{surveyId, surveyKey, questionId, sqId, relationshipId};
    }

    private void rekey(String table, String keyColumn, long id) {
        em.createNativeQuery("UPDATE survey." + table + " SET " + keyColumn + " = ?1 WHERE id = ?2")
                .setParameter(1, UUID.randomUUID()).setParameter(2, id).executeUpdate();
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
        return persistRespondentTree(accessCode, "RX-" + accessCode);
    }

    /**
     * Same as {@link #persistRespondentTree(String)} with an explicit department code
     * ({@code null} models a department that was never given one, which UC-011 refuses to export).
     */
    private RespondentFixture persistRespondentTree(String accessCode, String departmentCode) {
        Survey survey = Survey.findById(1L);
        assertNotNull(survey, "test bootstrap should have seeded survey id=1");
        int surveyId = survey.id;

        Department department = new Department();
        department.name = "RX Dept " + accessCode;
        department.code = departmentCode;
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

        String stepName = "Step " + accessCode;
        String sectionName = "Section " + accessCode;
        long stepId = insertStep(surveyId, 1, stepName);
        long sectionId = insertSection(surveyId, 1, sectionName);
        long questionId = insertQuestion(surveyId, "Question " + accessCode + "?", null);
        long sqId = insertSectionQuestion(surveyId, questionId, sectionId, 1, null);
        long relationshipId = insertRelationship(surveyId, sqId, null);

        String key1 = "A1-" + accessCode;
        String key2 = "A2-" + accessCode;
        long answer1Id = insertAnswer(surveyId, respondentId, stepId, key1, "Answer text 1 " + accessCode, questionId, sqId);
        long answer2Id = insertAnswer(surveyId, respondentId, stepId, key2, "Answer text 2 " + accessCode, questionId, sqId);
        insertDependent(respondentId, answer1Id, answer2Id, relationshipId);

        insertRespondentPsa(respondentId, psa.id, "PENDING");

        return new RespondentFixture(respondentId, accessCode, subject.getFirstName(), subject.getEmail(),
                key1, key2, message.subjectLine, message.body, "PENDING", psa.name, departmentCode,
                questionId, sqId, relationshipId, stepName, sectionName);
    }

    /**
     * UC-011/UC-012: exporting a full respondent tree and importing it back creates a brand-new
     * respondent (new IDs throughout) whose data matches the original, including the dependent
     * link resolved by display_key, the question / section-question / relationship references
     * re-resolved by key and version, and the respondent_psa row attached to the respondent id
     * the import allocated (not a sequence {@code currval()}).
     */
    @Test
    @TestTransaction
    void exportThenImportRoundTripsAllRecordTypes() {
        RespondentFixture fixture = persistRespondentTree("RT1");

        String exported = respondentExportService.exportRespondent(fixture.respondentId());
        assertTrue(exported.contains("# ELICIT_EXPORT_V2"));
        assertTrue(exported.contains("# survey_key: " + SURVEY_1_KEY));
        assertTrue(exported.contains("# survey_name: Test Survey"));
        assertFalse(exported.contains("# survey_id:"), "V2 must not carry the local survey id");
        assertTrue(exported.contains("respondents: " + SURVEY_1_KEY + "|" + fixture.accessCode() + "|true|2|"));
        assertTrue(exported.contains("|" + fixture.departmentCode() + "|"), "subject carries the department code");
        assertTrue(exported.contains("respondents: "));
        assertTrue(exported.contains("answers: "));
        assertTrue(exported.contains("dependents: "));
        assertTrue(exported.contains("subjects: "));
        assertTrue(exported.contains("messages: "));
        assertTrue(exported.contains("respondent_psa: "));

        deleteSubjectAndMessages(fixture.respondentId());
        renameSourceAccessCode(fixture.respondentId());

        RespondentImportService.ImportResult result = respondentImportService.importFromFile(toStream(exported));

        assertTrue(result.isSuccess(), () -> "import errors: " + result.getErrors());
        assertEquals(1, result.getCounts().get("respondents"));
        assertEquals(2, result.getCounts().get("answers"));
        assertEquals(1, result.getCounts().get("dependents"));
        assertEquals(1, result.getCounts().get("subjects"));
        assertEquals(1, result.getCounts().get("messages"));
        assertEquals(1, result.getCounts().get("respondent_psa"));

        long newRespondentId = newestRespondentId(fixture.accessCode());
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
        assertEquals(2, queryLong("SELECT count(*) FROM survey.answers WHERE respondent_id = ?1 AND question_id = "
                + fixture.questionId() + " AND section_question_id = " + fixture.sectionQuestionId(), newRespondentId),
                "answers re-resolve question and section-question by key + version");

        assertEquals(1, queryLong("SELECT count(*) FROM survey.dependents WHERE respondent_id = ?1 AND relationship_id = "
                + fixture.relationshipId(), newRespondentId), "dependent re-resolves the relationship by key + version");

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
                "insertRespondentPsa must attach the row to the respondent id the import allocated");
        assertEquals(fixture.psaStatus(), newPsa[1]);
    }

    /**
     * UC-012 (BR-059, BR-099, BR-103): a file exported from one instance imports into a second
     * survey that shares the element keys, with every question / section-question / relationship
     * reference resolved to that survey's own rows and nothing pointing back at survey 1.
     */
    @Test
    @TestTransaction
    void importIntoSecondSurveyResolvesElementsByKey() {
        RespondentFixture fixture = persistRespondentTree("XS1");
        // Export first: the file must carry the keys the twin survey is about to share.
        String sourceExport = respondentExportService.exportRespondent(fixture.respondentId());
        Object[] twin = persistSecondSurveySharingElementKeys(fixture, "XS1");
        long twinSurveyId = (Long) twin[0];
        UUID twinSurveyKey = (UUID) twin[1];

        String exported = sourceExport.replace(SURVEY_1_KEY, twinSurveyKey.toString());
        deleteSubjectAndMessages(fixture.respondentId());

        RespondentImportService.ImportResult result = respondentImportService.importFromFile(toStream(exported));

        assertTrue(result.isSuccess(), () -> "import errors: " + result.getErrors());
        long newRespondentId = newestRespondentId(fixture.accessCode());
        assertNotEquals((long) fixture.respondentId(), newRespondentId);
        assertEquals(twinSurveyId, ((Number) respondentState(newRespondentId)[4]).longValue());

        assertEquals(2, queryLong("SELECT count(*) FROM survey.answers WHERE respondent_id = ?1 AND survey_id = "
                + twinSurveyId + " AND question_id = " + twin[2] + " AND section_question_id = " + twin[3], newRespondentId),
                "answers point at the twin survey's question and section-question rows");
        assertEquals(1, queryLong("SELECT count(*) FROM survey.dependents WHERE respondent_id = ?1 AND relationship_id = "
                + twin[4], newRespondentId), "dependent points at the twin survey's relationship row");
        assertEquals(1, queryLong("SELECT count(*) FROM survey.subjects WHERE respondent_id = ?1 AND survey_id = "
                + twinSurveyId, newRespondentId), "subject is attached to the twin survey");
        assertEquals(1, queryLong("SELECT count(*) FROM survey.respondent_psa rp JOIN survey.post_survey_actions pa "
                + "ON pa.id = rp.post_survey_action_id WHERE rp.respondent_id = ?1 AND pa.survey_id = " + twinSurveyId,
                newRespondentId), "PSA resolves by name on the twin survey");
    }

    /**
     * UC-011 (BR-101) / UC-012 (BR-104): a finished, inactive respondent is exported with its
     * finalized_dt, first_access_dt and active flag and comes back in exactly that state.
     */
    @Test
    @TestTransaction
    void finishedRespondentStaysFinishedAfterRoundTrip() {
        RespondentFixture fixture = persistRespondentTree("FIN1");
        em.createNativeQuery("UPDATE survey.respondents SET active = false, "
                        + "first_access_dt = '2026-02-01T09:15:00Z', finalized_dt = '2026-02-03T17:45:30.123456Z' WHERE id = ?1")
                .setParameter(1, fixture.respondentId()).executeUpdate();
        Object[] source = respondentState(fixture.respondentId());

        String exported = respondentExportService.exportRespondent(fixture.respondentId());
        assertTrue(exported.contains("|false|2|"), "active=false is carried in the respondents line");
        deleteSubjectAndMessages(fixture.respondentId());
        renameSourceAccessCode(fixture.respondentId());

        RespondentImportService.ImportResult result = respondentImportService.importFromFile(toStream(exported));

        assertTrue(result.isSuccess(), () -> "import errors: " + result.getErrors());
        Object[] imported = respondentState(newestRespondentId(fixture.accessCode()));
        assertEquals(Boolean.FALSE, imported[0], "active preserved");
        assertEquals(source[1], imported[1], "first_access_dt preserved");
        assertEquals(source[2], imported[2], "finalized_dt preserved");
        assertNotNull(imported[2], "finished respondent stays finished");
    }

    /**
     * UC-011 (BR-101) / UC-012 (BR-104): a respondent who never opened the survey exports empty
     * nullable timestamps (not now()) and is still not-started after import.
     */
    @Test
    @TestTransaction
    void notStartedRespondentKeepsNullTimestamps() {
        RespondentFixture fixture = persistRespondentTree("NS1");
        Object[] source = respondentState(fixture.respondentId());
        assertEquals(null, source[1], "fixture respondent has not accessed the survey");

        String exported = respondentExportService.exportRespondent(fixture.respondentId());
        String respondentsLine = exported.lines().filter(l -> l.startsWith("respondents: ")).findFirst().orElseThrow();
        assertTrue(respondentsLine.endsWith("||"), "first_access_dt and finalized_dt export as empty fields: " + respondentsLine);
        deleteSubjectAndMessages(fixture.respondentId());
        renameSourceAccessCode(fixture.respondentId());

        RespondentImportService.ImportResult result = respondentImportService.importFromFile(toStream(exported));

        assertTrue(result.isSuccess(), () -> "import errors: " + result.getErrors());
        Object[] imported = respondentState(newestRespondentId(fixture.accessCode()));
        assertEquals(Boolean.TRUE, imported[0]);
        assertEquals(null, imported[1], "first_access_dt stays NULL");
        assertEquals(null, imported[2], "finalized_dt stays NULL");
    }

    /**
     * UC-012 A5 (BR-102): a subject whose department code is unknown here aborts the import with
     * the message, the department is never auto-created, and the rollback leaves no respondent
     * behind. Deliberately not {@code @TestTransaction}: the service's own transaction must roll
     * back, and the file needs no fixture beyond the bootstrap-seeded survey 1.
     */
    @Test
    void unknownDepartmentCodeFailsAndInsertsNothing() {
        String accessCode = "DEPT-MISSING-" + UUID.randomUUID();
        String content = "# ELICIT_EXPORT_V2\n\n"
                + "respondents: " + SURVEY_1_KEY + "|" + accessCode + "|true|0|2026-01-01T00:00:00-05:00||\n"
                + "subjects: 0|XID-DM|First|Last||1990-01-15|dm@example.org||NO-SUCH-DEPT|2026-01-01T00:00:00-05:00\n";

        RespondentImportService.ImportValidationException ex = assertThrows(
                RespondentImportService.ImportValidationException.class,
                () -> respondentImportService.importFromFile(toStream(content)));

        assertTrue(ex.getMessage().startsWith("Line 4: "), ex.getMessage());
        assertTrue(ex.getMessage().contains("No department with code 'NO-SUCH-DEPT' exists in this instance; "
                + "create the department before importing"), ex.getMessage());
        assertEquals(0, queryLong("SELECT count(*) FROM survey.respondents WHERE access_code = ?1", accessCode),
                "the import is all-or-nothing: the respondent line must have been rolled back");
        assertEquals(0, queryLong("SELECT count(*) FROM survey.departments WHERE code = ?1", "NO-SUCH-DEPT"),
                "departments are never auto-created");
    }

    /** UC-012 A4 (BR-059): a survey key no survey here carries aborts the import and points at UC-018. */
    @Test
    @TestTransaction
    void unknownSurveyKeyFails() {
        String content = "# ELICIT_EXPORT_V2\n\n"
                + "respondents: 00000000-0000-0000-0000-0000000000ff|NOSURVEY|true|0|2026-01-01T00:00:00-05:00||\n";

        RespondentImportService.ImportValidationException ex = assertThrows(
                RespondentImportService.ImportValidationException.class,
                () -> respondentImportService.importFromFile(toStream(content)));

        assertTrue(ex.getMessage().contains("No survey with survey_key 00000000-0000-0000-0000-0000000000ff exists in this instance; "
                + "apply the survey definition (UC-018) first"), ex.getMessage());
    }

    /** UC-012 A6 (BR-103): a question version this instance does not have aborts the import - no fallback to another version. */
    @Test
    @TestTransaction
    void unknownQuestionVersionFails() {
        RespondentFixture fixture = persistRespondentTree("QV1");
        UUID questionKey = elementKey("questions", "question_key", fixture.questionId());

        String exported = respondentExportService.exportRespondent(fixture.respondentId())
                .replace(questionKey + "|0|", questionKey + "|7|");
        assertTrue(exported.contains(questionKey + "|7|"), "answer lines carry the question key + version");
        deleteSubjectAndMessages(fixture.respondentId());
        renameSourceAccessCode(fixture.respondentId());

        RespondentImportService.ImportValidationException ex = assertThrows(
                RespondentImportService.ImportValidationException.class,
                () -> respondentImportService.importFromFile(toStream(exported)));

        assertTrue(ex.getMessage().contains("No question with key " + questionKey + " version 7 in this instance; "
                + "update the survey definition (UC-017) so the source survey's versions exist here"), ex.getMessage());
    }

    /** UC-012 A7 (BR-105): an access code already on the destination survey is rejected, never regenerated. */
    @Test
    @TestTransaction
    void duplicateAccessCodeIsRejected() {
        RespondentFixture fixture = persistRespondentTree("DUP1");
        String exported = respondentExportService.exportRespondent(fixture.respondentId());
        deleteSubjectAndMessages(fixture.respondentId());

        RespondentImportService.ImportValidationException ex = assertThrows(
                RespondentImportService.ImportValidationException.class,
                () -> respondentImportService.importFromFile(toStream(exported)));

        assertTrue(ex.getMessage().contains("Access code 'DUP1' already exists on survey Test Survey (id 1) in this instance; "
                + "the import was not performed"), ex.getMessage());
    }

    /** UC-012 A8 (BR-106): a legacy V1 file is refused with the re-export instruction, as a failed result, not an exception. */
    @Test
    @TestTransaction
    void legacyV1HeaderReturnsFailedResultWithoutThrowing() {
        String content = "# ELICIT_EXPORT_V1\n# survey_id: 1\n\nrespondents: 1|tok|0|2026-01-01T00:00:00-05:00|\n";

        RespondentImportService.ImportResult result = respondentImportService.importFromFile(toStream(content));

        assertFalse(result.isSuccess());
        assertEquals(0, result.getRecordsImported());
        String error = result.getErrors().get(0);
        assertTrue(error.contains("This file is in the ELICIT_EXPORT_V1 format, which carries identifiers specific to the "
                + "instance that produced it and cannot be imported. Re-export the respondent from the source instance "
                + "(which now produces ELICIT_EXPORT_V2)."), error);
    }

    /** UC-011 A3 (BR-100): a subject whose department has no code cannot be carried portably, so the export is refused. */
    @Test
    @TestTransaction
    void exportFailsWhenDepartmentHasNoCode() {
        RespondentFixture fixture = persistRespondentTree("NOCODE1", null);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> respondentExportService.exportRespondent(fixture.respondentId()));

        assertTrue(ex.getMessage().contains("Department 'RX Dept NOCODE1' (id "), ex.getMessage());
        assertTrue(ex.getMessage().endsWith(") has no code; assign a department code before exporting"), ex.getMessage());
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
        String content = "respondents: " + SURVEY_1_KEY + "|tok|true|0|2026-01-01T00:00:00-05:00||\n";

        RespondentImportService.ImportResult result = respondentImportService.importFromFile(toStream(content));

        assertFalse(result.isSuccess());
        assertTrue(result.getErrors().get(0).contains("valid format header"), result.getErrors().toString());
    }

    /** UC-012: an unrecognized table name is collected as an error but does not abort the import. */
    @Test
    @TestTransaction
    void unknownTableNameIsCollectedAsErrorWithoutThrowing() {
        String content = "# ELICIT_EXPORT_V2\n\nfoobar: 1|2|3\n";

        RespondentImportService.ImportResult result = respondentImportService.importFromFile(toStream(content));

        assertFalse(result.isSuccess());
        assertTrue(result.getErrors().get(0).contains("Unknown table: foobar"), result.getErrors().toString());
    }

    /** UC-012: too few fields on a data line throws, wrapping the field-count IllegalArgumentException. */
    @Test
    @TestTransaction
    void shortFieldCountThrowsRuntimeException() {
        String content = "# ELICIT_EXPORT_V2\n\nrespondents: " + SURVEY_1_KEY + "|tok\n";

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> respondentImportService.importFromFile(toStream(content)));

        assertTrue(ex.getMessage().contains("Respondent requires 7 fields"), ex.getMessage());
    }

    /** UC-012: an answers line before any respondents line is skipped with an error, not thrown. */
    @Test
    @TestTransaction
    void answerBeforeRespondentIsSkippedWithErrorNotThrown() {
        String content = "# ELICIT_EXPORT_V2\n\nanswers: 1|2|3\n";

        RespondentImportService.ImportResult result = respondentImportService.importFromFile(toStream(content));

        assertFalse(result.isSuccess());
        assertEquals(0, result.getCounts().get("answers"));
        assertTrue(result.getErrors().get(0).contains("Cannot insert answer before respondent"), result.getErrors().toString());
    }
}
