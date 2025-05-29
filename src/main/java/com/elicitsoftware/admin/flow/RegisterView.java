package com.elicitsoftware.admin.flow;

import com.elicitsoftware.exception.TokenGenerationError;
import com.elicitsoftware.model.Department;
import com.elicitsoftware.model.Respondent;
import com.elicitsoftware.model.Subject;
import com.elicitsoftware.model.User;
import com.elicitsoftware.service.TokenService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.binder.ValidationException;
import com.vaadin.flow.data.validator.EmailValidator;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.LocalDate;
import java.util.Optional;

@Route(value = "register", layout = MainLayout.class)
@RolesAllowed("user")
public class RegisterView extends VerticalLayout implements HasDynamicTitle, BeforeEnterObserver {

    @Inject
    UiSessionLogin uiSessionLogin;

    @Inject
    TokenService tokenService;

    private User user;
    private Subject subject = new Subject();
    private Binder<Subject> binder; // Make binder a class field
    private Button saveButton;
    private Button updateButton;

    @PostConstruct
    public void init() {

        user = uiSessionLogin.getUser();

        FormLayout formLayout = new FormLayout();
        formLayout.setResponsiveSteps(
                new FormLayout.ResponsiveStep("0", 1)
        );
        formLayout.setWidth("600px");
        ComboBox<Department> departmentComboBox = getDepartmentComboBox();
        if (user.departments.size() == 1) {
            departmentComboBox.setValue(user.departments.iterator().next());
            subject.setDepartmentId(user.departments.iterator().next().id);
        }

        TextField firstName = new TextField("First Name");
        TextField lastName = new TextField("Last Name");
        TextField middleName = new TextField("Middle Name");
        DatePicker dob = new DatePicker("Date of Birth");
        EmailField email = new EmailField("Email");
        TextField phone = new TextField("Phone");
        phone.setPlaceholder("123-456-7890");
        TextField xid = new TextField("external ID");

        formLayout.add(departmentComboBox, firstName, lastName, middleName, dob, email, phone, xid);

        binder = new Binder<>(Subject.class);

        // Add this binder for departmentComboBox
        binder.forField(departmentComboBox)
                .asRequired("Department is required")
                .bind(
                        s -> {
                            // Find the Department object by id
                            if (s.getDepartmentId() == 0) return null;
                            return user.departments.stream()
                                    .filter(d -> d.id == s.getDepartmentId())
                                    .findFirst()
                                    .orElse(null);
                        },
                        (s, dept) -> s.setDepartmentId(dept == null ? null : dept.id)
                );

        binder.forField(xid)
                .withConverter(
                        str -> str == null || str.trim().isEmpty() ? null : str,
                        obj -> obj == null ? "" : obj
                )
                .bind("xid");

        binder.forField(firstName)
                .asRequired("First name is required")
                .bind("firstName");

        binder.forField(lastName)
                .asRequired("Last name is required")
                .bind("lastName");

        binder.forField(middleName)
                .bind("middleName");

        binder.forField(dob)
                .withValidator(date -> date == null || date.isBefore(java.time.LocalDate.now()), "DOB must be in the past")
                .bind(
                        s -> s.getDob() == null ? null : s.getDob(),
                        (s, value) -> s.setDob(value == null ? null : LocalDate.from(value))
                );

        binder.forField(email)
                .withValidator(new EmailValidator("Enter a valid email address"))
                .bind("email");

        binder.forField(phone)
                .withConverter(
                        str -> (str == null || str.trim().isEmpty()) ? null : str,
                        obj -> obj == null ? "" : obj
                )
                .withValidator(
                        phoneVal -> phoneVal == null || phoneVal.matches("^\\d{3}-\\d{3}-\\d{4}$"),
                        "Phone must be ###-###-####"
                )
                .bind("phone");

        saveButton = new Button("Save", event -> {
            try {
                saveSubject(binder);
            } catch (jakarta.persistence.PersistenceException e) {
                Notification.show("Duplicate entry: A subject with this External ID " + subject.getXid() + " already exists for this department.", 5000, Notification.Position.MIDDLE);
                subject = new Subject();
            } catch (Exception e) {
                Notification.show("Database error: " + e.getMessage(), 5000, Notification.Position.MIDDLE);
                subject = new Subject();
            }
        });

        updateButton = new Button("Update Subject", event -> {
            try {
                updateSubject(binder);
                // Navigate back to the search view after update
                getUI().ifPresent(ui -> ui.navigate(""));
            } catch (Exception e) {
                Notification.show("Database error: " + e.getMessage(), 5000, Notification.Position.MIDDLE);
            }
        });

        // By default, show only saveButton
        saveButton.setVisible(true);
        updateButton.setVisible(false);

        add(formLayout, saveButton, updateButton);
        binder.readBean(subject);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        Optional<String> tokenOpt = event.getLocation().getQueryParameters().getParameters().getOrDefault("token", java.util.List.of()).stream().findFirst();
        if (tokenOpt.isPresent()) {
            String token = tokenOpt.get();
            // Fetch the subject by token (implement this in your StatusDataSource or Subject repository)
            Subject found = Subject.findSubjectByToken(token);
            if (found != null) {
                this.subject = found;
                if (binder != null) {
                    binder.readBean(subject);
                }
                // Editing: show update, hide save
                if (updateButton != null && saveButton != null) {
                    updateButton.setVisible(true);
                    saveButton.setVisible(false);
                }
            } else {
                Notification.show("Subject not found for token: " + token, 3000, Notification.Position.MIDDLE);
                // New: show save, hide update
                if (updateButton != null && saveButton != null) {
                    updateButton.setVisible(false);
                    saveButton.setVisible(true);
                }
            }
        } else {
            // New: show save, hide update
            if (updateButton != null && saveButton != null) {
                updateButton.setVisible(false);
                saveButton.setVisible(true);
            }
        }
    }

    public void saveSubject(Binder<Subject> binder) {
        try {
            binder.writeBean(subject);
            Respondent respondent = tokenService.getToken(1);
            subject.setRespondent(respondent);
            subject.setSurveyId(respondent.survey.id);
            // Optionally, flush to force exception now:
            subject.persistAndFlush();
            Notification.show("Subject saved", 3000, Notification.Position.MIDDLE);
            subject = new Subject();
            binder.readBean(subject); // reset form
        } catch (ValidationException e) {
            Notification.show("Please fix validation errors", 3000, Notification.Position.MIDDLE);
        } catch (TokenGenerationError e) {
            Notification.show("Error generating new token. Please try again", 3000, Notification.Position.MIDDLE);
        }
    }

    // New method for updating an existing subject
    @Transactional
    public void updateSubject(Binder<Subject> binder) {
        try {
            binder.writeBean(subject);
            subject = Subject.getEntityManager().merge(subject);
            Subject.getEntityManager().flush();
            Notification.show("Subject updated", 3000, Notification.Position.MIDDLE);
        } catch (ValidationException e) {
            Notification.show("Please fix validation errors", 3000, Notification.Position.MIDDLE);
        }
    }

    private ComboBox<Department> getDepartmentComboBox() {
        ComboBox<Department> departmentComboBox = new ComboBox<>("Deparments");
        departmentComboBox.setItems(user.departments);
        departmentComboBox.setItemLabelGenerator(Department::getName);
        return departmentComboBox;
    }

    @Override
    public String getPageTitle() {
        return "Elicit Register";
    }
}