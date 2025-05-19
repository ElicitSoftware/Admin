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
import com.elicitsoftware.model.Subject;
import com.elicitsoftware.model.User;
import com.vaadin.flow.component.Direction;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.combobox.MultiSelectComboBox;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.quarkus.annotation.UIScoped;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import jakarta.persistence.Transient;

import java.util.List;

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
public class MainView extends VerticalLayout implements HasDynamicTitle {

    @Inject
    SecurityIdentity identity;

    @Inject
    UiSessionLogin uiSessionLogin;

    @Transient
    User user;

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

            this.setAlignItems(Alignment.CENTER);
            if (identity.hasRole("admin")) {
                add(new Paragraph("You are also an admin! You can navigate to the admin view either via sidebar or the button below."));
            } else {
                add(new Paragraph("You are not an admin. If you try clicking the button below the view won't be shown (behaviour depends a bit if on dev mode (shows known views) or on production (empty screen/404)."));
            }
        }
        createRespondentsTable();
    }

    private void createRespondentsTable() {
        VerticalLayout respondentsLayout = new VerticalLayout();
        respondentsLayout.setSizeFull();

        //If there are more than one department give the user a dropbox to choose one or all.
        respondentsLayout.add(getDepartmentComboBox());
        add(respondentsLayout);
        add(getSubjectGrid());
    }

    private MultiSelectComboBox getDepartmentComboBox() {
        MultiSelectComboBox<Department> departmentComboBox = new MultiSelectComboBox<>("Deparments");
        List<Department> departments = Department.listAll();
        departmentComboBox.setItems(user.departments);
        departmentComboBox.setItemLabelGenerator(Department::getName);
        return departmentComboBox;
    }

    private Grid<Subject> getSubjectGrid() {
        Grid<Subject> grid = new Grid<>(Subject.class, false);
        grid.addColumn(Subject::getFirstName).setHeader("First name");
        grid.addColumn(Subject::getLastName).setHeader("Last name");
        grid.addColumn(Subject::getEmail).setHeader("Email");
        grid.addColumn(Subject::getPhone).setHeader("Phone");

        List<Subject> subjects = Subject.listAll();
        grid.setItems(subjects);

        return grid;
    };

    @Override
    public String getPageTitle() {
        return "";
    }
}
