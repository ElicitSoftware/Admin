package com.elicitsoftware.admin.flow;

/*-
 * ***LICENSE_START***
 * Elicit Admin
 * %%
 * Copyright (C) 2025 - 2026 The Regents of the University of Michigan - Rogel Cancer Center
 * %%
 * PolyForm Noncommercial License 1.0.0
 * <https://polyformproject.org/licenses/noncommercial/1.0.0>
 * ***LICENSE_END***
 */

import com.elicitsoftware.model.Survey;
import com.elicitsoftware.service.ReportingSchemaRenameClient;
import com.elicitsoftware.test.PostgresTestResource;
import com.vaadin.browserless.quarkus.QuarkusBrowserlessTest;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.textfield.TextField;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link RenameReportingSchemaDialog} over a scripted {@link ReportingSchemaRenameClient}:
 * UC-030 steps 3-8, A2 (the field refuses a bad name and nothing is sent), A3 (Survey's refusal
 * stays in the dialog with the typed name) and BR-118 (the warning is shown).
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class RenameReportingSchemaDialogTest extends QuarkusBrowserlessTest {

    /** A client that answers what it is told and remembers what it was asked. */
    private static final class ScriptedClient extends ReportingSchemaRenameClient {
        final List<String> asked = new ArrayList<>();
        Outcome answer;

        @Override
        public Outcome rename(UUID surveyKey, String newName) {
            asked.add(newName);
            return answer;
        }
    }

    private static Survey survey() {
        Survey survey = new Survey();
        survey.name = "Family History Survey";
        survey.surveyKey = UUID.randomUUID();
        survey.reportSchema = "report_family_history_survey";
        return survey;
    }

    private TextField nameField(RenameReportingSchemaDialog dialog) {
        return find(TextField.class, dialog).single();
    }

    /** The outcome line, found by a plain tree walk: the harness's finders skip hidden components. */
    private Paragraph outcome(RenameReportingSchemaDialog dialog) {
        return walk(dialog)
                .filter(c -> RenameReportingSchemaDialog.OUTCOME_ID.equals(c.getId().orElse(null)))
                .map(Paragraph.class::cast)
                .findFirst().orElseThrow();
    }

    private static java.util.stream.Stream<com.vaadin.flow.component.Component> walk(com.vaadin.flow.component.Component root) {
        return java.util.stream.Stream.concat(java.util.stream.Stream.of(root), root.getChildren().flatMap(RenameReportingSchemaDialogTest::walk));
    }

    @Test
    void opensWithTheCurrentNameAndTheWarning() {
        ScriptedClient client = new ScriptedClient();
        RenameReportingSchemaDialog dialog = new RenameReportingSchemaDialog(survey(), client, renamed -> { });
        dialog.open();

        assertEquals("report_family_history_survey", nameField(dialog).getValue(), "step 3: the field starts at the current name");
        assertTrue(find(Paragraph.class, dialog).all().stream()
                .anyMatch(p -> p.getText().contains("outside Elicit")), "BR-118: the warning is about the outside");
        assertFalse(outcome(dialog).isVisible());
    }

    @Test
    void badNameIsRefusedInTheFieldAndNothingIsSent() {
        ScriptedClient client = new ScriptedClient();
        RenameReportingSchemaDialog dialog = new RenameReportingSchemaDialog(survey(), client, renamed -> { });
        dialog.open();

        nameField(dialog).setValue("Report-FHH");
        assertTrue(nameField(dialog).isInvalid(), "BR-116: checked as typed");

        dialog.rename();

        assertTrue(client.asked.isEmpty(), "A2: an unacceptable name is never sent");
        assertTrue(dialog.isOpened());
    }

    @Test
    void surveysRefusalStaysInTheDialog() {
        ScriptedClient client = new ScriptedClient();
        client.answer = new ReportingSchemaRenameClient.Outcome(ReportingSchemaRenameClient.Status.TAKEN,
                "The name report_fhh is already in use.", "report_family_history_survey");
        List<String> renamed = new ArrayList<>();
        RenameReportingSchemaDialog dialog = new RenameReportingSchemaDialog(survey(), client, renamed::add);
        dialog.open();

        nameField(dialog).setValue("report_fhh");
        dialog.rename();

        assertEquals(List.of("report_fhh"), client.asked);
        assertTrue(dialog.isOpened(), "A3: the dialog stays open");
        assertEquals("report_fhh", nameField(dialog).getValue(), "A3: with the typed name");
        assertTrue(outcome(dialog).isVisible());
        assertTrue(outcome(dialog).getText().contains("report_fhh"), outcome(dialog).getText());
        assertTrue(renamed.isEmpty());
    }

    @Test
    void successTellsTheCallerAndCloses() {
        ScriptedClient client = new ScriptedClient();
        client.answer = new ReportingSchemaRenameClient.Outcome(ReportingSchemaRenameClient.Status.RENAMED,
                "renamed", "report_fhh");
        List<String> renamed = new ArrayList<>();
        RenameReportingSchemaDialog dialog = new RenameReportingSchemaDialog(survey(), client, renamed::add);
        dialog.open();

        nameField(dialog).setValue("report_fhh");
        dialog.rename();

        assertEquals(List.of("report_fhh"), renamed, "step 8: the caller learns the new name");
        assertFalse(dialog.isOpened());
    }
}
