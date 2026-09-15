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
import java.util.ArrayList;
import java.util.List;

/**
 * Exports all survey definition tables for a single survey as an importable data file.
 * <p>
 * The following tables are included in the export (in dependency order):
 * surveys, select_groups, select_items, steps, sections, steps_sections,
 * questions, sections_questions, relationships, reports, post_survey_actions,
 * dimensions, ontology, metadata.
 * <p>
 * The export format is a custom text format designed for safe import via parameterized queries:
 * <pre>
 * # ELICIT_SURVEY_EXPORT_V1
 * # survey_id: 5
 * # survey_key: 3fa85f64-5717-4562-b3fc-2c963f66afa6
 * # survey_name: My Survey
 * # survey_revision: 2026-09-15T14:32:07.412-04:00
 * surveys: source_id|survey_key|name|display_order|title|description|initial_display_key|post_survey_url|published_by|published_comment
 * select_groups: source_id|element_key|name|description|data_type|version|effective_from|effective_to|published_by|published_comment|is_draft
 * select_items: source_id|element_key|select_group_id|display_text|display_order|coded_value|version|effective_from|effective_to|published_by|published_comment|is_draft
 * steps: source_id|element_key|display_order|name|dimension_name|description|version|effective_from|effective_to|published_by|published_comment|is_draft
 * sections: source_id|element_key|display_order|name|dimension_name|description|version|effective_from|effective_to|published_by|published_comment|is_draft
 * steps_sections: source_id|element_key|step_id|step_display_order|section_id|section_display_order|display_key|version|effective_from|effective_to|published_by|published_comment|is_draft
 * questions: source_id|element_key|type_id|text|short_text|tool_tip|required|min_value|max_value|validation_text|select_group_id|mask|placeholder|default_value|variant|version|effective_from|effective_to|published_by|published_comment|is_draft
 * sections_questions: source_id|element_key|question_id|section_id|display_order|version|effective_from|effective_to|published_by|published_comment|is_draft
 * relationships: source_id|element_key|upstream_step_id|upstream_sq_id|downstream_step_id|downstream_ss_id|downstream_sq_id|operator_id|action_id|description|token|reference_value|default_upstream_value|override_upstream_value|version|effective_from|effective_to|published_by|published_comment|is_draft
 * reports: source_id|element_key|name|description|url|display_order
 * post_survey_actions: source_id|element_key|name|description|url|execution_order
 * dimensions: source_id|element_key|name
 * ontology: source_id|element_key|name|tag|dimension
 * metadata: source_id|element_key|steps_sections_id|question_id|sections_question_id|ontology_id|value
 * </pre>
 * <p>
 * The {@code source_id} (first field of every data line) is the original database ID from the
 * exporting system, used during import to resolve FK references across tables — new IDs are
 * allocated from sequences in the target system and mapped via source_id. It is per-instance-local
 * and reallocated fresh on every import, whether or not the table is Type 2 versioned (surveys,
 * reports, post_survey_actions, dimensions, ontology, and metadata keep their plain surrogate id,
 * since none of those are Type 2 versioned).
 * <p>
 * {@code survey_key} (second field of the {@code surveys:} line, also duplicated in the header)
 * and {@code element_key} (second field of every other table's line — every table here is part of
 * the survey definition and carries one, Type 2 versioned or not) are different from every other
 * identifier in this format: they are the values that must be preserved verbatim across a
 * create-import rather than remapped, since they are what let two independent deployments of the
 * same authored survey (e.g. two institutions) recognize themselves as "the same survey" — and the
 * same steps/sections/questions/reports/dimensions/etc. within it — for a later update (see
 * {@code SurveyDefinitionUpdateService}). Answers, respondents, subjects, and the audit log are not
 * part of the survey definition and are out of scope for this format entirely. Every other durable
 * key in this file is reallocated fresh per import and has no meaning across separate databases.
 * <p>
 * {@code survey_revision} (a header field, not a per-row one) is this file's revision identifier:
 * the exporting system's timestamp at the moment the file was written. It is the only identifier
 * in the format that is comparable <em>across</em> deployments. The {@code version} column on each
 * Type 2 table is not — those are derived locally by each target instance
 * ({@link SurveyDefinitionUpdateService} inserts {@code targetCurrentVersion + 1}, ignoring
 * whatever the file says), so a site that joined a multi-site study late sits at a lower
 * {@code version} than its peers for byte-identical content. "Which revision of this instrument is
 * this site running?" is answered by {@code survey.survey_log.revision}, which records the
 * revision of every file applied here.
 * <p>
 * Each export mints a fresh revision, including a re-export of a survey that was itself imported.
 * That makes "same revision" mean "same file", so a multi-site study should distribute one
 * exported file to every site rather than re-exporting per site. {@link
 * SurveyDefinitionUpdateService} refuses a file whose revision predates the one already applied
 * at the target, so an out-of-order distribution is caught rather than silently regressing a site.
 * <p>
 * The trailing {@code version|effective_from|effective_to|published_by|published_comment|is_draft}
 * fields on every Type 2 table are exported for informational/audit purposes only — on import,
 * every row is created fresh as the current, non-draft, version 0 row, and these six values are
 * discarded rather than fed into the INSERT (see {@link SurveyDefinitionImportService}). Only
 * current, non-draft rows (i.e. rows that would satisfy each table's own "one current row"
 * partial unique index) are exported in the first place.
 * <p>
 * Field delimiter: | (pipe)<br>
 * Escape sequences: \| for literal pipe, \\ for literal backslash,
 * \n for newline, \r for carriage return<br>
 * Null values: empty string (empty field between pipes)
 *
 * @see SurveyDefinitionImportService
 */
@ApplicationScoped
public class SurveyDefinitionExportService {

    /**
     * Default constructor for CDI.
     */
    public SurveyDefinitionExportService() {
        // CDI managed bean
    }

    static final String FORMAT_VERSION = "ELICIT_SURVEY_EXPORT_V1";
    private static final String FIELD_DELIMITER = "|";

    @Inject
    EntityManager em;

    /**
     * Generates an export file for a single survey definition in the custom Elicit Survey format.
     * <p>
     * All cross-table FK references are preserved as source IDs; the importer resolves them
     * to new IDs in the target system. Static lookup table references (type_id, operator_id,
     * action_id) are exported verbatim on the assumption that the same lookup
     * values exist in the target system.
     *
     * @param surveyId source survey identifier
     * @return export data as a String that can be safely imported via {@link SurveyDefinitionImportService}
     * @throws IllegalArgumentException if the survey is not found
     */
    @Transactional
    public String exportSurvey(Integer surveyId) {
        Object[] survey = getSurvey(surveyId);
        if (survey == null) {
            throw new IllegalArgumentException("Survey not found: " + surveyId);
        }

        List<Object[]> selectGroups = getSelectGroups(surveyId);
        List<Object[]> selectItems = getSelectItems(surveyId);
        List<Object[]> steps = getSteps(surveyId);
        List<Object[]> sections = getSections(surveyId);
        List<Object[]> stepsSections = getStepsSections(surveyId);
        List<Object[]> questions = getQuestions(surveyId);
        List<Object[]> sectionsQuestions = getSectionsQuestions(surveyId);
        List<Object[]> relationships = getRelationships(surveyId);
        List<Object[]> reports = getReports(surveyId);
        List<Object[]> postSurveyActions = getPostSurveyActions(surveyId);
        List<Object[]> dimensions = getDimensions(surveyId);
        List<Object[]> ontology = getOntology(surveyId);
        List<Object[]> metadata = getMetadata(surveyId);

        StringBuilder out = new StringBuilder();

        // Header
        out.append("# ").append(FORMAT_VERSION).append("\n");
        out.append("# survey_id: ").append(surveyId).append("\n");
        out.append("# survey_key: ").append(survey[1]).append("\n");
        out.append("# survey_name: ").append(survey[2]).append("\n");
        out.append("# surveys: 1\n");
        out.append("# select_groups: ").append(selectGroups.size()).append("\n");
        out.append("# select_items: ").append(selectItems.size()).append("\n");
        out.append("# steps: ").append(steps.size()).append("\n");
        out.append("# sections: ").append(sections.size()).append("\n");
        out.append("# steps_sections: ").append(stepsSections.size()).append("\n");
        out.append("# questions: ").append(questions.size()).append("\n");
        out.append("# sections_questions: ").append(sectionsQuestions.size()).append("\n");
        out.append("# relationships: ").append(relationships.size()).append("\n");
        out.append("# reports: ").append(reports.size()).append("\n");
        out.append("# post_survey_actions: ").append(postSurveyActions.size()).append("\n");
        out.append("# dimensions: ").append(dimensions.size()).append("\n");
        out.append("# ontology: ").append(ontology.size()).append("\n");
        out.append("# metadata: ").append(metadata.size()).append("\n");
        OffsetDateTime exportedAt = OffsetDateTime.now();
        out.append("# generated: ").append(exportedAt).append("\n");
        out.append("# survey_revision: ").append(exportedAt).append("\n");
        out.append("\n");

        // surveys: source_id|survey_key|name|display_order|title|description|initial_display_key|post_survey_url|published_by|published_comment
        out.append("surveys: ");
        out.append(escapeField(survey[0]));                                    // source_id
        out.append(FIELD_DELIMITER).append(escapeField(survey[1]));            // survey_key
        out.append(FIELD_DELIMITER).append(escapeField(survey[2]));            // name
        out.append(FIELD_DELIMITER).append(escapeField(survey[3]));            // display_order
        out.append(FIELD_DELIMITER).append(escapeField(survey[4]));            // title
        out.append(FIELD_DELIMITER).append(escapeField(survey[5]));            // description
        out.append(FIELD_DELIMITER).append(escapeField(survey[6]));            // initial_display_key
        out.append(FIELD_DELIMITER).append(escapeField(survey[7]));            // post_survey_url
        out.append(FIELD_DELIMITER).append(escapeField(survey[8]));            // published_by
        out.append(FIELD_DELIMITER).append(escapeField(survey[9]));            // published_comment
        out.append("\n");

        // select_groups: source_id(=select_group_id)|element_key|name|description|data_type|version|effective_from|effective_to|published_by|published_comment|is_draft
        for (Object[] sg : selectGroups) {
            out.append("select_groups: ");
            out.append(escapeField(sg[0]));                                    // source_id (durable select_group_id)
            out.append(FIELD_DELIMITER).append(escapeField(sg[1]));            // element_key (select_group_key)
            out.append(FIELD_DELIMITER).append(escapeField(sg[2]));            // name
            out.append(FIELD_DELIMITER).append(escapeField(sg[3]));            // description
            out.append(FIELD_DELIMITER).append(escapeField(sg[4]));            // data_type
            out.append(FIELD_DELIMITER).append(escapeField(sg[5]));            // version
            out.append(FIELD_DELIMITER).append(escapeField(sg[6]));            // effective_from
            out.append(FIELD_DELIMITER).append(escapeField(sg[7]));            // effective_to
            out.append(FIELD_DELIMITER).append(escapeField(sg[8]));            // published_by
            out.append(FIELD_DELIMITER).append(escapeField(sg[9]));            // published_comment
            out.append(FIELD_DELIMITER).append(escapeField(sg[10]));           // is_draft
            out.append("\n");
        }

        // select_items: source_id(=select_item_id)|element_key|select_group_id(durable)|display_text|display_order|coded_value|version|effective_from|effective_to|published_by|published_comment|is_draft
        for (Object[] si : selectItems) {
            out.append("select_items: ");
            out.append(escapeField(si[0]));                                    // source_id (durable select_item_id)
            out.append(FIELD_DELIMITER).append(escapeField(si[1]));            // element_key (select_item_key)
            out.append(FIELD_DELIMITER).append(escapeField(si[2]));            // select_group_id (durable)
            out.append(FIELD_DELIMITER).append(escapeField(si[3]));            // display_text
            out.append(FIELD_DELIMITER).append(escapeField(si[4]));            // display_order
            out.append(FIELD_DELIMITER).append(escapeField(si[5]));            // coded_value
            out.append(FIELD_DELIMITER).append(escapeField(si[6]));            // version
            out.append(FIELD_DELIMITER).append(escapeField(si[7]));            // effective_from
            out.append(FIELD_DELIMITER).append(escapeField(si[8]));            // effective_to
            out.append(FIELD_DELIMITER).append(escapeField(si[9]));            // published_by
            out.append(FIELD_DELIMITER).append(escapeField(si[10]));           // published_comment
            out.append(FIELD_DELIMITER).append(escapeField(si[11]));           // is_draft
            out.append("\n");
        }

        // steps: source_id(=step_id)|element_key|display_order|name|dimension_name|description|version|effective_from|effective_to|published_by|published_comment|is_draft
        for (Object[] step : steps) {
            out.append("steps: ");
            out.append(escapeField(step[0]));                                  // source_id (durable step_id)
            out.append(FIELD_DELIMITER).append(escapeField(step[1]));          // element_key (step_key)
            out.append(FIELD_DELIMITER).append(escapeField(step[2]));          // display_order
            out.append(FIELD_DELIMITER).append(escapeField(step[3]));          // name
            out.append(FIELD_DELIMITER).append(escapeField(step[4]));          // dimension_name
            out.append(FIELD_DELIMITER).append(escapeField(step[5]));          // description
            out.append(FIELD_DELIMITER).append(escapeField(step[6]));          // version
            out.append(FIELD_DELIMITER).append(escapeField(step[7]));          // effective_from
            out.append(FIELD_DELIMITER).append(escapeField(step[8]));          // effective_to
            out.append(FIELD_DELIMITER).append(escapeField(step[9]));          // published_by
            out.append(FIELD_DELIMITER).append(escapeField(step[10]));         // published_comment
            out.append(FIELD_DELIMITER).append(escapeField(step[11]));         // is_draft
            out.append("\n");
        }

        // sections: source_id(=section_id)|element_key|display_order|name|dimension_name|description|version|effective_from|effective_to|published_by|published_comment|is_draft
        for (Object[] section : sections) {
            out.append("sections: ");
            out.append(escapeField(section[0]));                               // source_id (durable section_id)
            out.append(FIELD_DELIMITER).append(escapeField(section[1]));       // element_key (section_key)
            out.append(FIELD_DELIMITER).append(escapeField(section[2]));       // display_order
            out.append(FIELD_DELIMITER).append(escapeField(section[3]));       // name
            out.append(FIELD_DELIMITER).append(escapeField(section[4]));       // dimension_name
            out.append(FIELD_DELIMITER).append(escapeField(section[5]));       // description
            out.append(FIELD_DELIMITER).append(escapeField(section[6]));       // version
            out.append(FIELD_DELIMITER).append(escapeField(section[7]));       // effective_from
            out.append(FIELD_DELIMITER).append(escapeField(section[8]));       // effective_to
            out.append(FIELD_DELIMITER).append(escapeField(section[9]));       // published_by
            out.append(FIELD_DELIMITER).append(escapeField(section[10]));      // published_comment
            out.append(FIELD_DELIMITER).append(escapeField(section[11]));      // is_draft
            out.append("\n");
        }

        // steps_sections: source_id(=steps_sections_id)|element_key|step_id(durable)|step_display_order|section_id(durable)|section_display_order|display_key|version|effective_from|effective_to|published_by|published_comment|is_draft
        for (Object[] ss : stepsSections) {
            out.append("steps_sections: ");
            out.append(escapeField(ss[0]));                                    // source_id (durable steps_sections_id)
            out.append(FIELD_DELIMITER).append(escapeField(ss[1]));            // element_key (steps_sections_key)
            out.append(FIELD_DELIMITER).append(escapeField(ss[2]));            // step_id (durable)
            out.append(FIELD_DELIMITER).append(escapeField(ss[3]));            // step_display_order
            out.append(FIELD_DELIMITER).append(escapeField(ss[4]));            // section_id (durable)
            out.append(FIELD_DELIMITER).append(escapeField(ss[5]));            // section_display_order
            out.append(FIELD_DELIMITER).append(escapeField(ss[6]));            // display_key
            out.append(FIELD_DELIMITER).append(escapeField(ss[7]));            // version
            out.append(FIELD_DELIMITER).append(escapeField(ss[8]));            // effective_from
            out.append(FIELD_DELIMITER).append(escapeField(ss[9]));            // effective_to
            out.append(FIELD_DELIMITER).append(escapeField(ss[10]));           // published_by
            out.append(FIELD_DELIMITER).append(escapeField(ss[11]));           // published_comment
            out.append(FIELD_DELIMITER).append(escapeField(ss[12]));           // is_draft
            out.append("\n");
        }

        // questions: source_id(=question_id)|type_id|text|short_text|tool_tip|required|min_value|max_value|
        //            validation_text|select_group_id(durable)|mask|placeholder|default_value|variant|
        //            version|effective_from|effective_to|published_by|published_comment|is_draft
        for (Object[] q : questions) {
            out.append("questions: ");
            out.append(escapeField(q[0]));                                     // source_id (durable question_id)
            out.append(FIELD_DELIMITER).append(escapeField(q[1]));             // element_key (question_key)
            out.append(FIELD_DELIMITER).append(escapeField(q[2]));             // type_id (static)
            out.append(FIELD_DELIMITER).append(escapeField(q[3]));             // text
            out.append(FIELD_DELIMITER).append(escapeField(q[4]));             // short_text
            out.append(FIELD_DELIMITER).append(escapeField(q[5]));             // tool_tip
            out.append(FIELD_DELIMITER).append(escapeField(q[6]));             // required
            out.append(FIELD_DELIMITER).append(escapeField(q[7]));             // min_value
            out.append(FIELD_DELIMITER).append(escapeField(q[8]));             // max_value
            out.append(FIELD_DELIMITER).append(escapeField(q[9]));             // validation_text
            out.append(FIELD_DELIMITER).append(escapeField(q[10]));            // select_group_id (durable, nullable)
            out.append(FIELD_DELIMITER).append(escapeField(q[11]));            // mask
            out.append(FIELD_DELIMITER).append(escapeField(q[12]));            // placeholder
            out.append(FIELD_DELIMITER).append(escapeField(q[13]));            // default_value
            out.append(FIELD_DELIMITER).append(escapeField(q[14]));            // variant
            out.append(FIELD_DELIMITER).append(escapeField(q[15]));            // version
            out.append(FIELD_DELIMITER).append(escapeField(q[16]));            // effective_from
            out.append(FIELD_DELIMITER).append(escapeField(q[17]));            // effective_to
            out.append(FIELD_DELIMITER).append(escapeField(q[18]));            // published_by
            out.append(FIELD_DELIMITER).append(escapeField(q[19]));            // published_comment
            out.append(FIELD_DELIMITER).append(escapeField(q[20]));            // is_draft
            out.append("\n");
        }

        // sections_questions: source_id(=sections_question_id)|element_key|question_id(durable)|section_id(durable)|display_order|version|effective_from|effective_to|published_by|published_comment|is_draft
        for (Object[] sq : sectionsQuestions) {
            out.append("sections_questions: ");
            out.append(escapeField(sq[0]));                                    // source_id (durable sections_question_id)
            out.append(FIELD_DELIMITER).append(escapeField(sq[1]));            // element_key (sections_question_key)
            out.append(FIELD_DELIMITER).append(escapeField(sq[2]));            // question_id (durable)
            out.append(FIELD_DELIMITER).append(escapeField(sq[3]));            // section_id (durable)
            out.append(FIELD_DELIMITER).append(escapeField(sq[4]));            // display_order
            out.append(FIELD_DELIMITER).append(escapeField(sq[5]));            // version
            out.append(FIELD_DELIMITER).append(escapeField(sq[6]));            // effective_from
            out.append(FIELD_DELIMITER).append(escapeField(sq[7]));            // effective_to
            out.append(FIELD_DELIMITER).append(escapeField(sq[8]));            // published_by
            out.append(FIELD_DELIMITER).append(escapeField(sq[9]));            // published_comment
            out.append(FIELD_DELIMITER).append(escapeField(sq[10]));           // is_draft
            out.append("\n");
        }

        // relationships: source_id(=relationship_id)|upstream_step_id(durable)|upstream_sq_id(durable)|downstream_step_id(durable)|
        //                downstream_ss_id(durable)|downstream_sq_id(durable)|operator_id|action_id|
        //                description|token|reference_value|default_upstream_value|override_upstream_value|
        //                version|effective_from|effective_to|published_by|published_comment|is_draft
        for (Object[] rel : relationships) {
            out.append("relationships: ");
            out.append(escapeField(rel[0]));                                   // source_id (durable relationship_id)
            out.append(FIELD_DELIMITER).append(escapeField(rel[1]));           // element_key (relationship_key)
            out.append(FIELD_DELIMITER).append(escapeField(rel[2]));           // upstream_step_id (durable, nullable)
            out.append(FIELD_DELIMITER).append(escapeField(rel[3]));           // upstream_sq_id (durable)
            out.append(FIELD_DELIMITER).append(escapeField(rel[4]));           // downstream_step_id (durable, nullable)
            out.append(FIELD_DELIMITER).append(escapeField(rel[5]));           // downstream_ss_id (durable, nullable)
            out.append(FIELD_DELIMITER).append(escapeField(rel[6]));           // downstream_sq_id (durable, nullable)
            out.append(FIELD_DELIMITER).append(escapeField(rel[7]));           // operator_id (static)
            out.append(FIELD_DELIMITER).append(escapeField(rel[8]));           // action_id (static)
            out.append(FIELD_DELIMITER).append(escapeField(rel[9]));           // description
            out.append(FIELD_DELIMITER).append(escapeField(rel[10]));          // token
            out.append(FIELD_DELIMITER).append(escapeField(rel[11]));          // reference_value
            out.append(FIELD_DELIMITER).append(escapeField(rel[12]));          // default_upstream_value
            out.append(FIELD_DELIMITER).append(escapeField(rel[13]));          // override_upstream_value
            out.append(FIELD_DELIMITER).append(escapeField(rel[14]));          // version
            out.append(FIELD_DELIMITER).append(escapeField(rel[15]));          // effective_from
            out.append(FIELD_DELIMITER).append(escapeField(rel[16]));          // effective_to
            out.append(FIELD_DELIMITER).append(escapeField(rel[17]));          // published_by
            out.append(FIELD_DELIMITER).append(escapeField(rel[18]));          // published_comment
            out.append(FIELD_DELIMITER).append(escapeField(rel[19]));          // is_draft
            out.append("\n");
        }

        // reports: source_id|element_key|name|description|url|display_order
        for (Object[] report : reports) {
            out.append("reports: ");
            out.append(escapeField(report[0]));                                // source_id
            out.append(FIELD_DELIMITER).append(escapeField(report[1]));        // element_key (report_key)
            out.append(FIELD_DELIMITER).append(escapeField(report[2]));        // name
            out.append(FIELD_DELIMITER).append(escapeField(report[3]));        // description
            out.append(FIELD_DELIMITER).append(escapeField(report[4]));        // url
            out.append(FIELD_DELIMITER).append(escapeField(report[5]));        // display_order
            out.append("\n");
        }

        // post_survey_actions: source_id|element_key|name|description|url|execution_order
        for (Object[] psa : postSurveyActions) {
            out.append("post_survey_actions: ");
            out.append(escapeField(psa[0]));                                   // source_id
            out.append(FIELD_DELIMITER).append(escapeField(psa[1]));           // element_key (post_survey_action_key)
            out.append(FIELD_DELIMITER).append(escapeField(psa[2]));           // name
            out.append(FIELD_DELIMITER).append(escapeField(psa[3]));           // description
            out.append(FIELD_DELIMITER).append(escapeField(psa[4]));           // url
            out.append(FIELD_DELIMITER).append(escapeField(psa[5]));           // execution_order
            out.append("\n");
        }

        // dimensions: source_id|element_key|name
        for (Object[] dim : dimensions) {
            out.append("dimensions: ");
            out.append(escapeField(dim[0]));                                   // source_id
            out.append(FIELD_DELIMITER).append(escapeField(dim[1]));           // element_key (dimension_key)
            out.append(FIELD_DELIMITER).append(escapeField(dim[2]));           // name
            out.append("\n");
        }

        // ontology: source_id|element_key|name|tag|dimension
        for (Object[] ont : ontology) {
            out.append("ontology: ");
            out.append(escapeField(ont[0]));                                   // source_id
            out.append(FIELD_DELIMITER).append(escapeField(ont[1]));           // element_key (ontology_key)
            out.append(FIELD_DELIMITER).append(escapeField(ont[2]));           // name
            out.append(FIELD_DELIMITER).append(escapeField(ont[3]));           // tag
            out.append(FIELD_DELIMITER).append(escapeField(ont[4]));           // dimension (static FK, nullable)
            out.append("\n");
        }

        // metadata: source_id|element_key|steps_sections_id(durable)|question_id(durable)|sections_question_id(durable)|ontology_id|value
        for (Object[] meta : metadata) {
            out.append("metadata: ");
            out.append(escapeField(meta[0]));                                  // source_id (metadata's own id — not Type 2 versioned)
            out.append(FIELD_DELIMITER).append(escapeField(meta[1]));          // element_key (metadata_key)
            out.append(FIELD_DELIMITER).append(escapeField(meta[2]));          // steps_sections_id (durable, nullable)
            out.append(FIELD_DELIMITER).append(escapeField(meta[3]));          // question_id (durable, nullable)
            out.append(FIELD_DELIMITER).append(escapeField(meta[4]));          // sections_question_id (durable, nullable)
            out.append(FIELD_DELIMITER).append(escapeField(meta[5]));          // ontology_id (old)
            out.append(FIELD_DELIMITER).append(escapeField(meta[6]));          // value
            out.append("\n");
        }

        return out.toString();
    }

    // -------------------------------------------------------------------------
    // Data fetch methods
    // -------------------------------------------------------------------------

    /**
     * Loads the root survey row for export.
     *
     * @param surveyId source survey ID
     * @return one survey row, or {@code null} if not found
     */
    private Object[] getSurvey(Integer surveyId) {
        Query query = em.createNativeQuery(
                "SELECT id, survey_key, name, display_order, title, description, initial_display_key, post_survey_url, " +
                "published_by, published_comment " +
                "FROM survey.surveys WHERE id = :surveyId");
        query.setParameter("surveyId", surveyId);
        List<Object[]> results = toObjectArrayRows(query.getResultList(), "surveys");
        return results.isEmpty() ? null : results.get(0);
    }

    /**
     * Loads select groups belonging to the survey.
     *
     * @param surveyId source survey ID
     * @return ordered rows from {@code survey.select_groups}
     */
    private List<Object[]> getSelectGroups(Integer surveyId) {
        Query query = em.createNativeQuery(
                "SELECT select_group_id, select_group_key, name, description, data_type, " +
                "version, effective_from, effective_to, published_by, published_comment, is_draft " +
                "FROM survey.select_groups " +
                "WHERE survey_id = :surveyId AND effective_to = '9999-12-31 23:59:59+00' AND is_draft = false " +
                "ORDER BY select_group_id");
        query.setParameter("surveyId", surveyId);
        return toObjectArrayRows(query.getResultList(), "select_groups");
    }

    /**
     * Loads select items belonging to the survey.
     *
     * @param surveyId source survey ID
     * @return ordered rows from {@code survey.select_items}
     */
    private List<Object[]> getSelectItems(Integer surveyId) {
        Query query = em.createNativeQuery(
                "SELECT select_item_id, select_item_key, select_group_id, display_text, display_order, coded_value, " +
                "version, effective_from, effective_to, published_by, published_comment, is_draft " +
                "FROM survey.select_items " +
                "WHERE survey_id = :surveyId AND effective_to = '9999-12-31 23:59:59+00' AND is_draft = false " +
                "ORDER BY select_group_id, display_order");
        query.setParameter("surveyId", surveyId);
        return toObjectArrayRows(query.getResultList(), "select_items");
    }

    /**
     * Loads step definitions for the survey.
     *
     * @param surveyId source survey ID
     * @return ordered rows from {@code survey.steps}
     */
    private List<Object[]> getSteps(Integer surveyId) {
        Query query = em.createNativeQuery(
                "SELECT step_id, step_key, display_order, name, dimension_name, description, " +
                "version, effective_from, effective_to, published_by, published_comment, is_draft " +
                "FROM survey.steps " +
                "WHERE survey_id = :surveyId AND effective_to = '9999-12-31 23:59:59+00' AND is_draft = false " +
                "ORDER BY display_order");
        query.setParameter("surveyId", surveyId);
        return toObjectArrayRows(query.getResultList(), "steps");
    }

    /**
     * Loads section definitions for the survey.
     *
     * @param surveyId source survey ID
     * @return ordered rows from {@code survey.sections}
     */
    private List<Object[]> getSections(Integer surveyId) {
        Query query = em.createNativeQuery(
                "SELECT section_id, section_key, display_order, name, dimension_name, description, " +
                "version, effective_from, effective_to, published_by, published_comment, is_draft " +
                "FROM survey.sections " +
                "WHERE survey_id = :surveyId AND effective_to = '9999-12-31 23:59:59+00' AND is_draft = false " +
                "ORDER BY display_order");
        query.setParameter("surveyId", surveyId);
        return toObjectArrayRows(query.getResultList(), "sections");
    }

    /**
     * Loads step-section relationships for the survey.
     *
     * @param surveyId source survey ID
     * @return ordered rows from {@code survey.steps_sections}
     */
    private List<Object[]> getStepsSections(Integer surveyId) {
        Query query = em.createNativeQuery(
                "SELECT steps_sections_id, steps_sections_key, step_id, step_display_order, section_id, section_display_order, display_key, " +
                "version, effective_from, effective_to, published_by, published_comment, is_draft " +
                "FROM survey.steps_sections " +
                "WHERE survey_id = :surveyId AND effective_to = '9999-12-31 23:59:59+00' AND is_draft = false " +
                "ORDER BY display_key");
        query.setParameter("surveyId", surveyId);
        return toObjectArrayRows(query.getResultList(), "steps_sections");
    }

    /**
     * Loads questions for the survey.
     *
     * @param surveyId source survey ID
     * @return ordered rows from {@code survey.questions}
     */
    private List<Object[]> getQuestions(Integer surveyId) {
        Query query = em.createNativeQuery(
                "SELECT question_id, question_key, type_id, text, short_text, tool_tip, required, min_value, max_value, " +
                "validation_text, select_group_id, mask, placeholder, default_value, variant, " +
                "version, effective_from, effective_to, published_by, published_comment, is_draft " +
                "FROM survey.questions " +
                "WHERE survey_id = :surveyId AND effective_to = '9999-12-31 23:59:59+00' AND is_draft = false " +
                "ORDER BY question_id");
        query.setParameter("surveyId", surveyId);
        return toObjectArrayRows(query.getResultList(), "questions");
    }

    /**
     * Loads section-question links for the survey.
     *
     * @param surveyId source survey ID
     * @return ordered rows from {@code survey.sections_questions}
     */
    private List<Object[]> getSectionsQuestions(Integer surveyId) {
        Query query = em.createNativeQuery(
                "SELECT sections_question_id, sections_question_key, question_id, section_id, display_order, " +
                "version, effective_from, effective_to, published_by, published_comment, is_draft " +
                "FROM survey.sections_questions " +
                "WHERE survey_id = :surveyId AND effective_to = '9999-12-31 23:59:59+00' AND is_draft = false " +
                "ORDER BY sections_question_id");
        query.setParameter("surveyId", surveyId);
        return toObjectArrayRows(query.getResultList(), "sections_questions");
    }

    /**
     * Loads relationship rules for the survey.
     *
     * @param surveyId source survey ID
     * @return ordered rows from {@code survey.relationships}
     */
    private List<Object[]> getRelationships(Integer surveyId) {
        Query query = em.createNativeQuery(
                "SELECT relationship_id, relationship_key, upstream_step_id, upstream_sq_id, downstream_step_id, downstream_ss_id, " +
                "downstream_sq_id, operator_id, action_id, description, token, reference_value, " +
                "default_upstream_value, override_upstream_value, " +
                "version, effective_from, effective_to, published_by, published_comment, is_draft " +
                "FROM survey.relationships " +
                "WHERE survey_id = :surveyId AND effective_to = '9999-12-31 23:59:59+00' AND is_draft = false " +
                "ORDER BY relationship_id");
        query.setParameter("surveyId", surveyId);
        return toObjectArrayRows(query.getResultList(), "relationships");
    }

    /**
     * Loads report definitions for the survey.
     *
     * @param surveyId source survey ID
     * @return ordered rows from {@code survey.reports}
     */
    private List<Object[]> getReports(Integer surveyId) {
        Query query = em.createNativeQuery(
                "SELECT id, report_key, name, description, url, display_order " +
                "FROM survey.reports WHERE survey_id = :surveyId ORDER BY display_order");
        query.setParameter("surveyId", surveyId);
        return toObjectArrayRows(query.getResultList(), "reports");
    }

    /**
     * Loads post-survey actions for the survey.
     *
     * @param surveyId source survey ID
     * @return ordered rows from {@code survey.post_survey_actions}
     */
    private List<Object[]> getPostSurveyActions(Integer surveyId) {
        Query query = em.createNativeQuery(
                "SELECT id, post_survey_action_key, name, description, url, execution_order " +
                "FROM survey.post_survey_actions WHERE survey_id = :surveyId ORDER BY execution_order");
        query.setParameter("surveyId", surveyId);
        return toObjectArrayRows(query.getResultList(), "post_survey_actions");
    }

    /**
     * Loads dimension rows referenced by the survey's ontology.
     * Only dimensions that are actually referenced are exported.
     *
     * @param surveyId source survey ID
     * @return ordered rows from {@code survey.dimensions}
     */
    private List<Object[]> getDimensions(Integer surveyId) {
        Query query = em.createNativeQuery(
                "SELECT DISTINCT d.id, d.dimension_key, d.name " +
                "FROM survey.dimensions d " +
                "INNER JOIN survey.ontology o ON o.dimension = d.id " +
                "WHERE o.survey_id = :surveyId ORDER BY d.id");
        query.setParameter("surveyId", surveyId);
        return toObjectArrayRows(query.getResultList(), "dimensions");
    }

    /**
     * Loads ontology rows referenced by the survey.
     *
     * @param surveyId source survey ID
     * @return ordered rows from {@code survey.ontology}
     */
    private List<Object[]> getOntology(Integer surveyId) {
        Query query = em.createNativeQuery(
                "SELECT id, ontology_key, name, tag, dimension " +
                "FROM survey.ontology WHERE survey_id = :surveyId ORDER BY id");
        query.setParameter("surveyId", surveyId);
        return toObjectArrayRows(query.getResultList(), "ontology");
    }

    /**
     * Loads metadata rows for the survey.
     *
     * @param surveyId source survey ID
     * @return ordered rows from {@code survey.metadata}
     */
    private List<Object[]> getMetadata(Integer surveyId) {
        Query query = em.createNativeQuery(
                "SELECT id, metadata_key, steps_sections_id, question_id, sections_question_id, ontology_id, value " +
                "FROM survey.metadata WHERE survey_id = :surveyId ORDER BY id");
        query.setParameter("surveyId", surveyId);
        return toObjectArrayRows(query.getResultList(), "metadata");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Normalizes native-query result rows to {@code Object[]} and validates row shape.
     *
     * @param rawRows raw JPA query results
     * @param queryName logical query name for error messages
     * @return normalized rows
     */
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

    /**
     * Escapes one export field value for pipe-delimited output.
     *
     * @param value field value (nullable)
     * @return escaped string safe for export format
     */
    private String escapeField(Object value) {
        if (value == null) {
            return "";
        }
        String str = value.toString();
        str = str.replace("\\", "\\\\");
        str = str.replace("|", "\\|");
        str = str.replace("\n", "\\n");
        str = str.replace("\r", "\\r");
        return str;
    }
}
