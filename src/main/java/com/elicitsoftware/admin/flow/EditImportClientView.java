package com.elicitsoftware.admin.flow;

/*-
 * ***LICENSE_START***
 * Elicit Admin
 * %%
 * Copyright (C) 2025 - 2026 Elicit Software LLC
 * %%
 * PolyForm Noncommercial License 1.0.0
 * <https://polyformproject.org/licenses/noncommercial/1.0.0>
 * ***LICENSE_END***
 */

import com.elicitsoftware.model.Department;
import com.elicitsoftware.model.ImportClient;
import com.elicitsoftware.security.ElicitRoles;
import com.elicitsoftware.service.ImportClientService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.MultiSelectComboBox;
import com.vaadin.flow.component.combobox.MultiSelectComboBoxVariant;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.textfield.TextFieldVariant;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.binder.ValidationException;
import com.vaadin.flow.data.validator.StringLengthValidator;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Adds, changes, deactivates and deletes one import client (UC-032).
 *
 * <p>The name is the principal name the client's tokens carry (BR-165) and may not be one a
 * user holds (BR-166). The departments are the ones the registration API admits the client
 * for (UC-010 BR-137); the role {@code elicit_importer} is assigned in the identity provider
 * and is not edited here.</p>
 *
 * @see ImportClientsView
 */
@Route(value = "edit-import-client/:id?", layout = MainLayout.class)
@RolesAllowed(ElicitRoles.ADMIN)
public class EditImportClientView extends VerticalLayout implements BeforeEnterObserver, HasDynamicTitle {

    @Inject
    ImportClientService importClientService;

    private ImportClient client;

    private final TextField name = new TextField();
    private final TextField description = new TextField();
    private final Checkbox activeCheckbox = new Checkbox();
    private final MultiSelectComboBox<Department> departmentsBox = new MultiSelectComboBox<>();
    private final Binder<ImportClient> binder = new Binder<>(ImportClient.class);
    private final Button saveBtn = new Button();
    private final Button deleteBtn = new Button();

    /**
     * Builds the form. The record it edits is loaded in {@link #beforeEnter(BeforeEnterEvent)}.
     */
    public EditImportClientView() {
        name.setLabel(getTranslation("editImportClientView.name"));
        name.setHelperText(getTranslation("editImportClientView.name.helper"));
        name.addThemeVariants(TextFieldVariant.LUMO_SMALL);
        name.setRequiredIndicatorVisible(true);
        name.setWidth("28em");
        name.setMaxWidth("100%");

        description.setLabel(getTranslation("editImportClientView.description"));
        description.addThemeVariants(TextFieldVariant.LUMO_SMALL);
        description.setWidth("28em");
        description.setMaxWidth("100%");

        activeCheckbox.setLabel(getTranslation("editImportClientView.active"));

        departmentsBox.setLabel(getTranslation("editImportClientView.departments"));
        departmentsBox.setItemLabelGenerator(Department::getName);
        departmentsBox.addThemeVariants(MultiSelectComboBoxVariant.LUMO_SMALL);
        List<Department> allDepartments = Department.findAll().list();
        departmentsBox.setItems(allDepartments);

        add(name, description, activeCheckbox, departmentsBox);

        saveBtn.setText(getTranslation("common.save"));
        saveBtn.addClickListener(e -> save());
        saveBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Button cancelBtn = new Button(getTranslation("common.cancel"), e -> backToList());
        cancelBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        deleteBtn.setText(getTranslation("editImportClientView.delete"));
        deleteBtn.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY);
        deleteBtn.addClickListener(e -> confirmDelete());
        // A record that is not stored yet has nothing to delete; hidden rather than disabled.
        deleteBtn.setVisible(false);

        add(new HorizontalLayout(saveBtn, cancelBtn, deleteBtn));

        setupValidation();
    }

    private void setupValidation() {
        binder.forField(name)
                .asRequired(getTranslation("editImportClientView.error.nameRequired"))
                .withValidator(new StringLengthValidator(
                        getTranslation("editImportClientView.error.nameLength"), 1, 100))
                .bind(ImportClient::getName, ImportClient::setName);
        binder.forField(description)
                .withNullRepresentation("")
                .withValidator(new StringLengthValidator(
                        getTranslation("editImportClientView.error.descriptionLength"), 0, 255))
                .bind(ImportClient::getDescription, ImportClient::setDescription);
        binder.forField(activeCheckbox)
                .bind(ImportClient::isActive, ImportClient::setActive);

        binder.addStatusChangeListener(event -> saveBtn.setEnabled(binder.isValid()));
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        String idStr = event.getRouteParameters().get("id").orElse(null);
        if (idStr != null && !idStr.equals("0")) {
            long id;
            try {
                id = Long.parseLong(idStr);
            } catch (NumberFormatException e) {
                id = -1;
            }
            client = id > 0 ? ImportClient.findById(id) : null;
            if (client == null) {
                // UC-032 A1
                Notification.show(getTranslation("editImportClientView.error.notFound"));
                event.forwardTo(ImportClientsView.class);
                return;
            }
            if (client.getDepartments() != null) {
                departmentsBox.setValue(client.getDepartments());
            }
            deleteBtn.setVisible(true);
        } else {
            client = new ImportClient();
            client.setActive(true); // BR-167
        }
        binder.readBean(client);
    }

    /**
     * Validates the form and stores the import client (UC-032 steps 4 and 5). A name that a
     * user or another import client holds is refused and the form stays open (A2, BR-166).
     */
    public void save() {
        String enteredName = name.getValue() == null ? "" : name.getValue().trim();
        name.setValue(enteredName);
        if (!binder.validate().isOk()) {
            Notification.show(getTranslation("editImportClientView.error.fixValidation"));
            return;
        }
        switch (importClientService.conflictForImportClient(enteredName, client.getId())) {
            case USER -> {
                refuseName(getTranslation("editImportClientView.error.nameIsUser"));
                return;
            }
            case IMPORT_CLIENT -> {
                refuseName(getTranslation("editImportClientView.error.nameExists"));
                return;
            }
            case NONE -> {
                // free
            }
        }
        try {
            binder.writeBean(client);
        } catch (ValidationException e) {
            Notification.show(getTranslation("editImportClientView.error.fixValidation"));
            return;
        }
        if (client.getDescription() != null && client.getDescription().isBlank()) {
            client.setDescription(null);
        }
        // MultiSelectComboBox.getValue() returns an immutable Set, which Hibernate's merge()
        // cannot mutate in place on a later save -- copy into a fresh HashSet.
        Set<Department> selected = departmentsBox.getValue();
        client.setDepartments(selected != null ? new HashSet<>(selected) : new HashSet<>());

        importClientService.save(client);

        Notification.show(getTranslation("editImportClientView.saved"));
        backToList();
    }

    private void refuseName(String message) {
        name.setInvalid(true);
        name.setErrorMessage(message);
        Notification.show(message, 5000, Notification.Position.MIDDLE);
    }

    private void confirmDelete() {
        ConfirmDialog dialog = new ConfirmDialog();
        dialog.setHeader(getTranslation("editImportClientView.delete.title"));
        dialog.setText(getTranslation("editImportClientView.delete.text", client.getName()));
        dialog.setCancelable(true);
        dialog.setCancelText(getTranslation("common.cancel"));
        dialog.setConfirmText(getTranslation("editImportClientView.delete"));
        dialog.setConfirmButtonTheme("error primary"); // i18n:ignore (theme names)
        dialog.addConfirmListener(e -> delete());
        dialog.open();
    }

    /**
     * Removes the import client and its department assignments (UC-032 A4, BR-169) and returns
     * to the list. Reached from the confirmation dialog.
     */
    public void delete() {
        importClientService.delete(client.getId());
        Notification.show(getTranslation("editImportClientView.deleted"));
        backToList();
    }

    private void backToList() {
        getUI().ifPresent(ui -> ui.navigate(ImportClientsView.class));
    }

    @Override
    public String getPageTitle() {
        return getTranslation("editImportClientView.pageTitle");
    }
}
