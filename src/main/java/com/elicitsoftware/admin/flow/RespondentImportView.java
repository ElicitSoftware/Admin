package com.elicitsoftware.admin.flow;

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

import com.elicitsoftware.service.RespondentImportService;
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

import java.io.ByteArrayInputStream;
import java.io.InputStream;

/**
 * Admin-only view for importing a respondent data file previously produced by the
 * "Export" action on the Search Subjects grid. Complements
 * {@link com.elicitsoftware.rest.RespondentExportResource} /
 * {@link com.elicitsoftware.rest.RespondentImportResource}, which expose the same
 * capability over REST.
 */
@Route(value = "respondent-import", layout = MainLayout.class)
@RolesAllowed("elicit_admin")
public class RespondentImportView extends VerticalLayout {

    @Inject
    RespondentImportService respondentImportService;

    public RespondentImportView() {
        setSizeFull();

        add(new H3("Import Respondent"));
        add(new Paragraph("Upload a respondent export file (.elicit) produced by the \"Export\" "
                + "action on the Search Subjects grid to import that respondent into this instance."));

        Upload upload = new Upload();
        upload.setId("respondent-import-upload");
        upload.setAcceptedFileTypes(".elicit");
        upload.setMaxFiles(1);
        upload.setMaxFileSize(5 * 1024 * 1024); // 5MB limit

        Button uploadButton = new Button("Upload");
        uploadButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        upload.setUploadButton(uploadButton);

        upload.setUploadHandler(UploadHandler.inMemory((metadata, data) -> handleUpload(data)));

        add(upload);
    }

    /**
     * Imports an uploaded export file and shows the outcome in a result dialog. Package-private
     * so tests can drive it directly without simulating a real file upload.
     *
     * @param data the raw bytes of the uploaded file
     */
    void handleUpload(byte[] data) {
        try {
            InputStream inputStream = new ByteArrayInputStream(data);
            RespondentImportService.ImportResult result = respondentImportService.importFromFile(inputStream);
            if (result.isSuccess()) {
                showResultDialog("Import Successful", buildSummary(result), false);
            } else {
                showResultDialog("Import Failed", buildSummary(result), true);
            }
        } catch (Exception e) {
            showResultDialog("Import Failed", e.getMessage(), true);
        }
    }

    private String buildSummary(RespondentImportService.ImportResult result) {
        StringBuilder summary = new StringBuilder();
        summary.append("Records imported: ").append(result.getRecordsImported()).append("\n");
        result.getCounts().forEach((table, count) -> summary.append("  ").append(table).append(": ").append(count).append("\n"));
        if (!result.getErrors().isEmpty()) {
            summary.append("\nErrors:\n");
            result.getErrors().forEach(error -> summary.append("  ").append(error).append("\n"));
        }
        return summary.toString();
    }

    private void showResultDialog(String title, String message, boolean isError) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(title);

        Span messageSpan = new Span(message);
        messageSpan.addClassName(LumoUtility.Whitespace.PRE_WRAP);
        if (isError) {
            messageSpan.addClassName(LumoUtility.TextColor.ERROR);
        } else {
            messageSpan.addClassName(LumoUtility.TextColor.SUCCESS);
        }

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
