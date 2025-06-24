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

import com.elicitsoftware.model.User;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;


import java.util.List;

/**
 * A Vaadin Flow view for managing user accounts and their department assignments.
 * This administrative view provides a comprehensive interface for viewing, editing,
 * and creating user accounts within the system.
 * 
 * <p>The view displays users in a sortable grid with the following information:</p>
 * <ul>
 *   <li><strong>Edit Action:</strong> Button column with edit icons for each user</li>
 *   <li><strong>Username:</strong> The user's unique login identifier</li>
 *   <li><strong>Personal Information:</strong> First name and last name</li>
 *   <li><strong>Status:</strong> Whether the user account is active or inactive</li>
 *   <li><strong>Departments:</strong> List of departments the user is assigned to</li>
 * </ul>
 * 
 * <p>Key features include:</p>
 * <ul>
 *   <li><strong>Multi-column sorting:</strong> Sort by any combination of columns</li>
 *   <li><strong>Direct editing:</strong> Navigate to edit view for individual users</li>
 *   <li><strong>User creation:</strong> Add new users through dedicated interface</li>
 *   <li><strong>Department display:</strong> Shows all assigned departments in a comma-separated list</li>
 *   <li><strong>Responsive design:</strong> Auto-width columns and full-width layout</li>
 * </ul>
 * 
 * <p>The view is accessible at the "/users" route and requires "elicit_admin" role for access.
 * It includes informational text explaining the relationship between OIDC authentication
 * and department assignments managed through this interface.</p>
 * 
 * <p><strong>Important Note:</strong> This view manages department assignments only.
 * User authentication and role management (Admin/User) must be configured in the
 * OpenID Connect (OIDC) authentication system separately.</p>
 * 
 * @author Elicit Software
 * @version 1.0
 * @since 1.0
 * @see User
 * @see EditUserView
 */
@Route(value = "users", layout = MainLayout.class)
@RolesAllowed("elicit_admin")
public class UsersView extends VerticalLayout {

    /** Grid component for displaying user information in a tabular format. */
    private Grid<User> userGrid = new Grid<>(User.class, false);

    /**
     * Constructs a new UsersView.
     * 
     * <p>Initializes the complete user management interface by:</p>
     * <ol>
     *   <li>Configuring the data grid with appropriate columns and sorting</li>
     *   <li>Adding informational text about OIDC integration</li>
     *   <li>Setting up the user grid and footer with action buttons</li>
     *   <li>Loading the initial user data from the database</li>
     * </ol>
     * 
     * <p>The layout is organized vertically with the information text at the top,
     * followed by the user grid, and action buttons at the bottom.</p>
     */
    public UsersView() {
        configureGrid();
        add(createInfoText(), userGrid, createFooter());
        updateGrid();
    }

    /**
     * Creates informational text explaining the user management system.
     * 
     * <p>This method generates a styled paragraph that explains the relationship
     * between OIDC authentication and department management. The text serves to
     * clarify that:</p>
     * <ul>
     *   <li>User authentication is handled by the OIDC system</li>
     *   <li>Roles (Admin/User) must be configured in OIDC</li>
     *   <li>Department assignments are managed through this interface</li>
     * </ul>
     * 
     * <p>The paragraph is styled with appropriate margins and secondary text color
     * to distinguish it as informational content.</p>
     * 
     * @return a styled Paragraph component containing user management information
     */
    private Paragraph createInfoText() {
        Paragraph info = new Paragraph("Users must be configured in the OpenID Connect (OIDC) authentication system with the roles \"Admin\" or \"User\". Departments are assigned through this interface.");
        info.getStyle().set("margin-bottom", "1em");
        info.getStyle().set("color", "var(--lumo-secondary-text-color)");
        return info;
    }

    /**
     * Configures the user data grid with columns, sorting, and styling.
     * 
     * <p>This method sets up the complete grid structure with the following columns:</p>
     * <ol>
     *   <li><strong>Edit:</strong> Component column with edit button for each user</li>
     *   <li><strong>Username:</strong> User's unique login identifier</li>
     *   <li><strong>First Name:</strong> User's first name</li>
     *   <li><strong>Last Name:</strong> User's last name</li>
     *   <li><strong>Active:</strong> User's active status (Yes/No)</li>
     *   <li><strong>Department:</strong> Comma-separated list of assigned departments</li>
     * </ol>
     * 
     * <p>Grid features configured:</p>
     * <ul>
     *   <li><strong>Auto-width columns:</strong> All columns automatically size to content</li>
     *   <li><strong>Multi-column sorting:</strong> Users can sort by multiple columns</li>
     *   <li><strong>Full-width display:</strong> Grid spans the entire available width</li>
     *   <li><strong>Edit buttons:</strong> Each row has an edit icon that navigates to EditUserView</li>
     * </ul>
     * 
     * <p>The department column uses stream processing to convert the user's department
     * collection into a readable comma-separated string format.</p>
     */
    private void configureGrid() {
        userGrid.addComponentColumn(user -> {
            Button editBtn = new Button(new Icon(VaadinIcon.EDIT));
            editBtn.addClickListener(e ->
                    editUser(user.getId())
            );
            return editBtn;
        }).setHeader("Edit").setAutoWidth(true);

        userGrid.addColumn(User::getUsername).setHeader("Username").setAutoWidth(true).setSortable(true);
        userGrid.addColumn(User::getFirstName).setHeader("First Name").setAutoWidth(true).setSortable(true);
        userGrid.addColumn(User::getLastName).setHeader("Last Name").setAutoWidth(true).setSortable(true);
        userGrid.addColumn(user -> user.isActive() ? "Yes" : "No").setHeader("Active").setAutoWidth(true).setSortable(true);
        userGrid.addColumn(user -> {
            if (user.getDepartments() != null && !user.getDepartments().isEmpty()) {
                return user.getDepartments().stream()
                        .map(dept -> dept.getName())
                        .collect(java.util.stream.Collectors.joining(", "));
            }
            return "";
        }).setHeader("Department").setAutoWidth(true).setSortable(true);

        userGrid.setMultiSort(true);
        userGrid.setWidthFull();
    }

    /**
     * Updates the grid with the latest user data from the database.
     * 
     * <p>This method refreshes the grid content by:</p>
     * <ol>
     *   <li>Retrieving all users from the database using {@link User#listAll()}</li>
     *   <li>Setting the retrieved user list as the grid's data source</li>
     * </ol>
     * 
     * <p>This method is called during view initialization and can be used to
     * refresh the grid after user data changes. The grid automatically handles
     * rendering and sorting of the updated data.</p>
     * 
     * @see User#listAll()
     */
    private void updateGrid() {
        List<User> users = User.listAll();
        userGrid.setItems(users);
    }

    /**
     * Navigates to the EditUserView for the specified user.
     * 
     * <p>This method handles navigation to the user editing interface by:</p>
     * <ol>
     *   <li>Converting the user ID to a string parameter</li>
     *   <li>Navigating to the "edit-user" route with the user ID</li>
     *   <li>Handling both existing users (ID > 0) and new users (ID = 0)</li>
     * </ol>
     * 
     * <p>The navigation is performed programmatically using Vaadin's navigation API.
     * If the UI is not available (unlikely in normal operation), the navigation
     * is silently ignored.</p>
     * 
     * <p><strong>Route Format:</strong> {@code edit-user/{userId}}</p>
     * <ul>
     *   <li>For existing users: {@code edit-user/123} (where 123 is the user ID)</li>
     *   <li>For new users: {@code edit-user/0}</li>
     * </ul>
     * 
     * @param userId the unique identifier of the user to edit, or 0 for new users
     * @see EditUserView
     */
    private void editUser(long userId) {
        String idParam = (userId > 0) ? String.valueOf(userId) : "0";
        getUI().ifPresent(ui ->
                ui.navigate("edit-user/" + idParam)
        );
    }

    /**
     * Creates the footer layout with user management action buttons.
     * 
     * <p>This method constructs the bottom section of the view containing
     * action buttons for user management operations. Currently includes:</p>
     * <ul>
     *   <li><strong>Add User:</strong> Button to create a new user account</li>
     * </ul>
     * 
     * <p>The footer uses a horizontal layout to arrange buttons in a row.
     * Additional action buttons can be added to this layout as needed for
     * future functionality.</p>
     * 
     * <p>The "Add User" button is configured with a click listener that
     * navigates to the EditUserView in "new user" mode (ID = 0).</p>
     * 
     * @return a HorizontalLayout containing the user management action buttons
     * @see #addUser()
     */
    private HorizontalLayout createFooter() {
        Button addUserBtn = new Button("Add User", e -> addUser());
        return new HorizontalLayout(addUserBtn);
    }

    /**
     * Initiates the creation of a new user account.
     * 
     * <p>This method navigates to the EditUserView in "new user" mode by
     * passing an ID of 0, which signals the edit view to create a new user
     * rather than edit an existing one.</p>
     * 
     * <p>The navigation is performed programmatically using Vaadin's navigation API.
     * If the UI is not available (unlikely in normal operation), the navigation
     * is silently ignored.</p>
     * 
     * <p><strong>Navigation Target:</strong> {@code edit-user/0}</p>
     * 
     * @see EditUserView
     * @see #editUser(long)
     */
    private void addUser() {
        getUI().ifPresent(ui -> ui.navigate("edit-user/0"));
    }

}
