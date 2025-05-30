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
import com.elicitsoftware.service.StatusDataSource;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Direction;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.MultiSelectComboBox;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.HeaderRow;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H5;
import com.vaadin.flow.component.html.Paragraph;
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
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.security.RolesAllowed;
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
 * The `MainView` class represents the main view of the application, providing the user interface
 * for login functionality and handling survey selection. It is the default landing page of the
 * application and dynamically updates its title based on the current locale.
 * <p>
 * This class extends `VerticalLayout` and implements `HasDynamicTitle` to define the main layout
 * and its dynamic page title. It utilizes dependency injection for `TokenService` and
 * `QuestionService` to interact with business logic related to user authentication and survey
 * initialization.
 * <p>
 * The view dynamically adjusts its layout based on the user’s locale, supporting both left-to-right
 * (LTR) and right-to-left (RTL) layouts. It incorporates Vaadin components such as
 * `TextField`, `Button`, and `ComboBox` for user interaction.
 * <p>
 * Key Features:
 * - Language-sensitive layout direction (LTR/RTL).
 * - Login functionality using a token supplied by the user.
 * - Survey selection with support for multiple or single available surveys.
 * - Navigation to different views (`section` or `report`) depending on the respondent's state.
 * - Accessibility and enhanced user experience with theme variants, tooltips, and keyboard shortcuts.
 */
@Route(value = "", layout = MainLayout.class)
@RolesAllowed("user")
class SearchView extends VerticalLayout implements HasDynamicTitle {

    private final PaginationControls paginationControls = new PaginationControls();
    private final StatusDataSource dataSource = new StatusDataSource();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    @Inject
    SecurityIdentity identity;
    @Inject
    UiSessionLogin uiSessionLogin;
    User user;
    Grid<Status> subjectGrid;
    private UI ui;
    // Add these as class fields:
    private MultiSelectComboBox<Department> departmentComboBox;
    private TextField tokenField;
    private TextField firstNameField;
    private TextField lastNameField;
    private TextField emailField;
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

    private void createSubjectsTable() {
        VerticalLayout respondentsLayout = new VerticalLayout();
        respondentsLayout.setSizeFull();
        add(respondentsLayout);
        getSubjectGrid();
    }

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
        // --- End edit icon column ---

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

    private VerticalLayout wrapWithVerticalLayout(Component component1, Component component2) {
        var gridWithPaginationLayout = new VerticalLayout(component1, component2);
        gridWithPaginationLayout.setPadding(false);
        gridWithPaginationLayout.setSpacing(false);
        gridWithPaginationLayout.getThemeList().add("spacing-xs");
        return gridWithPaginationLayout;
    }

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

    @Override
    public String getPageTitle() {
        return "Elicit Search";
    }
}
