package com.elicitsoftware.admin.flow;

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

import com.elicitsoftware.service.SurveyDefinitionApplyService;
import com.elicitsoftware.service.SurveyDefinitionImportService;
import com.elicitsoftware.service.SurveyDefinitionUpdateService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.streams.UploadHandler;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Admin-only view for applying an authored survey definition (.elicit) to this deployment —
 * UC-018. The administrator uploads the file and nothing else: the file's stable survey key
 * decides whether this deployment is seeing the survey for the first time (install, UC-014) or
 * already has it (update in place, UC-017), and for an update the file's revision must not
 * predate the one already installed here.
 * <p>
 * After a successful apply the result also says whether the Survey application rebuilt its
 * reporting schema; a failure there is reported on its own line and does not change the title.
 * <p>
 * Complements {@link com.elicitsoftware.rest.SurveyDefinitionApplyResource}, which exposes the
 * same capability over REST.
 */
@Route(value = "survey-apply", layout = MainLayout.class)
@RolesAllowed("elicit_admin")
public class SurveyDefinitionApplyView extends VerticalLayout {

    @Inject
    SurveyDefinitionApplyService applyService;

    /**
     * Builds the upload form.
     */
    public SurveyDefinitionApplyView() {
        setSizeFull();

        add(new H3(getTranslation("surveyDefinitionApplyView.title")));
        add(new Paragraph(getTranslation("surveyDefinitionApplyView.intro")));
        add(new Paragraph(getTranslation("surveyDefinitionApplyView.revisionNote")));

        Upload upload = new Upload();
        upload.setId("survey-apply-upload");
        upload.setAcceptedFileTypes(".elicit");
        upload.setMaxFiles(1);
        upload.setMaxFileSize(SurveyDefinitionApplyService.MAX_FILE_BYTES);

        Button uploadButton = new Button(getTranslation("surveyDefinitionApplyView.upload"));
        uploadButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        upload.setUploadButton(uploadButton);

        upload.setUploadHandler(UploadHandler.inMemory((metadata, data) ->
                handleUpload(data, metadata.fileName())));

        add(upload);
    }

    /**
     * Applies an uploaded file and shows the outcome. Package-private so tests can drive it
     * directly without simulating a real upload — same precedent as {@link RespondentImportView}.
     *
     * @param data the raw bytes of the uploaded file
     * @param fileName the uploaded file's name, recorded in the survey log
     */
    void handleUpload(byte[] data, String fileName) {
        try {
            SurveyDefinitionApplyService.ApplyResult result = applyService.apply(data, fileName);
            new ResultDialog(titleFor(result), buildSummary(result), !result.success()).open();
        } catch (Exception e) {
            String message = e.getMessage() != null ? e.getMessage() : e.toString();
            new ResultDialog(getTranslation("surveyDefinitionApplyView.applyFailed"), message, true).open();
        }
    }

    private String titleFor(SurveyDefinitionApplyService.ApplyResult result) {
        if (!result.success()) {
            return getTranslation("surveyDefinitionApplyView.applyFailed");
        }
        return result.action() == SurveyDefinitionApplyService.ApplyResult.Action.IMPORT
                ? getTranslation("surveyDefinitionApplyView.newSurveyInstalled")
                : getTranslation("surveyDefinitionApplyView.surveyUpdated");
    }

    /**
     * Renders the routing decision first — which operation happened is the thing an administrator
     * could not have predicted from the file alone — then the per-table detail beneath it.
     * <p>
     * Returns the summary as sections of lines rather than as one newline-separated string, so
     * {@link ResultDialog} can lay each line out as its own element; see that class for why a
     * pre-formatted string did not survive rendering.
     */
    private List<ResultDialog.Section> buildSummary(SurveyDefinitionApplyService.ApplyResult result) {
        List<ResultDialog.Section> sections = new ArrayList<>();

        List<ResultDialog.Line> outcome = new ArrayList<>();
        outcome.add(ResultDialog.Line.message(result.message()));
        if (result.surveyKey() != null) {
            outcome.add(ResultDialog.Line.message(
                    getTranslation("surveyDefinitionApplyView.surveyKey", result.surveyKey())));
        }
        sections.add(new ResultDialog.Section(outcome));

        switch (result.detail()) {
            case SurveyDefinitionImportService.ImportResult imported -> {
                List<ResultDialog.Line> installed = new ArrayList<>();
                installed.add(ResultDialog.Line.heading(getTranslation(
                        "surveyDefinitionApplyView.recordsInstalled", imported.getRecordsImported())));
                imported.getCounts().forEach((table, count) ->
                        installed.add(ResultDialog.Line.detail(table + ": " + count)));
                sections.add(new ResultDialog.Section(installed));
                addErrors(sections, imported.getErrors());
            }
            case SurveyDefinitionUpdateService.UpdateResult updated -> {
                Map<String, SurveyDefinitionUpdateService.TableUpdateCounts> counts = updated.getCounts();
                if (counts != null) {
                    List<ResultDialog.Line> reconciled = new ArrayList<>();
                    reconciled.add(ResultDialog.Line.heading(
                            getTranslation("surveyDefinitionApplyView.countsHeader")));
                    counts.forEach((table, c) -> reconciled.add(ResultDialog.Line.detail(
                            table + ": " + c.created() + " / " + c.versioned()
                                    + " / " + c.unchanged() + " / " + c.retired())));
                    sections.add(new ResultDialog.Section(reconciled));
                }
                addErrors(sections, updated.getErrors());
            }
            default -> {
                // A rejection carries no per-table detail; the message above is the whole story.
            }
        }
        if (result.reporting() != null) {
            // Last, because it happened last: the definition is applied whatever this line says.
            sections.add(ResultDialog.Section.of(ResultDialog.Line.message(result.reporting())));
        }
        return sections;
    }

    private void addErrors(List<ResultDialog.Section> sections, List<String> errors) {
        if (errors != null && !errors.isEmpty()) {
            List<ResultDialog.Line> lines = new ArrayList<>();
            lines.add(ResultDialog.Line.heading(getTranslation("surveyDefinitionApplyView.errors")));
            errors.forEach(error -> lines.add(ResultDialog.Line.error(error)));
            sections.add(new ResultDialog.Section(lines));
        }
    }
}
