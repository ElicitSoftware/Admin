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

import com.elicitsoftware.service.RespondentImportService;
import com.elicitsoftware.service.RespondentImportService.ImportResult;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.upload.Upload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.elicitsoftware.admin.flow.ResultDialogSupport.detailsIn;
import static com.elicitsoftware.admin.flow.ResultDialogSupport.errorsIn;
import static com.elicitsoftware.admin.flow.ResultDialogSupport.headingsIn;
import static com.elicitsoftware.admin.flow.ResultDialogSupport.messagesIn;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UC-012: Import Respondent Data — {@link RespondentImportView} in a plain browserless UI with a
 * scripted stand-in for the import service. Covers the single-file upload, the per-table summary
 * of a successful import, the errors of a refused one (BR-042) and an unexpected failure.
 */
class RespondentImportViewUnitTest extends PlainSmallViewTestSupport {

    private final List<String> imported = new ArrayList<>();
    private Object answer;
    private RespondentImportView view;

    @BeforeEach
    void build() {
        view = new RespondentImportView();
        view.respondentImportService = new RespondentImportService() {
            @Override
            public ImportResult importFromFile(InputStream inputStream) {
                try {
                    imported.add(new String(inputStream.readAllBytes(), StandardCharsets.UTF_8));
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
                if (answer instanceof RuntimeException e) {
                    throw e;
                }
                return (ImportResult) answer;
            }
        };
        UI.getCurrent().add(view);
    }

    private static final String ERROR_COLOR = "var(--lumo-error-text-color, hsl(3, 85%, 48%))";
    private static final String SUCCESS_COLOR = "var(--lumo-success-text-color, hsl(145, 72%, 30%))";

    private static String headingColor(ResultDialog dialog) {
        return new com.vaadin.browserless.ComponentQuery<>(com.vaadin.flow.component.html.Div.class)
                .withClassName("elicit-result-heading").from(dialog).first().getStyle().get("color");
    }

    private ResultDialog openDialog() {
        List<Dialog> dialogs = $(Dialog.class).all();
        assertEquals(1, dialogs.size());
        assertTrue(dialogs.get(0).isOpened());
        return (ResultDialog) dialogs.get(0);
    }

    @Test
    void given_view_when_built_then_introAndSingleFileUploadAreShown() {
        List<Component> parts = view.getChildren().toList();
        assertEquals(t("respondentImportView.title"), ((H3) parts.get(0)).getText());
        assertEquals(t("respondentImportView.intro"), ((Paragraph) parts.get(1)).getText());
        Upload upload = (Upload) parts.get(2);
        assertEquals("respondent-import-upload", upload.getId().orElseThrow());
        assertEquals(List.of(".elicit"), upload.getAcceptedFileExtensions());
        assertEquals(1, upload.getMaxFiles());
        assertEquals(5 * 1024 * 1024, upload.getMaxFileSize());
        Button button = (Button) upload.getUploadButton();
        assertEquals(t("respondentImportView.upload"), button.getText());
        assertTrue(button.getThemeNames().contains(ButtonVariant.LUMO_PRIMARY.getVariantName()));
        assertEquals("100%", view.getWidth());
        assertEquals("100%", view.getHeight());
    }

    @Test
    void given_validExport_when_uploaded_then_importIsSummarisedPerTable() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("respondents", 1);
        counts.put("answers", 12);
        answer = new ImportResult(true, 13, List.of(), counts);

        test((Upload) view.getComponentAt(2)).upload("r.elicit", "application/octet-stream",
                "respondent".getBytes(StandardCharsets.UTF_8));

        assertEquals(List.of("respondent"), imported);
        ResultDialog dialog = openDialog();
        assertEquals(t("respondentImportView.importSuccessful"), dialog.getHeaderTitle());
        assertEquals(List.of(t("respondentImportView.recordsImported", 13)), headingsIn(dialog));
        assertEquals(List.of("respondents: 1", "answers: 12"), detailsIn(dialog));
        assertEquals(List.of(), errorsIn(dialog));
        assertEquals(SUCCESS_COLOR, headingColor(dialog));
    }

    @Test
    void given_refusedImport_when_uploaded_then_errorsAreListed() {
        answer = new ImportResult(false, 0, List.of("department CARD not found"), Map.of());

        view.handleUpload(new byte[0]);

        ResultDialog dialog = openDialog();
        assertEquals(t("respondentImportView.importFailed"), dialog.getHeaderTitle());
        assertEquals(List.of(t("respondentImportView.recordsImported", 0), t("respondentImportView.errors")),
                headingsIn(dialog));
        assertEquals(List.of("department CARD not found"), errorsIn(dialog));
        assertEquals(ERROR_COLOR, headingColor(dialog));
    }

    @Test
    void given_serviceThrows_when_uploaded_then_failureDialogCarriesTheMessage() {
        answer = new IllegalStateException("corrupt file");

        view.handleUpload(new byte[0]);

        ResultDialog dialog = openDialog();
        assertEquals(t("respondentImportView.importFailed"), dialog.getHeaderTitle());
        assertEquals(List.of("corrupt file"), messagesIn(dialog));
    }

    @Test
    void given_serviceThrowsWithoutMessage_when_uploaded_then_theExceptionItselfIsShown() {
        answer = new IllegalStateException();

        view.handleUpload(new byte[0]);

        assertEquals(List.of("java.lang.IllegalStateException"), messagesIn(openDialog()));
    }
}
