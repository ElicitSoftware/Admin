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
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.binder.ValidationException;
import com.vaadin.flow.data.validator.EmailValidator;
import com.vaadin.flow.data.validator.StringLengthValidator;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;
import jakarta.transaction.Transactional;

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

    /** The department entity being edited or created. */
    private Department department;
    
    /** Text field for the department name. */
    private TextField nameField = new TextField("Department Name");
    
    /** Text field for the department code. */
    private TextField codeField = new TextField("Department Code");
    
    /** Text field for the default message ID. */
    private TextField defaultMessageIdField = new TextField("Default Message ID");
    
    /** Email field for the from email address. */
    private EmailField fromEmailField = new EmailField("From Email");
    
    /** Text area for notification emails. */
//    private TextArea notificationEmailsField = new TextArea("Notification Emails");
    
    /** Data binder for form validation and data binding. */
    private final Binder<Department> binder = new Binder<>(Department.class);
    
    /** Save button for creating or updating the department. */
    private Button saveBtn = new Button("Save");
    
    /** Cancel button to return to departments list. */
    private Button cancelBtn = new Button("Cancel");

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
        
        // Configure form fields
        nameField.setRequired(true);
        nameField.setRequiredIndicatorVisible(true);
        nameField.setWidth("300px");
        
        codeField.setHelperText("Short abbreviation for the department (e.g., 'CARD', 'HR')");
        codeField.setWidth("300px");
        
        defaultMessageIdField.setRequired(true);
        defaultMessageIdField.setRequiredIndicatorVisible(true);
        defaultMessageIdField.setValue("1"); // Default value
        defaultMessageIdField.setHelperText("Default message template ID");
        defaultMessageIdField.setWidth("300px");
        
        fromEmailField.setRequired(true);
        fromEmailField.setRequiredIndicatorVisible(true);
        fromEmailField.setHelperText("Email address that appears as sender for department communications");
        fromEmailField.setWidth("300px");
        
//        notificationEmailsField.setHelperText("Comma-separated list of emails to notify when respondents finish surveys");
//        notificationEmailsField.setHeight("100px");
//        notificationEmailsField.setWidth("300px");
        
        // Add form fields to layout
//        add(nameField, codeField, defaultMessageIdField, fromEmailField, notificationEmailsField);
        add(nameField, codeField, defaultMessageIdField, fromEmailField);

        // Configure buttons
        saveBtn.addClickListener(e -> saveDepartment());
        cancelBtn.addClickListener(e -> cancelEdit());
        
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
                .asRequired("Department name is required")
                .withValidator(new StringLengthValidator(
                        "Department name must be 1-255 characters", 1, 255))
                .bind("name");
        
        // Code field validation (optional, unique in DB, max 100 chars)
        binder.forField(codeField)
                .withValidator(new StringLengthValidator(
                        "Department code must be 100 characters or less", 0, 100))
                .bind("code");
        
        // Default Message ID validation (required, max 100 chars)
        binder.forField(defaultMessageIdField)
                .asRequired("Default message ID is required")
                .withValidator(new StringLengthValidator(
                        "Default message ID must be 1-100 characters", 1, 100))
                .bind("defaultMessageId");
        
        // From Email validation (required, valid email, max 50 chars)
        binder.forField(fromEmailField)
                .asRequired("From email is required")
                .withValidator(new EmailValidator("Please enter a valid email address"))
                .withValidator(new StringLengthValidator(
                        "From email must be 50 characters or less", 1, 50))
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
                getUI().ifPresent(ui -> ui.getPage().setTitle("Create New Department"));
                saveBtn.setText("Create Department");
            } else {
                // Edit existing department
                department = Department.findById(id);
                if (department == null) {
                    Notification.show("Department not found", 3000, Notification.Position.MIDDLE);
                    event.forwardTo(DepartmentsView.class);
                    return;
                }
                
                binder.setBean(department);
                
                // Update page title and button text
                getUI().ifPresent(ui -> ui.getPage().setTitle("Edit Department: " + department.name));
                saveBtn.setText("Update Department");
            }
        } catch (NumberFormatException e) {
            Notification.show("Invalid department ID", 3000, Notification.Position.MIDDLE);
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
    @Transactional
    public void saveDepartment() {
        try {
            // Validate and write form data to the department entity
            binder.writeBean(department);
            
            if (department.id == 0) {
                // Create new department
                department.persist();
                Notification.show("Department created successfully", 3000, Notification.Position.MIDDLE);
            } else {
                // Update existing department
                Department.getEntityManager().merge(department);
                Notification.show("Department updated successfully", 3000, Notification.Position.MIDDLE);
            }
            
            // Navigate back to departments list
            getUI().ifPresent(ui -> ui.navigate(DepartmentsView.class));
            
        } catch (ValidationException e) {
            Notification.show("Please fix the validation errors before saving", 
                    3000, Notification.Position.MIDDLE);
        } catch (Exception e) {
            // Handle potential unique constraint violations or other database errors
            String errorMessage = e.getMessage();
            if (errorMessage != null && errorMessage.contains("unique")) {
                if (errorMessage.contains("department_name_un")) {
                    Notification.show("Department name already exists. Please choose a different name.", 
                            5000, Notification.Position.MIDDLE);
                } else if (errorMessage.contains("department_code_un")) {
                    Notification.show("Department code already exists. Please choose a different code.", 
                            5000, Notification.Position.MIDDLE);
                } else {
                    Notification.show("A department with this name or code already exists.", 
                            5000, Notification.Position.MIDDLE);
                }
            } else {
                Notification.show("Error saving department: " + errorMessage, 
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
    @Transactional
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
