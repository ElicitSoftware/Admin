package com.elicitsoftware.admin.flow;

import com.elicitsoftware.model.Department;
import com.elicitsoftware.model.User;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.MultiSelectComboBox;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;
import jakarta.transaction.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Route(value = "edit-user/:id?", layout = MainLayout.class)
@RolesAllowed("admin")
public class EditUserView extends VerticalLayout implements BeforeEnterObserver {

    private User user;
    private TextField username = new TextField("Username");
    private TextField firstName = new TextField("First Name");
    private TextField lastName = new TextField("Last Name");
    private Checkbox activeCheckbox = new Checkbox("Active");
    private MultiSelectComboBox<Department> departmentsBox = new MultiSelectComboBox<>("Departments");

    public EditUserView() {
        departmentsBox.setItemLabelGenerator(Department::getName);
        List<Department> allDepartments = Department.findAll().list();
        departmentsBox.setItems(allDepartments);

        add(username, firstName, lastName, activeCheckbox, departmentsBox);

        Button saveBtn = new Button("Save", e -> saveUser());
        Button cancelBtn = new Button("Cancel", e -> cancelEdit());

        add(new HorizontalLayout(saveBtn, cancelBtn));
    }

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
            user = (User) user.getEntityManager().merge(user);
        }

        Notification.show("User saved");
        getUI().ifPresent(ui -> ui.navigate(UsersView.class));
    }

    private void cancelEdit() {
        getUI().ifPresent(ui -> ui.navigate(UsersView.class));
    }
}