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
import com.vaadin.flow.component.ModalityMode;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.streams.UploadHandler;
import com.vaadin.flow.theme.lumo.LumoUtility;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;

import java.util.Map;

/**
 * Admin-only view for applying an authored survey definition (.elicit) to this deployment —
 * UC-018. The administrator uploads the file and nothing else: the file's stable survey key
 * decides whether this deployment is seeing the survey for the first time (install, UC-014) or
 * already has it (update in place, UC-017), and for an update the file's revision must not
 * predate the one already installed here.
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

        add(new H3("Apply Survey Definition"));
        add(new Paragraph("Upload a survey definition file (.elicit) exported from the Author "
                + "tool. You do not need to say whether this is a new survey or a revision of one "
                + "already here — the file identifies itself, and this instance works out which "
                + "it is."));
        add(new Paragraph("A revision older than the one already installed is refused. Reverting "
                + "to an earlier revision is a database restore, not an upload."));

        Upload upload = new Upload();
        upload.setId("survey-apply-upload");
        upload.setAcceptedFileTypes(".elicit");
        upload.setMaxFiles(1);
        upload.setMaxFileSize(SurveyDefinitionApplyService.MAX_FILE_BYTES);

        Button uploadButton = new Button("Upload");
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
            showResultDialog(titleFor(result), buildSummary(result), !result.success());
        } catch (Exception e) {
            showResultDialog("Apply Failed", e.getMessage(), true);
        }
    }

    private String titleFor(SurveyDefinitionApplyService.ApplyResult result) {
        if (!result.success()) {
            return "Apply Failed";
        }
        return result.action() == SurveyDefinitionApplyService.ApplyResult.Action.IMPORT
                ? "New Survey Installed"
                : "Survey Updated";
    }

    /**
     * Renders the routing decision first — which operation happened is the thing an administrator
     * could not have predicted from the file alone — then the per-table detail beneath it.
     */
    private String buildSummary(SurveyDefinitionApplyService.ApplyResult result) {
        StringBuilder summary = new StringBuilder();
        summary.append(result.message()).append("\n");
        if (result.surveyKey() != null) {
            summary.append("Survey key: ").append(result.surveyKey()).append("\n");
        }

        switch (result.detail()) {
            case SurveyDefinitionImportService.ImportResult imported -> {
                summary.append("\nRecords installed: ").append(imported.getRecordsImported()).append("\n");
                imported.getCounts().forEach((table, count) ->
                        summary.append("  ").append(table).append(": ").append(count).append("\n"));
                appendErrors(summary, imported.getErrors());
            }
            case SurveyDefinitionUpdateService.UpdateResult updated -> {
                Map<String, SurveyDefinitionUpdateService.TableUpdateCounts> counts = updated.getCounts();
                if (counts != null) {
                    summary.append("\ncreated / versioned / unchanged / retired:\n");
                    counts.forEach((table, c) -> summary.append("  ").append(table).append(": ")
                            .append(c.created()).append(" / ").append(c.versioned())
                            .append(" / ").append(c.unchanged()).append(" / ").append(c.retired()).append("\n"));
                }
                appendErrors(summary, updated.getErrors());
            }
            default -> {
                // A rejection carries no per-table detail; the message above is the whole story.
            }
        }
        return summary.toString();
    }

    private void appendErrors(StringBuilder summary, java.util.List<String> errors) {
        if (errors != null && !errors.isEmpty()) {
            summary.append("\nErrors:\n");
            errors.forEach(error -> summary.append("  ").append(error).append("\n"));
        }
    }

    private void showResultDialog(String title, String message, boolean isError) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(title);

        Span messageSpan = new Span(message);
        messageSpan.addClassName(LumoUtility.Whitespace.PRE_WRAP);
        messageSpan.addClassName(isError ? LumoUtility.TextColor.ERROR : LumoUtility.TextColor.SUCCESS);

        Button closeButton = new Button("Close", evt -> dialog.close());
        closeButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        closeButton.addClassName(LumoUtility.Margin.Top.MEDIUM);

        VerticalLayout dialogLayout = new VerticalLayout(messageSpan, closeButton);
        dialogLayout.setAlignItems(Alignment.CENTER);
        dialogLayout.setSpacing(true);

        dialog.add(dialogLayout);
        dialog.setModality(ModalityMode.STRICT);
        dialog.setDraggable(false);
        dialog.setResizable(true);
        dialog.setWidth("600px");
        dialog.setMaxWidth("90vw");

        dialog.open();
    }
}
