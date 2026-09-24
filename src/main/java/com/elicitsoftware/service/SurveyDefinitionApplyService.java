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

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Routes a survey definition file to whichever of {@link SurveyDefinitionImportService} (UC-014,
 * create) or {@link SurveyDefinitionUpdateService} (UC-017, update in place) is correct for this
 * instance, deciding by the file's {@code survey_key}.
 * <p>
 * Both underlying services already enforce the distinction defensively — import rejects a file
 * whose key is already present here, update rejects one whose key doesn't match the selected
 * target — but each requires the operator to have chosen correctly up front. That choice is not
 * something an operator can make from the file alone: whether a given survey already exists is a
 * property of <em>this</em> deployment, and in a multi-site rollout the same file is a create at
 * one site and an update at another. This service answers that question by looking the key up,
 * so the same file can be handed to every site with no per-site instructions.
 * <p>
 * Routing only. It makes no schema changes of its own — the service it delegates to does all the
 * work, inside that service's own transaction, and its result is returned unchanged.
 * <p>
 * One thing happens after a successful apply, once that transaction has committed: the Survey
 * application is asked to rebuild its reporting schema ({@link ReportingSchemaRebuildClient}),
 * so the survey just installed or updated is reportable without a restart. The outcome is
 * reported alongside the result and never fails the apply.
 *
 * @see SurveyDefinitionImportService
 * @see SurveyDefinitionUpdateService
 * @see ReportingSchemaRebuildClient
 */
@ApplicationScoped
public class SurveyDefinitionApplyService {

    /**
     * Default constructor for CDI.
     */
    public SurveyDefinitionApplyService() {
        // CDI managed bean
    }

    /**
     * Upper bound on a buffered definition file, matching the upload limit the Admin UI enforces.
     * Routing has to read the {@code surveys:} line before dispatching and then replay the whole
     * stream, so the file is held in memory; this keeps a hostile or malformed upload from
     * becoming an allocation problem.
     * <p>
     * Public so the upload UI can enforce the same ceiling client-side and reject an oversized
     * file before it is streamed, rather than the two drifting apart.
     */
    public static final int MAX_FILE_BYTES = 5 * 1024 * 1024;

    @Inject
    SurveyDefinitionImportService importService;

    @Inject
    SurveyDefinitionUpdateService updateService;

    @Inject
    ReportingSchemaRebuildClient reportingSchemaRebuildClient;

    /**
     * Which service a file was routed to, and what that service reported.
     *
     * @param action    the operation selected for the file
     * @param surveyKey the file's stable survey key, or {@code null} if it was rejected before one was read
     * @param success   whether the selected service applied the file
     * @param message   the routing summary or the failure reason
     * @param detail    the import or update service's own result, or {@code null}
     * @param reporting the reporting schema rebuild's summary line after a successful apply
     *                  ("Reporting schema rebuilt." or "Reporting schema not rebuilt: ..."), or
     *                  {@code null} when nothing was applied or the call is disabled
     */
    public record ApplyResult(Action action, UUID surveyKey, boolean success, String message,
            Object detail, String reporting) {

        /** A result with no reporting outcome: a rejection, a failure, or a rebuild that was skipped. */
        public ApplyResult(Action action, UUID surveyKey, boolean success, String message, Object detail) {
            this(action, surveyKey, success, message, detail, null);
        }

        /** The operation {@link SurveyDefinitionApplyService} selected for a file. */
        public enum Action {
            /** No survey with this key existed here; the file created one. */
            IMPORT,
            /** A survey with this key already existed here; the file was applied to it. */
            UPDATE,
            /** The file could not be routed; nothing was attempted. */
            REJECTED
        }
    }

    /**
     * Applies a definition file, choosing create-or-update by its {@code survey_key}.
     *
     * @param data the complete file contents
     * @param fileName the uploaded file's original name, for the {@code survey_log} audit row
     * @return what was done and the underlying service's own result
     */
    public ApplyResult apply(byte[] data, String fileName) {
        if (data == null || data.length == 0) {
            return new ApplyResult(ApplyResult.Action.REJECTED, null, false, "File is empty", null);
        }
        if (data.length > MAX_FILE_BYTES) {
            return new ApplyResult(ApplyResult.Action.REJECTED, null, false,
                    "File exceeds the " + (MAX_FILE_BYTES / (1024 * 1024)) + "MB limit", null);
        }

        UUID surveyKey;
        try {
            surveyKey = readSurveyKey(data);
        } catch (IOException e) {
            return new ApplyResult(ApplyResult.Action.REJECTED, null, false,
                    "Failed to read file: " + e.getMessage(), null);
        } catch (IllegalArgumentException e) {
            return new ApplyResult(ApplyResult.Action.REJECTED, null, false, e.getMessage(), null);
        }

        if (surveyKey == null) {
            // Pre-key files can still be installed, but only as a new survey, and only by an
            // operator who has confirmed that is what they want — routing cannot infer it.
            return new ApplyResult(ApplyResult.Action.REJECTED, null, false,
                    "This file carries no survey_key, so it cannot be matched against the surveys "
                            + "already installed here. Import it explicitly as a new survey if that "
                            + "is what you intend.", null);
        }

        Survey existing = Survey.find("surveyKey", surveyKey).firstResult();
        if (existing == null) {
            SurveyDefinitionImportService.ImportResult result =
                    importService.importFromFile(new ByteArrayInputStream(data), fileName);
            return new ApplyResult(ApplyResult.Action.IMPORT, surveyKey, result.isSuccess(),
                    result.isSuccess()
                            ? "Installed as a new survey"
                            : String.join("; ", result.getErrors()),
                    result,
                    result.isSuccess() ? rebuildReportingSchema() : null);
        }

        SurveyDefinitionUpdateService.UpdateResult result = updateService.updateFromFile(
                new ByteArrayInputStream(data), fileName, existing.id);
        return new ApplyResult(ApplyResult.Action.UPDATE, surveyKey, result.isSuccess(),
                result.isSuccess()
                        ? "Applied to existing survey " + existing.id
                        : String.join("; ", result.getErrors()),
                result,
                result.isSuccess() ? rebuildReportingSchema() : null);
    }

    /**
     * Asks Survey to rebuild its reporting schema now that the definition is committed. Runs
     * outside the import/update transaction (this method is not transactional and the delegate
     * has returned), so Survey sees the committed rows. The line comes back for the result and
     * the apply stands whatever it says.
     */
    private String rebuildReportingSchema() {
        return reportingSchemaRebuildClient.rebuild().summaryLine();
    }

    /**
     * Reads the {@code survey_key} from the file's {@code surveys:} line without consuming the
     * rest of the file's meaning — the caller replays the bytes to whichever service handles it.
     * <p>
     * Stops at the {@code surveys:} line, which the exporter always writes as the first data
     * line, so this does not scan the whole file.
     *
     * @return the declared key, or {@code null} if the file predates key assignment
     * @throws IllegalArgumentException if the file has no {@code surveys:} line at all, or its
     *     key is not a valid UUID
     */
    private UUID readSurveyKey(byte[] data) throws IOException {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new ByteArrayInputStream(data), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                int colonIndex = line.indexOf(':');
                if (colonIndex < 0) {
                    continue;
                }
                if (!"surveys".equals(line.substring(0, colonIndex).trim().toLowerCase())) {
                    continue;
                }
                String[] fields = SurveyDefinitionFileFields.parseFields(line.substring(colonIndex + 1).trim());
                if (fields.length < 2) {
                    throw new IllegalArgumentException("The surveys record is malformed: expected at "
                            + "least 2 fields, got " + fields.length);
                }
                String rawKey = SurveyDefinitionFileFields.nullIfEmpty(fields[1]);
                if (rawKey == null) {
                    return null;
                }
                try {
                    return UUID.fromString(rawKey.trim());
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException("Invalid survey_key in file: " + rawKey, e);
                }
            }
        }
        throw new IllegalArgumentException("This file contains no surveys record, so it is not a "
                + "survey definition export.");
    }
}
