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
import com.vaadin.flow.component.Direction;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.MultiSelectComboBox;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H5;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.Route;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.persistence.Query;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
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
class MainView extends VerticalLayout implements HasDynamicTitle {

    @Inject
    SecurityIdentity identity;

    @Inject
    UiSessionLogin uiSessionLogin;

    User user;

    Grid<Status> subjectGrid;

    @PostConstruct
    public void init() {

        user = uiSessionLogin.getUser();

        if (user == null) {
            add(new Paragraph(identity.getPrincipal().getName() + " user not found in the survey database. Please ask an adminstrator create a new user. "));
        } else {
            //Set up the I18n
            final UI ui = UI.getCurrent();
            if (ui.getLocale().getLanguage().equals("ar")) {
                ui.setDirection(Direction.RIGHT_TO_LEFT);
            } else {
                ui.setDirection(Direction.LEFT_TO_RIGHT);
            }
        }
        add(new H5("Subject search"));
        createSearchBar();
        createRespondentsTable();
    }

    private void createSearchBar() {
        HorizontalLayout searchBar = new HorizontalLayout();
        searchBar.setPadding(true);
        searchBar.setJustifyContentMode(FlexComponent.JustifyContentMode.CENTER);
        searchBar.setWidth("100%");
        searchBar.setSpacing(true);

        //Add input fields
        MultiSelectComboBox<Department> departmentComboBox = getDepartmentComboBox();
        searchBar.add(departmentComboBox);

        TextField firstNameField = new TextField("First name");
        searchBar.add(firstNameField);

        TextField lastNameField = new TextField("Last name");
        searchBar.add(lastNameField);

        TextField email = new TextField("Email");
        searchBar.add(email);

        TextField phone = new TextField("Phone");
        searchBar.add(phone);

        Button searchButton = new Button("Search");
        searchButton.addClickListener(e -> {
            populateSubjectGrid(getSelectedDepartmentIds(departmentComboBox), firstNameField.getValue(), lastNameField.getValue(), email.getValue(), phone.getValue());
        });
        searchBar.add(searchButton);
        add(searchBar);
    }

    private void createRespondentsTable() {
        VerticalLayout respondentsLayout = new VerticalLayout();
        respondentsLayout.setSizeFull();
        add(respondentsLayout);
        add(getSubjectGrid());
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
        departmentsWithAll.addAll(user.departments);
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
            } else if(newValues.size() == 1 && newValues.contains(allDepartments)) {
                departmentComboBox.setValue(Set.of(allDepartments));
            }else {
                // Optionally, you can de-select "All Departments"
                Set<Department> filtered = newValues.stream()
                        .filter(d -> d.id != -1)
                        .collect(Collectors.toSet());
                departmentComboBox.setValue(filtered);
            }
        });

        return departmentComboBox;
    }

    private Grid<Status> getSubjectGrid() {
        subjectGrid = new Grid<>(Status.class, true);
//        subjectGrid.addColumn(Status::getToken).setHeader("Token");
//        subjectGrid.addColumn(Status::getDepartmentName).setHeader("Department");
//        subjectGrid.addColumn(Status::getFirstName).setHeader("First name");
//        subjectGrid.addColumn(Status::getMiddleName).setHeader("Middle name");
//        subjectGrid.addColumn(Status::getLastName).setHeader("Last name");
//        subjectGrid.addColumn(Status::getEmail).setHeader("Email");
//        subjectGrid.addColumn(Status::getPhone).setHeader("Phone");
//        subjectGrid.addColumn(Status::getStatus).setHeader("Status");
        return subjectGrid;
    }

    private void populateSubjectGrid(String departments, String firstName, String lastName, String email, String phone) {
        String sql = getStatusSQL(departments, firstName, lastName, email, phone);
        Query query = Status.getEntityManager().createNativeQuery(sql, Status.class);
        List<Status> statusList = query.getResultList();
        subjectGrid.setItems(statusList);
    }

    private String getStatusSQL(String departments, String firstName, String lastName, String email, String phone) {
        String sql = """
                 SELECT
                    s.id,
                    s.survey_id,
                 	s.firstname,
                	s.lastname,
                	s.dob,
                	s.email,
                	s.middlename,
                	s.phone,
                	s.xid,
                	s.created_dt,
                	s.department_name,
                    s.token,
                    s.status
                    FROM survey.status s
                    WHERE s.department_id in (
                """;
        sql += departments + ")";
        return sql;
    }

    private String getSelectedDepartmentIds(MultiSelectComboBox<Department> departmentComboBox) {
        String ids = "";
        Set<Department> selecteDepartments = departmentComboBox.getSelectedItems();
        if (selecteDepartments.isEmpty()) {
            Notification.show("Please select one or more departments", 3000, Notification.Position.MIDDLE);
        } else {
            for (Department department : selecteDepartments) {
                if (department.id == -1) {
                    for (Department d : user.departments) {
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
        return "Elicit Administration";
    }
}
