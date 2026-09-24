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

import com.elicitsoftware.model.Department;
import com.elicitsoftware.service.DepartmentService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.textfield.TextFieldVariant;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.binder.ValidationException;
import com.vaadin.flow.data.validator.EmailValidator;
import com.vaadin.flow.data.validator.StringLengthValidator;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;

/**
 * A Vaadin Flow view for editing department information.
 * This view provides functionality to create new departments or edit existing ones.
 *
 * <p>The view is accessible at the "/edit-department" route with an optional ID parameter.
 * It requires "elicit_admin" role for access. The ID parameter determines whether this is
 * an edit operation (existing department) or a create operation (new department).</p>
 *
 * <p>Route patterns:</p>
 * <ul>
 *   <li>/edit-department/0 - Create a new department</li>
 *   <li>/edit-department/123 - Edit department with ID 123</li>
 * </ul>
 *
 * @author Elicit Software
 * @version 1.0
 * @since 1.0
 */
@Route(value = "edit-department/:id?", layout = MainLayout.class)
@RolesAllowed("elicit_admin")
public class EditDepartmentView extends VerticalLayout implements BeforeEnterObserver {

    /** Service that owns the transactional persistence of departments. */
    @Inject
    DepartmentService departmentService;

    /** Names the administrator a new department is assigned to (UC-028 BR-113). */
    @Inject
    SecurityIdentity identity;

    /** Re-read after a department is created so the console notices the assignment (UC-028 BR-114). */
    @Inject
    UiSessionLogin uiSessionLogin;

    /** The department entity being edited or created. */
    private Department department;
    
    /** Text field for the department name. */
    private TextField nameField = new TextField();
    
    /** Text field for the department code. */
    private TextField codeField = new TextField();
    
    /** Text field for the default message ID. */
    private TextField defaultMessageIdField = new TextField();
    
    /** Email field for the from email address. */
    private EmailField fromEmailField = new EmailField();
    
    /** Text area for notification emails. */
//    private TextArea notificationEmailsField = new TextArea("Notification Emails");
    
    /** Data binder for form validation and data binding. */
    private final Binder<Department> binder = new Binder<>(Department.class);
    
    /** Save button for creating or updating the department. */
    private Button saveBtn = new Button();
    
    /** Cancel button to return to departments list. */
    private Button cancelBtn = new Button();

    /**
     * Constructs a new EditDepartmentView.
     * 
     * <p>Initializes the form layout with the following components:</p>
     * <ul>
     *   <li>Department name text field (required, max 255 characters)</li>
     *   <li>Department code text field (optional, max 100 characters)</li>
     *   <li>Default message ID text field (required, max 100 characters)</li>
     *   <li>From email field (required, max 50 characters)</li>
     *   <li>Notification emails text area (optional, max 2000 characters)</li>
     *   <li>Save and Cancel buttons with appropriate event handlers</li>
     * </ul>
     */
    public EditDepartmentView() {
        setSpacing(true);
        setPadding(true);

        nameField.setLabel(getTranslation("editDepartmentView.name"));
        codeField.setLabel(getTranslation("editDepartmentView.code"));
        defaultMessageIdField.setLabel(getTranslation("editDepartmentView.defaultMessageId"));
        fromEmailField.setLabel(getTranslation("editDepartmentView.fromEmail"));
        saveBtn.setText(getTranslation("common.save"));
        cancelBtn.setText(getTranslation("common.cancel"));
        
        // Configure form fields
        nameField.setRequired(true);
        nameField.setRequiredIndicatorVisible(true);
        nameField.setWidth("300px");
        nameField.addThemeVariants(TextFieldVariant.LUMO_SMALL);
        
        codeField.setHelperText(getTranslation("editDepartmentView.code.helper"));
        codeField.setWidth("300px");
        codeField.addThemeVariants(TextFieldVariant.LUMO_SMALL);
        
        defaultMessageIdField.setRequired(true);
        defaultMessageIdField.setRequiredIndicatorVisible(true);
        defaultMessageIdField.setValue("1"); // Default value
        defaultMessageIdField.setHelperText(getTranslation("editDepartmentView.defaultMessageId.helper"));
        defaultMessageIdField.setWidth("300px");
        defaultMessageIdField.addThemeVariants(TextFieldVariant.LUMO_SMALL);
        
        fromEmailField.setRequired(true);
        fromEmailField.setRequiredIndicatorVisible(true);
        fromEmailField.setHelperText(getTranslation("editDepartmentView.fromEmail.helper"));
        fromEmailField.setWidth("300px");
        
//        notificationEmailsField.setHelperText("Comma-separated list of emails to notify when respondents finish surveys");
//        notificationEmailsField.setHeight("100px");
//        notificationEmailsField.setWidth("300px");
        
        // Add form fields to layout
//        add(nameField, codeField, defaultMessageIdField, fromEmailField, notificationEmailsField);
        add(nameField, codeField, defaultMessageIdField, fromEmailField);

        // Configure buttons
        saveBtn.addClickListener(e -> saveDepartment());
        saveBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        
        cancelBtn.addClickListener(e -> cancelEdit());
        cancelBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        
        // Button layout
        HorizontalLayout buttonLayout = new HorizontalLayout(saveBtn, cancelBtn);
        buttonLayout.setSpacing(true);
        add(buttonLayout);
        
        // Configure validation
        setupValidation();
    }

    /**
     * Sets up form validation using Vaadin's Binder.
     * 
     * <p>Configures validation rules based on database constraints:</p>
     * <ul>
     *   <li>Name: Required, 1-255 characters</li>
     *   <li>Code: Optional, max 100 characters</li>
     *   <li>Default Message ID: Required, 1-100 characters</li>
     *   <li>From Email: Required, valid email, max 50 characters</li>
     *   <li>Notification Emails: Optional, max 2000 characters</li>
     * </ul>
     */
    private void setupValidation() {
        // Name field validation (required, unique in DB, max 255 chars)
        binder.forField(nameField)
                .asRequired(getTranslation("editDepartmentView.error.nameRequired"))
                .withValidator(new StringLengthValidator(
                        getTranslation("editDepartmentView.error.nameLength"), 1, 255))
                .bind("name");
        
        // Code field validation (optional, unique in DB, max 100 chars)
        binder.forField(codeField)
                .withValidator(new StringLengthValidator(
                        getTranslation("editDepartmentView.error.codeLength"), 0, 100))
                .bind("code");
        
        // Default Message ID validation (required, max 100 chars)
        binder.forField(defaultMessageIdField)
                .asRequired(getTranslation("editDepartmentView.error.defaultMessageIdRequired"))
                .withValidator(new StringLengthValidator(
                        getTranslation("editDepartmentView.error.defaultMessageIdLength"), 1, 100))
                .bind("defaultMessageId");
        
        // From Email validation (required, valid email, max 50 chars)
        binder.forField(fromEmailField)
                .asRequired(getTranslation("editDepartmentView.error.fromEmailRequired"))
                .withValidator(new EmailValidator(getTranslation("editDepartmentView.error.fromEmailInvalid")))
                .withValidator(new StringLengthValidator(
                        getTranslation("editDepartmentView.error.fromEmailLength"), 1, 50))
                .bind("fromEmail");
        
        // Notification Emails validation (optional, max 2000 chars)
//        binder.forField(notificationEmailsField)
//                .withValidator(new StringLengthValidator(
//                        "Notification emails must be 2000 characters or less", 0, 2000))
//                .bind("notificationEmails");
        
        // Enable/disable save button based on validation
        binder.addStatusChangeListener(event -> 
                saveBtn.setEnabled(binder.isValid()));
    }

    /**
     * Called before the user enters this view to handle route parameters.
     * 
     * <p>This method determines whether the view is in create or edit mode
     * based on the ID parameter:</p>
     * <ul>
     *   <li>If ID is "0": Create mode - initializes a new Department with default values</li>
     *   <li>If ID is a valid number > 0: Edit mode - loads the existing department and populates form fields</li>
     * </ul>
     * 
     * <p>If an invalid department ID is provided, an error notification is shown
     * and the user is redirected to the departments list view.</p>
     * 
     * @param event the BeforeEnterEvent containing navigation information and route parameters
     * @see BeforeEnterObserver#beforeEnter(BeforeEnterEvent)
     */
    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        String idStr = event.getRouteParameters().get("id").orElse("0");
        
        try {
            long id = Long.parseLong(idStr);
            
            if (id == 0) {
                // Create new department
                department = new Department();
                department.defaultMessageId = "1"; // Set default value
                binder.setBean(department);
                
                // Update page title and button text
                getUI().ifPresent(ui -> ui.getPage().setTitle(getTranslation("editDepartmentView.title.create")));
                saveBtn.setText(getTranslation("editDepartmentView.btnCreate"));
            } else {
                // Edit existing department
                department = Department.findById(id);
                if (department == null) {
                    Notification.show(getTranslation("editDepartmentView.error.notFound"), 3000, Notification.Position.MIDDLE);
                    event.forwardTo(DepartmentsView.class);
                    return;
                }
                
                binder.setBean(department);
                
                // Update page title and button text
                getUI().ifPresent(ui -> ui.getPage().setTitle(getTranslation("editDepartmentView.title.edit", department.name)));
                saveBtn.setText(getTranslation("editDepartmentView.btnUpdate"));
            }
        } catch (NumberFormatException e) {
            Notification.show(getTranslation("editDepartmentView.error.invalidId"), 3000, Notification.Position.MIDDLE);
            event.forwardTo(DepartmentsView.class);
        }
    }

    /**
     * Saves the department data from the form to the database.
     * 
     * <p>This method performs the following operations:</p>
     * <ol>
     *   <li>Validates form data using the binder</li>
     *   <li>For new departments (ID = 0): persists the entity</li>
     *   <li>For existing departments: merges changes into the database</li>
     *   <li>Shows a success notification</li>
     *   <li>Navigates back to the departments list view</li>
     * </ol>
     * 
     * <p>If validation fails, error messages are displayed and the save operation
     * is not performed.</p>
     */
    public void saveDepartment() {
        try {
            // Validate and write form data to the department entity
            binder.writeBean(department);

            boolean isNew = department.id == 0;
            // Persistence (transaction + insert/merge) lives in the service layer.
            if (isNew) {
                // A new department is assigned to its creator in the same transaction
                // (UC-028 BR-113); the session copy is refreshed only once that has committed.
                departmentService.create(department, identity.getPrincipal().getName());
                uiSessionLogin.refresh();
            } else {
                departmentService.save(department);
            }
            Notification.show(isNew ? getTranslation("editDepartmentView.created") : getTranslation("editDepartmentView.updated"),
                    3000, Notification.Position.MIDDLE);

            // Navigate back to departments list
            getUI().ifPresent(ui -> ui.navigate(DepartmentsView.class));

        } catch (ValidationException e) {
            Notification.show(getTranslation("editDepartmentView.error.fixValidation"),
                    3000, Notification.Position.MIDDLE);
        } catch (Exception e) {
            // Handle potential unique constraint violations or other database errors
            String errorMessage = e.getMessage();
            if (errorMessage != null && errorMessage.contains("unique")) {
                if (errorMessage.contains("department_name_un")) {
                    Notification.show(getTranslation("editDepartmentView.error.nameExists"),
                            5000, Notification.Position.MIDDLE);
                } else if (errorMessage.contains("department_code_un")) {
                    Notification.show(getTranslation("editDepartmentView.error.codeExists"),
                            5000, Notification.Position.MIDDLE);
                } else {
                    Notification.show(getTranslation("editDepartmentView.error.nameOrCodeExists"),
                            5000, Notification.Position.MIDDLE);
                }
            } else {
                Notification.show(getTranslation("editDepartmentView.error.save", errorMessage),
                        5000, Notification.Position.MIDDLE);
            }
        }
    }

    /**
     * Updates an existing department in the database.
     *
     * <p>This method is deprecated in favor of the unified {@link #saveDepartment()} method
     * which handles both creation and updates based on the department's ID.</p>
     * 
     * @deprecated Use {@link #saveDepartment()} instead
     */
    @Deprecated
    public void updateDepartment() {
        saveDepartment();
    }
    
    /**
     * Cancels the edit operation and navigates back to the departments list view.
     * 
     * <p>This method discards any changes made to the form and returns the user
     * to the main departments list without saving. No database operations are performed.</p>
     */
    private void cancelEdit() {
        getUI().ifPresent(ui -> ui.navigate(DepartmentsView.class));
    }
}
