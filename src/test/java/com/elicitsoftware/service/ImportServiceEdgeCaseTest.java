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
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests error and edge-case paths in both import services.
 *
 * All tests use @TestTransaction so DB state is rolled back after each method.
 */
@QuarkusTest
@QuarkusTestResource(PostgreSQLTestResource.class)
class ImportServiceEdgeCaseTest {

    @Inject
    SurveyDefinitionImportService surveyImportService;

    @Inject
    RespondentImportService respondentImportService;

    // -----------------------------------------------------------------------
    // SurveyDefinitionImportService – error paths
    // -----------------------------------------------------------------------

    @Test
    @TestTransaction
    void surveyImportEmptyFileReturnsFalse() {
        SurveyDefinitionImportService.ImportResult result = surveyImportService.importFromFile(
                stream(""));
        assertFalse(result.isSuccess());
    }

    @Test
    @TestTransaction
    void surveyImportNoVersionHeaderReturnsFalse() {
        // File has no version comment at all
        String content = "surveys: 1|MySurvey|1|Title|null|S1|null\n";
        SurveyDefinitionImportService.ImportResult result = surveyImportService.importFromFile(stream(content));
        assertFalse(result.isSuccess());
        assertFalse(result.getErrors().isEmpty());
    }

    @Test
    @TestTransaction
    void surveyImportOnlyCommentsWithNoVersionReturnsFalse() {
        // Has comments but none contains the version string
        String content = "# This is a comment\n# Another comment\n";
        SurveyDefinitionImportService.ImportResult result = surveyImportService.importFromFile(stream(content));
        assertFalse(result.isSuccess());
    }

    @Test
    @TestTransaction
    void surveyImportMissingColonAddsError() {
        // After version header, a data line that lacks ':'
        String content = "# ELICIT_SURVEY_EXPORT_V1\nthislinehasnocolon\n";
        SurveyDefinitionImportService.ImportResult result = surveyImportService.importFromFile(stream(content));
        // Should complete (not fatal) but record an error
        assertFalse(result.getErrors().isEmpty());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("missing colon")));
    }

    @Test
    @TestTransaction
    void surveyImportUnknownTableAddsError() {
        String content = "# ELICIT_SURVEY_EXPORT_V1\nwhatever: 1|foo|bar\n";
        SurveyDefinitionImportService.ImportResult result = surveyImportService.importFromFile(stream(content));
        assertFalse(result.getErrors().isEmpty());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("Unknown table") || e.contains("unknown")));
    }

    @Test
    @TestTransaction
    void surveyImportSelectGroupBeforeSurveyAddsError() {
        // select_groups before survey line should add an error
        String content = "# ELICIT_SURVEY_EXPORT_V1\nselect_groups: 1|GroupName|null\n";
        SurveyDefinitionImportService.ImportResult result = surveyImportService.importFromFile(stream(content));
        assertFalse(result.getErrors().isEmpty());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("Cannot insert select_group before survey")));
    }

    @Test
    @TestTransaction
    void surveyImportStepsBeforeSurveyAddsError() {
        String content = "# ELICIT_SURVEY_EXPORT_V1\nsteps: 1|1|1|Step Name|null|null\n";
        SurveyDefinitionImportService.ImportResult result = surveyImportService.importFromFile(stream(content));
        assertFalse(result.getErrors().isEmpty());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("Cannot insert step before survey")));
    }

    @Test
    @TestTransaction
    void surveyImportSectionsBeforeSurveyAddsError() {
        String content = "# ELICIT_SURVEY_EXPORT_V1\nsections: 1|1|1|Section Name|null|null\n";
        SurveyDefinitionImportService.ImportResult result = surveyImportService.importFromFile(stream(content));
        assertFalse(result.getErrors().isEmpty());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("Cannot insert section before survey")));
    }

    @Test
    @TestTransaction
    void surveyImportRelationshipsBeforeSurveyAddsError() {
        String content = "# ELICIT_SURVEY_EXPORT_V1\nrelationships: 1|1|1|1|1|1|1|1|null\n";
        SurveyDefinitionImportService.ImportResult result = surveyImportService.importFromFile(stream(content));
        assertFalse(result.getErrors().isEmpty());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("Cannot insert relationship before survey")));
    }

    @Test
    @TestTransaction
    void surveyImportReportsBeforeSurveyAddsError() {
        String content = "# ELICIT_SURVEY_EXPORT_V1\nreports: 1|1|ReportName|url\n";
        SurveyDefinitionImportService.ImportResult result = surveyImportService.importFromFile(stream(content));
        assertFalse(result.getErrors().isEmpty());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("Cannot insert report before survey")));
    }

    @Test
    @TestTransaction
    void surveyImportPostSurveyActionsBeforeSurveyAddsError() {
        String content = "# ELICIT_SURVEY_EXPORT_V1\npost_survey_actions: 1|1|url|body\n";
        SurveyDefinitionImportService.ImportResult result = surveyImportService.importFromFile(stream(content));
        assertFalse(result.getErrors().isEmpty());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("Cannot insert post_survey_action before survey")));
    }

    @Test
    @TestTransaction
    void surveyImportOntologyBeforeSurveyAddsError() {
        String content = "# ELICIT_SURVEY_EXPORT_V1\nontology: 1|1|TagName|Code|null\n";
        SurveyDefinitionImportService.ImportResult result = surveyImportService.importFromFile(stream(content));
        assertFalse(result.getErrors().isEmpty());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("Cannot insert ontology before survey")));
    }

    @Test
    @TestTransaction
    void surveyImportMetadataBeforeSurveyAddsError() {
        String content = "# ELICIT_SURVEY_EXPORT_V1\nmetadata: 1|1|1|1|1|Value\n";
        SurveyDefinitionImportService.ImportResult result = surveyImportService.importFromFile(stream(content));
        assertFalse(result.getErrors().isEmpty());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("Cannot insert metadata before survey")));
    }

    // -----------------------------------------------------------------------
    // RespondentImportService – error paths
    // -----------------------------------------------------------------------

    @Test
    @TestTransaction
    void respondentImportEmptyFileReturnsFalse() {
        RespondentImportService.ImportResult result = respondentImportService.importFromFile(stream(""));
        assertFalse(result.isSuccess());
    }

    @Test
    @TestTransaction
    void respondentImportNoVersionHeaderReturnsFalse() {
        String content = "respondents: 1|tok|1|true|0|2024-01-01T00:00:00Z|null|null\n";
        RespondentImportService.ImportResult result = respondentImportService.importFromFile(stream(content));
        assertFalse(result.isSuccess());
        assertFalse(result.getErrors().isEmpty());
    }

    @Test
    @TestTransaction
    void respondentImportOnlyCommentsWithNoVersionReturnsFalse() {
        String content = "# Just a comment without the version token\n";
        RespondentImportService.ImportResult result = respondentImportService.importFromFile(stream(content));
        assertFalse(result.isSuccess());
    }

    @Test
    @TestTransaction
    void respondentImportMissingColonAddsError() {
        String content = "# ELICIT_EXPORT_V1\nthislinehasnocolon\n";
        RespondentImportService.ImportResult result = respondentImportService.importFromFile(stream(content));
        // Missing colon is non-fatal – import continues, error recorded
        assertFalse(result.getErrors().isEmpty());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("missing colon")));
    }

    @Test
    @TestTransaction
    void respondentImportAnswerBeforeRespondentAddsError() {
        String content = "# ELICIT_EXPORT_V1\nanswers: 1|2|1|1|1|1|1|1|1|DK|text|val|false|2024-01-01T00:00:00Z|null\n";
        RespondentImportService.ImportResult result = respondentImportService.importFromFile(stream(content));
        assertFalse(result.getErrors().isEmpty());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("Cannot insert answer before respondent")));
    }

    @Test
    @TestTransaction
    void respondentImportDependentBeforeRespondentAddsError() {
        String content = "# ELICIT_EXPORT_V1\ndependents: DK1|DK2|1|false\n";
        RespondentImportService.ImportResult result = respondentImportService.importFromFile(stream(content));
        assertFalse(result.getErrors().isEmpty());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("Cannot insert dependent before respondent")));
    }

    @Test
    @TestTransaction
    void respondentImportSubjectBeforeRespondentAddsError() {
        String content = "# ELICIT_EXPORT_V1\nsubjects: 0|xid|First|Last|null|null|email|phone|1|1|2024-01-01T00:00:00Z\n";
        RespondentImportService.ImportResult result = respondentImportService.importFromFile(stream(content));
        assertFalse(result.getErrors().isEmpty());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("Cannot insert subject before respondent")));
    }

    @Test
    @TestTransaction
    void respondentImportPsaBeforeRespondentAddsError() {
        String content = "# ELICIT_EXPORT_V1\nrespondent_psa: 1|0|OK|null|2024-01-01T00:00:00Z|null\n";
        RespondentImportService.ImportResult result = respondentImportService.importFromFile(stream(content));
        assertFalse(result.getErrors().isEmpty());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("Cannot insert PSA before respondent")));
    }

    @Test
    @TestTransaction
    void respondentImportUnknownTableAddsError() {
        String content = "# ELICIT_EXPORT_V1\nextrainfo: 1|foo|bar\n";
        RespondentImportService.ImportResult result = respondentImportService.importFromFile(stream(content));
        assertFalse(result.getErrors().isEmpty());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("Unknown table")));
    }

    @Test
    @TestTransaction
    void respondentImportInvalidMessageIndexAddsError() {
        // Message with an invalid subject index (no subjects loaded)
        String content = "# ELICIT_EXPORT_V1\nmessages: 99|1|text/html|Subject|Body|2024-01-01T00:00:00Z|null\n";
        RespondentImportService.ImportResult result = respondentImportService.importFromFile(stream(content));
        assertFalse(result.getErrors().isEmpty());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("Invalid subject index")));
    }

    // -----------------------------------------------------------------------
    // Helper
    // -----------------------------------------------------------------------

    private ByteArrayInputStream stream(String text) {
        return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
    }
}
