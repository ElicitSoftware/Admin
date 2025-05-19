package com.elicitsoftware.admin.flow;

import com.elicitsoftware.admin.TransactionService;
import com.elicitsoftware.model.Subject;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.binder.ValidationException;
import com.vaadin.flow.data.validator.EmailValidator;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;

import java.time.ZoneId;
import java.util.Date;

@Route(value = "register", layout = MainLayout.class)
@RolesAllowed("user")
public class SubjectFormView extends VerticalLayout {

    @Inject
    TransactionService transactionService;

    private Subject subject = new Subject();

    public SubjectFormView() {
        FormLayout formLayout = new FormLayout();

        TextField xid = new TextField("external ID");
        TextField firstName = new TextField("First Name");
        TextField lastName = new TextField("Last Name");
        TextField middleName = new TextField("Middle Name");
        DatePicker dob = new DatePicker("Date of Birth");
        EmailField email = new EmailField("Email");
        TextField mobile = new TextField("Mobile");

        formLayout.add(xid, firstName, lastName, middleName, dob, email, mobile);

        Binder<Subject> binder = new Binder<>(Subject.class);

        binder.forField(xid)
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

        binder.forField(mobile)
                .bind("mobile");

        Button saveButton = new Button("Save", event -> {
            try {
                binder.writeBean(subject);
                subject = transactionService.saveSubject(subject);
                Notification.show("Subject saved", 3000, Notification.Position.MIDDLE);
                binder.readBean(new Subject()); // reset form
            } catch (ValidationException e) {
                Notification.show("Please fix validation errors", 3000, Notification.Position.MIDDLE);
            }
        });
        add(formLayout, saveButton);
        binder.readBean(subject);
    }
}