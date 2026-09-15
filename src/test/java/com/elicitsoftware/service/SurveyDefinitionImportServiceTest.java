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

import com.elicitsoftware.model.PostSurveyAction;
import com.elicitsoftware.model.ReportDefinition;
import com.elicitsoftware.model.Survey;
import com.elicitsoftware.test.PostgresTestResource;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Booted round-trip and error-path tests for {@link SurveyDefinitionExportService} and
 * {@link SurveyDefinitionImportService}.
 *
 * <p>Traceability: UC-013 (Export Survey Definition) and UC-014 (Import Survey Definition). Both
 * services had zero test coverage before this class. The round-trip test seeds the full
 * cross-module definition tree ({@code select_groups}, {@code select_items}, {@code steps},
 * {@code sections}, {@code steps_sections}, {@code questions}, {@code sections_questions},
 * {@code relationships}, {@code dimensions}, {@code ontology}, {@code metadata}) those services
 * read/write via native SQL, using the real columns/FKs {@code V0.0.0.1__TEST_BOOTSTRAP.sql} now
 * defines for them instead of one-column stubs.</p>
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class SurveyDefinitionImportServiceTest {

    @Inject
    SurveyDefinitionExportService surveyDefinitionExportService;

    @Inject
    SurveyDefinitionImportService surveyDefinitionImportService;

    @Inject
    EntityManager em;

    /** Holds the seeded values a round trip needs to assert against post-import. */
    private record SurveyDefinitionFixture(int sourceSurveyId, java.util.UUID surveyKey, String surveyName,
                                            String stepName, String sectionName, String displayKey, String questionText,
                                            String reportName, String psaName, String ontologyName,
                                            String ontologyTag, String metadataValue) {
    }

    private InputStream toStream(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }

    private long nextVal(String sequence) {
        return ((Number) em.createNativeQuery("SELECT nextval('" + sequence + "')").getSingleResult()).longValue();
    }

    private long queryLong(String sql, Object... params) {
        Query query = em.createNativeQuery(sql);
        for (int i = 0; i < params.length; i++) {
            query.setParameter(i + 1, params[i]);
        }
        return ((Number) query.getSingleResult()).longValue();
    }

    private long insertSelectGroup(int surveyId, String name) {
        long id = nextVal("survey.select_groups_seq");
        em.createNativeQuery("INSERT INTO survey.select_groups (id, survey_id, name, description, data_type) "
                        + "VALUES (?1, ?2, ?3, 'seeded', 'Text')")
                .setParameter(1, id).setParameter(2, surveyId).setParameter(3, name)
                .executeUpdate();
        return queryLong("SELECT select_group_id FROM survey.select_groups WHERE id = ?1", id);
    }

    private void insertSelectItem(int surveyId, long groupId, String displayText, int displayOrder) {
        long id = nextVal("survey.select_items_seq");
        em.createNativeQuery("INSERT INTO survey.select_items (id, survey_id, select_group_id, display_text, display_order, coded_value) "
                        + "VALUES (?1, ?2, ?3, ?4, ?5, 'CODE')")
                .setParameter(1, id).setParameter(2, surveyId).setParameter(3, groupId)
                .setParameter(4, displayText).setParameter(5, displayOrder)
                .executeUpdate();
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

    private long insertStepsSection(int surveyId, long stepId, long sectionId, String displayKey) {
        long id = nextVal("survey.steps_sections_seq");
        em.createNativeQuery("INSERT INTO survey.steps_sections "
                        + "(id, survey_id, step_id, step_display_order, section_id, section_display_order, display_key) "
                        + "VALUES (?1, ?2, ?3, 1, ?4, 1, ?5)")
                .setParameter(1, id).setParameter(2, surveyId).setParameter(3, stepId)
                .setParameter(4, sectionId).setParameter(5, displayKey)
                .executeUpdate();
        return queryLong("SELECT steps_sections_id FROM survey.steps_sections WHERE id = ?1", id);
    }

    private long insertQuestion(int surveyId, long selectGroupId, String text) {
        long id = nextVal("survey.questions_seq");
        em.createNativeQuery("INSERT INTO survey.questions (id, survey_id, type_id, text, select_group_id) "
                        + "VALUES (?1, ?2, 1, ?3, ?4)")
                .setParameter(1, id).setParameter(2, surveyId).setParameter(3, text).setParameter(4, selectGroupId)
                .executeUpdate();
        return queryLong("SELECT question_id FROM survey.questions WHERE id = ?1", id);
    }

    private long insertSectionsQuestion(int surveyId, long questionId, long sectionId) {
        long id = nextVal("survey.sections_questions_seq");
        em.createNativeQuery("INSERT INTO survey.sections_questions (id, survey_id, question_id, section_id, display_order) "
                        + "VALUES (?1, ?2, ?3, ?4, 1)")
                .setParameter(1, id).setParameter(2, surveyId).setParameter(3, questionId).setParameter(4, sectionId)
                .executeUpdate();
        return queryLong("SELECT sections_question_id FROM survey.sections_questions WHERE id = ?1", id);
    }

    /** Minimal relationship: only the required upstream_sq_id/operator/action - downstream_* left
     *  null, which satisfies relationships' downstream_ck CHECK (a NULL sum is not FALSE). */
    private void insertRelationship(int surveyId, long upstreamSqId) {
        long id = nextVal("survey.relationships_seq");
        em.createNativeQuery("INSERT INTO survey.relationships (id, survey_id, upstream_sq_id, operator_id, action_id) "
                        + "VALUES (?1, ?2, ?3, 1, 1)")
                .setParameter(1, id).setParameter(2, surveyId).setParameter(3, upstreamSqId)
                .executeUpdate();
    }

    private long insertDimension(String name) {
        long id = nextVal("survey.dimensions_seq");
        em.createNativeQuery("INSERT INTO survey.dimensions (id, name) VALUES (?1, ?2)")
                .setParameter(1, id).setParameter(2, name)
                .executeUpdate();
        return id;
    }

    private long insertOntology(int surveyId, String name, String tag, long dimensionId) {
        long id = nextVal("survey.ontology_seq");
        em.createNativeQuery("INSERT INTO survey.ontology (id, survey_id, name, tag, dimension) "
                        + "VALUES (?1, ?2, ?3, ?4, ?5)")
                .setParameter(1, id).setParameter(2, surveyId).setParameter(3, name)
                .setParameter(4, tag).setParameter(5, dimensionId)
                .executeUpdate();
        return id;
    }

    private void insertMetadata(int surveyId, long stepsSectionId, long ontologyId, String value) {
        long id = nextVal("survey.metadata_seq");
        em.createNativeQuery("INSERT INTO survey.metadata (id, survey_id, steps_sections_id, ontology_id, value) "
                        + "VALUES (?1, ?2, ?3, ?4, ?5)")
                .setParameter(1, id).setParameter(2, surveyId).setParameter(3, stepsSectionId)
                .setParameter(4, ontologyId).setParameter(5, value)
                .executeUpdate();
    }

    /**
     * Seeds a full survey definition tree on a brand-new survey (not the shared bootstrap
     * survey id=1) covering all fourteen record types the export/import format carries.
     */
    private SurveyDefinitionFixture persistSurveyDefinitionTree(String token) {
        Survey survey = new Survey();
        survey.name = "SD Survey " + token;
        survey.displayOrder = 1000;
        survey.title = "SD Title " + token;
        survey.persist();
        int surveyId = survey.id;

        long groupId = insertSelectGroup(surveyId, "Group " + token);
        insertSelectItem(surveyId, groupId, "Item " + token, 1);

        String stepName = "Step " + token;
        String sectionName = "Section " + token;
        long stepId = insertStep(surveyId, 1, stepName);
        long sectionId = insertSection(surveyId, 1, sectionName);
        String displayKey = "SK-" + token;
        long stepsSectionId = insertStepsSection(surveyId, stepId, sectionId, displayKey);

        String questionText = "Question " + token + "?";
        long questionId = insertQuestion(surveyId, groupId, questionText);
        long sqId = insertSectionsQuestion(surveyId, questionId, sectionId);

        insertRelationship(surveyId, sqId);

        ReportDefinition report = new ReportDefinition();
        report.survey = survey;
        report.name = "Report " + token;
        report.displayOrder = 1;
        report.persist();

        PostSurveyAction psa = new PostSurveyAction();
        psa.survey = survey;
        psa.name = "PSA " + token;
        psa.persistAndFlush();

        String dimensionName = "Dim-" + token;
        long dimensionId = insertDimension(dimensionName);
        String ontologyName = "Ont-" + token;
        String ontologyTag = "Tag-" + token;
        long ontologyId = insertOntology(surveyId, ontologyName, ontologyTag, dimensionId);

        String metadataValue = "Meta-" + token;
        insertMetadata(surveyId, stepsSectionId, ontologyId, metadataValue);

        return new SurveyDefinitionFixture(surveyId, survey.surveyKey, survey.name, stepName, sectionName, displayKey,
                questionText, "Report " + token, "PSA " + token, ontologyName, ontologyTag, metadataValue);
    }

    /**
     * UC-013/UC-014: exporting a full survey definition tree and importing it back creates a
     * brand-new survey (new IDs throughout, {@code display_order} recomputed rather than copied)
     * whose child rows match the original, including FK references resolved across the
     * source_id-&gt;new_id maps (steps_sections -&gt; steps/sections, relationships -&gt;
     * sections_questions, metadata -&gt; steps_sections/ontology).
     */
    @Test
    @TestTransaction
    void exportThenImportRoundTripsAllRecordTypes() {
        SurveyDefinitionFixture fixture = persistSurveyDefinitionTree("SDT1");

        String exported = surveyDefinitionExportService.exportSurvey(fixture.sourceSurveyId());
        assertTrue(exported.contains("# ELICIT_SURVEY_EXPORT_V1"));

        // BR-062 correctly refuses to re-import a survey_key that already exists in this
        // instance — which the source survey's own key always does, since we never delete it.
        // Blanking the key here simulates importing into a genuinely different instance that
        // has never seen this survey before (the file-predates-key-assignment path, BR-061);
        // the duplicate-key rejection itself is covered by importingDuplicateSurveyKeyIsRejected.
        String simulatedFreshInstanceExport = exported.replace(fixture.surveyKey().toString(), "");

        SurveyDefinitionImportService.ImportResult result =
                surveyDefinitionImportService.importFromFile(toStream(simulatedFreshInstanceExport), "test.elicit");

        assertTrue(result.isSuccess(), () -> "import errors: " + result.getErrors());
        assertEquals(1, result.getCounts().get("surveys"));
        assertEquals(1, result.getCounts().get("select_groups"));
        assertEquals(1, result.getCounts().get("select_items"));
        assertEquals(1, result.getCounts().get("steps"));
        assertEquals(1, result.getCounts().get("sections"));
        assertEquals(1, result.getCounts().get("steps_sections"));
        assertEquals(1, result.getCounts().get("questions"));
        assertEquals(1, result.getCounts().get("sections_questions"));
        assertEquals(1, result.getCounts().get("relationships"));
        assertEquals(1, result.getCounts().get("reports"));
        assertEquals(1, result.getCounts().get("post_survey_actions"));
        assertEquals(1, result.getCounts().get("dimensions"));
        assertEquals(1, result.getCounts().get("ontology"));
        assertEquals(1, result.getCounts().get("metadata"));

        long newSurveyId = queryLong(
                "SELECT id FROM survey.surveys WHERE name = ?1 ORDER BY id DESC LIMIT 1", fixture.surveyName());
        assertNotEquals((long) fixture.sourceSurveyId(), newSurveyId,
                "import should create a new survey, not reuse the original");

        assertEquals(1L, queryLong(
                "SELECT count(*) FROM survey.steps WHERE survey_id = ?1 AND name = ?2", newSurveyId, fixture.stepName()));
        assertEquals(1L, queryLong(
                "SELECT count(*) FROM survey.sections WHERE survey_id = ?1 AND name = ?2", newSurveyId, fixture.sectionName()));
        assertEquals(1L, queryLong(
                "SELECT count(*) FROM survey.steps_sections WHERE survey_id = ?1 AND display_key = ?2",
                newSurveyId, fixture.displayKey()));
        assertEquals(1L, queryLong(
                "SELECT count(*) FROM survey.questions WHERE survey_id = ?1 AND text = ?2", newSurveyId, fixture.questionText()));

        long newSqId = queryLong("SELECT id FROM survey.sections_questions WHERE survey_id = ?1", newSurveyId);
        assertEquals(1L, queryLong(
                "SELECT count(*) FROM survey.relationships WHERE survey_id = ?1 AND upstream_sq_id = ?2",
                newSurveyId, newSqId),
                "relationships.upstream_sq_id must resolve to the new sections_questions row, not the old one");

        assertEquals(1L, queryLong(
                "SELECT count(*) FROM survey.reports WHERE survey_id = ?1 AND name = ?2", newSurveyId, fixture.reportName()));
        assertEquals(1L, queryLong(
                "SELECT count(*) FROM survey.post_survey_actions WHERE survey_id = ?1 AND name = ?2",
                newSurveyId, fixture.psaName()));

        long newOntologyId = queryLong(
                "SELECT id FROM survey.ontology WHERE name = ?1 AND tag = ?2", fixture.ontologyName(), fixture.ontologyTag());
        assertEquals(1L, queryLong(
                "SELECT count(*) FROM survey.metadata WHERE survey_id = ?1 AND ontology_id = ?2 AND value = ?3",
                newSurveyId, newOntologyId, fixture.metadataValue()),
                "metadata's step_section_id/ontology_id must resolve to the newly imported rows");

        Integer newDisplayOrder = (Integer) em.createNativeQuery("SELECT display_order FROM survey.surveys WHERE id = ?1")
                .setParameter(1, newSurveyId).getSingleResult();
        assertNotEquals(1000, newDisplayOrder,
                "insertSurvey recomputes display_order as MAX+1 rather than copying the exported value");
    }

    /** UC-013: exporting a nonexistent survey id fails fast rather than returning an empty file. */
    @Test
    @TestTransaction
    void exportSurveyNotFoundThrows() {
        assertThrows(IllegalArgumentException.class, () -> surveyDefinitionExportService.exportSurvey(999999));
    }

    /**
     * BR-062/A4: importing a file whose survey_key already exists in this instance is rejected
     * as a duplicate deployment, directing the administrator to UC-017 (Update) instead. No rows
     * from the file are inserted.
     */
    @Test
    @TestTransaction
    void importingDuplicateSurveyKeyIsRejected() {
        SurveyDefinitionFixture fixture = persistSurveyDefinitionTree("SDT2");
        String exported = surveyDefinitionExportService.exportSurvey(fixture.sourceSurveyId());

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> surveyDefinitionImportService.importFromFile(toStream(exported), "duplicate.elicit"));

        assertTrue(ex.getMessage().contains("use Update instead"), ex.getMessage());
        assertEquals(1L, queryLong("SELECT count(*) FROM survey.surveys WHERE survey_key = ?1", fixture.surveyKey()),
                "the duplicate-key import must not create a second survey row");
    }

    /** UC-014: a file missing the format-version header returns a failed result, not an exception. */
    @Test
    @TestTransaction
    void malformedHeaderReturnsFailedResultWithoutThrowing() {
        String content = "surveys: 1|Name|1|Title|||||\n";

        SurveyDefinitionImportService.ImportResult result = surveyDefinitionImportService.importFromFile(toStream(content), "test.elicit");

        assertFalse(result.isSuccess());
        assertTrue(result.getErrors().get(0).contains("valid format header"), result.getErrors().toString());
    }

    /** UC-014: an unrecognized table name is collected as an error but does not abort the import. */
    @Test
    @TestTransaction
    void unknownTableNameIsCollectedAsErrorWithoutThrowing() {
        String content = "# ELICIT_SURVEY_EXPORT_V1\n\nfoobar: 1|2\n";

        SurveyDefinitionImportService.ImportResult result = surveyDefinitionImportService.importFromFile(toStream(content), "test.elicit");

        assertFalse(result.isSuccess());
        assertTrue(result.getErrors().get(0).contains("Unknown table: foobar"), result.getErrors().toString());
    }

    /**
     * UC-014: a select_items line referencing a group_id never defined by an earlier
     * select_groups line throws, wrapping resolveRequired's IllegalStateException.
     */
    @Test
    @TestTransaction
    void danglingForeignKeyReferenceThrowsRuntimeException() {
        String content = "# ELICIT_SURVEY_EXPORT_V1\n\n"
                + "surveys: 1|11111111-1111-1111-1111-111111111111|Name|1|Title|||||\n\n"
                + "select_items: 1|22222222-2222-2222-2222-222222222222|999|Text|1|CODE||||||\n";

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> surveyDefinitionImportService.importFromFile(toStream(content), "test.elicit"));

        assertTrue(ex.getMessage().contains("No ID mapping found"), ex.getMessage());
    }
}
