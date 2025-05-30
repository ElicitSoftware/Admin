package com.elicitsoftware.admin.flow;

import com.elicitsoftware.exception.TokenGenerationError;
import com.elicitsoftware.model.Department;
import com.elicitsoftware.model.Respondent;
import com.elicitsoftware.model.Subject;
import com.elicitsoftware.model.User;
import com.elicitsoftware.service.CsvImportService;
import com.elicitsoftware.service.TokenService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.component.upload.receivers.MemoryBuffer;
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
public class RegisterView extends HorizontalLayout implements HasDynamicTitle, BeforeEnterObserver {

    @Inject
    UiSessionLogin uiSessionLogin;

    @Inject
    TokenService tokenService;

    private User user;
    private Subject subject = new Subject();
    private Binder<Subject> binder; // Make binder a class field
    private Button saveButton;
    private Button updateButton;
    private VerticalLayout leftLayout = new VerticalLayout();
    private VerticalLayout rightLayout = new VerticalLayout();

    @PostConstruct
    public void init() {

        user = uiSessionLogin.getUser();

        FormLayout formLayout = new FormLayout();
        formLayout.setResponsiveSteps(
                new FormLayout.ResponsiveStep("0", 1)
        );
        formLayout.setWidth("100%");
        ComboBox<Department> departmentComboBox = getDepartmentComboBox();
        if (user.getDepartments().size() == 1) {
            departmentComboBox.setValue(user.getDepartments().iterator().next());
            subject.setDepartmentId(user.getDepartments().iterator().next().id);
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
                            return user.getDepartments().stream()
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

        leftLayout.add(formLayout, saveButton, updateButton);

        // Set the layouts to take 50% width
        leftLayout.setWidth("50%");
        rightLayout.setWidth("50%");

        // Create CSV upload button
        MemoryBuffer buffer = new MemoryBuffer();
        Upload csvUpload = new Upload(buffer);
        csvUpload.setAcceptedFileTypes(".csv");
        csvUpload.setMaxFiles(1);
        csvUpload.setMaxFileSize(5 * 1024 * 1024); // 5MB limit
        csvUpload.setUploadButton(new Button("Upload CSV"));

        csvUpload.addSucceededListener(event -> {
            try {
                CsvImportService importService = new CsvImportService(tokenService);
                int imported = importService.importSubjects(buffer.getInputStream(), user);
                Notification.show("Successfully imported " + imported + " subjects", 5000, Notification.Position.MIDDLE);
            } catch (Exception e) {
                Dialog errorDialog = new Dialog();
                errorDialog.setHeaderTitle("CSV Import Error");

                Span errorMessage = new Span(e.getMessage());
                errorMessage.getStyle().set("white-space", "pre-wrap");

                Button closeButton = new Button("Close", evt -> errorDialog.close());
                closeButton.getStyle().set("margin-top", "20px");

                VerticalLayout dialogLayout = new VerticalLayout(errorMessage, closeButton);
                dialogLayout.setAlignItems(FlexComponent.Alignment.CENTER);
                dialogLayout.setSpacing(true);

                errorDialog.add(dialogLayout);
                errorDialog.setModal(true);
                errorDialog.setDraggable(false);
                errorDialog.setResizable(true);
                errorDialog.setWidth("600px");
                errorDialog.setMaxWidth("90vw");

                errorDialog.open();
            }
        });

        // Create REST API instructions accordion
        Details restApiDetails = new Details("REST API Instructions", createRestApiContent());
        restApiDetails.setOpened(false);

        rightLayout.add(getRestfulInstructionsDiv(), csvUpload, restApiDetails);

        // Set the main layout (this) to use full width and ensure proper spacing
        setWidth("100%");
        setSpacing(true);

        // Add both layouts to the main view
        add(leftLayout, rightLayout);

        // Read the bean to populate the form
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
        departmentComboBox.setItems(user.getDepartments());
        departmentComboBox.setItemLabelGenerator(Department::getName);
        return departmentComboBox;
    }

    private Div getRestfulInstructionsDiv() {
        Div div = new Div();

        // Create CSV Structure Accordion
        Details csvDetails = new Details("CSV File Structure", createCsvStructureContent());
        csvDetails.setOpened(false); // Collapsed by default

        div.add(
            new Paragraph("You can register subjects individually using the form, or upload a CSV file with multiple subjects."),
            csvDetails
        );

        return div;
    }

    private Div createCsvStructureContent() {
        Div content = new Div();

        content.add(
            new Paragraph("The CSV file should contain the following columns in order:"),
            new Paragraph("#departmentId, firstName, lastName, middleName, dob, email, phone, xid"),
            new Paragraph("All rows starting with a '#' character are considered comments and will be ignored."),

            new H4("Column Descriptions:"),
            new Div() {{
                getElement().setProperty("innerHTML",
                    "<ul>" +
                    "<li><strong>departmentId:</strong> Integer (required) - Must be a valid department ID for the user</li>" +
                    "<li><strong>firstName:</strong> String (required) - Subject's first name</li>" +
                    "<li><strong>lastName:</strong> String (required) - Subject's last name</li>" +
                    "<li><strong>middleName:</strong> String (optional) - Subject's middle name</li>" +
                    "<li><strong>dob:</strong> Date (optional) - Date of birth in yyyy-MM-dd or MM/dd/yyyy format</li>" +
                    "<li><strong>email:</strong> String (required) - Valid email address</li>" +
                    "<li><strong>phone:</strong> String (optional) - Phone number in ###-###-#### format</li>" +
                    "<li><strong>xid:</strong> String (optional) - External ID for the subject</li>" +
                    "</ul>"
                );
            }},

            new H4("Example CSV data:"),
            new Pre("#departmentId,firstName,lastName,middleName,dob,email,phone,xid\n" +
                   "1,John,Doe,Michael,1990-01-15,john.doe@email.com,123-456-7890,EXT001\n" +
                   "2,Jane,Smith,,1985-03-22,jane.smith@email.com,555-123-4567,EXT002\n" +
                   "1,Bob,Johnson,Robert,12/10/1992,bob.johnson@email.com,999-888-7777,EXT003")
        );

        return content;
    }
    private Div createRestApiContent() {
        Div content = new Div();

        content.add(
            new Paragraph("You can also add subjects programmatically using the REST API endpoint:"),

            new H4("Endpoint:"),
            new Pre("POST /secured/add/subject"),

            new H4("Authentication:"),
            new Paragraph("Requires 'user' or 'token' role authorization"),

            new H4("Content-Type:"),
            new Pre("application/json"),

            new H4("Request Body Example:"),
            new Pre("{\n" +
                   "  \"surveyId\": 1,\n" +
                   "  \"departmentId\": 1,\n" +
                   "  \"firstName\": \"John\",\n" +
                   "  \"lastName\": \"Doe\",\n" +
                   "  \"middleName\": \"Michael\",\n" +
                   "  \"dob\": \"1990-01-15\",\n" +
                   "  \"email\": \"john.doe@email.com\",\n" +
                   "  \"phone\": \"123-456-7890\",\n" +
                   "  \"xid\": \"EXT001\"\n" +
                   "}"),

            new H4("Response Example:"),
            new Pre("{\n" +
                   "  \"respondentId\": 12345,\n" +
                   "  \"token\": \"ABC123XYZ\",\n" +
                   "  \"error\": null\n" +
                   "}")
        );

        return content;
    }
    @Override
    public String getPageTitle() {
        return "Elicit Register";
    }
}