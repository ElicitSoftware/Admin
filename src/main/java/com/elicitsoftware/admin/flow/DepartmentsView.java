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

import java.util.List;

import com.elicitsoftware.model.Department;
import com.elicitsoftware.service.DepartmentService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;

/**
 * A Vaadin Flow view that displays a list of departments in a grid format.
 * This view provides functionality to view, edit, and create new departments.
 * 
 * <p>The view is accessible at the "/departments" route and requires 
 * "elicit_admin" role for access. It displays departments in a sortable grid
 * with edit buttons for each row and includes a button to create new departments.</p>
 * 
 * @author Elicit Software
 */
@Route(value = "departments", layout = MainLayout.class)
@RolesAllowed("elicit_admin")
public class DepartmentsView extends VerticalLayout {

    /**
     * Reads each department's surveys for the grid (UC-006 BR-172). Injected after the
     * constructor runs, which is before the grid first renders a row.
     */
    @Inject
    DepartmentService departmentService;

    /** The injected service, or CDI's, for a view a test built with {@code new}. */
    private DepartmentService departmentService() {
        if (departmentService == null) {
            departmentService = jakarta.enterprise.inject.spi.CDI.current().select(DepartmentService.class).get();
        }
        return departmentService;
    }
    /**
     * Constructs a new DepartmentsView.
     * 
     * <p>Initializes the view by creating a grid to display departments with
     * the following features:</p>
     * <ul>
     *   <li>Edit button column with edit icons for each department</li>
     *   <li>Department name column that is sortable and auto-width</li>
     *   <li>A "New Department" button to create new departments</li>
     * </ul>
     * 
     * <p>The grid is populated with all departments retrieved from the database
     * using the Department.findAll() method.</p>
     */
    public DepartmentsView() {

        // Create a grid to display departments
       Grid<Department> grid = new Grid<>(Department.class, false);
        grid.addComponentColumn(department -> {
            Button editBtn = new Button(new Icon(VaadinIcon.EDIT));
            editBtn.addThemeVariants(ButtonVariant.LUMO_ICON);
            editBtn.addClickListener(e ->
                editDepartment(department.id)
            );
            return editBtn;
        }).setHeader(getTranslation("common.edit")).setWidth("80px").setFlexGrow(0);

        grid.addColumn(Department::getName).setHeader(getTranslation("departmentsView.grid.name")).setSortable(true).setAutoWidth(true);
        grid.addColumn(Department::getCode).setHeader(getTranslation("departmentsView.grid.code")).setSortable(true).setAutoWidth(true);
        grid.addColumn(Department::getDefaultMessageId).setHeader(getTranslation("departmentsView.grid.defaultMessageId")).setSortable(true).setAutoWidth(true);
        grid.addColumn(Department::getFromEmail).setHeader(getTranslation("departmentsView.grid.fromEmail")).setSortable(true).setAutoWidth(true);
        // UC-006 step 1, BR-172: the surveys each department registers onto.
        grid.addColumn(department -> departmentService().surveysOf(department.id).stream()
                .map(survey -> survey.name).collect(java.util.stream.Collectors.joining(", ")))
                .setHeader(getTranslation("departmentsView.grid.surveys")).setAutoWidth(true);
        // Eager load: acceptable for the small departments table. If the department count grows
        // past ~1000 rows, switch to a lazy CallbackDataProvider (see the data-providers skill).
        List<Department> departments = Department.findAll().list();
        grid.setItems(departments);
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_COMPACT);
        Button newDepartmentButton = new Button(getTranslation("departmentsView.newDepartment"), event ->
            getUI().ifPresent(ui -> ui.navigate("edit-department/0"))
        );
        newDepartmentButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        add(grid);
        add(new HorizontalLayout(newDepartmentButton));
    }

    /**
     * Navigates to the edit department view for the specified department.
     * 
     * <p>This method constructs the appropriate navigation URL using the
     * provided department ID to edit an existing department.</p>
     * 
     * @param departmentId the ID of the department to edit
     */
    private void editDepartment(Long departmentId) {
        getUI().ifPresent(ui ->
                ui.navigate("edit-department/" + departmentId)
        );
    }
}
