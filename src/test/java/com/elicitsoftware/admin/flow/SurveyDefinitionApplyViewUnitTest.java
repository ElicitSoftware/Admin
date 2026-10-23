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
import com.elicitsoftware.service.SurveyDefinitionApplyService.ApplyResult;
import com.elicitsoftware.service.SurveyDefinitionApplyService.ApplyResult.Action;
import com.elicitsoftware.service.SurveyDefinitionImportService;
import com.elicitsoftware.service.SurveyDefinitionUpdateService;
import com.elicitsoftware.service.SurveyDefinitionUpdateService.TableUpdateCounts;
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

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.elicitsoftware.admin.flow.ResultDialogSupport.detailsIn;
import static com.elicitsoftware.admin.flow.ResultDialogSupport.errorsIn;
import static com.elicitsoftware.admin.flow.ResultDialogSupport.headingsIn;
import static com.elicitsoftware.admin.flow.ResultDialogSupport.messagesIn;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UC-018: Apply Survey Definition — {@link SurveyDefinitionApplyView} in a plain browserless UI
 * with a scripted stand-in for the apply service. Covers the single-file upload, the outcome
 * titled by what the routing chose (BR-073), the per-table summary of an import or an update,
 * and the reporting-schema line reported last (BR-107, BR-108).
 *
 * <p>A refused file (BR-075) carries no detail; the failure dialog shows the refusal reason
 * (#131: the view's pattern {@code switch} used to throw on the null detail).</p>
 */
class SurveyDefinitionApplyViewUnitTest extends PlainSmallViewTestSupport {

    private static final UUID KEY = UUID.fromString("00000000-0000-0000-0000-0000000000aa");

    private final List<String> applied = new ArrayList<>();
    private Object answer;
    private SurveyDefinitionApplyView view;

    @BeforeEach
    void build() {
        view = new SurveyDefinitionApplyView();
        view.applyService = new SurveyDefinitionApplyService() {
            @Override
            public ApplyResult apply(byte[] data, String fileName) {
                applied.add(fileName + ":" + new String(data, StandardCharsets.UTF_8));
                if (answer instanceof RuntimeException e) {
                    throw e;
                }
                return (ApplyResult) answer;
            }
        };
        UI.getCurrent().add(view);
    }

    private ResultDialog openDialog() {
        List<Dialog> dialogs = $(Dialog.class).all();
        assertEquals(1, dialogs.size(), "exactly one result dialog");
        assertTrue(dialogs.get(0).isOpened());
        return (ResultDialog) dialogs.get(0);
    }

    private static String color(ResultDialog dialog) {
        return new com.vaadin.browserless.ComponentQuery<>(com.vaadin.flow.component.html.Div.class)
                .withClassName("elicit-result-message").from(dialog).first().getStyle().get("color");
    }

    @Test
    void given_view_when_built_then_introAndSingleFileUploadAreShown() {
        List<Component> parts = view.getChildren().toList();
        assertEquals(t("surveyDefinitionApplyView.title"), ((H3) parts.get(0)).getText());
        assertEquals(t("surveyDefinitionApplyView.intro"), ((Paragraph) parts.get(1)).getText());
        assertEquals(t("surveyDefinitionApplyView.revisionNote"), ((Paragraph) parts.get(2)).getText());
        Upload upload = (Upload) parts.get(3);
        assertEquals("survey-apply-upload", upload.getId().orElseThrow());
        assertEquals(List.of(".elicit"), upload.getAcceptedFileExtensions());
        assertEquals(1, upload.getMaxFiles());
        assertEquals(SurveyDefinitionApplyService.MAX_FILE_BYTES, upload.getMaxFileSize());
        Button button = (Button) upload.getUploadButton();
        assertEquals(t("surveyDefinitionApplyView.upload"), button.getText());
        assertTrue(button.getThemeNames().contains(ButtonVariant.LUMO_PRIMARY.getVariantName()));
        assertEquals("100%", view.getWidth());
        assertEquals("100%", view.getHeight());
    }

    @Test
    void given_newSurveyFile_when_uploaded_then_importIsSummarisedPerTable() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("surveys", 1);
        counts.put("questions", 40);
        answer = new ApplyResult(Action.IMPORT, KEY, true, "Imported as a new survey",
                new SurveyDefinitionImportService.ImportResult(true, 41, List.of(), counts), "Reporting schema rebuilt.");
        Upload upload = (Upload) view.getComponentAt(3);

        test(upload).upload("family.elicit", "application/octet-stream", "surveys: x".getBytes(StandardCharsets.UTF_8));

        assertEquals(List.of("family.elicit:surveys: x"), applied);
        ResultDialog dialog = openDialog();
        assertEquals(t("surveyDefinitionApplyView.newSurveyInstalled"), dialog.getHeaderTitle());
        assertEquals(List.of("Imported as a new survey", t("surveyDefinitionApplyView.surveyKey", KEY),
                "Reporting schema rebuilt."), messagesIn(dialog));
        assertEquals(List.of(t("surveyDefinitionApplyView.recordsInstalled", 41)), headingsIn(dialog));
        assertEquals(List.of("surveys: 1", "questions: 40"), detailsIn(dialog));
        assertEquals(List.of(), errorsIn(dialog));
        assertEquals("var(--lumo-success-text-color, hsl(145, 72%, 30%))", color(dialog));
    }

    @Test
    void given_importWithErrors_when_applied_then_errorsAreListedUnderTheirHeading() {
        answer = new ApplyResult(Action.IMPORT, KEY, true, "Imported",
                new SurveyDefinitionImportService.ImportResult(true, 0, List.of("bad row 3", "bad row 9"), Map.of()));

        view.handleUpload(new byte[0], "f.elicit");

        ResultDialog dialog = openDialog();
        assertEquals(List.of(t("surveyDefinitionApplyView.recordsInstalled", 0), t("surveyDefinitionApplyView.errors")),
                headingsIn(dialog));
        assertEquals(List.of("bad row 3", "bad row 9"), errorsIn(dialog));
        assertEquals(List.of("Imported", t("surveyDefinitionApplyView.surveyKey", KEY)), messagesIn(dialog),
                "no reporting line when none was reported");
    }

    @Test
    void given_revisionFile_when_applied_then_updateCountsAreSummarised() {
        Map<String, TableUpdateCounts> counts = new LinkedHashMap<>();
        counts.put("questions", new TableUpdateCounts(1, 2, 3, 4));
        answer = new ApplyResult(Action.UPDATE, KEY, true, "Updated",
                new SurveyDefinitionUpdateService.UpdateResult(true, List.of(), counts),
                "Reporting schema not rebuilt: Survey unreachable");

        view.handleUpload(new byte[0], "f.elicit");

        ResultDialog dialog = openDialog();
        assertEquals(t("surveyDefinitionApplyView.surveyUpdated"), dialog.getHeaderTitle());
        assertEquals(List.of(t("surveyDefinitionApplyView.countsHeader")), headingsIn(dialog));
        assertEquals(List.of("questions: 1 / 2 / 3 / 4"), detailsIn(dialog));
        assertEquals("Reporting schema not rebuilt: Survey unreachable",
                messagesIn(dialog).get(messagesIn(dialog).size() - 1), "the reporting outcome reads last");
    }

    @Test
    void given_failedUpdateWithoutCounts_when_applied_then_onlyItsErrorsAreListed() {
        answer = new ApplyResult(Action.UPDATE, KEY, false, "Update failed",
                new SurveyDefinitionUpdateService.UpdateResult(false, List.of("older revision"), null));

        view.handleUpload(new byte[0], "f.elicit");

        ResultDialog dialog = openDialog();
        assertEquals(t("surveyDefinitionApplyView.applyFailed"), dialog.getHeaderTitle());
        assertEquals(List.of(t("surveyDefinitionApplyView.errors")), headingsIn(dialog));
        assertEquals(List.of("older revision"), errorsIn(dialog));
        assertEquals("var(--lumo-error-text-color, hsl(3, 85%, 48%))", color(dialog));
    }

    @Test
    void given_updateWithEmptyErrorList_when_applied_then_noErrorsHeadingIsShown() {
        answer = new ApplyResult(Action.UPDATE, KEY, true, "Updated",
                new SurveyDefinitionUpdateService.UpdateResult(true, List.of(), Map.of()));

        view.handleUpload(new byte[0], "f.elicit");

        assertEquals(List.of(t("surveyDefinitionApplyView.countsHeader")), headingsIn(openDialog()));
    }

    @Test
    void given_updateWithoutKeyOrErrorList_when_applied_then_onlyTheMessageIsShown() {
        answer = new ApplyResult(Action.UPDATE, null, true, "Updated",
                new SurveyDefinitionUpdateService.UpdateResult(true, null, null));

        view.handleUpload(new byte[0], "f.elicit");

        ResultDialog dialog = openDialog();
        assertEquals(List.of(), headingsIn(dialog));
        assertEquals(List.of("Updated"), messagesIn(dialog), "no survey key line without a key");
    }

    @Test
    void given_aRefusedFile_when_applied_then_theFailureDialogShowsTheReason() {
        // UC-018 BR-075 (#131): a refusal carries no per-table detail (a null detail), and the
        // summary used to throw on it, so the dialog showed a NullPointerException instead.
        answer = new ApplyResult(Action.REJECTED, null, false, "The file has no survey key.", null);

        view.handleUpload(new byte[0], "f.elicit");

        ResultDialog dialog = openDialog();
        assertEquals(t("surveyDefinitionApplyView.applyFailed"), dialog.getHeaderTitle());
        assertEquals(List.of("The file has no survey key."), messagesIn(dialog));
        assertEquals(List.of(), headingsIn(dialog));
    }

    @Test
    void given_aRefusedFileWithAKey_when_applied_then_theReasonAndTheKeyAreShown() {
        // UC-018 BR-075 (#131): a refusal that read the survey key names it under the reason.
        answer = new ApplyResult(Action.REJECTED, KEY, false, "The file is empty.", null);

        view.handleUpload(new byte[0], "f.elicit");

        ResultDialog dialog = openDialog();
        assertEquals(t("surveyDefinitionApplyView.applyFailed"), dialog.getHeaderTitle());
        assertEquals(List.of("The file is empty.", t("surveyDefinitionApplyView.surveyKey", KEY)), messagesIn(dialog));
    }

    @Test
    void given_serviceThrows_when_applied_then_failureDialogCarriesTheMessage() {
        answer = new IllegalStateException("disk full");

        view.handleUpload(new byte[0], "f.elicit");

        ResultDialog dialog = openDialog();
        assertEquals(t("surveyDefinitionApplyView.applyFailed"), dialog.getHeaderTitle());
        assertEquals(List.of("disk full"), messagesIn(dialog));
    }

    @Test
    void given_serviceThrowsWithoutMessage_when_applied_then_theExceptionItselfIsShown() {
        answer = new IllegalStateException();

        view.handleUpload(new byte[0], "f.elicit");

        assertEquals(List.of("java.lang.IllegalStateException"), messagesIn(openDialog()));
    }
}
