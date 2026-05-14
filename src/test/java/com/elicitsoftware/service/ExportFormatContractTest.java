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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * US3: Asserts the exact field count of every section in the V1 export formats
 * without a database. A failure here signals that a section's column list has
 * changed and the import parser must be updated.
 *
 * No @QuarkusTest — no Quarkus startup, no Dev Services container needed.
 */
class ExportFormatContractTest {

    // -----------------------------------------------------------------------
    // Minimal hand-crafted ELICIT_SURVEY_EXPORT_V1 with one row per section
    // -----------------------------------------------------------------------
    private static final String SURVEY_EXPORT =
            "# ELICIT_SURVEY_EXPORT_V1\n" +
            "# survey_id: 1\n" +
            "# survey_name: Test\n" +
            "# surveys: 1\n" +
            "# select_groups: 1\n" +
            "# select_items: 1\n" +
            "# steps: 1\n" +
            "# sections: 1\n" +
            "# steps_sections: 1\n" +
            "# questions: 1\n" +
            "# sections_questions: 1\n" +
            "# relationships: 1\n" +
            "# reports: 1\n" +
            "# post_survey_actions: 1\n" +
            "# dimensions: 1\n" +
            "# ontology: 1\n" +
            "# metadata: 1\n" +
            "# generated: 2026-01-01T00:00:00+00:00\n" +
            "\n" +
            // surveys: source_id|name|display_order|title|description|initial_display_key|post_survey_url (7)
            "surveys: 1|Test Survey|1|Test Title|Test Description|S001|\n" +
            // select_groups: source_id|name|description|data_type (4)
            "select_groups: 1|Group A|Group Description|text\n" +
            // select_items: source_id|group_id|display_text|display_order|coded_value (5)
            "select_items: 1|1|Option A|1|A\n" +
            // steps: source_id|display_order|name|dimension_name|description (5)
            "steps: 1|1|Step One||\n" +
            // sections: source_id|display_order|name|dimension_name|description (5)
            "sections: 1|1|Section One||\n" +
            // steps_sections: source_id|step_id|step_display_order|section_id|section_display_order|display_key (6)
            "steps_sections: 1|1|1|1|1|SS001\n" +
            // questions: source_id|type_id|text|short_text|tool_tip|required|min_value|max_value|validation_text|select_group_id|mask|placeholder|default_value|variant (14)
            "questions: 1|1|Question text|||false||||||||\n" +
            // sections_questions: source_id|question_id|section_id|display_order (4)
            "sections_questions: 1|1|1|1\n" +
            // relationships: source_id|upstream_step_id|upstream_sq_id|downstream_step_id|downstream_s_id|downstream_sq_id|operator_id|action_id|description|token|reference_value|default_upstream_value|override_upstream_value (13)
            "relationships: 1|1|1||||1|1|||||\n" +
            // reports: source_id|name|description|url|display_order (5)
            "reports: 1|Report 1|Report Desc|http://example.com|1\n" +
            // post_survey_actions: source_id|name|description|url|execution_order (5)
            "post_survey_actions: 1|PSA 1|PSA Desc|http://example.com|1\n" +
            // dimensions: source_id|name (2)
            "dimensions: 1|Dimension A\n" +
            // ontology: source_id|name|tag|dimension (4)
            "ontology: 1|Ont A|tag1|Dimension A\n" +
            // metadata: source_id|step_section_id|question_id|section_question_id|ontology_id|value (6)
            "metadata: 1|1|1|1|1|value1\n";

    // Minimal hand-crafted ELICIT_EXPORT_V1 with one answer row
    private static final String RESPONDENT_EXPORT =
            "# ELICIT_EXPORT_V1\n" +
            "# respondent_id: 1\n" +
            "# survey_id: 1\n" +
            "# token: tok1\n" +
            "# answers: 1\n" +
            "# dependents: 0\n" +
            "# subjects: 0\n" +
            "# messages: 0\n" +
            "# respondent_psa: 0\n" +
            "# timezone: UTC\n" +
            "# generated: 2026-01-01T00:00:00+00:00\n" +
            "\n" +
            // respondents: survey_id|token|logins|created_dt|first_access_dt (5)
            "respondents: 1|tok1|0|2026-01-01T00:00:00Z|\n" +
            // answers: survey_id|step|step_instance|section|section_instance|question_display_order|
            //          question_instance|section_question_id|question_id|display_key|display_text|
            //          text_value|deleted|created_dt|saved_dt  (15)
            "answers: 1|1|1|1|1|1|1|1|1|Q1-S1|Question text|answer text|false|2026-01-01T00:00:00Z|2026-01-01T00:00:00Z\n";

    // -----------------------------------------------------------------------
    // Helper: find a data line by section label and return its field array
    // -----------------------------------------------------------------------
    private String[] fieldsFor(String export, String sectionLabel) {
        String prefix = sectionLabel + ": ";
        for (String line : export.split("\n")) {
            if (line.startsWith(prefix)) {
                String fieldData = line.substring(prefix.length());
                return SurveyDefinitionImportService.parseFields(fieldData);
            }
        }
        throw new AssertionError("Section '" + sectionLabel + "' not found in export string");
    }

    // T016: surveys line has 7 fields
    @Test
    void surveysLineHasSevenFields() {
        String[] fields = fieldsFor(SURVEY_EXPORT, "surveys");
        assertEquals(7, fields.length,
                "surveys section must have 7 fields: source_id|name|display_order|title|description|initial_display_key|post_survey_url");
    }

    // T017: steps and sections each have 5 fields
    @Test
    void stepsLineHasFiveFields() {
        assertEquals(5, fieldsFor(SURVEY_EXPORT, "steps").length,
                "steps section must have 5 fields: source_id|display_order|name|dimension_name|description");
    }

    @Test
    void sectionsLineHasFiveFields() {
        assertEquals(5, fieldsFor(SURVEY_EXPORT, "sections").length,
                "sections section must have 5 fields: source_id|display_order|name|dimension_name|description");
    }

    // T018: parameterized field count check for all remaining sections
    static Stream<Arguments> sectionFieldCounts() {
        return Stream.of(
                Arguments.of("select_groups",      4),
                Arguments.of("select_items",        5),
                Arguments.of("steps_sections",      6),
                Arguments.of("questions",           14),
                Arguments.of("sections_questions",  4),
                Arguments.of("relationships",       13),
                Arguments.of("reports",             5),
                Arguments.of("post_survey_actions", 5),
                Arguments.of("dimensions",          2),
                Arguments.of("ontology",            4),
                Arguments.of("metadata",            6)
        );
    }

    @ParameterizedTest(name = "{0} has {1} fields")
    @MethodSource("sectionFieldCounts")
    void sectionHasExpectedFieldCount(String sectionLabel, int expectedCount) {
        String[] fields = fieldsFor(SURVEY_EXPORT, sectionLabel);
        assertEquals(expectedCount, fields.length,
                sectionLabel + " must have " + expectedCount + " fields");
    }

    // T019: answers line has 15 fields (V1 format — no question_version field)
    // This test MUST fail when spec 001 adds question_version, signalling the parser must be updated.
    @Test
    void answersLineHasV1FieldCount() {
        String[] fields = fieldsFor(RESPONDENT_EXPORT, "answers");
        assertEquals(15, fields.length,
                "answers section must have 15 fields in V1 (survey_id through saved_dt, no question_version)");
    }
}
