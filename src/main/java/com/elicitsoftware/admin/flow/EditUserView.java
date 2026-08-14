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
import com.elicitsoftware.security.AuthorizationModeConfig;
import com.elicitsoftware.security.ElicitRoles;
import com.elicitsoftware.service.UserRoleService;
import com.elicitsoftware.service.UserService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.combobox.MultiSelectComboBox;
import com.vaadin.flow.component.combobox.MultiSelectComboBoxVariant;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Paragraph;
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
import com.vaadin.flow.router.Route;
import com.vaadin.flow.theme.lumo.LumoUtility;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;

import java.util.ArrayList;
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

    /** Service that owns the transactional persistence of users. */
    @Inject
    UserService userService;

    /** Service that owns the transactional persistence of database role grants. */
    @Inject
    UserRoleService userRoleService;

    /** Controls whether the database role-assignment section is shown. */
    @Inject
    AuthorizationModeConfig authorizationModeConfig;

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

    /** Single-select dropdown for the user's database role grant. */
    private final ComboBox<String> roleBox = new ComboBox<>("Role");

    /** Wraps the role dropdown with a heading and explanatory text; shown only in DATABASE mode. */
    private final VerticalLayout roleSection = new VerticalLayout();

    /** Data binder for form validation and field binding. */
    private final Binder<User> binder = new Binder<>(User.class);

    /** Save button, enabled only when the form is valid. */
    private final Button saveBtn = new Button("Save");

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
        username.setRequiredIndicatorVisible(true);
        firstName.addThemeVariants(TextFieldVariant.LUMO_SMALL);
        firstName.setRequiredIndicatorVisible(true);
        lastName.addThemeVariants(TextFieldVariant.LUMO_SMALL);
        lastName.setRequiredIndicatorVisible(true);

        departmentsBox.setItemLabelGenerator(Department::getName);
        departmentsBox.addThemeVariants(MultiSelectComboBoxVariant.LUMO_SMALL);
        List<Department> allDepartments = Department.findAll().list();
        departmentsBox.setItems(allDepartments);

        roleBox.setItems(new ArrayList<>(ElicitRoles.ALL));
        roleBox.setClearButtonVisible(true);
        roleBox.setWidthFull();
        Paragraph roleSectionInfo = new Paragraph(
                "Sets this user's database role fallback. Choose the user's highest role; "
                        + "elicit_admin and elicit_user each imply the roles below them.");
        roleSectionInfo.addClassNames(LumoUtility.Margin.NONE, LumoUtility.TextColor.SECONDARY);
        roleSection.add(new H4("Database Role Assignment"), roleSectionInfo, roleBox);
        roleSection.setPadding(false);
        roleSection.setSpacing(false);
        // Visibility is finalized in initRoleSectionVisibility() (@PostConstruct), not here:
        // @Inject fields (authorizationModeConfig) aren't populated until after this
        // constructor returns.

        add(username, firstName, lastName, activeCheckbox, departmentsBox, roleSection);

        saveBtn.addClickListener(e -> saveUser());
        saveBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Button cancelBtn = new Button("Cancel", e -> cancelEdit());
        cancelBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        add(new HorizontalLayout(saveBtn, cancelBtn));

        setupValidation();
    }

    /**
     * Finalizes the Database Role Assignment section's visibility once injected fields are
     * available.
     *
     * <p>{@code @Inject} fields are not populated until after the constructor returns, so this
     * cannot be done there; it must also not depend on {@link #beforeEnter}, which under
     * {@code @QuarkusTest} is not invoked when a view is attached directly (no route
     * navigation), so gating visibility only in {@code beforeEnter} would leave the section
     * visible in tests that build the view via CDI.</p>
     */
    @PostConstruct
    private void initRoleSectionVisibility() {
        roleSection.setVisible(authorizationModeConfig.isDatabaseMode());
    }

    /**
     * Configures field binding and validation using Vaadin's {@link Binder}.
     *
     * <p>Validation rules:</p>
     * <ul>
     *   <li>Username: required, 1-255 characters</li>
     *   <li>First name: required, 1-255 characters</li>
     *   <li>Last name: required, 1-255 characters</li>
     * </ul>
     *
     * <p>The save button is enabled only while the bound form is valid.</p>
     */
    private void setupValidation() {
        binder.forField(username)
                .asRequired("Username is required")
                .withValidator(new StringLengthValidator(
                        "Username must be 1-255 characters", 1, 255))
                .bind(User::getUsername, User::setUsername);

        binder.forField(firstName)
                .asRequired("First name is required")
                .withValidator(new StringLengthValidator(
                        "First name must be 1-255 characters", 1, 255))
                .bind(User::getFirstName, User::setFirstName);

        binder.forField(lastName)
                .asRequired("Last name is required")
                .withValidator(new StringLengthValidator(
                        "Last name must be 1-255 characters", 1, 255))
                .bind(User::getLastName, User::setLastName);

        binder.forField(activeCheckbox)
                .bind(User::isActive, User::setActive);

        binder.addStatusChangeListener(event -> saveBtn.setEnabled(binder.isValid()));
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
        if (idStr != null && !idStr.equals("0")) {
            long id = Long.parseLong(idStr);
            user = User.findById(id);
            if (user == null) {
                Notification.show("User not found");
                event.forwardTo(UsersView.class);
                return;
            }
            binder.setBean(user);
            if (user.getDepartments() != null) {
                departmentsBox.setValue(user.getDepartments());
            }
            if (authorizationModeConfig.isDatabaseMode()) {
                roleBox.setValue(userRoleService.findRoleName(user.getId()).orElse(null));
            }
        } else {
            user = new User();
            user.setActive(true); // Default new users to active
            binder.setBean(user);
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
    public void saveUser() {
        try {
            // Validate and write the bound fields (username, names, active) into the entity.
            binder.writeBean(user);
        } catch (ValidationException e) {
            Notification.show("Please fix the validation errors before saving");
            return;
        }

        // Departments are managed outside the binder (multi-select), so copy them explicitly.
        // MultiSelectComboBox.getValue() returns an immutable Set, which Hibernate's merge()
        // cannot mutate in place on a later save -- copy into a fresh HashSet.
        Set<Department> selectedDepartments = departmentsBox.getValue();
        user.setDepartments(selectedDepartments != null ? new HashSet<>(selectedDepartments) : new HashSet<>());

        userService.save(user);

        // The role grant is keyed on the user's id, so it must be saved after userService.save
        // (a new user has no id until persisted). Skipped entirely in OIDC mode.
        if (authorizationModeConfig.isDatabaseMode()) {
            String selectedRole = roleBox.getValue();
            if (selectedRole != null) {
                userRoleService.setRole(user.getId(), selectedRole);
            } else {
                userRoleService.clearRole(user.getId());
            }
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
