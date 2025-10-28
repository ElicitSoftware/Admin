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
import com.elicitsoftware.model.User;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.MultiSelectComboBox;
import com.vaadin.flow.component.combobox.MultiSelectComboBoxVariant;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.textfield.TextFieldVariant;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;
import jakarta.transaction.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A Vaadin Flow view for editing and creating user accounts.
 * This view provides a comprehensive interface for managing user information
 * including personal details, active status, and department assignments.
 * 
 * <p>The view includes the following features:</p>
 * <ul>
 *   <li>User identification fields (username, first name, last name)</li>
 *   <li>Active status checkbox to enable/disable user accounts</li>
 *   <li>Multi-select department assignment</li>
 *   <li>Save and cancel functionality with proper navigation</li>
 * </ul>
 * 
 * <p>Route patterns:</p>
 * <ul>
 *   <li>/edit-user - Create a new user</li>
 *   <li>/edit-user/0 - Create a new user (explicit)</li>
 *   <li>/edit-user/123 - Edit user with ID 123</li>
 * </ul>
 * 
 * @author Elicit Software
 * @version 1.0
 * @since 1.0
 */
@Route(value = "edit-user/:id?", layout = MainLayout.class)
@RolesAllowed("elicit_admin")
public class EditUserView extends VerticalLayout implements BeforeEnterObserver {

    /** The user entity being edited or created. */
    private User user;
    
    /** Text field for the user's username. */
    private TextField username = new TextField("Username");
    
    /** Text field for the user's first name. */
    private TextField firstName = new TextField("First Name");
    
    /** Text field for the user's last name. */
    private TextField lastName = new TextField("Last Name");
    
    /** Checkbox to control whether the user account is active. */
    private Checkbox activeCheckbox = new Checkbox("Active");
    
    /** Multi-select combo box for assigning the user to departments. */
    private MultiSelectComboBox<Department> departmentsBox = new MultiSelectComboBox<>("Departments");

    /**
     * Constructs a new EditUserView.
     * 
     * <p>Initializes the form layout with the following components:</p>
     * <ul>
     *   <li>Username, first name, and last name text fields</li>
     *   <li>Active status checkbox</li>
     *   <li>Department multi-select combo box populated with all available departments</li>
     *   <li>Save and Cancel buttons with appropriate event handlers</li>
     * </ul>
     * 
     * <p>The departments combo box is configured to display department names
     * and is populated with all departments from the database.</p>
     */
    public EditUserView() {
        username.addThemeVariants(TextFieldVariant.LUMO_SMALL);
        firstName.addThemeVariants(TextFieldVariant.LUMO_SMALL);
        lastName.addThemeVariants(TextFieldVariant.LUMO_SMALL);
        
        departmentsBox.setItemLabelGenerator(Department::getName);
        departmentsBox.addThemeVariants(MultiSelectComboBoxVariant.LUMO_SMALL);
        List<Department> allDepartments = Department.findAll().list();
        departmentsBox.setItems(allDepartments);

        add(username, firstName, lastName, activeCheckbox, departmentsBox);

        Button saveBtn = new Button("Save", e -> saveUser());
        saveBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        
        Button cancelBtn = new Button("Cancel", e -> cancelEdit());
        cancelBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        add(new HorizontalLayout(saveBtn, cancelBtn));
    }

    /**
     * Called before the user enters this view to handle route parameters.
     * 
     * <p>This method determines whether the view is in create or edit mode
     * based on the presence and value of the ID parameter:</p>
     * <ul>
     *   <li>If ID is null or "0": Create mode - initializes a new User with default values</li>
     *   <li>If ID is a valid number: Edit mode - loads the existing user and populates form fields</li>
     * </ul>
     * 
     * <p>In create mode, the user is set to active by default. In edit mode,
     * all form fields are populated with the existing user's data including
     * username, names, active status, and department assignments.</p>
     * 
     * <p>If an invalid user ID is provided, an error notification is shown
     * and the user is redirected to the users list view.</p>
     * 
     * @param event the BeforeEnterEvent containing navigation information and route parameters
     * @see BeforeEnterObserver#beforeEnter(BeforeEnterEvent)
     */
    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        String idStr = event.getRouteParameters().get("id").orElse(null);
        if (idStr != null) {
            long id = Long.parseLong(idStr);
            if (id == 0) {
                user = new User();
                activeCheckbox.setValue(true); // Default new users to active
            } else {
                user = User.findById(id);
                if (user == null) {
                    Notification.show("User not found");
                    event.forwardTo(UsersView.class);
                    return;
                }
                // Populate fields
                username.setValue(user.getUsername() != null ? user.getUsername() : "");
                firstName.setValue(user.getFirstName() != null ? user.getFirstName() : "");
                lastName.setValue(user.getLastName() != null ? user.getLastName() : "");
                activeCheckbox.setValue(user.isActive());
                if (user.getDepartments() != null) {
                    departmentsBox.setValue(user.getDepartments());
                }
            }
        } else {
            user = new User();
            activeCheckbox.setValue(true); // Default new users to active
        }
    }

    /**
     * Saves the user data from the form to the database.
     * 
     * <p>This method performs the following operations:</p>
     * <ol>
     *   <li>Extracts data from all form fields</li>
     *   <li>Updates the user entity with the form values</li>
     *   <li>Handles department assignments (empty set if none selected)</li>
     *   <li>Persists new users or merges existing users</li>
     *   <li>Shows a success notification</li>
     *   <li>Navigates back to the users list view</li>
     * </ol>
     * 
     * <p>For new users (ID = 0), the persist() method is used. For existing users,
     * the merge() operation is performed to update the database with changes.</p>
     */
    @Transactional
    public void saveUser() {
        user.setUsername(username.getValue());
        user.setFirstName(firstName.getValue());
        user.setLastName(lastName.getValue());
        user.setActive(activeCheckbox.getValue());
        Set<Department> selectedDepartments = departmentsBox.getValue();
        user.setDepartments(selectedDepartments != null ? selectedDepartments : new HashSet<>());

        if (user.getId() == 0) {
            user.persist();
        } else {
            user = (User) User.getEntityManager().merge(user);
        }

        Notification.show("User saved");
        getUI().ifPresent(ui -> ui.navigate(UsersView.class));
    }

    /**
     * Cancels the edit operation and navigates back to the users list view.
     * 
     * <p>This method discards any changes made to the form and returns the user
     * to the main users list without saving. No database operations are performed.</p>
     */
    private void cancelEdit() {
        getUI().ifPresent(ui -> ui.navigate(UsersView.class));
    }
}
