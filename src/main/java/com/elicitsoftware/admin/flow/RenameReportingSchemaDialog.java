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

import com.elicitsoftware.admin.i18n.Translations;
import com.elicitsoftware.model.Survey;
import com.elicitsoftware.service.ReportingSchemaRenameClient;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;

import java.util.function.Consumer;

/**
 * The dialog that renames a survey's reporting schema (UC-030): names the survey and its current
 * schema, warns that queries and BI connections outside Elicit which name the old schema stop
 * working (BR-118), checks the typed name as the administrator types it (BR-116), and asks the
 * Survey application to do the rename (BR-117).
 * <p>
 * A refusal, from the check or from Survey, is shown in the dialog and leaves it open with the
 * typed name, so the administrator can correct it; nothing has changed on either side.
 */
final class RenameReportingSchemaDialog extends Dialog {

    /** Element id of the name field. */
    static final String NAME_FIELD_ID = "rename-reporting-schema-name";

    /** Element id of the Rename button. */
    static final String RENAME_BUTTON_ID = "rename-reporting-schema-rename";

    /** Element id of the Cancel button. */
    static final String CANCEL_BUTTON_ID = "rename-reporting-schema-cancel";

    /** Element id of the line that shows Survey's refusal or failure. */
    static final String OUTCOME_ID = "rename-reporting-schema-outcome";

    private final Survey survey;
    private final ReportingSchemaRenameClient client;
    private final Consumer<String> onRenamed;
    private final TextField name;
    private final Paragraph outcome;

    /**
     * @param survey    the survey whose schema is renamed; must have one
     * @param client    the call to Survey
     * @param onRenamed told the new name once Survey has renamed the schema
     */
    RenameReportingSchemaDialog(Survey survey, ReportingSchemaRenameClient client, Consumer<String> onRenamed) {
        this.survey = survey;
        this.client = client;
        this.onRenamed = onRenamed;
        setHeaderTitle(Translations.get("reportingRename.title", survey.name));
        setCloseOnEsc(true);
        setCloseOnOutsideClick(false);

        name = new TextField(Translations.get("reportingRename.nameLabel"));
        name.setId(NAME_FIELD_ID);
        name.setValue(survey.reportSchema);
        name.setWidthFull();
        name.setValueChangeMode(ValueChangeMode.EAGER);
        name.addValueChangeListener(event -> check());

        outcome = new Paragraph();
        outcome.setId(OUTCOME_ID);
        outcome.setVisible(false);

        VerticalLayout body = new VerticalLayout(
                new Paragraph(Translations.get("reportingRename.current", survey.reportSchema)),
                new Paragraph(Translations.get("reportingRename.warning")),
                name,
                outcome);
        body.setPadding(false);
        add(body);

        Button cancel = new Button(Translations.get("reportingRename.btnCancel"), event -> close());
        cancel.setId(CANCEL_BUTTON_ID);
        Button rename = new Button(Translations.get("reportingRename.btnRename"), event -> rename());
        rename.setId(RENAME_BUTTON_ID);
        rename.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        getFooter().add(cancel, rename);
    }

    /** BR-116, as the administrator types: the field says why a name is not acceptable. */
    private boolean check() {
        String objection = ReportingSchemaRenameClient.objection(name.getValue() == null ? null : name.getValue().trim());
        name.setErrorMessage(objection);
        name.setInvalid(objection != null);
        return objection == null;
    }

    /** UC-030 steps 6-8: ask Survey, and either close on success or show the refusal. */
    void rename() {
        if (!check()) {
            return;
        }
        String newName = name.getValue().trim();
        ReportingSchemaRenameClient.Outcome result = client.rename(survey.surveyKey, newName);
        if (result.status() == ReportingSchemaRenameClient.Status.RENAMED) {
            String renamed = result.schema() == null ? newName : result.schema();
            Notification.show(Translations.get("reportingRename.renamed", survey.name, renamed), 5000,
                    Notification.Position.BOTTOM_START).addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            onRenamed.accept(renamed);
            close();
            return;
        }
        outcome.setText(messageFor(result));
        outcome.setVisible(true);
    }

    /** Survey's refusal, in the administrator's language where this console knows the reason. */
    static String messageFor(ReportingSchemaRenameClient.Outcome result) {
        return switch (result.status()) {
            case RENAMED -> result.message();
            case INVALID -> Translations.get("reportingRename.refusedInvalid", result.message());
            case TAKEN -> Translations.get("reportingRename.refusedTaken", result.message());
            case UNBUILT -> Translations.get("reportingRename.refusedUnbuilt");
            case UNKNOWN -> Translations.get("reportingRename.refusedUnknown");
            case FAILED -> Translations.get("reportingRename.failed", result.message());
        };
    }
}
