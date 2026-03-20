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
 * # survey_name: My Survey
 * surveys: source_id|name|display_order|title|description|initial_display_key|post_survey_url
 * select_groups: source_id|name|description|data_type
 * select_items: source_id|group_id|display_text|display_order|coded_value
 * steps: source_id|display_order|name|dimension_name|description
 * sections: source_id|display_order|name|dimension_name|description
 * steps_sections: source_id|step_id|step_display_order|section_id|section_display_order|display_key
 * questions: source_id|type_id|text|short_text|tool_tip|required|min_value|max_value|validation_text|select_group_id|mask|placeholder|default_value|variant
 * sections_questions: source_id|question_id|section_id|display_order
 * relationships: source_id|upstream_step_id|upstream_sq_id|downstream_step_id|downstream_s_id|downstream_sq_id|operator_id|action_id|description|token|reference_value|default_upstream_value|override_upstream_value
 * reports: source_id|name|description|url|display_order
 * post_survey_actions: source_id|name|description|url|execution_order
 * dimensions: source_id|name
 * ontology: source_id|name|tag|dimension
 * metadata: source_id|step_section_id|question_id|section_question_id|ontology_id|value
 * </pre>
 * <p>
 * The {@code source_id} (first field of every data line) is the original database ID from the
 * exporting system. It is used during import to resolve FK references across tables — new IDs
 * are allocated from sequences in the target system and mapped via source_id.
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
        out.append("# survey_name: ").append(survey[1]).append("\n");
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
        out.append("# generated: ").append(OffsetDateTime.now()).append("\n");
        out.append("\n");

        // surveys: source_id|name|display_order|title|description|initial_display_key|post_survey_url
        out.append("surveys: ");
        out.append(escapeField(survey[0]));                                    // source_id
        out.append(FIELD_DELIMITER).append(escapeField(survey[1]));            // name
        out.append(FIELD_DELIMITER).append(escapeField(survey[2]));            // display_order
        out.append(FIELD_DELIMITER).append(escapeField(survey[3]));            // title
        out.append(FIELD_DELIMITER).append(escapeField(survey[4]));            // description
        out.append(FIELD_DELIMITER).append(escapeField(survey[5]));            // initial_display_key
        out.append(FIELD_DELIMITER).append(escapeField(survey[6]));            // post_survey_url
        out.append("\n");

        // select_groups: source_id|name|description|data_type
        for (Object[] sg : selectGroups) {
            out.append("select_groups: ");
            out.append(escapeField(sg[0]));                                    // source_id
            out.append(FIELD_DELIMITER).append(escapeField(sg[1]));            // name
            out.append(FIELD_DELIMITER).append(escapeField(sg[2]));            // description
            out.append(FIELD_DELIMITER).append(escapeField(sg[3]));            // data_type
            out.append("\n");
        }

        // select_items: source_id|group_id|display_text|display_order|coded_value
        for (Object[] si : selectItems) {
            out.append("select_items: ");
            out.append(escapeField(si[0]));                                    // source_id
            out.append(FIELD_DELIMITER).append(escapeField(si[1]));            // group_id (old)
            out.append(FIELD_DELIMITER).append(escapeField(si[2]));            // display_text
            out.append(FIELD_DELIMITER).append(escapeField(si[3]));            // display_order
            out.append(FIELD_DELIMITER).append(escapeField(si[4]));            // coded_value
            out.append("\n");
        }

        // steps: source_id|display_order|name|dimension_name|description
        for (Object[] step : steps) {
            out.append("steps: ");
            out.append(escapeField(step[0]));                                  // source_id
            out.append(FIELD_DELIMITER).append(escapeField(step[1]));          // display_order
            out.append(FIELD_DELIMITER).append(escapeField(step[2]));          // name
            out.append(FIELD_DELIMITER).append(escapeField(step[3]));          // dimension_name
            out.append(FIELD_DELIMITER).append(escapeField(step[4]));          // description
            out.append("\n");
        }

        // sections: source_id|display_order|name|dimension_name|description
        for (Object[] section : sections) {
            out.append("sections: ");
            out.append(escapeField(section[0]));                               // source_id
            out.append(FIELD_DELIMITER).append(escapeField(section[1]));       // display_order
            out.append(FIELD_DELIMITER).append(escapeField(section[2]));       // name
            out.append(FIELD_DELIMITER).append(escapeField(section[3]));       // dimension_name
            out.append(FIELD_DELIMITER).append(escapeField(section[4]));       // description
            out.append("\n");
        }

        // steps_sections: source_id|step_id|step_display_order|section_id|section_display_order|display_key
        for (Object[] ss : stepsSections) {
            out.append("steps_sections: ");
            out.append(escapeField(ss[0]));                                    // source_id
            out.append(FIELD_DELIMITER).append(escapeField(ss[1]));            // step_id (old)
            out.append(FIELD_DELIMITER).append(escapeField(ss[2]));            // step_display_order
            out.append(FIELD_DELIMITER).append(escapeField(ss[3]));            // section_id (old)
            out.append(FIELD_DELIMITER).append(escapeField(ss[4]));            // section_display_order
            out.append(FIELD_DELIMITER).append(escapeField(ss[5]));            // display_key
            out.append("\n");
        }

        // questions: source_id|type_id|text|short_text|tool_tip|required|min_value|max_value|
        //            validation_text|select_group_id|mask|placeholder|default_value|variant
        for (Object[] q : questions) {
            out.append("questions: ");
            out.append(escapeField(q[0]));                                     // source_id
            out.append(FIELD_DELIMITER).append(escapeField(q[1]));             // type_id (static)
            out.append(FIELD_DELIMITER).append(escapeField(q[2]));             // text
            out.append(FIELD_DELIMITER).append(escapeField(q[3]));             // short_text
            out.append(FIELD_DELIMITER).append(escapeField(q[4]));             // tool_tip
            out.append(FIELD_DELIMITER).append(escapeField(q[5]));             // required
            out.append(FIELD_DELIMITER).append(escapeField(q[6]));             // min_value
            out.append(FIELD_DELIMITER).append(escapeField(q[7]));             // max_value
            out.append(FIELD_DELIMITER).append(escapeField(q[8]));             // validation_text
            out.append(FIELD_DELIMITER).append(escapeField(q[9]));             // select_group_id (old, nullable)
            out.append(FIELD_DELIMITER).append(escapeField(q[10]));            // mask
            out.append(FIELD_DELIMITER).append(escapeField(q[11]));            // placeholder
            out.append(FIELD_DELIMITER).append(escapeField(q[12]));            // default_value
            out.append(FIELD_DELIMITER).append(escapeField(q[13]));            // variant
            out.append("\n");
        }

        // sections_questions: source_id|question_id|section_id|display_order
        for (Object[] sq : sectionsQuestions) {
            out.append("sections_questions: ");
            out.append(escapeField(sq[0]));                                    // source_id
            out.append(FIELD_DELIMITER).append(escapeField(sq[1]));            // question_id (old)
            out.append(FIELD_DELIMITER).append(escapeField(sq[2]));            // section_id (old)
            out.append(FIELD_DELIMITER).append(escapeField(sq[3]));            // display_order
            out.append("\n");
        }

        // relationships: source_id|upstream_step_id|upstream_sq_id|downstream_step_id|
        //                downstream_s_id|downstream_sq_id|operator_id|action_id|
        //                description|token|reference_value|default_upstream_value|override_upstream_value
        for (Object[] rel : relationships) {
            out.append("relationships: ");
            out.append(escapeField(rel[0]));                                   // source_id
            out.append(FIELD_DELIMITER).append(escapeField(rel[1]));           // upstream_step_id (old, nullable)
            out.append(FIELD_DELIMITER).append(escapeField(rel[2]));           // upstream_sq_id (old)
            out.append(FIELD_DELIMITER).append(escapeField(rel[3]));           // downstream_step_id (old, nullable)
            out.append(FIELD_DELIMITER).append(escapeField(rel[4]));           // downstream_s_id (old, nullable)
            out.append(FIELD_DELIMITER).append(escapeField(rel[5]));           // downstream_sq_id (old, nullable)
            out.append(FIELD_DELIMITER).append(escapeField(rel[6]));           // operator_id (static)
            out.append(FIELD_DELIMITER).append(escapeField(rel[7]));           // action_id (static)
            out.append(FIELD_DELIMITER).append(escapeField(rel[8]));           // description
            out.append(FIELD_DELIMITER).append(escapeField(rel[9]));           // token
            out.append(FIELD_DELIMITER).append(escapeField(rel[10]));          // reference_value
            out.append(FIELD_DELIMITER).append(escapeField(rel[11]));          // default_upstream_value
            out.append(FIELD_DELIMITER).append(escapeField(rel[12]));          // override_upstream_value
            out.append("\n");
        }

        // reports: source_id|name|description|url|display_order
        for (Object[] report : reports) {
            out.append("reports: ");
            out.append(escapeField(report[0]));                                // source_id
            out.append(FIELD_DELIMITER).append(escapeField(report[1]));        // name
            out.append(FIELD_DELIMITER).append(escapeField(report[2]));        // description
            out.append(FIELD_DELIMITER).append(escapeField(report[3]));        // url
            out.append(FIELD_DELIMITER).append(escapeField(report[4]));        // display_order
            out.append("\n");
        }

        // post_survey_actions: source_id|name|description|url|execution_order
        for (Object[] psa : postSurveyActions) {
            out.append("post_survey_actions: ");
            out.append(escapeField(psa[0]));                                   // source_id
            out.append(FIELD_DELIMITER).append(escapeField(psa[1]));           // name
            out.append(FIELD_DELIMITER).append(escapeField(psa[2]));           // description
            out.append(FIELD_DELIMITER).append(escapeField(psa[3]));           // url
            out.append(FIELD_DELIMITER).append(escapeField(psa[4]));           // execution_order
            out.append("\n");
        }

        // dimensions: source_id|name
        for (Object[] dim : dimensions) {
            out.append("dimensions: ");
            out.append(escapeField(dim[0]));                                   // source_id
            out.append(FIELD_DELIMITER).append(escapeField(dim[1]));           // name
            out.append("\n");
        }

        // ontology: source_id|name|tag|dimension
        for (Object[] ont : ontology) {
            out.append("ontology: ");
            out.append(escapeField(ont[0]));                                   // source_id
            out.append(FIELD_DELIMITER).append(escapeField(ont[1]));           // name
            out.append(FIELD_DELIMITER).append(escapeField(ont[2]));           // tag
            out.append(FIELD_DELIMITER).append(escapeField(ont[3]));           // dimension (static FK, nullable)
            out.append("\n");
        }

        // metadata: source_id|step_section_id|question_id|section_question_id|ontology_id|value
        for (Object[] meta : metadata) {
            out.append("metadata: ");
            out.append(escapeField(meta[0]));                                  // source_id
            out.append(FIELD_DELIMITER).append(escapeField(meta[1]));          // step_section_id (old, nullable)
            out.append(FIELD_DELIMITER).append(escapeField(meta[2]));          // question_id (old, nullable)
            out.append(FIELD_DELIMITER).append(escapeField(meta[3]));          // section_question_id (old, nullable)
            out.append(FIELD_DELIMITER).append(escapeField(meta[4]));          // ontology_id (old)
            out.append(FIELD_DELIMITER).append(escapeField(meta[5]));          // value
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
                "SELECT id, name, display_order, title, description, initial_display_key, post_survey_url " +
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
                "SELECT id, name, description, data_type " +
                "FROM survey.select_groups WHERE survey_id = :surveyId ORDER BY id");
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
                "SELECT id, group_id, display_text, display_order, coded_value " +
                "FROM survey.select_items WHERE survey_id = :surveyId ORDER BY group_id, display_order");
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
                "SELECT id, display_order, name, dimension_name, description " +
                "FROM survey.steps WHERE survey_id = :surveyId ORDER BY display_order");
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
                "SELECT id, display_order, name, dimension_name, description " +
                "FROM survey.sections WHERE survey_id = :surveyId ORDER BY display_order");
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
                "SELECT id, step_id, step_display_order, section_id, section_display_order, display_key " +
                "FROM survey.steps_sections WHERE survey_id = :surveyId ORDER BY display_key");
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
                "SELECT id, type_id, text, short_text, tool_tip, required, min_value, max_value, " +
                "validation_text, select_group_id, mask, placeholder, default_value, variant " +
                "FROM survey.questions WHERE survey_id = :surveyId ORDER BY id");
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
                "SELECT id, question_id, section_id, display_order " +
                "FROM survey.sections_questions WHERE survey_id = :surveyId ORDER BY id");
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
                "SELECT id, upstream_step_id, upstream_sq_id, downstream_step_id, downstream_s_id, " +
                "downstream_sq_id, operator_id, action_id, description, token, reference_value, " +
                "default_upstream_value, override_upstream_value " +
                "FROM survey.relationships WHERE survey_id = :surveyId ORDER BY id");
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
                "SELECT id, name, description, url, display_order " +
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
                "SELECT id, name, description, url, execution_order " +
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
                "SELECT DISTINCT d.id, d.name " +
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
                "SELECT id, name, tag, dimension " +
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
                "SELECT id, step_section_id, question_id, section_question_id, ontology_id, value " +
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
