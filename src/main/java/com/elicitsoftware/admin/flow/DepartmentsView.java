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

@Route(value = "departments", layout = MainLayout.class)
@RolesAllowed("elicit_admin")
public class DepartmentsView extends VerticalLayout {
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

    private void editDepartment(Long departmentId) {
        String idParam = (departmentId > 0) ? String.valueOf(departmentId) : "0";
        getUI().ifPresent(ui ->
                ui.navigate("edit-department/" + idParam)
        );
    }
}
