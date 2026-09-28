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

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

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
            String title = result.isSuccess() ? "Import Successful" : "Import Failed";
            new ResultDialog(title, buildSummary(result), !result.isSuccess()).open();
        } catch (Exception e) {
            String message = e.getMessage() != null ? e.getMessage() : e.toString();
            new ResultDialog("Import Failed", message, true).open();
        }
    }

    /**
     * Returns the summary as sections of lines rather than as one newline-separated string, so
     * {@link ResultDialog} can lay each line out as its own element; see that class for why a
     * pre-formatted string did not survive rendering.
     */
    private List<ResultDialog.Section> buildSummary(RespondentImportService.ImportResult result) {
        List<ResultDialog.Section> sections = new ArrayList<>();

        List<ResultDialog.Line> imported = new ArrayList<>();
        imported.add(ResultDialog.Line.heading("Records imported: " + result.getRecordsImported()));
        result.getCounts().forEach((table, count) ->
                imported.add(ResultDialog.Line.detail(table + ": " + count)));
        sections.add(new ResultDialog.Section(imported));

        if (!result.getErrors().isEmpty()) {
            List<ResultDialog.Line> errors = new ArrayList<>();
            errors.add(ResultDialog.Line.heading("Errors:"));
            result.getErrors().forEach(error -> errors.add(ResultDialog.Line.error(error)));
            sections.add(new ResultDialog.Section(errors));
        }
        return sections;
    }
}
