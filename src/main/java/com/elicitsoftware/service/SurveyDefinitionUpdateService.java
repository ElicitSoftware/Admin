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

import com.elicitsoftware.model.Survey;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.transaction.Transactional;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Applies a survey definition file to an <em>existing</em> survey in place — UC-017 (Update
 * Survey Definition), the counterpart to {@link SurveyDefinitionImportService} (UC-014, which
 * always creates a brand-new survey).
 * <p>
 * Every table in the {@code ELICIT_SURVEY_EXPORT_V1} format is part of the survey definition and
 * is updatable here: {@code surveys} itself (Kimball Type 1 — attributes changed in place), the
 * eight Type 2 structural tables ({@code select_groups}, {@code select_items}, {@code steps},
 * {@code sections}, {@code steps_sections}, {@code questions}, {@code sections_questions},
 * {@code relationships} — matched, then either left as-is or version-closed-and-reinserted), and
 * the five remaining Type 1 tables ({@code reports}, {@code post_survey_actions},
 * {@code dimensions}, {@code ontology}, {@code metadata} — matched, then either left as-is or
 * updated in place). Answers, respondents, subjects, and the audit log are not part of the survey
 * definition and are never touched by this service.
 * <p>
 * <strong>Matching:</strong> every row in the file — the survey itself and every one of its
 * children — carries a stable {@code element_key} (a UUID; the survey's own is called
 * {@code survey_key}) that is preserved verbatim across separate deployments (see
 * {@link SurveyDefinitionExportService}). This service matches each file row to the target
 * survey's current row with the same {@code element_key}, never by the file's local {@code
 * source_id} (a per-instance surrogate/durable id that has no meaning in the target database) and
 * never by content. The one exception is {@code dimensions}, a shared global lookup matched by
 * {@code name} exactly as on import (its {@code element_key} is only used when a same-named row
 * doesn't already exist).
 * <p>
 * <strong>Versioning:</strong> for the eight Type 2 tables, an element_key match whose content
 * differs from the file is versioned — the current row's {@code effective_to} is closed to now,
 * and a new row is inserted carrying the <em>same durable id</em> (e.g. {@code step_id}) with
 * {@code version + 1}, {@code effective_from = now()}, and open-ended {@code effective_to}.
 * The durable id is what lets sibling rows keep referencing this
 * element across the version boundary with no remapping. An element_key match with identical
 * content is left completely untouched. An element_key with no match is inserted as a brand-new
 * row (version 0). For the five Type 1 tables (and the survey shell itself), there is no version
 * history — a content difference is a plain in-place {@code UPDATE}.
 * <p>
 * A target row whose {@code element_key} no longer appears anywhere in the file (i.e. the element
 * was removed from the authored copy) is left completely alone — this service never deletes.
 * A record the file marks as <em>retired</em> (closed {@code effective_to}, the authoring tool's
 * removal) has its current row closed here too; nothing is inserted (see {@code retireCurrentRow}).
 * <p>
 * The entire update runs inside one {@code @Transactional} method; any failure rolls back every
 * change made so far (BR-066).
 *
 * @see SurveyDefinitionExportService
 * @see SurveyDefinitionImportService
 */
@ApplicationScoped
public class SurveyDefinitionUpdateService {

    private static final String FORMAT_VERSION = SurveyDefinitionExportService.FORMAT_VERSION;
    private static final String OPEN_ENDED = "9999-12-31 23:59:59+00";

    /**
     * Default constructor for CDI.
     */
    public SurveyDefinitionUpdateService() {
        // CDI managed bean
    }

    @Inject
    EntityManager em;

    @Inject
    SurveyLogService surveyLogService;

    /**
     * How one file row was reconciled against the target survey's current state.
     */
    public enum ChangeType {
        CREATED, VERSIONED, UNCHANGED,
        /** The file marks the record as retired and the target's current row was closed. */
        RETIRED
    }

    /**
     * Per-table breakdown of how many rows were created, versioned (Type 2) or updated in place
     * (Type 1), and left unchanged.
     */
    public record TableUpdateCounts(int created, int versioned, int unchanged, int retired) {
    }

    /**
     * Result of an update operation.
     */
    public static class UpdateResult {
        private final boolean success;
        private final List<String> errors;
        private final Map<String, TableUpdateCounts> counts;

        /**
         * Creates a new UpdateResult.
         *
         * @param success whether the update succeeded
         * @param errors list of error messages
         * @param counts breakdown of created/versioned/unchanged records by table
         */
        public UpdateResult(boolean success, List<String> errors, Map<String, TableUpdateCounts> counts) {
            this.success = success;
            this.errors = errors;
            this.counts = counts;
        }

        /** @return whether the update succeeded */
        public boolean isSuccess() { return success; }

        /** @return the list of error messages from the update attempt */
        public List<String> getErrors() { return errors; }

        /** @return breakdown of created/versioned/unchanged records by table */
        public Map<String, TableUpdateCounts> getCounts() { return counts; }
    }

    /** Accumulates created/versioned/unchanged counts for one table while the file is parsed. */
    private static final class MutableCounts {
        int created;
        int versioned;
        int unchanged;
        int retired;

        void record(ChangeType type) {
            switch (type) {
                case CREATED -> created++;
                case VERSIONED -> versioned++;
                case UNCHANGED -> unchanged++;
                case RETIRED -> retired++;
            }
        }

        TableUpdateCounts toImmutable() {
            return new TableUpdateCounts(created, versioned, unchanged, retired);
        }
    }

    /** One structural row's resolved (target-local) durable id and how it was reconciled. */
    private record UpsertOutcome(Long durableId, ChangeType changeType) {
    }

    /**
     * Applies a survey definition file to an existing survey.
     * <p>
     * Data lines must appear in dependency order as written by the exporter — the same order
     * {@link SurveyDefinitionImportService} expects.
     *
     * <h4>Revision regression</h4>
     * A file whose {@code survey_revision} header predates the newest revision already applied
     * to this survey here is rejected before any row is touched (see
     * {@link #checkRevisionRegression}), with no override. Applying an older file would not
     * "roll back" anything — it would close the current versions and open <em>newer</em> ones
     * carrying older content, which is both a silent content regression and indistinguishable,
     * afterwards, from a deliberate edit. Reverting a deployment is an operational procedure
     * (restore the prior database from backup, redeploy the prior image), not something this
     * write path can express — the same reason the Kimball migration ships no down-migration.
     *
     * @param inputStream the input stream containing the export file
     * @param fileName the uploaded file's original name, for the {@code survey_log} audit row
     * @param targetSurveyId the existing survey this file is being applied to
     * @return UpdateResult with success status, per-table counts, and any errors
     */
    @Transactional
    public UpdateResult updateFromFile(InputStream inputStream, String fileName, Integer targetSurveyId) {
        List<String> errors = new ArrayList<>();
        Map<String, MutableCounts> counts = new LinkedHashMap<>();
        for (String table : List.of("surveys", "select_groups", "select_items", "steps", "sections",
                "steps_sections", "questions", "sections_questions", "relationships",
                "reports", "post_survey_actions", "dimensions", "ontology", "metadata")) {
            counts.put(table, new MutableCounts());
        }

        Survey target = Survey.findById(targetSurveyId);
        if (target == null) {
            errors.add("Target survey not found: " + targetSurveyId);
            // No revision: the target could not be resolved, so the file was never opened.
            surveyLogService.logFailure(null, null, "UPDATE", fileName, String.join("; ", errors), null);
            return new UpdateResult(false, errors, null);
        }

        // Old durable id (as seen in the file) -> resolved target-local durable id.
        Map<Long, Long> selectGroupIdMap = new HashMap<>();
        Map<Long, Long> stepIdMap = new HashMap<>();
        Map<Long, Long> sectionIdMap = new HashMap<>();
        Map<Long, Long> stepsSectionIdMap = new HashMap<>();
        Map<Long, Long> questionIdMap = new HashMap<>();
        Map<Long, Long> sectionsQuestionIdMap = new HashMap<>();
        Map<Long, Long> dimensionIdMap = new HashMap<>();
        Map<Long, Long> ontologyIdMap = new HashMap<>();

        int lineNumber = 0;
        boolean surveyLineSeen = false;
        // Parsed from the "# survey_revision:" header, which always precedes the data lines.
        // The regression check itself is deferred until the surveys: line has confirmed this
        // file actually belongs to the target — otherwise a file for an entirely different
        // survey would be reported as a regression rather than as the key mismatch it is.
        OffsetDateTime fileRevision = null;

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            boolean versionValidated = false;

            while ((line = reader.readLine()) != null) {
                lineNumber++;
                line = line.trim();

                if (line.isEmpty()) {
                    continue;
                }

                if (line.startsWith("#")) {
                    if (line.contains(FORMAT_VERSION)) {
                        versionValidated = true;
                    }
                    try {
                        OffsetDateTime parsed = SurveyDefinitionFileFields.parseRevisionHeader(line);
                        if (parsed != null) {
                            fileRevision = parsed;
                        }
                    } catch (IllegalArgumentException e) {
                        errors.add("Line " + lineNumber + ": " + e.getMessage());
                        logAttempt(target, fileName, false, errors, null, null);
                        return new UpdateResult(false, errors, null);
                    }
                    continue;
                }

                if (!versionValidated) {
                    errors.add("Line " + lineNumber + ": File does not start with valid format header (expected # "
                            + FORMAT_VERSION + ")");
                    logAttempt(target, fileName, false, errors, null, fileRevision);
                    return new UpdateResult(false, errors, null);
                }

                int colonIndex = line.indexOf(':');
                if (colonIndex < 0) {
                    errors.add("Line " + lineNumber + ": Invalid format, missing colon separator");
                    continue;
                }

                String tableName = line.substring(0, colonIndex).trim().toLowerCase();
                String fieldData = line.substring(colonIndex + 1).trim();
                String[] fields = SurveyDefinitionFileFields.parseFields(fieldData);

                try {
                    switch (tableName) {
                        case "surveys": {
                            if (surveyLineSeen) {
                                errors.add("Line " + lineNumber + ": Multiple survey records found; only one supported per file");
                                continue;
                            }
                            surveyLineSeen = true;
                            // A3/A4: a key mismatch or a missing key is a normal rejection (like
                            // the malformed-header check above) — it must return a failed result,
                            // not fall into the generic catch below, which is reserved for A5
                            // (malformed/dangling records) and always aborts via exception.
                            String keyError = matchSurveyKey(fields, target);
                            if (keyError != null) {
                                errors.add("Line " + lineNumber + ": " + keyError);
                                logAttempt(target, fileName, false, errors, null, fileRevision);
                                return new UpdateResult(false, errors, null);
                            }
                            // Only now is this file known to belong to the target, so only now
                            // does comparing its revision against the target's history mean
                            // anything. Nothing has been written yet at this point.
                            String regressionError = checkRevisionRegression(fileRevision, target.surveyKey);
                            if (regressionError != null) {
                                errors.add(regressionError);
                                logAttempt(target, fileName, false, errors, null, fileRevision);
                                return new UpdateResult(false, errors, null);
                            }
                            counts.get("surveys").record(applySurveyAttributes(fields, target));
                            break;
                        }
                        case "select_groups": {
                            UpsertOutcome outcome = SurveyDefinitionFileFields.isRetired("select_groups", fields)
                                    ? retireCurrentRow("survey.select_groups", "select_group_key", "select_group_id", fields, target.id)
                                    : upsertSelectGroup(fields, target.id);
                            selectGroupIdMap.put(SurveyDefinitionFileFields.parseLongOrNull(fields[0]), outcome.durableId());
                            counts.get("select_groups").record(outcome.changeType());
                            break;
                        }
                        case "select_items": {
                            ChangeType outcome = SurveyDefinitionFileFields.isRetired("select_items", fields)
                                    ? retireCurrentRow("survey.select_items", "select_item_key", "select_item_id", fields, target.id).changeType()
                                    : upsertSelectItem(fields, target.id, selectGroupIdMap);
                            counts.get("select_items").record(outcome);
                            break;
                        }
                        case "steps": {
                            UpsertOutcome outcome = SurveyDefinitionFileFields.isRetired("steps", fields)
                                    ? retireCurrentRow("survey.steps", "step_key", "step_id", fields, target.id)
                                    : upsertStep(fields, target.id);
                            stepIdMap.put(SurveyDefinitionFileFields.parseLongOrNull(fields[0]), outcome.durableId());
                            counts.get("steps").record(outcome.changeType());
                            break;
                        }
                        case "sections": {
                            UpsertOutcome outcome = SurveyDefinitionFileFields.isRetired("sections", fields)
                                    ? retireCurrentRow("survey.sections", "section_key", "section_id", fields, target.id)
                                    : upsertSection(fields, target.id);
                            sectionIdMap.put(SurveyDefinitionFileFields.parseLongOrNull(fields[0]), outcome.durableId());
                            counts.get("sections").record(outcome.changeType());
                            break;
                        }
                        case "steps_sections": {
                            UpsertOutcome outcome = SurveyDefinitionFileFields.isRetired("steps_sections", fields)
                                    ? retireCurrentRow("survey.steps_sections", "steps_sections_key", "steps_sections_id", fields, target.id)
                                    : upsertStepsSection(fields, target.id, stepIdMap, sectionIdMap);
                            stepsSectionIdMap.put(SurveyDefinitionFileFields.parseLongOrNull(fields[0]), outcome.durableId());
                            counts.get("steps_sections").record(outcome.changeType());
                            break;
                        }
                        case "questions": {
                            UpsertOutcome outcome = SurveyDefinitionFileFields.isRetired("questions", fields)
                                    ? retireCurrentRow("survey.questions", "question_key", "question_id", fields, target.id)
                                    : upsertQuestion(fields, target.id, selectGroupIdMap);
                            questionIdMap.put(SurveyDefinitionFileFields.parseLongOrNull(fields[0]), outcome.durableId());
                            counts.get("questions").record(outcome.changeType());
                            break;
                        }
                        case "sections_questions": {
                            UpsertOutcome outcome = SurveyDefinitionFileFields.isRetired("sections_questions", fields)
                                    ? retireCurrentRow("survey.sections_questions", "sections_question_key", "sections_question_id", fields, target.id)
                                    : upsertSectionsQuestion(fields, target.id, questionIdMap, sectionIdMap);
                            sectionsQuestionIdMap.put(SurveyDefinitionFileFields.parseLongOrNull(fields[0]), outcome.durableId());
                            counts.get("sections_questions").record(outcome.changeType());
                            break;
                        }
                        case "relationships": {
                            ChangeType outcome = SurveyDefinitionFileFields.isRetired("relationships", fields)
                                    ? retireCurrentRow("survey.relationships", "relationship_key", "relationship_id", fields, target.id).changeType()
                                    : upsertRelationship(fields, target.id, stepIdMap, sectionsQuestionIdMap, stepsSectionIdMap);
                            counts.get("relationships").record(outcome);
                            break;
                        }
                        case "reports": {
                            ChangeType outcome = upsertReport(fields, target.id);
                            counts.get("reports").record(outcome);
                            break;
                        }
                        case "post_survey_actions": {
                            ChangeType outcome = upsertPostSurveyAction(fields, target.id);
                            counts.get("post_survey_actions").record(outcome);
                            break;
                        }
                        case "dimensions": {
                            UpsertOutcome outcome = upsertOrReuseDimension(fields);
                            dimensionIdMap.put(SurveyDefinitionFileFields.parseLongOrNull(fields[0]), outcome.durableId());
                            counts.get("dimensions").record(outcome.changeType());
                            break;
                        }
                        case "ontology": {
                            UpsertOutcome outcome = upsertOrReuseOntology(fields, target.id, dimensionIdMap);
                            ontologyIdMap.put(SurveyDefinitionFileFields.parseLongOrNull(fields[0]), outcome.durableId());
                            counts.get("ontology").record(outcome.changeType());
                            break;
                        }
                        case "metadata": {
                            ChangeType outcome = upsertMetadata(fields, target.id, stepsSectionIdMap, questionIdMap,
                                    sectionsQuestionIdMap, ontologyIdMap);
                            counts.get("metadata").record(outcome);
                            break;
                        }
                        default:
                            errors.add("Line " + lineNumber + ": Unknown table: " + tableName);
                    }
                } catch (Exception e) {
                    errors.add("Line " + lineNumber + ": " + e.getMessage());
                    logAttempt(target, fileName, false, errors, null, fileRevision);
                    throw new RuntimeException("Update failed at line " + lineNumber + ": " + e.getMessage(), e);
                }
            }

            if (!versionValidated) {
                errors.add("File does not contain valid format header");
                logAttempt(target, fileName, false, errors, null, fileRevision);
                return new UpdateResult(false, errors, null);
            }
            if (!surveyLineSeen) {
                errors.add("File does not contain a surveys record");
                logAttempt(target, fileName, false, errors, null, fileRevision);
                return new UpdateResult(false, errors, null);
            }

            Map<String, TableUpdateCounts> immutableCounts = new LinkedHashMap<>();
            counts.forEach((table, c) -> immutableCounts.put(table, c.toImmutable()));
            logAttempt(target, fileName, errors.isEmpty(), errors, immutableCounts, fileRevision);
            return new UpdateResult(errors.isEmpty(), errors, immutableCounts);

        } catch (IOException e) {
            errors.add("Failed to read file: " + e.getMessage());
            logAttempt(target, fileName, false, errors, null, fileRevision);
            return new UpdateResult(false, errors, null);
        }
    }

    /**
     * Records this update attempt to {@code survey.survey_log}. Unlike Import, the target survey
     * always existed before this attempt began, so referencing it by id is safe even on failure
     * (rolling back this attempt does not undo the target's own prior existence) — see
     * {@link SurveyLogService}.
     */
    private void logAttempt(Survey target, String fileName, boolean success,
            List<String> errors, Map<String, TableUpdateCounts> counts, OffsetDateTime revision) {
        if (success) {
            surveyLogService.logSuccess(target.id.longValue(), target.surveyKey, "UPDATE", fileName,
                    String.valueOf(counts), revision);
        } else {
            surveyLogService.logFailure(target.id.longValue(), target.surveyKey, "UPDATE", fileName,
                    String.join("; ", errors), revision);
        }
    }

    /**
     * Refuses a file that would regress this survey to an earlier authored revision.
     * <p>
     * Both "unknowns" deliberately pass rather than block, because neither is evidence of a
     * regression: a file with no {@code survey_revision} header predates the header entirely, and
     * a target with no recorded revision has either never had a file applied here or only ever
     * had pre-header ones. Blocking on those would make the first post-upgrade update impossible.
     * An equal revision also passes — re-applying the identical file is how an operator retries
     * after a partial-looking failure, and every row in it will simply reconcile as UNCHANGED.
     *
     * @param fileRevision the incoming file's revision, or {@code null} if it carries none
     * @param surveyKey the target's cross-deployment stable key
     * @return an error message describing the regression, or {@code null} if the file may proceed
     */
    private String checkRevisionRegression(OffsetDateTime fileRevision, UUID surveyKey) {
        if (fileRevision == null) {
            return null;
        }
        OffsetDateTime applied = surveyLogService.findLatestAppliedRevision(surveyKey);
        if (applied == null || !fileRevision.isBefore(applied)) {
            return null;
        }
        return "This file's revision (" + fileRevision + ") predates the newest revision already "
                + "applied to this survey here (" + applied + "). Applying it would open new "
                + "versions carrying older content rather than reverting anything. Apply the "
                + "newer file instead; reverting this deployment to the earlier revision is an "
                + "operational restore (prior database backup plus the prior image), not an update.";
    }

    // -------------------------------------------------------------------------
    // Survey shell (BR-063/BR-064/BR-065)
    // -------------------------------------------------------------------------

    /**
     * BR-063/A3/A4: the file's stable survey key must equal the target's. BR-065: the target's
     * key and database id are never touched by this comparison. A key mismatch or a missing key
     * is a normal rejection, reported back as an error message (not thrown) — only a wrong field
     * count (a malformed record, A5) throws. Fields: source_id|survey_key|name|display_order|
     * title|description|initial_display_key|post_survey_url|published_by|published_comment
     *
     * @return an error message describing why the key doesn't match, or {@code null} if it does
     */
    private String matchSurveyKey(String[] fields, Survey target) {
        if (fields.length < 10) {
            throw new IllegalArgumentException("surveys requires 10 fields, got " + fields.length);
        }
        String rawKey = SurveyDefinitionFileFields.nullIfEmpty(fields[1]);
        if (rawKey == null) {
            return "This file predates stable-key assignment and carries no survey_key to match against — "
                    + "import it as a new survey (UC-014), or re-export the target survey (UC-013) to see its "
                    + "current key before retrying.";
        }
        UUID fileKey;
        try {
            fileKey = UUID.fromString(rawKey.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid survey_key in file: " + rawKey, e);
        }
        if (!fileKey.equals(target.surveyKey)) {
            return "This file's survey_key (" + fileKey
                    + ") does not match the selected survey's key (" + target.surveyKey
                    + "); it does not belong to survey " + target.id + ".";
        }
        return null;
    }

    /**
     * BR-064: updates the survey's own Type 1 attributes in place. {@code display_order} is
     * deliberately left untouched — the target's position among surveys in this instance is a
     * per-deployment concern, not part of the authored definition. {@code id} and
     * {@code survey_key} are never written here.
     */
    private ChangeType applySurveyAttributes(String[] fields, Survey target) {
        String name = SurveyDefinitionFileFields.nullIfEmpty(fields[2]);
        String title = SurveyDefinitionFileFields.nullIfEmpty(fields[4]);
        String description = SurveyDefinitionFileFields.nullIfEmpty(fields[5]);
        // Rebased before the comparison below, not after: the stored key already carries this
        // deployment's survey id, so comparing it against the file's raw key would report a
        // difference on every update and re-version the survey each time.
        String initialDisplayKey = SurveyDefinitionFileFields.rebaseDisplayKey(fields[6], target.id);
        String postSurveyUrl = SurveyDefinitionFileFields.nullIfEmpty(fields[7]);
        String publishedBy = SurveyDefinitionFileFields.nullIfEmpty(fields[8]);
        String publishedComment = SurveyDefinitionFileFields.nullIfEmpty(fields[9]);

        boolean unchanged = Objects.equals(name, target.name)
                && Objects.equals(title, target.title)
                && Objects.equals(description, target.description)
                && Objects.equals(initialDisplayKey, target.initialDisplayKey)
                && Objects.equals(postSurveyUrl, target.postSurveyURL);
        // published_by/published_comment are not modeled on the Survey entity's current fields
        // (kept NULL by import); an update file is the one path that legitimately sets them.

        Query query = em.createNativeQuery("""
                UPDATE survey.surveys
                SET name = ?1, title = ?2, description = ?3, initial_display_key = ?4,
                    post_survey_url = ?5, published_by = ?6, published_comment = ?7
                WHERE id = ?8
                """);
        query.setParameter(1, name);
        query.setParameter(2, title);
        query.setParameter(3, description);
        query.setParameter(4, initialDisplayKey);
        query.setParameter(5, postSurveyUrl);
        query.setParameter(6, publishedBy);
        query.setParameter(7, publishedComment);
        query.setParameter(8, target.id);
        query.executeUpdate();

        return unchanged ? ChangeType.UNCHANGED : ChangeType.VERSIONED;
    }

    // -------------------------------------------------------------------------
    // Type 2 structural tables — matched by element_key, closed+reinserted on change
    // -------------------------------------------------------------------------

    /**
     * Fields: source_id|element_key|name|description|data_type|version|effective_from|effective_to|published_by|published_comment
     */
    private UpsertOutcome upsertSelectGroup(String[] fields, Integer surveyId) {
        if (fields.length < 10) {
            throw new IllegalArgumentException("select_groups requires 10 fields, got " + fields.length);
        }
        UUID elementKey = requireElementKey(fields[1], "select_groups");
        String name = SurveyDefinitionFileFields.nullIfEmpty(fields[2]);
        String description = SurveyDefinitionFileFields.nullIfEmpty(fields[3]);
        String dataType = SurveyDefinitionFileFields.nullIfEmpty(fields[4]);
        if (dataType == null) {
            dataType = "Text";
        }

        Object[] current = findCurrentRow("survey.select_groups", "select_group_key", elementKey, surveyId,
                "id, select_group_id, version, name, description, data_type");
        if (current == null) {
            Long newId = nextval("survey.select_groups_seq");
            em.createNativeQuery("""
                    INSERT INTO survey.select_groups (id, survey_id, select_group_key, name, description, data_type)
                    VALUES (?1, ?2, ?3, ?4, ?5, ?6)
                    """)
                    .setParameter(1, newId).setParameter(2, surveyId).setParameter(3, elementKey)
                    .setParameter(4, name).setParameter(5, description).setParameter(6, dataType)
                    .executeUpdate();
            return new UpsertOutcome(SurveyDefinitionFileFields.getDurableId(em, "select_group_id", "survey.select_groups", newId), ChangeType.CREATED);
        }

        Long oldSurrogateId = ((Number) current[0]).longValue();
        Long durableId = ((Number) current[1]).longValue();
        int oldVersion = ((Number) current[2]).intValue();
        boolean unchanged = Objects.equals(name, current[3]) && Objects.equals(description, current[4])
                && Objects.equals(dataType, current[5]);
        if (unchanged) {
            return new UpsertOutcome(durableId, ChangeType.UNCHANGED);
        }

        closeCurrentVersion("survey.select_groups", oldSurrogateId);
        Long newId = nextval("survey.select_groups_seq");
        em.createNativeQuery("""
                INSERT INTO survey.select_groups
                    (id, survey_id, select_group_key, name, description, data_type,
                     select_group_id, version, effective_from, effective_to)
                VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, NOW(), '9999-12-31 23:59:59+00')
                """)
                .setParameter(1, newId).setParameter(2, surveyId).setParameter(3, elementKey)
                .setParameter(4, name).setParameter(5, description).setParameter(6, dataType)
                .setParameter(7, durableId).setParameter(8, oldVersion + 1)
                .executeUpdate();
        return new UpsertOutcome(durableId, ChangeType.VERSIONED);
    }

    /**
     * Fields: source_id|element_key|select_group_id|display_text|display_order|coded_value|version|effective_from|effective_to|published_by|published_comment
     */
    private ChangeType upsertSelectItem(String[] fields, Integer surveyId, Map<Long, Long> selectGroupIdMap) {
        if (fields.length < 11) {
            throw new IllegalArgumentException("select_items requires 11 fields, got " + fields.length);
        }
        UUID elementKey = requireElementKey(fields[1], "select_items");
        Long newGroupId = SurveyDefinitionFileFields.resolveRequired(
                SurveyDefinitionFileFields.parseLongOrNull(fields[2]), selectGroupIdMap, "select_group (for select_item)");
        String displayText = SurveyDefinitionFileFields.nullIfEmpty(fields[3]);
        Integer displayOrder = SurveyDefinitionFileFields.parseIntOrNull(fields[4]);
        String codedValue = SurveyDefinitionFileFields.nullIfEmpty(fields[5]);

        Object[] current = findCurrentRow("survey.select_items", "select_item_key", elementKey, surveyId,
                "id, version, select_group_id, display_text, display_order, coded_value");
        if (current == null) {
            em.createNativeQuery("""
                    INSERT INTO survey.select_items
                        (id, survey_id, select_item_key, select_group_id, display_text, display_order, coded_value)
                    VALUES (nextval('survey.select_items_seq'), ?1, ?2, ?3, ?4, ?5, ?6)
                    """)
                    .setParameter(1, surveyId).setParameter(2, elementKey).setParameter(3, newGroupId)
                    .setParameter(4, displayText).setParameter(5, displayOrder).setParameter(6, codedValue)
                    .executeUpdate();
            return ChangeType.CREATED;
        }

        Long oldSurrogateId = ((Number) current[0]).longValue();
        int oldVersion = ((Number) current[1]).intValue();
        boolean unchanged = Objects.equals(newGroupId, toLong(current[2])) && Objects.equals(displayText, current[3])
                && Objects.equals(displayOrder, current[4]) && Objects.equals(codedValue, current[5]);
        if (unchanged) {
            return ChangeType.UNCHANGED;
        }

        closeCurrentVersion("survey.select_items", oldSurrogateId);
        em.createNativeQuery("""
                INSERT INTO survey.select_items
                    (id, survey_id, select_item_key, select_group_id, display_text, display_order, coded_value,
                     select_item_id, version, effective_from, effective_to)
                VALUES (nextval('survey.select_items_seq'), ?1, ?2, ?3, ?4, ?5, ?6,
                        (SELECT select_item_id FROM survey.select_items WHERE id = ?7),
                        ?8, NOW(), '9999-12-31 23:59:59+00')
                """)
                .setParameter(1, surveyId).setParameter(2, elementKey).setParameter(3, newGroupId)
                .setParameter(4, displayText).setParameter(5, displayOrder).setParameter(6, codedValue)
                .setParameter(7, oldSurrogateId).setParameter(8, oldVersion + 1)
                .executeUpdate();
        return ChangeType.VERSIONED;
    }

    /**
     * Fields: source_id|element_key|display_order|name|dimension_name|description|version|effective_from|effective_to|published_by|published_comment
     */
    private UpsertOutcome upsertStep(String[] fields, Integer surveyId) {
        if (fields.length < 11) {
            throw new IllegalArgumentException("steps requires 11 fields, got " + fields.length);
        }
        UUID elementKey = requireElementKey(fields[1], "steps");
        Integer displayOrder = SurveyDefinitionFileFields.parseIntOrNull(fields[2]);
        String name = SurveyDefinitionFileFields.nullIfEmpty(fields[3]);
        String dimensionName = SurveyDefinitionFileFields.nullIfEmpty(fields[4]);
        // Default dimension_name to step name if empty (NOT NULL constraint). Done before the
        // unchanged-comparison below so a re-uploaded file matches the defaulted stored value
        // instead of versioning the row on every apply.
        if (dimensionName == null) {
            dimensionName = name != null ? name : "";
        }
        String description = SurveyDefinitionFileFields.nullIfEmpty(fields[5]);

        Object[] current = findCurrentRow("survey.steps", "step_key", elementKey, surveyId,
                "id, step_id, version, display_order, name, dimension_name, description");
        if (current == null) {
            Long newId = nextval("survey.steps_seq");
            em.createNativeQuery("""
                    INSERT INTO survey.steps (id, survey_id, step_key, display_order, name, dimension_name, description)
                    VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7)
                    """)
                    .setParameter(1, newId).setParameter(2, surveyId).setParameter(3, elementKey)
                    .setParameter(4, displayOrder).setParameter(5, name).setParameter(6, dimensionName)
                    .setParameter(7, description).executeUpdate();
            return new UpsertOutcome(SurveyDefinitionFileFields.getDurableId(em, "step_id", "survey.steps", newId), ChangeType.CREATED);
        }

        Long oldSurrogateId = ((Number) current[0]).longValue();
        Long durableId = ((Number) current[1]).longValue();
        int oldVersion = ((Number) current[2]).intValue();
        boolean unchanged = Objects.equals(displayOrder, current[3]) && Objects.equals(name, current[4])
                && Objects.equals(dimensionName, current[5]) && Objects.equals(description, current[6]);
        if (unchanged) {
            return new UpsertOutcome(durableId, ChangeType.UNCHANGED);
        }

        closeCurrentVersion("survey.steps", oldSurrogateId);
        Long newId = nextval("survey.steps_seq");
        em.createNativeQuery("""
                INSERT INTO survey.steps
                    (id, survey_id, step_key, display_order, name, dimension_name, description,
                     step_id, version, effective_from, effective_to)
                VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, NOW(), '9999-12-31 23:59:59+00')
                """)
                .setParameter(1, newId).setParameter(2, surveyId).setParameter(3, elementKey)
                .setParameter(4, displayOrder).setParameter(5, name).setParameter(6, dimensionName)
                .setParameter(7, description).setParameter(8, durableId).setParameter(9, oldVersion + 1)
                .executeUpdate();
        return new UpsertOutcome(durableId, ChangeType.VERSIONED);
    }

    /**
     * Fields: source_id|element_key|display_order|name|dimension_name|description|version|effective_from|effective_to|published_by|published_comment
     */
    private UpsertOutcome upsertSection(String[] fields, Integer surveyId) {
        if (fields.length < 11) {
            throw new IllegalArgumentException("sections requires 11 fields, got " + fields.length);
        }
        UUID elementKey = requireElementKey(fields[1], "sections");
        Integer displayOrder = SurveyDefinitionFileFields.parseIntOrNull(fields[2]);
        String name = SurveyDefinitionFileFields.nullIfEmpty(fields[3]);
        String dimensionName = SurveyDefinitionFileFields.nullIfEmpty(fields[4]);
        if (dimensionName == null) {
            dimensionName = name != null ? name : "";
        }
        String description = SurveyDefinitionFileFields.nullIfEmpty(fields[5]);

        Object[] current = findCurrentRow("survey.sections", "section_key", elementKey, surveyId,
                "id, section_id, version, display_order, name, dimension_name, description");
        if (current == null) {
            Long newId = nextval("survey.sections_seq");
            em.createNativeQuery("""
                    INSERT INTO survey.sections (id, survey_id, section_key, display_order, name, dimension_name, description)
                    VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7)
                    """)
                    .setParameter(1, newId).setParameter(2, surveyId).setParameter(3, elementKey)
                    .setParameter(4, displayOrder).setParameter(5, name).setParameter(6, dimensionName)
                    .setParameter(7, description).executeUpdate();
            return new UpsertOutcome(SurveyDefinitionFileFields.getDurableId(em, "section_id", "survey.sections", newId), ChangeType.CREATED);
        }

        Long oldSurrogateId = ((Number) current[0]).longValue();
        Long durableId = ((Number) current[1]).longValue();
        int oldVersion = ((Number) current[2]).intValue();
        boolean unchanged = Objects.equals(displayOrder, current[3]) && Objects.equals(name, current[4])
                && Objects.equals(dimensionName, current[5]) && Objects.equals(description, current[6]);
        if (unchanged) {
            return new UpsertOutcome(durableId, ChangeType.UNCHANGED);
        }

        closeCurrentVersion("survey.sections", oldSurrogateId);
        Long newId = nextval("survey.sections_seq");
        em.createNativeQuery("""
                INSERT INTO survey.sections
                    (id, survey_id, section_key, display_order, name, dimension_name, description,
                     section_id, version, effective_from, effective_to)
                VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, NOW(), '9999-12-31 23:59:59+00')
                """)
                .setParameter(1, newId).setParameter(2, surveyId).setParameter(3, elementKey)
                .setParameter(4, displayOrder).setParameter(5, name).setParameter(6, dimensionName)
                .setParameter(7, description).setParameter(8, durableId).setParameter(9, oldVersion + 1)
                .executeUpdate();
        return new UpsertOutcome(durableId, ChangeType.VERSIONED);
    }

    /**
     * Fields: source_id|element_key|step_id|step_display_order|section_id|section_display_order|display_key|version|effective_from|effective_to|published_by|published_comment
     */
    private UpsertOutcome upsertStepsSection(String[] fields, Integer surveyId,
            Map<Long, Long> stepIdMap, Map<Long, Long> sectionIdMap) {
        if (fields.length < 12) {
            throw new IllegalArgumentException("steps_sections requires 12 fields, got " + fields.length);
        }
        UUID elementKey = requireElementKey(fields[1], "steps_sections");
        Long newStepId = SurveyDefinitionFileFields.resolveRequired(
                SurveyDefinitionFileFields.parseLongOrNull(fields[2]), stepIdMap, "step (for steps_sections)");
        Integer stepDisplayOrder = SurveyDefinitionFileFields.parseIntOrNull(fields[3]);
        Long newSectionId = SurveyDefinitionFileFields.resolveRequired(
                SurveyDefinitionFileFields.parseLongOrNull(fields[4]), sectionIdMap, "section (for steps_sections)");
        Integer sectionDisplayOrder = SurveyDefinitionFileFields.parseIntOrNull(fields[5]);
        // Rebased before the unchanged-comparison below, for the same reason as the survey's
        // initial_display_key — see SurveyDefinitionFileFields#rebaseDisplayKey.
        String displayKey = SurveyDefinitionFileFields.rebaseDisplayKey(fields[6], surveyId);

        Object[] current = findCurrentRow("survey.steps_sections", "steps_sections_key", elementKey, surveyId,
                "id, steps_sections_id, version, step_id, step_display_order, section_id, section_display_order, display_key");
        if (current == null) {
            Long newId = nextval("survey.steps_sections_seq");
            em.createNativeQuery("""
                    INSERT INTO survey.steps_sections
                        (id, survey_id, steps_sections_key, step_id, step_display_order, section_id, section_display_order, display_key)
                    VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8)
                    """)
                    .setParameter(1, newId).setParameter(2, surveyId).setParameter(3, elementKey)
                    .setParameter(4, newStepId).setParameter(5, stepDisplayOrder).setParameter(6, newSectionId)
                    .setParameter(7, sectionDisplayOrder).setParameter(8, displayKey).executeUpdate();
            return new UpsertOutcome(SurveyDefinitionFileFields.getDurableId(em, "steps_sections_id", "survey.steps_sections", newId), ChangeType.CREATED);
        }

        Long oldSurrogateId = ((Number) current[0]).longValue();
        Long durableId = ((Number) current[1]).longValue();
        int oldVersion = ((Number) current[2]).intValue();
        boolean unchanged = Objects.equals(newStepId, toLong(current[3])) && Objects.equals(stepDisplayOrder, current[4])
                && Objects.equals(newSectionId, toLong(current[5])) && Objects.equals(sectionDisplayOrder, current[6])
                && Objects.equals(displayKey, current[7]);
        if (unchanged) {
            return new UpsertOutcome(durableId, ChangeType.UNCHANGED);
        }

        closeCurrentVersion("survey.steps_sections", oldSurrogateId);
        Long newId = nextval("survey.steps_sections_seq");
        em.createNativeQuery("""
                INSERT INTO survey.steps_sections
                    (id, survey_id, steps_sections_key, step_id, step_display_order, section_id, section_display_order, display_key,
                     steps_sections_id, version, effective_from, effective_to)
                VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, NOW(), '9999-12-31 23:59:59+00')
                """)
                .setParameter(1, newId).setParameter(2, surveyId).setParameter(3, elementKey)
                .setParameter(4, newStepId).setParameter(5, stepDisplayOrder).setParameter(6, newSectionId)
                .setParameter(7, sectionDisplayOrder).setParameter(8, displayKey)
                .setParameter(9, durableId).setParameter(10, oldVersion + 1)
                .executeUpdate();
        return new UpsertOutcome(durableId, ChangeType.VERSIONED);
    }

    /**
     * Fields: source_id|element_key|type_id|text|short_text|tool_tip|required|min_value|max_value|
     *         validation_text|select_group_id|mask|placeholder|default_value|variant|
     *         version|effective_from|effective_to|published_by|published_comment
     */
    private UpsertOutcome upsertQuestion(String[] fields, Integer surveyId, Map<Long, Long> selectGroupIdMap) {
        if (fields.length < 20) {
            throw new IllegalArgumentException("questions requires 20 fields, got " + fields.length);
        }
        UUID elementKey = requireElementKey(fields[1], "questions");
        Integer typeId = SurveyDefinitionFileFields.parseIntOrNull(fields[2]);
        String text = SurveyDefinitionFileFields.nullIfEmpty(fields[3]);
        String shortText = SurveyDefinitionFileFields.nullIfEmpty(fields[4]);
        String toolTip = SurveyDefinitionFileFields.nullIfEmpty(fields[5]);
        boolean required = SurveyDefinitionFileFields.parseBooleanOrFalse(fields[6]);
        Integer minValue = SurveyDefinitionFileFields.parseIntOrNull(fields[7]);
        Integer maxValue = SurveyDefinitionFileFields.parseIntOrNull(fields[8]);
        String validationText = SurveyDefinitionFileFields.nullIfEmpty(fields[9]);
        Long oldSelectGroupId = SurveyDefinitionFileFields.parseLongOrNull(fields[10]);
        Long newSelectGroupId = oldSelectGroupId == null ? null
                : SurveyDefinitionFileFields.resolveRequired(oldSelectGroupId, selectGroupIdMap, "select_group (for question)");
        String mask = SurveyDefinitionFileFields.nullIfEmpty(fields[11]);
        String placeholder = SurveyDefinitionFileFields.nullIfEmpty(fields[12]);
        String defaultValue = SurveyDefinitionFileFields.nullIfEmpty(fields[13]);
        String variant = SurveyDefinitionFileFields.nullIfEmpty(fields[14]);

        Object[] current = findCurrentRow("survey.questions", "question_key", elementKey, surveyId,
                "id, question_id, version, type_id, text, short_text, tool_tip, required, min_value, max_value, "
                        + "validation_text, select_group_id, mask, placeholder, default_value, variant");
        if (current == null) {
            Long newId = nextval("survey.questions_seq");
            em.createNativeQuery("""
                    INSERT INTO survey.questions
                        (id, survey_id, question_key, type_id, text, short_text, tool_tip, required,
                         min_value, max_value, validation_text, select_group_id,
                         mask, placeholder, default_value, variant)
                    VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11, ?12, ?13, ?14, ?15, ?16)
                    """)
                    .setParameter(1, newId).setParameter(2, surveyId).setParameter(3, elementKey)
                    .setParameter(4, typeId).setParameter(5, text).setParameter(6, shortText)
                    .setParameter(7, toolTip).setParameter(8, required).setParameter(9, minValue)
                    .setParameter(10, maxValue).setParameter(11, validationText).setParameter(12, newSelectGroupId)
                    .setParameter(13, mask).setParameter(14, placeholder).setParameter(15, defaultValue)
                    .setParameter(16, variant).executeUpdate();
            return new UpsertOutcome(SurveyDefinitionFileFields.getDurableId(em, "question_id", "survey.questions", newId), ChangeType.CREATED);
        }

        Long oldSurrogateId = ((Number) current[0]).longValue();
        Long durableId = ((Number) current[1]).longValue();
        int oldVersion = ((Number) current[2]).intValue();
        boolean unchanged = Objects.equals(typeId, current[3]) && Objects.equals(text, current[4])
                && Objects.equals(shortText, current[5]) && Objects.equals(toolTip, current[6])
                && Objects.equals(required, current[7]) && Objects.equals(minValue, current[8])
                && Objects.equals(maxValue, current[9]) && Objects.equals(validationText, current[10])
                && Objects.equals(newSelectGroupId, toLong(current[11])) && Objects.equals(mask, current[12])
                && Objects.equals(placeholder, current[13]) && Objects.equals(defaultValue, current[14])
                && Objects.equals(variant, current[15]);
        if (unchanged) {
            return new UpsertOutcome(durableId, ChangeType.UNCHANGED);
        }

        closeCurrentVersion("survey.questions", oldSurrogateId);
        Long newId = nextval("survey.questions_seq");
        em.createNativeQuery("""
                INSERT INTO survey.questions
                    (id, survey_id, question_key, type_id, text, short_text, tool_tip, required,
                     min_value, max_value, validation_text, select_group_id,
                     mask, placeholder, default_value, variant,
                     question_id, version, effective_from, effective_to)
                VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11, ?12, ?13, ?14, ?15, ?16,
                        ?17, ?18, NOW(), '9999-12-31 23:59:59+00')
                """)
                .setParameter(1, newId).setParameter(2, surveyId).setParameter(3, elementKey)
                .setParameter(4, typeId).setParameter(5, text).setParameter(6, shortText)
                .setParameter(7, toolTip).setParameter(8, required).setParameter(9, minValue)
                .setParameter(10, maxValue).setParameter(11, validationText).setParameter(12, newSelectGroupId)
                .setParameter(13, mask).setParameter(14, placeholder).setParameter(15, defaultValue)
                .setParameter(16, variant).setParameter(17, durableId).setParameter(18, oldVersion + 1)
                .executeUpdate();
        return new UpsertOutcome(durableId, ChangeType.VERSIONED);
    }

    /**
     * Fields: source_id|element_key|question_id|section_id|display_order|version|effective_from|effective_to|published_by|published_comment
     */
    private UpsertOutcome upsertSectionsQuestion(String[] fields, Integer surveyId,
            Map<Long, Long> questionIdMap, Map<Long, Long> sectionIdMap) {
        if (fields.length < 10) {
            throw new IllegalArgumentException("sections_questions requires 10 fields, got " + fields.length);
        }
        UUID elementKey = requireElementKey(fields[1], "sections_questions");
        Long newQuestionId = SurveyDefinitionFileFields.resolveRequired(
                SurveyDefinitionFileFields.parseLongOrNull(fields[2]), questionIdMap, "question (for sections_questions)");
        Long newSectionId = SurveyDefinitionFileFields.resolveRequired(
                SurveyDefinitionFileFields.parseLongOrNull(fields[3]), sectionIdMap, "section (for sections_questions)");
        Integer displayOrder = SurveyDefinitionFileFields.parseIntOrNull(fields[4]);

        Object[] current = findCurrentRow("survey.sections_questions", "sections_question_key", elementKey, surveyId,
                "id, sections_question_id, version, question_id, section_id, display_order");
        if (current == null) {
            Long newId = nextval("survey.sections_questions_seq");
            em.createNativeQuery("""
                    INSERT INTO survey.sections_questions
                        (id, survey_id, sections_question_key, question_id, section_id, display_order)
                    VALUES (?1, ?2, ?3, ?4, ?5, ?6)
                    """)
                    .setParameter(1, newId).setParameter(2, surveyId).setParameter(3, elementKey)
                    .setParameter(4, newQuestionId).setParameter(5, newSectionId).setParameter(6, displayOrder)
                    .executeUpdate();
            return new UpsertOutcome(SurveyDefinitionFileFields.getDurableId(em, "sections_question_id", "survey.sections_questions", newId), ChangeType.CREATED);
        }

        Long oldSurrogateId = ((Number) current[0]).longValue();
        Long durableId = ((Number) current[1]).longValue();
        int oldVersion = ((Number) current[2]).intValue();
        boolean unchanged = Objects.equals(newQuestionId, toLong(current[3])) && Objects.equals(newSectionId, toLong(current[4]))
                && Objects.equals(displayOrder, current[5]);
        if (unchanged) {
            return new UpsertOutcome(durableId, ChangeType.UNCHANGED);
        }

        closeCurrentVersion("survey.sections_questions", oldSurrogateId);
        Long newId = nextval("survey.sections_questions_seq");
        em.createNativeQuery("""
                INSERT INTO survey.sections_questions
                    (id, survey_id, sections_question_key, question_id, section_id, display_order,
                     sections_question_id, version, effective_from, effective_to)
                VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, NOW(), '9999-12-31 23:59:59+00')
                """)
                .setParameter(1, newId).setParameter(2, surveyId).setParameter(3, elementKey)
                .setParameter(4, newQuestionId).setParameter(5, newSectionId).setParameter(6, displayOrder)
                .setParameter(7, durableId).setParameter(8, oldVersion + 1)
                .executeUpdate();
        return new UpsertOutcome(durableId, ChangeType.VERSIONED);
    }

    /**
     * Fields: source_id|element_key|upstream_step_id|upstream_sq_id|downstream_step_id|downstream_ss_id|
     *         downstream_sq_id|operator_id|action_id|description|token|reference_value|
     *         default_upstream_value|override_upstream_value|version|effective_from|
     *         effective_to|published_by|published_comment
     */
    private ChangeType upsertRelationship(String[] fields, Integer surveyId,
            Map<Long, Long> stepIdMap, Map<Long, Long> sectionsQuestionIdMap, Map<Long, Long> stepsSectionIdMap) {
        if (fields.length < 19) {
            throw new IllegalArgumentException("relationships requires 19 fields, got " + fields.length);
        }
        UUID elementKey = requireElementKey(fields[1], "relationships");
        Long newUpstreamStepId = SurveyDefinitionFileFields.resolveNullable(
                SurveyDefinitionFileFields.parseLongOrNull(fields[2]), stepIdMap, "step (upstream)");
        Long newUpstreamSqId = SurveyDefinitionFileFields.resolveRequired(
                SurveyDefinitionFileFields.parseLongOrNull(fields[3]), sectionsQuestionIdMap, "sections_question (upstream_sq)");
        Long newDownstreamStepId = SurveyDefinitionFileFields.resolveNullable(
                SurveyDefinitionFileFields.parseLongOrNull(fields[4]), stepIdMap, "step (downstream)");
        Long newDownstreamSsId = SurveyDefinitionFileFields.resolveNullable(
                SurveyDefinitionFileFields.parseLongOrNull(fields[5]), stepsSectionIdMap, "steps_section (downstream_ss)");
        Long newDownstreamSqId = SurveyDefinitionFileFields.resolveNullable(
                SurveyDefinitionFileFields.parseLongOrNull(fields[6]), sectionsQuestionIdMap, "sections_question (downstream_sq)");
        Integer operatorId = SurveyDefinitionFileFields.parseIntOrNull(fields[7]);
        Integer actionId = SurveyDefinitionFileFields.parseIntOrNull(fields[8]);
        String description = SurveyDefinitionFileFields.nullIfEmpty(fields[9]);
        String token = SurveyDefinitionFileFields.nullIfEmpty(fields[10]);
        String referenceValue = SurveyDefinitionFileFields.nullIfEmpty(fields[11]);
        String defaultUpstreamValue = SurveyDefinitionFileFields.nullIfEmpty(fields[12]);
        String overrideUpstreamValue = SurveyDefinitionFileFields.nullIfEmpty(fields[13]);

        Object[] current = findCurrentRow("survey.relationships", "relationship_key", elementKey, surveyId,
                "id, version, upstream_step_id, upstream_sq_id, downstream_step_id, downstream_ss_id, downstream_sq_id, "
                        + "operator_id, action_id, description, token, reference_value, default_upstream_value, override_upstream_value");
        if (current == null) {
            em.createNativeQuery("""
                    INSERT INTO survey.relationships
                        (id, survey_id, relationship_key, upstream_step_id, upstream_sq_id, downstream_step_id,
                         downstream_ss_id, downstream_sq_id, operator_id, action_id, description,
                         token, reference_value, default_upstream_value, override_upstream_value)
                    VALUES (nextval('survey.relationships_seq'), ?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11, ?12, ?13, ?14)
                    """)
                    .setParameter(1, surveyId).setParameter(2, elementKey).setParameter(3, newUpstreamStepId)
                    .setParameter(4, newUpstreamSqId).setParameter(5, newDownstreamStepId).setParameter(6, newDownstreamSsId)
                    .setParameter(7, newDownstreamSqId).setParameter(8, operatorId).setParameter(9, actionId)
                    .setParameter(10, description).setParameter(11, token).setParameter(12, referenceValue)
                    .setParameter(13, defaultUpstreamValue).setParameter(14, overrideUpstreamValue)
                    .executeUpdate();
            return ChangeType.CREATED;
        }

        Long oldSurrogateId = ((Number) current[0]).longValue();
        int oldVersion = ((Number) current[1]).intValue();
        boolean unchanged = Objects.equals(newUpstreamStepId, toLong(current[2])) && Objects.equals(newUpstreamSqId, toLong(current[3]))
                && Objects.equals(newDownstreamStepId, toLong(current[4])) && Objects.equals(newDownstreamSsId, toLong(current[5]))
                && Objects.equals(newDownstreamSqId, toLong(current[6])) && Objects.equals(operatorId, current[7])
                && Objects.equals(actionId, current[8]) && Objects.equals(description, current[9])
                && Objects.equals(token, current[10]) && Objects.equals(referenceValue, current[11])
                && Objects.equals(defaultUpstreamValue, current[12]) && Objects.equals(overrideUpstreamValue, current[13]);
        if (unchanged) {
            return ChangeType.UNCHANGED;
        }

        closeCurrentVersion("survey.relationships", oldSurrogateId);
        em.createNativeQuery("""
                INSERT INTO survey.relationships
                    (id, survey_id, relationship_key, upstream_step_id, upstream_sq_id, downstream_step_id,
                     downstream_ss_id, downstream_sq_id, operator_id, action_id, description,
                     token, reference_value, default_upstream_value, override_upstream_value,
                     relationship_id, version, effective_from, effective_to)
                VALUES (nextval('survey.relationships_seq'), ?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11, ?12, ?13, ?14,
                        (SELECT relationship_id FROM survey.relationships WHERE id = ?15), ?16, NOW(), '9999-12-31 23:59:59+00')
                """)
                .setParameter(1, surveyId).setParameter(2, elementKey).setParameter(3, newUpstreamStepId)
                .setParameter(4, newUpstreamSqId).setParameter(5, newDownstreamStepId).setParameter(6, newDownstreamSsId)
                .setParameter(7, newDownstreamSqId).setParameter(8, operatorId).setParameter(9, actionId)
                .setParameter(10, description).setParameter(11, token).setParameter(12, referenceValue)
                .setParameter(13, defaultUpstreamValue).setParameter(14, overrideUpstreamValue)
                .setParameter(15, oldSurrogateId).setParameter(16, oldVersion + 1)
                .executeUpdate();
        return ChangeType.VERSIONED;
    }

    // -------------------------------------------------------------------------
    // Type 1 tables — matched by element_key, updated in place on change (no version history)
    // -------------------------------------------------------------------------

    /**
     * Fields: source_id|element_key|name|description|url|display_order
     */
    private ChangeType upsertReport(String[] fields, Integer surveyId) {
        if (fields.length < 6) {
            throw new IllegalArgumentException("reports requires 6 fields, got " + fields.length);
        }
        UUID elementKey = requireElementKey(fields[1], "reports");
        String name = SurveyDefinitionFileFields.nullIfEmpty(fields[2]);
        String description = SurveyDefinitionFileFields.nullIfEmpty(fields[3]);
        String url = SurveyDefinitionFileFields.nullIfEmpty(fields[4]);
        Integer displayOrder = SurveyDefinitionFileFields.parseIntOrNull(fields[5]);

        Object[] current = findCurrentRow("survey.reports", "report_key", elementKey, surveyId,
                "id, name, description, url, display_order");
        if (current == null) {
            em.createNativeQuery("""
                    INSERT INTO survey.reports (id, survey_id, report_key, name, description, url, display_order)
                    VALUES (nextval('survey.reports_seq'), ?1, ?2, ?3, ?4, ?5, ?6)
                    """)
                    .setParameter(1, surveyId).setParameter(2, elementKey).setParameter(3, name)
                    .setParameter(4, description).setParameter(5, url).setParameter(6, displayOrder)
                    .executeUpdate();
            return ChangeType.CREATED;
        }

        Long id = ((Number) current[0]).longValue();
        boolean unchanged = Objects.equals(name, current[1]) && Objects.equals(description, current[2])
                && Objects.equals(url, current[3]) && Objects.equals(displayOrder, current[4]);
        if (unchanged) {
            return ChangeType.UNCHANGED;
        }
        em.createNativeQuery("UPDATE survey.reports SET name = ?1, description = ?2, url = ?3, display_order = ?4 WHERE id = ?5")
                .setParameter(1, name).setParameter(2, description).setParameter(3, url)
                .setParameter(4, displayOrder).setParameter(5, id)
                .executeUpdate();
        return ChangeType.VERSIONED;
    }

    /**
     * Fields: source_id|element_key|name|description|url|execution_order
     */
    private ChangeType upsertPostSurveyAction(String[] fields, Integer surveyId) {
        if (fields.length < 6) {
            throw new IllegalArgumentException("post_survey_actions requires 6 fields, got " + fields.length);
        }
        UUID elementKey = requireElementKey(fields[1], "post_survey_actions");
        String name = SurveyDefinitionFileFields.nullIfEmpty(fields[2]);
        String description = SurveyDefinitionFileFields.nullIfEmpty(fields[3]);
        String url = SurveyDefinitionFileFields.nullIfEmpty(fields[4]);
        Integer executionOrder = SurveyDefinitionFileFields.parseIntOrNull(fields[5]);

        Object[] current = findCurrentRow("survey.post_survey_actions", "post_survey_action_key", elementKey, surveyId,
                "id, name, description, url, execution_order");
        if (current == null) {
            em.createNativeQuery("""
                    INSERT INTO survey.post_survey_actions
                        (id, survey_id, post_survey_action_key, name, description, url, execution_order)
                    VALUES (nextval('survey.post_survey_actions_seq'), ?1, ?2, ?3, ?4, ?5, ?6)
                    """)
                    .setParameter(1, surveyId).setParameter(2, elementKey).setParameter(3, name)
                    .setParameter(4, description).setParameter(5, url).setParameter(6, executionOrder)
                    .executeUpdate();
            return ChangeType.CREATED;
        }

        Long id = ((Number) current[0]).longValue();
        boolean unchanged = Objects.equals(name, current[1]) && Objects.equals(description, current[2])
                && Objects.equals(url, current[3]) && Objects.equals(executionOrder, current[4]);
        if (unchanged) {
            return ChangeType.UNCHANGED;
        }
        em.createNativeQuery("UPDATE survey.post_survey_actions SET name = ?1, description = ?2, url = ?3, execution_order = ?4 WHERE id = ?5")
                .setParameter(1, name).setParameter(2, description).setParameter(3, url)
                .setParameter(4, executionOrder).setParameter(5, id)
                .executeUpdate();
        return ChangeType.VERSIONED;
    }

    /**
     * Dimensions are a shared global lookup (no survey_id), matched by {@code name} exactly as on
     * import — reuse wins over the file's element_key, since two different-instance dimension_keys
     * describing the same name in this instance must collapse to the one row already here.
     * Fields: source_id|element_key|name
     */
    private UpsertOutcome upsertOrReuseDimension(String[] fields) {
        if (fields.length < 3) {
            throw new IllegalArgumentException("dimensions requires 3 fields, got " + fields.length);
        }
        UUID elementKey = requireElementKey(fields[1], "dimensions");
        String name = SurveyDefinitionFileFields.nullIfEmpty(fields[2]);

        Query existQuery = em.createNativeQuery("SELECT id FROM survey.dimensions WHERE name = ?1");
        existQuery.setParameter(1, name);
        Long existingId = SurveyDefinitionFileFields.getLongResult(existQuery);
        if (existingId != null) {
            return new UpsertOutcome(existingId, ChangeType.UNCHANGED);
        }

        Long newId = nextval("survey.dimensions_seq");
        em.createNativeQuery("INSERT INTO survey.dimensions (id, dimension_key, name) VALUES (?1, ?2, ?3)")
                .setParameter(1, newId).setParameter(2, elementKey).setParameter(3, name)
                .executeUpdate();
        return new UpsertOutcome(newId, ChangeType.CREATED);
    }

    /**
     * Fields: source_id|element_key|name|tag|dimension
     */
    private UpsertOutcome upsertOrReuseOntology(String[] fields, Integer surveyId, Map<Long, Long> dimensionIdMap) {
        if (fields.length < 5) {
            throw new IllegalArgumentException("ontology requires 5 fields, got " + fields.length);
        }
        UUID elementKey = requireElementKey(fields[1], "ontology");
        String name = SurveyDefinitionFileFields.nullIfEmpty(fields[2]);
        String tag = SurveyDefinitionFileFields.nullIfEmpty(fields[3]);
        Long newDimensionId = SurveyDefinitionFileFields.resolveNullable(
                SurveyDefinitionFileFields.parseLongOrNull(fields[4]), dimensionIdMap, "dimension (ontology)");

        Object[] current = findCurrentRow("survey.ontology", "ontology_key", elementKey, surveyId,
                "id, name, tag, dimension");
        if (current == null) {
            // Fall back to the (name, tag) unique constraint, same reuse rule as import.
            Query existQuery = em.createNativeQuery("SELECT id FROM survey.ontology WHERE name = ?1 AND tag = ?2");
            existQuery.setParameter(1, name);
            existQuery.setParameter(2, tag);
            Long existingId = SurveyDefinitionFileFields.getLongResult(existQuery);
            if (existingId != null) {
                return new UpsertOutcome(existingId, ChangeType.UNCHANGED);
            }
            Long newId = nextval("survey.ontology_seq");
            em.createNativeQuery("""
                    INSERT INTO survey.ontology (id, survey_id, ontology_key, name, tag, dimension)
                    VALUES (?1, ?2, ?3, ?4, ?5, ?6)
                    """)
                    .setParameter(1, newId).setParameter(2, surveyId).setParameter(3, elementKey)
                    .setParameter(4, name).setParameter(5, tag).setParameter(6, newDimensionId)
                    .executeUpdate();
            return new UpsertOutcome(newId, ChangeType.CREATED);
        }

        Long id = ((Number) current[0]).longValue();
        boolean unchanged = Objects.equals(name, current[1]) && Objects.equals(tag, current[2])
                && Objects.equals(newDimensionId, toLong(current[3]));
        if (unchanged) {
            return new UpsertOutcome(id, ChangeType.UNCHANGED);
        }
        em.createNativeQuery("UPDATE survey.ontology SET name = ?1, tag = ?2, dimension = ?3 WHERE id = ?4")
                .setParameter(1, name).setParameter(2, tag).setParameter(3, newDimensionId).setParameter(4, id)
                .executeUpdate();
        return new UpsertOutcome(id, ChangeType.VERSIONED);
    }

    /**
     * Fields: source_id|element_key|steps_sections_id|question_id|sections_question_id|ontology_id|value
     */
    private ChangeType upsertMetadata(String[] fields, Integer surveyId,
            Map<Long, Long> stepsSectionIdMap, Map<Long, Long> questionIdMap,
            Map<Long, Long> sectionsQuestionIdMap, Map<Long, Long> ontologyIdMap) {
        if (fields.length < 7) {
            throw new IllegalArgumentException("metadata requires 7 fields, got " + fields.length);
        }
        UUID elementKey = requireElementKey(fields[1], "metadata");
        Long newStepsSectionsId = SurveyDefinitionFileFields.resolveNullable(
                SurveyDefinitionFileFields.parseLongOrNull(fields[2]), stepsSectionIdMap, "steps_section (metadata)");
        Long newQuestionId = SurveyDefinitionFileFields.resolveNullable(
                SurveyDefinitionFileFields.parseLongOrNull(fields[3]), questionIdMap, "question (metadata)");
        Long newSectionsQuestionId = SurveyDefinitionFileFields.resolveNullable(
                SurveyDefinitionFileFields.parseLongOrNull(fields[4]), sectionsQuestionIdMap, "sections_question (metadata)");
        Long newOntologyId = SurveyDefinitionFileFields.resolveRequired(
                SurveyDefinitionFileFields.parseLongOrNull(fields[5]), ontologyIdMap, "ontology (metadata)");
        String value = SurveyDefinitionFileFields.nullIfEmpty(fields[6]);

        Object[] current = findCurrentRow("survey.metadata", "metadata_key", elementKey, surveyId,
                "id, steps_sections_id, question_id, sections_question_id, ontology_id, value");
        if (current == null) {
            em.createNativeQuery("""
                    INSERT INTO survey.metadata
                        (id, survey_id, metadata_key, steps_sections_id, question_id, sections_question_id, ontology_id, value)
                    VALUES (nextval('survey.metadata_seq'), ?1, ?2, ?3, ?4, ?5, ?6, ?7)
                    """)
                    .setParameter(1, surveyId).setParameter(2, elementKey).setParameter(3, newStepsSectionsId)
                    .setParameter(4, newQuestionId).setParameter(5, newSectionsQuestionId).setParameter(6, newOntologyId)
                    .setParameter(7, value)
                    .executeUpdate();
            return ChangeType.CREATED;
        }

        Long id = ((Number) current[0]).longValue();
        boolean unchanged = Objects.equals(newStepsSectionsId, toLong(current[1])) && Objects.equals(newQuestionId, toLong(current[2]))
                && Objects.equals(newSectionsQuestionId, toLong(current[3])) && Objects.equals(newOntologyId, toLong(current[4]))
                && Objects.equals(value, current[5]);
        if (unchanged) {
            return ChangeType.UNCHANGED;
        }
        em.createNativeQuery("""
                UPDATE survey.metadata
                SET steps_sections_id = ?1, question_id = ?2, sections_question_id = ?3, ontology_id = ?4, value = ?5
                WHERE id = ?6
                """)
                .setParameter(1, newStepsSectionsId).setParameter(2, newQuestionId).setParameter(3, newSectionsQuestionId)
                .setParameter(4, newOntologyId).setParameter(5, value).setParameter(6, id)
                .executeUpdate();
        return ChangeType.VERSIONED;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * A record the file marks as retired (a closed {@code effective_to}, written by the authoring
     * tool when an element is removed): close the target's current row for that element key at
     * now, exactly as versioning would, but insert nothing. Respondents who started before this
     * instant keep the element through the as-of resolution; new respondents never see it. No
     * current row (already retired here, or never installed) is a no-op. The durable id is still
     * returned so retired dependents in the same file can resolve their references.
     */
    private UpsertOutcome retireCurrentRow(String table, String keyColumn, String durableColumn, String[] fields, Integer surveyId) {
        UUID elementKey = requireElementKey(fields[1], table.substring(table.indexOf('.') + 1));
        Object[] current = findCurrentRow(table, keyColumn, elementKey, surveyId, "id, " + durableColumn);
        if (current != null) {
            closeCurrentVersion(table, ((Number) current[0]).longValue());
            return new UpsertOutcome(toLong(current[1]), ChangeType.RETIRED);
        }
        Query latest = em.createNativeQuery("SELECT " + durableColumn + " FROM " + table
                + " WHERE survey_id = ?1 AND " + keyColumn + " = ?2 ORDER BY version DESC LIMIT 1");
        latest.setParameter(1, surveyId);
        latest.setParameter(2, elementKey);
        List<?> rows = latest.getResultList();
        return new UpsertOutcome(rows.isEmpty() ? null : toLong(rows.get(0)), ChangeType.UNCHANGED);
    }

    /**
     * Every structural row in an update file must carry its stable element_key — matching, the
     * whole point of this service, is impossible without one. Unlike
     * {@code SurveyDefinitionImportService#resolveElementKey}, a missing key here is a malformed
     * record (A5), not something to default.
     */
    private UUID requireElementKey(String raw, String tableName) {
        String value = SurveyDefinitionFileFields.nullIfEmpty(raw);
        if (value == null) {
            throw new IllegalStateException(
                    tableName + " row has no element_key to match against — this file predates "
                    + "stable-key assignment and cannot be applied via Update.");
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid element_key in " + tableName + " row: " + value, e);
        }
    }

    /**
     * Looks up the target's current (open-ended) row for one element_key, returning
     * the requested columns as an {@code Object[]} (first column always {@code id}), or
     * {@code null} if no such row exists yet.
     */
    private Object[] findCurrentRow(String table, String keyColumn, UUID elementKey, Integer surveyId, String columns) {
        Query query = em.createNativeQuery(
                "SELECT " + columns + " FROM " + table
                        + " WHERE survey_id = ?1 AND " + keyColumn + " = ?2"
                        + (table.equals("survey.reports") || table.equals("survey.post_survey_actions")
                                || table.equals("survey.ontology") || table.equals("survey.metadata")
                                ? ""
                                : " AND effective_to = '" + OPEN_ENDED + "'"));
        query.setParameter(1, surveyId);
        query.setParameter(2, elementKey);
        List<?> results = query.getResultList();
        if (results.isEmpty()) {
            return null;
        }
        Object row = results.get(0);
        return row instanceof Object[] columns2 ? columns2 : new Object[] { row };
    }

    private void closeCurrentVersion(String table, Long oldSurrogateId) {
        em.createNativeQuery("UPDATE " + table + " SET effective_to = NOW() WHERE id = ?1")
                .setParameter(1, oldSurrogateId)
                .executeUpdate();
    }

    private Long nextval(String sequence) {
        return ((Number) em.createNativeQuery("SELECT nextval('" + sequence + "')").getSingleResult()).longValue();
    }

    private static Long toLong(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }
}
