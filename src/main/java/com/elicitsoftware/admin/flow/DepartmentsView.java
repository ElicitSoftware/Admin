package com.elicitsoftware.admin.flow;

import java.util.List;

import com.elicitsoftware.model.Department;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;

/**
 * A Vaadin Flow view that displays a list of departments in a grid format.
 * This view provides functionality to view, edit, and create new departments.
 * 
 * <p>The view is accessible at the "/departments" route and requires 
 * "elicit_admin" role for access. It displays departments in a sortable grid
 * with edit buttons for each row and includes a button to create new departments.</p>
 * 
 * @author Elicit Software
 * @version 1.0
 * @since 1.0
 */
@Route(value = "departments", layout = MainLayout.class)
@RolesAllowed("elicit_admin")
public class DepartmentsView extends VerticalLayout {
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
            editBtn.addClickListener(e ->
                editDepartment(-1l)
            );
            return editBtn;
        }).setHeader("Edit").setWidth("40px");

        grid.addColumn(Department::getName).setHeader("Department Name").setSortable(true).setAutoWidth(true);

        List<Department> departments = Department.findAll().list();
        grid.setItems(departments);
        Button newDepartmentButton = new Button("New Department", event ->
            getUI().ifPresent(ui -> ui.navigate(EditDepartmentView.class))
        );

        add(grid);
        add(new HorizontalLayout(newDepartmentButton));
    }

    /**
     * Navigates to the edit department view for the specified department.
     * 
     * <p>This method constructs the appropriate navigation URL based on the
     * provided department ID. If the department ID is greater than 0, it uses
     * the actual ID; otherwise, it uses "0" which typically indicates creating
     * a new department.</p>
     * 
     * @param departmentId the ID of the department to edit, or a value <= 0 
     *                     to indicate creation of a new department
     */
    private void editDepartment(Long departmentId) {
        String idParam = (departmentId > 0) ? String.valueOf(departmentId) : "0";
        getUI().ifPresent(ui ->
                ui.navigate("edit-department/" + idParam)
        );
    }
}
