package com.elicitsoftware.admin.flow;

import com.elicitsoftware.exception.TokenGenerationError;
import com.elicitsoftware.model.Department;
import com.elicitsoftware.model.Respondent;
import com.elicitsoftware.model.Subject;
import com.elicitsoftware.model.User;
import com.elicitsoftware.request.AddRequest;
import com.elicitsoftware.service.TokenService;
import com.elicitsoftware.service.TransactionService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.MultiSelectComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.binder.ValidationException;
import com.vaadin.flow.data.validator.EmailValidator;
import com.vaadin.flow.data.validator.RegexpValidator;
import com.vaadin.flow.router.Route;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.ZoneId;
import java.util.Date;

@Route(value = "register", layout = MainLayout.class)
@RolesAllowed("user")
public class SubjectFormView extends VerticalLayout {

    @Inject
    TransactionService transactionService;

    @Inject
    TokenService tokenService;

    private User user;
    private Subject subject = new Subject();

    @PostConstruct
    public void init() {

//        user = (User) VaadinSession.getCurrent().getAttribute("user");

        FormLayout formLayout = new FormLayout();
        formLayout.setResponsiveSteps(
            new FormLayout.ResponsiveStep("0", 1)
        );
        formLayout.setWidth("600px");
//        MultiSelectComboBox multiSelectComboBox = getDepartmentComboBox();
//        if(multiSelectComboBox.getSelectedItems().size() == 1){
//            multiSelectComboBox.setValue(1);
//        }
        TextField firstName = new TextField("First Name");
        TextField lastName = new TextField("Last Name");
        TextField middleName = new TextField("Middle Name");
        DatePicker dob = new DatePicker("Date of Birth");
        EmailField email = new EmailField("Email");
        TextField phone = new TextField("Phone");
        phone.setPlaceholder("123-456-7890");

        // Add a value change listener to auto-format the phone number
        phone.addValueChangeListener(event -> {
            String digits = event.getValue().replaceAll("\\D", "");
            if (digits.length() == 10) {
                String formatted = String.format("%s-%s-%s",
                        digits.substring(0, 3),
                        digits.substring(3, 6),
                        digits.substring(6, 10));
                // Avoid infinite loop by only setting if different
                if (!formatted.equals(event.getValue())) {
                    phone.setValue(formatted);
                }
            }
        });
        TextField xid = new TextField("external ID");

        formLayout.add(firstName, lastName, middleName, dob, email, phone, xid);

        Binder<Subject> binder = new Binder<>(Subject.class);

        binder.forField(xid)
                .withConverter(
                        str -> str == null || str.trim().isEmpty() ? null : str,
                        obj -> obj == null ? "" : obj
                )
                .asRequired("First name is required")
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
                        s -> s.getDob() == null ? null : s.getDob().toInstant().atZone(ZoneId.systemDefault()).toLocalDate(),
                        (s, value) -> s.setDob(value == null ? null : Date.from(value.atStartOfDay(ZoneId.systemDefault()).toInstant()))
                );

        binder.forField(email)
                .withValidator(new EmailValidator("Enter a valid email address"))
                .bind("email");

        binder.forField(phone)
                .withValidator(new RegexpValidator("Phone must be ###-###-####", "^\\d{3}-\\d{3}-\\d{4}$"))
                .bind("phone");

        Button saveButton = new Button("Save", event -> {
            try {
                saveSubject(binder);
            } catch (jakarta.persistence.PersistenceException e) {
                Notification.show("Duplicate entry: A subject with this External ID " + subject.getXid() + " already exists for this department.", 5000, Notification.Position.MIDDLE);
                subject = new Subject();
                return;
            } catch (Exception e) {
                Notification.show("Database error: " + e.getMessage(), 5000, Notification.Position.MIDDLE);
                subject = new Subject();
                return;
            }
        });
        add(formLayout, saveButton);
        binder.readBean(subject);
    }

    @Transactional
    public void saveSubject(Binder<Subject> binder) {
        try {
            binder.writeBean(subject);
            AddRequest request = new AddRequest();
            Respondent respondent = tokenService.getToken(1);
            subject.setRespondent(respondent);
            subject.setSurveyId(respondent.survey.id);
            subject.setDepartmentId(1);
            subject.persist();
            // Optionally, flush to force exception now:
            Subject.getEntityManager().flush();
            Notification.show("Subject saved", 3000, Notification.Position.MIDDLE);
            subject = new Subject();
            binder.readBean(subject); // reset form
        } catch (ValidationException e) {
            Notification.show("Please fix validation errors", 3000, Notification.Position.MIDDLE);
        } catch (TokenGenerationError e) {
            Notification.show("Error generating new token. Please try again", 3000, Notification.Position.MIDDLE);
        }
    }

    private MultiSelectComboBox getDepartmentComboBox() {
        MultiSelectComboBox<Department> departmentComboBox = new MultiSelectComboBox<>("Deparments");
        departmentComboBox.setItems(user.departments);
        departmentComboBox.setItemLabelGenerator(Department::getName);
        return departmentComboBox;
    }
}