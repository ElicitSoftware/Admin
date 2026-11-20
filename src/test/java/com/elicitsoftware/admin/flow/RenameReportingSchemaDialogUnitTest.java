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

import com.elicitsoftware.model.Survey;
import com.elicitsoftware.service.ReportingSchemaRenameClient;
import com.elicitsoftware.service.ReportingSchemaRenameClient.Outcome;
import com.elicitsoftware.service.ReportingSchemaRenameClient.Status;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UC-030: Rename a Survey's Reporting Schema — {@link RenameReportingSchemaDialog} in a plain
 * browserless UI, with a scripted stand-in for the call to Survey. Covers the field check before
 * any call (BR-116), the rename itself left to Survey (BR-117), the warning about outside
 * consumers (BR-118) and the wording of every refusal.
 */
class RenameReportingSchemaDialogUnitTest extends PlainSmallViewTestSupport {

    private static final UUID KEY = UUID.fromString("00000000-0000-0000-0000-000000000042");

    private Survey survey;
    private final List<String> calls = new ArrayList<>();
    private final List<String> renamed = new ArrayList<>();
    private Outcome answer;
    private ReportingSchemaRenameClient client;

    @BeforeEach
    void stubs() {
        survey = new Survey();
        survey.name = "Family History";
        survey.surveyKey = KEY;
        survey.reportSchema = "report_family";
        client = new ReportingSchemaRenameClient() {
            @Override
            public Outcome rename(UUID surveyKey, String newName) {
                calls.add(surveyKey + ":" + newName);
                return answer;
            }
        };
    }

    private RenameReportingSchemaDialog open() {
        RenameReportingSchemaDialog dialog = new RenameReportingSchemaDialog(survey, client, renamed::add);
        dialog.open();
        roundTrip();
        return dialog;
    }

    private static TextField field(RenameReportingSchemaDialog dialog) {
        return byId(dialog, RenameReportingSchemaDialog.NAME_FIELD_ID, TextField.class);
    }

    private static Paragraph outcome(RenameReportingSchemaDialog dialog) {
        return byId(dialog, RenameReportingSchemaDialog.OUTCOME_ID, Paragraph.class);
    }

    private static Button footerButton(RenameReportingSchemaDialog dialog, String id) {
        return dialog.getFooter().getElement().getChildren()
                .map(e -> e.getComponent().orElseThrow())
                .filter(c -> c.getId().filter(id::equals).isPresent())
                .map(Button.class::cast).findFirst().orElseThrow();
    }

    @Test
    void given_builtSchema_when_opened_then_dialogShowsCurrentNameWarningAndPrefilledField() {
        RenameReportingSchemaDialog dialog = open();

        assertEquals(t("reportingRename.title", "Family History"), dialog.getHeaderTitle());
        assertTrue(dialog.isCloseOnEsc());
        assertFalse(dialog.isCloseOnOutsideClick());
        VerticalLayout body = (VerticalLayout) dialog.getChildren().findFirst().orElseThrow();
        assertFalse(body.isPadding());
        List<Component> parts = body.getChildren().toList();
        assertEquals(4, parts.size());
        assertEquals(t("reportingRename.current", "report_family"), ((Paragraph) parts.get(0)).getText());
        assertEquals(t("reportingRename.warning"), ((Paragraph) parts.get(1)).getText());
        TextField name = field(dialog);
        assertEquals(parts.get(2), name);
        assertEquals(t("reportingRename.nameLabel"), name.getLabel());
        assertEquals("report_family", name.getValue());
        assertEquals("100%", name.getWidth());
        assertEquals(ValueChangeMode.EAGER, name.getValueChangeMode());
        assertFalse(outcome(dialog).isVisible());

        Button cancel = footerButton(dialog, RenameReportingSchemaDialog.CANCEL_BUTTON_ID);
        assertEquals(t("reportingRename.btnCancel"), cancel.getText());
        Button rename = footerButton(dialog, RenameReportingSchemaDialog.RENAME_BUTTON_ID);
        assertEquals(t("reportingRename.btnRename"), rename.getText());
        assertTrue(rename.getThemeNames().contains(ButtonVariant.LUMO_PRIMARY.getVariantName()));
    }

    @Test
    void given_invalidName_when_typed_then_fieldShowsTheObjection() {
        RenameReportingSchemaDialog dialog = open();
        TextField name = field(dialog);

        test(name).setValue("Bad Name");

        assertTrue(name.isInvalid());
        assertEquals(t("reportingRename.namePattern"), name.getErrorMessage());

        test(name).setValue("good_name");

        assertFalse(name.isInvalid());
        assertEquals("", Objects.toString(name.getErrorMessage(), ""));
    }

    @Test
    void given_reservedName_when_renameClicked_then_noCallIsMade() {
        RenameReportingSchemaDialog dialog = open();
        test(field(dialog)).setValue("public");

        test(footerButton(dialog, RenameReportingSchemaDialog.RENAME_BUTTON_ID)).click();

        assertEquals(List.of(), calls);
        assertTrue(dialog.isOpened());
        assertEquals(t("reportingRename.nameReserved", "public"), field(dialog).getErrorMessage());
    }

    @Test
    void given_blankName_when_renameCalled_then_itIsRequiredAndNotSent() {
        RenameReportingSchemaDialog dialog = open();
        field(dialog).clear();

        dialog.rename();

        assertEquals(List.of(), calls);
        assertEquals(t("reportingRename.nameRequired"), field(dialog).getErrorMessage());
    }

    @Test
    void given_surveyRenames_when_renameClicked_then_trimmedNameIsSentCallerToldAndDialogClosed() {
        answer = new Outcome(Status.RENAMED, "ok", "report_new");
        RenameReportingSchemaDialog dialog = open();
        test(field(dialog)).setValue("  report_new  ");

        test(footerButton(dialog, RenameReportingSchemaDialog.RENAME_BUTTON_ID)).click();

        assertEquals(List.of(KEY + ":report_new"), calls);
        assertEquals(List.of("report_new"), renamed);
        assertFalse(dialog.isOpened());
        Notification shown = $(Notification.class).single();
        assertEquals(t("reportingRename.renamed", "Family History", "report_new"), test(shown).getText());
        assertEquals(5000, shown.getDuration());
        assertEquals(Notification.Position.BOTTOM_START, shown.getPosition());
        assertTrue(shown.hasThemeName(NotificationVariant.LUMO_SUCCESS.getVariantName()));
    }

    @Test
    void given_surveyRenamesWithoutNamingTheSchema_when_renamed_then_theRequestedNameIsUsed() {
        answer = new Outcome(Status.RENAMED, "ok", null);
        RenameReportingSchemaDialog dialog = open();
        test(field(dialog)).setValue("report_other");

        dialog.rename();

        assertEquals(List.of("report_other"), renamed);
        assertEquals(List.of(t("reportingRename.renamed", "Family History", "report_other")), notifications());
    }

    @Test
    void given_surveyRefuses_when_renameClicked_then_outcomeIsShownAndDialogStaysOpen() {
        answer = new Outcome(Status.TAKEN, "report_x belongs to another survey", "report_family");
        RenameReportingSchemaDialog dialog = open();
        test(field(dialog)).setValue("report_x");

        test(footerButton(dialog, RenameReportingSchemaDialog.RENAME_BUTTON_ID)).click();

        Paragraph outcome = outcome(dialog);
        assertTrue(outcome.isVisible());
        assertEquals(t("reportingRename.refusedTaken", "report_x belongs to another survey"), outcome.getText());
        assertTrue(dialog.isOpened());
        assertEquals(List.of(), renamed);
        assertEquals(List.of(), notifications());
    }

    @Test
    void given_openDialog_when_cancelClicked_then_itClosesWithoutACall() {
        RenameReportingSchemaDialog dialog = open();

        test(footerButton(dialog, RenameReportingSchemaDialog.CANCEL_BUTTON_ID)).click();

        assertFalse(dialog.isOpened());
        assertEquals(List.of(), calls);
    }

    @Test
    void given_eachStatus_when_messageAsked_then_itIsWordedForTheAdministrator() {
        assertEquals("done", RenameReportingSchemaDialog.messageFor(new Outcome(Status.RENAMED, "done", "s")));
        assertEquals(t("reportingRename.refusedInvalid", "m1"),
                RenameReportingSchemaDialog.messageFor(new Outcome(Status.INVALID, "m1", null)));
        assertEquals(t("reportingRename.refusedTaken", "m2"),
                RenameReportingSchemaDialog.messageFor(new Outcome(Status.TAKEN, "m2", null)));
        assertEquals(t("reportingRename.refusedUnbuilt"),
                RenameReportingSchemaDialog.messageFor(new Outcome(Status.UNBUILT, "m3", null)));
        assertEquals(t("reportingRename.refusedUnknown"),
                RenameReportingSchemaDialog.messageFor(new Outcome(Status.UNKNOWN, "m4", null)));
        assertEquals(t("reportingRename.failed", "m5"),
                RenameReportingSchemaDialog.messageFor(new Outcome(Status.FAILED, "m5", null)));
    }
}
