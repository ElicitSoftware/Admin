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
 * US1: Verifies that exporting a known survey definition and re-importing it
 * produces the identical structure with all FK references intact.
 *
 * Each test method runs in its own @TestTransaction that is rolled back after
 * completion, so tests are fully isolated from each other and from dev-data rows
 * seeded by Flyway migrations.
 */
@QuarkusTest
@QuarkusTestResource(PostgreSQLTestResource.class)
class SurveyDefinitionRoundTripTest {

    @Inject
    SurveyDefinitionExportService exportService;

    @Inject
    SurveyDefinitionImportService importService;

    @Inject
    EntityManager em;

    // -----------------------------------------------------------------------
    // Fixture helper: inserts a minimal survey structure and returns the surveyId
    // -----------------------------------------------------------------------
    private int seedFixture() {
        // Survey
        Number surveyId = (Number) em.createNativeQuery(
                "SELECT nextval('survey.surveys_seq')").getSingleResult();
        em.createNativeQuery(
                "INSERT INTO survey.surveys (id, name, display_order, title, description, initial_display_key, post_survey_url) " +
                "VALUES (?1, ?2, 1, 'RT Title', null, 'S1', null)")
                .setParameter(1, surveyId)
                .setParameter(2, "RT Survey " + surveyId)
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

        // Sections-Questions join 1
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

        // Sections-Questions join 2
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

        // Relationship
        Number relId = (Number) em.createNativeQuery(
                "SELECT nextval('survey.relationships_seq')").getSingleResult();
        em.createNativeQuery(
                "INSERT INTO survey.relationships (id, survey_id, upstream_step_id, upstream_sq_id, " +
                "downstream_step_id, downstream_s_id, downstream_sq_id, operator_id, action_id, " +
                "description, token, reference_value, default_upstream_value, override_upstream_value) " +
                "VALUES (?1, ?2, ?3, ?4, null, null, null, 1, 1, null, null, null, null, null)")
                .setParameter(1, relId)
                .setParameter(2, surveyId)
                .setParameter(3, stepId)
                .setParameter(4, sq1Id)
                .executeUpdate();

        return surveyId.intValue();
    }

    // T007: import result counts match the seeded fixture
    @Test
    @TestTransaction
    void surveyRoundTripPreservesElementCounts() {
        int surveyId = seedFixture();

        String exported = exportService.exportSurvey(surveyId);
        SurveyDefinitionImportService.ImportResult result = importService.importFromFile(
                new ByteArrayInputStream(exported.getBytes(StandardCharsets.UTF_8)));

        assertNotNull(result);
        assertTrue(result.isSuccess(), () -> "Import failed: " + result.getErrors());
        assertEquals(1, result.getCounts().get("surveys"));
        assertEquals(1, result.getCounts().get("steps"));
        assertEquals(1, result.getCounts().get("sections"));
        assertEquals(2, result.getCounts().get("questions"));
        assertEquals(1, result.getCounts().get("steps_sections"));
        assertEquals(2, result.getCounts().get("sections_questions"));
        assertEquals(1, result.getCounts().get("relationships"));
    }

    // T008: first line of the export is the version header
    @Test
    @TestTransaction
    void headerVersionLineIsElicitSurveyExportV1() {
        int surveyId = seedFixture();

        String exported = exportService.exportSurvey(surveyId);
        String[] lines = exported.split("\n");

        assertEquals("# ELICIT_SURVEY_EXPORT_V1", lines[0]);
        // Verify comment count lines match actual body row counts
        assertEquals("# surveys: 1", lines[3]);
        assertEquals("# steps: 1", lines[6]);
        assertEquals("# sections: 1", lines[7]);
        assertEquals("# questions: 2", lines[9]);
        assertEquals("# sections_questions: 2", lines[10]);
        assertEquals("# relationships: 1", lines[11]);
    }

    // T009: special characters in survey name survive a full round-trip
    @Test
    @TestTransaction
    void escapedPipeAndNewlineRestoredAfterImport() {
        Number surveyId = (Number) em.createNativeQuery(
                "SELECT nextval('survey.surveys_seq')").getSingleResult();
        em.createNativeQuery(
                "INSERT INTO survey.surveys (id, name, display_order, title, description, initial_display_key, post_survey_url) " +
                "VALUES (?1, ?2, 1, 'T', null, 'S1', null)")
                .setParameter(1, surveyId)
                .setParameter(2, "a|b\nc")
                .executeUpdate();

        String exported = exportService.exportSurvey(surveyId.intValue());
        SurveyDefinitionImportService.ImportResult result = importService.importFromFile(
                new ByteArrayInputStream(exported.getBytes(StandardCharsets.UTF_8)));
        assertTrue(result.isSuccess(), () -> "Import failed: " + result.getErrors());

        Long importedId = ((Number) em.createNativeQuery(
                "SELECT MAX(id) FROM survey.surveys").getSingleResult()).longValue();
        String importedName = (String) em.createNativeQuery(
                "SELECT name FROM survey.surveys WHERE id = ?1")
                .setParameter(1, importedId)
                .getSingleResult();

        assertEquals("a|b\nc", importedName);
    }

    // T010: FK references in the imported survey all resolve (no orphaned FKs)
    @Test
    @TestTransaction
    void allFkReferencesResolveAfterImport() {
        int surveyId = seedFixture();

        String exported = exportService.exportSurvey(surveyId);
        SurveyDefinitionImportService.ImportResult result = importService.importFromFile(
                new ByteArrayInputStream(exported.getBytes(StandardCharsets.UTF_8)));
        assertTrue(result.isSuccess(), () -> "Import failed: " + result.getErrors());

        Long importedSurveyId = ((Number) em.createNativeQuery(
                "SELECT MAX(id) FROM survey.surveys").getSingleResult()).longValue();

        Number joinCount = (Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM survey.steps_sections ss " +
                "JOIN survey.steps st ON ss.step_id = st.id " +
                "JOIN survey.sections sec ON ss.section_id = sec.id " +
                "WHERE ss.survey_id = ?1")
                .setParameter(1, importedSurveyId)
                .getSingleResult();

        assertEquals(1L, joinCount.longValue());
    }

    // Additional: select_groups, select_items, reports, post_survey_actions,
    // dimensions, ontology and metadata all round-trip correctly.
    @Test
    @TestTransaction
    void selectGroupsItemsReportsAndDimensionsRoundTrip() {
        int surveyId = seedRichFixture();

        String exported = exportService.exportSurvey(surveyId);

        SurveyDefinitionImportService.ImportResult result = importService.importFromFile(
                new ByteArrayInputStream(exported.getBytes(StandardCharsets.UTF_8)));

        assertTrue(result.isSuccess(), () -> "Import failed: " + result.getErrors());
        assertEquals(1, result.getCounts().get("select_groups"),     "select_groups count");
        assertEquals(2, result.getCounts().get("select_items"),      "select_items count");
        assertEquals(1, result.getCounts().get("reports"),           "reports count");
        assertEquals(1, result.getCounts().get("post_survey_actions"), "post_survey_actions count");
        assertEquals(1, result.getCounts().get("dimensions"),        "dimensions count");
        assertEquals(1, result.getCounts().get("ontology"),          "ontology count");
        assertEquals(1, result.getCounts().get("metadata"),          "metadata count");
    }

    /**
     * Seeds a richer fixture that includes select_groups, select_items,
     * reports, post_survey_actions, dimensions, ontology, and metadata.
     */
    private int seedRichFixture() {
        // Survey
        Number surveyId = (Number) em.createNativeQuery(
                "SELECT nextval('survey.surveys_seq')").getSingleResult();
        em.createNativeQuery(
                "INSERT INTO survey.surveys (id, name, display_order, title, description, initial_display_key, post_survey_url) " +
                "VALUES (?1, ?2, 1, 'Rich Survey', null, 'S1', null)")
                .setParameter(1, surveyId)
                .setParameter(2, "Rich Survey " + surveyId)
                .executeUpdate();

        // Step
        Number stepId = (Number) em.createNativeQuery(
                "SELECT nextval('survey.steps_seq')").getSingleResult();
        em.createNativeQuery(
                "INSERT INTO survey.steps (id, survey_id, display_order, name, dimension_name, description) " +
                "VALUES (?1, ?2, 1, 'Rich Step', null, null)")
                .setParameter(1, stepId).setParameter(2, surveyId).executeUpdate();

        // Section
        Number sectionId = (Number) em.createNativeQuery(
                "SELECT nextval('survey.sections_seq')").getSingleResult();
        em.createNativeQuery(
                "INSERT INTO survey.sections (id, survey_id, display_order, name, dimension_name, description) " +
                "VALUES (?1, ?2, 1, 'Rich Section', null, null)")
                .setParameter(1, sectionId).setParameter(2, surveyId).executeUpdate();

        // Steps-Sections
        Number ssId = (Number) em.createNativeQuery(
                "SELECT nextval('survey.steps_sections_seq')").getSingleResult();
        em.createNativeQuery(
                "INSERT INTO survey.steps_sections (id, survey_id, step_id, step_display_order, section_id, section_display_order, display_key) " +
                "VALUES (?1, ?2, ?3, 1, ?4, 1, 'RICH-SS1')")
                .setParameter(1, ssId).setParameter(2, surveyId)
                .setParameter(3, stepId).setParameter(4, sectionId).executeUpdate();

        // Select Group
        Number sgId = (Number) em.createNativeQuery(
                "SELECT nextval('survey.select_groups_seq')").getSingleResult();
        em.createNativeQuery(
                "INSERT INTO survey.select_groups (id, survey_id, name, description, data_type) " +
                "VALUES (?1, ?2, 'YesNo', null, 'Text')")
                .setParameter(1, sgId).setParameter(2, surveyId).executeUpdate();

        // Select Items
        em.createNativeQuery(
                "INSERT INTO survey.select_items (id, survey_id, group_id, display_text, display_order, coded_value) " +
                "VALUES (nextval('survey.select_items_seq'), ?1, ?2, 'Yes', 1, 'Y')")
                .setParameter(1, surveyId).setParameter(2, sgId).executeUpdate();
        em.createNativeQuery(
                "INSERT INTO survey.select_items (id, survey_id, group_id, display_text, display_order, coded_value) " +
                "VALUES (nextval('survey.select_items_seq'), ?1, ?2, 'No', 2, 'N')")
                .setParameter(1, surveyId).setParameter(2, sgId).executeUpdate();

        // Question linked to select group
        Number qId = (Number) em.createNativeQuery(
                "SELECT nextval('survey.questions_seq')").getSingleResult();
        em.createNativeQuery(
                "INSERT INTO survey.questions (id, survey_id, type_id, text, short_text, tool_tip, required, " +
                "min_value, max_value, validation_text, select_group_id, mask, placeholder, default_value, variant) " +
                "VALUES (?1, ?2, 1, 'Q Rich', null, null, false, null, null, null, ?3, null, null, null, null)")
                .setParameter(1, qId).setParameter(2, surveyId).setParameter(3, sgId).executeUpdate();

        // Sections-Questions
        Number sqId = (Number) em.createNativeQuery(
                "SELECT nextval('survey.sections_questions_seq')").getSingleResult();
        em.createNativeQuery(
                "INSERT INTO survey.sections_questions (id, survey_id, question_id, section_id, display_order) " +
                "VALUES (?1, ?2, ?3, ?4, 1)")
                .setParameter(1, sqId).setParameter(2, surveyId)
                .setParameter(3, qId).setParameter(4, sectionId).executeUpdate();

        // Report
        em.createNativeQuery(
                "INSERT INTO survey.reports (id, survey_id, name, description, url, display_order) " +
                "VALUES (nextval('survey.reports_seq'), ?1, 'Rich Report', null, 'https://example.com', 1)")
                .setParameter(1, surveyId).executeUpdate();

        // Post-Survey Action
        em.createNativeQuery(
                "INSERT INTO survey.post_survey_actions (id, survey_id, name, description, url, execution_order) " +
                "VALUES (nextval('survey.post_survey_actions_seq'), ?1, 'Rich Action', null, 'https://hook.example.com', 1)")
                .setParameter(1, surveyId).executeUpdate();

        // Dimension
        Number dimId = (Number) em.createNativeQuery(
                "SELECT nextval('survey.dimensions_seq')").getSingleResult();
        em.createNativeQuery(
                "INSERT INTO survey.dimensions (id, name) VALUES (?1, ?2)")
                .setParameter(1, dimId).setParameter(2, "RichDim " + dimId).executeUpdate();

        // Ontology
        Number ontId = (Number) em.createNativeQuery(
                "SELECT nextval('survey.ontology_seq')").getSingleResult();
        em.createNativeQuery(
                "INSERT INTO survey.ontology (id, survey_id, name, tag, dimension) " +
                "VALUES (?1, ?2, ?3, 'TAG1', ?4)")
                .setParameter(1, ontId).setParameter(2, surveyId)
                .setParameter(3, "RichOnt " + ontId).setParameter(4, dimId).executeUpdate();

        // Metadata (tied to ontology + sections_question)
        em.createNativeQuery(
                "INSERT INTO survey.metadata (id, survey_id, step_section_id, question_id, section_question_id, ontology_id, value) " +
                "VALUES (nextval('survey.metadata_seq'), ?1, ?2, ?3, ?4, ?5, 'MetaVal')")
                .setParameter(1, surveyId).setParameter(2, ssId)
                .setParameter(3, qId).setParameter(4, sqId).setParameter(5, ontId).executeUpdate();

        return surveyId.intValue();
    }
}
