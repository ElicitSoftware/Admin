package com.elicitsoftware.admin.flow;

import com.elicitsoftware.model.User;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;
import jakarta.transaction.Transactional;

import java.util.List;

@Route(value = "users", layout = MainLayout.class)
@RolesAllowed("admin")
public class UsersView extends VerticalLayout {

    private Grid<User> userGrid = new Grid<>(User.class, false);

    public UsersView() {
        configureGrid();
        add(createInfoText(), userGrid, createFooter());
        updateGrid();
    }

    private Paragraph createInfoText() {
        Paragraph info = new Paragraph("Users must be configured in the OIDC authentication system with the roles \"Admin\" or \"User\". Departments are assigned through this interface.");
        info.getStyle().set("margin-bottom", "1em");
        info.getStyle().set("color", "var(--lumo-secondary-text-color)");
        return info;
    }

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

    private void updateGrid() {
        List<User> users = User.listAll();
        userGrid.setItems(users);
    }

    private void editUser(long userId) {
        String idParam = (userId > 0) ? String.valueOf(userId) : "0";
        getUI().ifPresent(ui ->
                ui.navigate("edit-user/" + idParam)
        );
    }

    private HorizontalLayout createFooter() {
        Button addUserBtn = new Button("Add User", e -> addUser());
        return new HorizontalLayout(addUserBtn);
    }

    private void addUser() {
        getUI().ifPresent(ui -> ui.navigate("edit-user/0"));
    }

}
