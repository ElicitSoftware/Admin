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
import com.elicitsoftware.model.Status;
import com.elicitsoftware.model.User;
import com.elicitsoftware.service.EmailService;
import com.elicitsoftware.service.ReportingService;
import com.elicitsoftware.service.StatusDataSource;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Direction;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.combobox.MultiSelectComboBox;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.HeaderRow;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H5;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.provider.DataProvider;
import com.vaadin.flow.data.provider.QuerySortOrder;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.QueryParameters;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.BeforeEnterEvent;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * A comprehensive subject search and management view that provides advanced filtering,
 * pagination, and action capabilities for subject records. This view serves as the primary
 * interface for finding, viewing, and performing operations on subjects within the system.
 *
 * <p>The view features a sophisticated search interface with multiple filter options:</p>
 * <ul>
 *   <li><strong>Department Filtering:</strong> Multi-select department filter with "All Departments" option</li>
 *   <li><strong>Token Search:</strong> Find subjects by their unique survey tokens</li>
 *   <li><strong>Name Search:</strong> Filter by first name, middle name, or last name</li>
 *   <li><strong>Contact Information:</strong> Search by email address or phone number</li>
 * </ul>
 *
 * <p>The results are displayed in a sortable, paginated grid with the following features:</p>
 * <ul>
 *   <li><strong>Multi-column sorting:</strong> Sort by any combination of columns</li>
 *   <li><strong>Pagination controls:</strong> Configurable page sizes and navigation</li>
 *   <li><strong>Auto-refresh:</strong> Automatic data updates every 10 seconds</li>
 *   <li><strong>Real-time filtering:</strong> Immediate search results without page reload</li>
 * </ul>
 *
 * <p>Subject management capabilities include:</p>
 * <ul>
 *   <li><strong>Edit Functionality:</strong> Direct navigation to subject editing interface</li>
 *   <li><strong>Email Actions:</strong> Send emails to individual subjects</li>
 *   <li><strong>Report Generation:</strong> Generate reports for completed surveys</li>
 *   <li><strong>Status Tracking:</strong> View current survey completion status</li>
 * </ul>
 *
 * <p>The view implements responsive design with RTL support for Arabic locales and
 * includes comprehensive error handling for all operations. Access is restricted to
 * users with "elicit_user" role, and department filtering respects user permissions.</p>
 *
 * <p><strong>Technical Features:</strong></p>
 * <ul>
 *   <li>Lazy loading data provider for efficient large dataset handling</li>
 *   <li>Scheduled background refresh for real-time data updates</li>
 *   <li>Dynamic SQL generation based on filter criteria</li>
 *   <li>Integrated pagination with customizable page sizes</li>
 * </ul>
 *
 * @author Elicit Software
 * @version 1.0
 * @since 1.0
 * @see Status
 * @see StatusDataSource
 * @see com.elicitsoftware.service.EmailService
 * @see com.elicitsoftware.service.ReportingService
 */
@Route(value = "", layout = MainLayout.class)
class SearchView extends VerticalLayout implements HasDynamicTitle, BeforeEnterObserver {

    /** Pagination controls for managing page navigation and data loading. */
    private final PaginationControls paginationControls = new PaginationControls();

    /** Data source for executing status queries and managing database connections. */
    private final StatusDataSource dataSource = new StatusDataSource();

    /** Scheduled executor for automatic data refresh functionality. */
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    /** Security identity for user authentication and role checking. */
    @Inject
    SecurityIdentity identity;

    /** Injected service for handling user session and authentication. */
    @Inject
    UiSessionLogin uiSessionLogin;

    /** Injected service for sending emails to subjects. */
    @Inject
    EmailService emailService;

    /** Injected service for generating and managing reports. */
    @Inject
    ReportingService reportingService;

    /** The current authenticated user. */
    User user;

    /** Grid component for displaying subject status information. */
    Grid<Status> subjectGrid;

    /** UI instance for accessing current user interface context. */
    private UI ui;

    /** Multi-select combo box for department filtering. */
    private MultiSelectComboBox<Department> departmentComboBox;

    /** Text field for token-based search filtering. */
    private TextField tokenField;

    /** Text field for first name search filtering. */
    private TextField firstNameField;

    /** Text field for last name search filtering. */
    private TextField lastNameField;

    /** Text field for email search filtering. */
    private TextField emailField;

    /** Text field for phone number search filtering. */
    private TextField phoneField;
    // 1. Update the DataProvider to use the filter parameter:
    private final DataProvider<Status, String> pagingDataProvider = DataProvider.fromFilteringCallbacks(
            query -> {
                query.getLimit();
                query.getOffset();

                var offset = paginationControls.calculateOffset();
                var limit = paginationControls.getPageSize();
                String sql = query.getFilter().orElse(getStatusSQL());
                // Build ORDER BY from query.getSortOrders()
                List<QuerySortOrder> sortOrders = query.getSortOrders();
                String orderBy = sortOrders.stream()
                        .map(order -> {
                            String column = switch (order.getSorted()) {
                                case "token" -> "s.token";
                                case "departmentName" -> "s.departmentName";
                                case "firstName" -> "s.firstName";
                                case "middleName" -> "s.middleName";
                                case "lastName" -> "s.lastName";
                                case "email" -> "s.email";
                                case "phone" -> "s.phone";
                                case "status" -> "s.status";
                                case "createdDt" -> "s.createdDt";
                                default -> null;
                            };
                            if (column != null) {
                                return column + (order.getDirection().name().equals("DESCENDING") ? " DESC" : " ASC");
                            }
                            return null;
                        })
                        .filter(x -> x != null)
                        .reduce((a, b) -> a + ", " + b)
                        .map(s -> " ORDER BY " + s)
                        .orElse("");

                sql = sql + orderBy;
                return dataSource.fetch(sql, offset, limit);
            },
            query -> {
                String sql = query.getFilter().orElse(getStatusSQL());
                var itemCount = dataSource.count(sql);
                paginationControls.recalculatePageCount(itemCount);
                var offset = paginationControls.calculateOffset();
                var limit = paginationControls.getPageSize();
                var remainingItemsCount = itemCount - offset;
                return Math.min(remainingItemsCount, limit);
            }
    );

    /**
     * Initializes the search view components and layout after dependency injection.
     *
     * <p>This method performs the following initialization steps:</p>
     *
     * <ol>
     *   <li><strong>UI Context Setup:</strong> Captures current UI instance for background operations</li>
     *   <li><strong>User Authentication:</strong> Retrieves and validates current user session</li>
     *   <li><strong>Error Handling:</strong> Displays appropriate error message for invalid users</li>
     *   <li><strong>Locale Configuration:</strong> Sets up RTL layout for Arabic locales</li>
     *   <li><strong>Component Creation:</strong> Builds search interface and data grid</li>
     * </ol>
     *
     * <p>If the user is not found or inactive, an error message is displayed explaining
     * the authentication issue and directing them to contact an administrator.</p>
     *
     * <p>For valid users, the method sets up the complete search interface including
     * the search bar with multiple filter options and the sortable data grid with
     * pagination controls.</p>
     */
    
    /**
     * Checks user authentication and authorization before entering the view.
     * Redirects authenticated users without proper roles to the unauthorized page.
     */
    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        // Check authorization using helper
        RoleAuthorizationHelper.checkAuthorization(event, identity);
    }
    
    @PostConstruct
    public void init() {

        // Safe to call getCurrent here
        this.ui = UI.getCurrent();

        user = uiSessionLogin.getUser();

        if (user == null) {
            Div errorDiv = new Div();
            errorDiv.getElement().setProperty("innerHTML", " You have successfully logged in to the Open ID connect system.<br/> Unfortunately, there is no user named <b>" + identity.getPrincipal().getName() + "</b> in the application or it is set to inactive.<br/>Please ask an Elicit Admin for help.");
            add(errorDiv);
        } else {
            //Set up the I18n
            final UI ui = UI.getCurrent();
            if (ui.getLocale().getLanguage().equals("ar")) {
                ui.setDirection(Direction.RIGHT_TO_LEFT);
            } else {
                ui.setDirection(Direction.LEFT_TO_RIGHT);
            }
            add(new H5("Subject search"));
            createSearchBar();
            createSubjectsTable();
        }
    }

    /**
     * Creates and configures the search bar with multiple filter options.
     *
     * <p>This method builds a horizontal search interface that includes:</p>
     *
     * <ul>
     *   <li><strong>Department Filter:</strong> Multi-select combo box with "All Departments" option</li>
     *   <li><strong>Text Filters:</strong> Individual search fields for token, names, email, and phone</li>
     *   <li><strong>Search Action:</strong> Button to trigger filtering with pagination reset</li>
     * </ul>
     *
     * <p>The search bar is configured with:</p>
     * <ul>
     *   <li>Full width layout with centered justification</li>
     *   <li>Consistent spacing and padding</li>
     *   <li>Baseline alignment for visual consistency</li>
     * </ul>
     *
     * <p>When the search button is clicked, the pagination is reset to the first page
     * and the data provider is refreshed with the new filter criteria.</p>
     */
    private void createSearchBar() {
        HorizontalLayout searchBar = new HorizontalLayout();
        searchBar.setPadding(true);
        searchBar.setJustifyContentMode(FlexComponent.JustifyContentMode.CENTER);
        searchBar.setWidth("100%");
        searchBar.setSpacing(true);

        // Use class fields
        departmentComboBox = getDepartmentComboBox();
        searchBar.add(departmentComboBox);

        tokenField = new TextField("Token");
        searchBar.add(tokenField);

        firstNameField = new TextField("First name");
        searchBar.add(firstNameField);

        lastNameField = new TextField("Last name");
        searchBar.add(lastNameField);

        emailField = new TextField("Email");
        searchBar.add(emailField);

        phoneField = new TextField("Phone");
        searchBar.add(phoneField);

        Button searchButton = new Button("Search");
        searchButton.addClickListener(e -> {
            paginationControls.resetToFirstPage();
            pagingDataProvider.refreshAll(); // This will use the latest getStatusSQL() for filtering
        });
        searchBar.add(searchButton);
        searchBar.setAlignItems(Alignment.BASELINE);
        add(searchBar);
    }

    /**
     * Creates the main subjects table layout and initializes the grid component.
     *
     * <p>This method sets up the container layout for the subjects grid and
     * calls the grid initialization method. The layout is configured to use
     * the full available space for optimal data display.</p>
     */
    private void createSubjectsTable() {
        VerticalLayout respondentsLayout = new VerticalLayout();
        respondentsLayout.setSizeFull();
        add(respondentsLayout);
        getSubjectGrid();
    }

    /**
     * Creates and configures the department selection component with special "All Departments" functionality.
     *
     * <p>This method builds a multi-select department filter with the following features:</p>
     *
     * <ul>
     *   <li><strong>"All Departments" Option:</strong> Special entry that selects all user departments</li>
     *   <li><strong>User Permission Filtering:</strong> Only shows departments the user has access to</li>
     *   <li><strong>Smart Selection Logic:</strong> Automatically handles conflicts between "All" and individual selections</li>
     *   <li><strong>Default Selection:</strong> "All Departments" is selected by default</li>
     * </ul>
     *
     * <p>The selection behavior implements the following logic:</p>
     * <ul>
     *   <li>When "All Departments" is selected, individual department selections are cleared</li>
     *   <li>When individual departments are selected, "All Departments" is automatically deselected</li>
     *   <li>If only "All Departments" remains selected, it stays selected</li>
     * </ul>
     *
     * @return a configured MultiSelectComboBox for department filtering
     */
    private MultiSelectComboBox<Department> getDepartmentComboBox() {

        MultiSelectComboBox<Department> departmentComboBox = new MultiSelectComboBox<>("Deparment(s)");

        // Create "All Departments" entry
        Department allDepartments = new Department();
        allDepartments.name = "All Departments";
        allDepartments.id = -1; // Use a special ID or flag to identify this entry

        // Combine "All Departments" with user's departments
        List<Department> departmentsWithAll = new ArrayList<>();
        departmentsWithAll.add(allDepartments);
        departmentsWithAll.addAll(user.getDepartments());
        departmentComboBox.setItems(departmentsWithAll);

        departmentComboBox.setItemLabelGenerator(Department::getName);
        departmentComboBox.setRequiredIndicatorVisible(true);

        // Set "All Departments" as the default
        departmentComboBox.setValue(Set.of(allDepartments));

        // Add ValueChangeListener
        departmentComboBox.addValueChangeListener(event -> {

            Set<Department> oldValues = event.getOldValue();
            Set<Department> newValues = event.getValue();
            //If the oldValues did not have allDepartments but the newValues does
            if (oldValues.stream().noneMatch(od -> od.id == -1)
                    && newValues.stream().anyMatch(d -> d.id == -1)) {
                // If "All Departments" is selected, set only it as selected
                departmentComboBox.setValue(Set.of(allDepartments));
                //If the newValues only has allDepartments
            } else if (newValues.size() == 1 && newValues.contains(allDepartments)) {
                departmentComboBox.setValue(Set.of(allDepartments));
            } else {
                // Optionally, you can de-select "All Departments"
                Set<Department> filtered = newValues.stream()
                        .filter(d -> d.id != -1)
                        .collect(Collectors.toSet());
                departmentComboBox.setValue(filtered);
            }
        });

        return departmentComboBox;
    }

    /**
     * Creates and configures the main data grid for displaying subject status information.
     *
     * <p>This method builds a comprehensive data grid with the following features:</p>
     *
     * <h4>Column Configuration:</h4>
     * <ul>
     *   <li><strong>Token:</strong> Fixed-width column (150px) for survey tokens</li>
     *   <li><strong>Department:</strong> Department name with sorting capability</li>
     *   <li><strong>Names:</strong> First, middle, and last name columns with sorting</li>
     *   <li><strong>Contact:</strong> Email and phone columns with sorting</li>
     *   <li><strong>Metadata:</strong> Creation date and status columns with sorting</li>
     * </ul>
     *
     * <h4>Interactive Features:</h4>
     * <ul>
     *   <li><strong>Edit Column:</strong> Edit buttons that navigate to subject registration with token parameter</li>
     *   <li><strong>Action Column:</strong> Dropdown menus for email sending and report generation</li>
     *   <li><strong>Multi-sort:</strong> Support for sorting by multiple columns simultaneously</li>
     * </ul>
     *
     * <h4>Action System:</h4>
     * <ul>
     *   <li><strong>Email Actions:</strong> Send emails to individual subjects with error handling</li>
     *   <li><strong>Report Generation:</strong> Generate reports for completed surveys (status-dependent)</li>
     *   <li><strong>Dynamic Menus:</strong> Action options change based on subject status</li>
     * </ul>
     *
     * <h4>Data Management:</h4>
     * <ul>
     *   <li><strong>Lazy Loading:</strong> Efficient data provider with pagination support</li>
     *   <li><strong>Auto-refresh:</strong> Scheduled updates every 10 seconds</li>
     *   <li><strong>Sort Integration:</strong> SQL ordering based on grid sort configuration</li>
     * </ul>
     *
     * <p>The grid is integrated with pagination controls and includes comprehensive error
     * handling for all user actions.</p>
     */
    private void getSubjectGrid() {
        subjectGrid = new Grid<>(Status.class, false);
        subjectGrid.addColumn(Status::getToken).setHeader("Token").setSortable(true).setSortProperty("token").setWidth("150px").setFlexGrow(0);
        subjectGrid.addColumn(Status::getDepartmentName).setHeader("Department").setSortable(true).setSortProperty("departmentName");
        subjectGrid.addColumn(Status::getFirstName).setHeader("First name").setSortable(true).setSortProperty("firstName");
        subjectGrid.addColumn(Status::getMiddleName).setHeader("Middle name").setSortable(true).setSortProperty("middleName");
        subjectGrid.addColumn(Status::getLastName).setHeader("Last name").setSortable(true).setSortProperty("lastName");
        subjectGrid.addColumn(Status::getEmail).setHeader("Email").setSortable(true).setSortProperty("email");
        subjectGrid.addColumn(Status::getPhone).setHeader("Phone").setSortable(true).setSortProperty("phone");
        subjectGrid.addColumn(Status::getCreated).setHeader("Created").setSortable(true).setSortProperty("createdDt");
        subjectGrid.addColumn(Status::getStatus).setHeader("Status").setSortable(true).setSortProperty("status");
        subjectGrid.setMultiSort(true, Grid.MultiSortPriority.APPEND);
        HeaderRow headerRow = subjectGrid.appendHeaderRow();

        // --- Add edit icon column ---
        subjectGrid.addComponentColumn(status -> {
            Button editButton = new Button(new Icon(VaadinIcon.EDIT));
            editButton.addThemeVariants(ButtonVariant.LUMO_ICON, ButtonVariant.LUMO_TERTIARY);
            editButton.getElement().setProperty("title", "Edit");
            editButton.addClickListener(e -> {
                // Pass the token as a query parameter (or use another unique identifier)
                ui.navigate("register", QueryParameters.simple(Map.of("token", status.getToken())));
            });
            return editButton;
        }).setHeader("Edit").setWidth("80px").setFlexGrow(0);

        // --- Add action column ---
        subjectGrid.addComponentColumn(status -> {
            HorizontalLayout actionLayout = new HorizontalLayout();
            actionLayout.setSpacing(true);
            actionLayout.setAlignItems(Alignment.CENTER);

            ComboBox<String> actionComboBox = new ComboBox<>();
            actionComboBox.setItems("Send Email", "Print Reports");
            actionComboBox.setPlaceholder("Select action");
            actionComboBox.setWidth("120px");

            // Enable/disable "Print Reports" based on status
            if (!"Finished".equals(status.getStatus())) {
                actionComboBox.setItems("Send Email");
            }

            Button submitButton = new Button("Submit");
            submitButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SMALL);
            submitButton.setEnabled(false);

            // Enable submit button when action is selected
            actionComboBox.addValueChangeListener(event -> {
                submitButton.setEnabled(event.getValue() != null);
            });

            submitButton.addClickListener(e -> {
                String selectedAction = actionComboBox.getValue();
                if (selectedAction != null) {
                    if ("Send Email".equals(selectedAction)) {
                        try {
                            emailService.sendEmail(status);
                            Notification.show("Email sent successfully", 3000, Notification.Position.TOP_CENTER);
                        } catch (Exception ex) {
                            Notification.show("Failed to send email: " + ex.getMessage(), 5000, Notification.Position.TOP_CENTER);
                        }
                    } else if ("Print Reports".equals(selectedAction)) {
                        try {
                            reportingService.printReports(status);
                            Notification.show("Reports generated successfully", 3000, Notification.Position.TOP_CENTER);
                        } catch (Exception ex) {
                            Notification.show("Failed to generate reports: " + ex.getMessage(), 5000, Notification.Position.TOP_CENTER);
                        }
                    }
                    // Reset the combo box after action
                    actionComboBox.clear();
                    submitButton.setEnabled(false);
                }
            });

            actionLayout.add(actionComboBox, submitButton);
            return actionLayout;
        }).setHeader("Action").setWidth("250px").setFlexGrow(0);
        // --- End action column ---

        // Set the data provider here
        subjectGrid.setDataProvider(pagingDataProvider);

        paginationControls.onPageChanged(() -> subjectGrid.getDataProvider().refreshAll());

        // Schedule data refresh every 10 seconds
        scheduler.scheduleAtFixedRate(() -> {
            if (ui != null) {
                ui.access(() -> {
                    // Replace existing data
                    pagingDataProvider.refreshAll();
                });
            }
        }, 0, 10, TimeUnit.SECONDS);

        add(wrapWithVerticalLayout(subjectGrid, paginationControls));
    }

    /**
     * Creates a vertical layout container for the grid and pagination components.
     *
     * <p>This utility method wraps the provided components in a properly configured
     * vertical layout with optimized spacing and padding settings for the data
     * display area.</p>
     *
     * @param component1 the first component (typically the data grid)
     * @param component2 the second component (typically pagination controls)
     * @return a configured VerticalLayout containing both components
     */
    private VerticalLayout wrapWithVerticalLayout(Component component1, Component component2) {
        var gridWithPaginationLayout = new VerticalLayout(component1, component2);
        gridWithPaginationLayout.setPadding(false);
        gridWithPaginationLayout.setSpacing(false);
        gridWithPaginationLayout.getThemeList().add("spacing-xs");
        return gridWithPaginationLayout;
    }

    /**
     * Generates the SQL query string based on current filter criteria.
     *
     * <p>This method dynamically constructs a JPQL query that incorporates all active
     * filter conditions from the search form. The query building process includes:</p>
     *
     * <h4>Required Filters:</h4>
     * <ul>
     *   <li><strong>Department Filter:</strong> Always applied based on selected departments</li>
     * </ul>
     *
     * <h4>Optional Filters:</h4>
     * <ul>
     *   <li><strong>Token Search:</strong> Case-insensitive partial matching</li>
     *   <li><strong>Name Filters:</strong> Case-insensitive partial matching for first and last names</li>
     *   <li><strong>Email Filter:</strong> Case-insensitive partial matching</li>
     *   <li><strong>Phone Filter:</strong> Partial matching without case conversion</li>
     * </ul>
     *
     * <p>All text-based filters use LIKE operators with wildcard matching for flexible
     * search capabilities. Only non-blank filter values are included in the query.</p>
     *
     * @return a JPQL query string incorporating all active filter criteria
     */
    private String getStatusSQL() {
        String departments = getSelectedDepartmentIds(departmentComboBox);
        String token = tokenField.getValue();
        String firstName = firstNameField.getValue();
        String lastName = lastNameField.getValue();
        String email = emailField.getValue();
        String phone = phoneField.getValue();

        StringBuilder jpql = new StringBuilder("SELECT s FROM Status s WHERE ");

        // Department IDs (required)
        jpql.append("s.department_id IN (").append(departments).append(")");

        // Optional filters
        if (token != null && !token.isBlank()) {
            jpql.append(" AND LOWER(s.token) LIKE LOWER('%").append(token).append("%')");
        }
        if (firstName != null && !firstName.isBlank()) {
            jpql.append(" AND LOWER(s.firstName) LIKE LOWER('%").append(firstName).append("%')");
        }
        if (lastName != null && !lastName.isBlank()) {
            jpql.append(" AND LOWER(s.lastName) LIKE LOWER('%").append(lastName).append("%')");
        }
        if (email != null && !email.isBlank()) {
            jpql.append(" AND LOWER(s.email) LIKE LOWER('%").append(email).append("%')");
        }
        if (phone != null && !phone.isBlank()) {
            jpql.append(" AND s.phone LIKE '%").append(phone).append("%'");
        }

        return jpql.toString();
    }

    /**
     * Extracts selected department IDs from the department combo box for query filtering.
     *
     * <p>This method processes the department selection and handles the special "All Departments"
     * option by converting it to the actual department IDs that the user has access to.</p>
     *
     * <h4>Processing Logic:</h4>
     * <ul>
     *   <li><strong>Empty Selection:</strong> Shows notification and returns empty string</li>
     *   <li><strong>"All Departments" Selected:</strong> Expands to all user's department IDs</li>
     *   <li><strong>Individual Departments:</strong> Returns specific department IDs</li>
     * </ul>
     *
     * <p>The method ensures that users can only query departments they have permission
     * to access, maintaining security boundaries in the search functionality.</p>
     *
     * @param departmentComboBox the multi-select combo box containing department selections
     * @return a comma-separated string of department IDs for use in SQL queries
     */
    private String getSelectedDepartmentIds(MultiSelectComboBox<Department> departmentComboBox) {
        String ids = "";
        Set<Department> selecteDepartments = departmentComboBox.getSelectedItems();
        if (selecteDepartments.isEmpty()) {
            Notification.show("Please select one or more departments", 3000, Notification.Position.MIDDLE);
        } else {
            for (Department department : selecteDepartments) {
                if (department.id == -1) {
                    for (Department d : user.getDepartments()) {
                        ids += d.id + ",";
                    }
                    break;
                }
                ids += department.id + ",";
            }
            ids = ids.substring(0, ids.length() - 1);
        }
        return ids;
    }

    /**
     * Provides the dynamic page title for the browser tab and navigation.
     *
     * @return the page title string
     * @see HasDynamicTitle#getPageTitle()
     */
    @Override    public String getPageTitle() {
        return "Elicit Search";
    }
}
