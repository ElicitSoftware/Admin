package com.elicitsoftware.admin.flow;

import com.elicitsoftware.exception.TokenGenerationError;
import com.elicitsoftware.model.*;
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
import java.util.ArrayList;
import java.util.Optional;

/**
 * A comprehensive subject registration view that provides multiple methods for adding
 * and updating subjects in the system. This view serves as a central hub for subject
 * management with support for individual registration, bulk CSV uploads, and REST API integration.
 * 
 * <p>The view features a two-column layout:</p>
 * <ul>
 *   <li><strong>Left Column:</strong> Subject registration form with validation</li>
 *   <li><strong>Right Column:</strong> CSV upload functionality and API documentation</li>
 * </ul>
 * 
 * <p>Key features include:</p>
 * <ul>
 *   <li><strong>Individual Registration:</strong> Complete form with validation for all subject fields</li>
 *   <li><strong>Bulk CSV Import:</strong> Upload CSV files with multiple subjects for batch processing</li>
 *   <li><strong>Subject Updates:</strong> Edit existing subjects using URL tokens</li>
 *   <li><strong>Department Integration:</strong> Automatic department filtering based on user permissions</li>
 *   <li><strong>Token Generation:</strong> Automatic creation of unique tokens for survey access</li>
 *   <li><strong>Message Creation:</strong> Automatic generation of communication messages for new subjects</li>
 * </ul>
 * 
 * <p>The view supports two operational modes:</p>
 * <ul>
 *   <li><strong>Registration Mode:</strong> Default mode for creating new subjects</li>
 *   <li><strong>Update Mode:</strong> Activated when accessing with a valid subject token parameter</li>
 * </ul>
 * 
 * <p>Form validation includes:</p>
 * <ul>
 *   <li>Required field validation (department, first name, last name, email)</li>
 *   <li>Email format validation</li>
 *   <li>Phone number format validation (###-###-####)</li>
 *   <li>Date of birth validation (must be in the past)</li>
 *   <li>External ID uniqueness within department</li>
 * </ul>
 * 
 * @author Elicit Software
 * @version 1.0
 * @since 1.0
 * @see Subject
 * @see CsvImportService
 * @see TokenService
 */
@Route(value = "register", layout = MainLayout.class)
@RolesAllowed("elicit_user")
public class RegisterView extends HorizontalLayout implements HasDynamicTitle, BeforeEnterObserver {

    /** Injected service for handling user session and authentication. */
    @Inject
    UiSessionLogin uiSessionLogin;

    /** Injected service for generating and managing survey tokens. */
    @Inject
    TokenService tokenService;

    /** The current authenticated user. */
    private User user;
    
    /** The subject entity being registered or updated. */
    private Subject subject = new Subject();
    
    /** Data binder for form validation and data binding. */
    private Binder<Subject> binder;
    
    /** Button for saving new subjects. */
    private Button saveButton;
    
    /** Button for updating existing subjects. */
    private Button updateButton;
    
    /** Left column layout containing the registration form. */
    private VerticalLayout leftLayout = new VerticalLayout();
    
    /** Right column layout containing CSV upload and documentation. */
    private VerticalLayout rightLayout = new VerticalLayout();

    /**
     * Initializes the registration view components and layout after dependency injection.
     * 
     * <p>This method sets up the complete user interface including:</p>
     * 
     * <h4>Form Configuration:</h4>
     * <ul>
     *   <li>Responsive single-column form layout</li>
     *   <li>Department selection (auto-populated if user has single department)</li>
     *   <li>Personal information fields (name, date of birth, contact info)</li>
     *   <li>External ID field for integration purposes</li>
     * </ul>
     * 
     * <h4>Data Binding and Validation:</h4>
     * <ul>
     *   <li>Department selection with required validation</li>
     *   <li>Name fields with required validation</li>
     *   <li>Email validation with proper format checking</li>
     *   <li>Phone number format validation (###-###-####)</li>
     *   <li>Date of birth validation (must be in past)</li>
     *   <li>External ID handling with null conversion</li>
     * </ul>
     * 
     * <h4>CSV Upload Integration:</h4>
     * <ul>
     *   <li>File type restriction (.csv only)</li>
     *   <li>File size limitation (5MB maximum)</li>
     *   <li>Error handling with detailed dialog messages</li>
     *   <li>Success notifications with import counts</li>
     * </ul>
     * 
     * <h4>Documentation and Help:</h4>
     * <ul>
     *   <li>CSV file structure guidelines</li>
     *   <li>REST API documentation and examples</li>
     *   <li>Collapsible sections for clean interface</li>
     * </ul>
     * 
     * <p>The layout uses a 50/50 split between form and documentation areas,
     * optimizing space for both data entry and user guidance.</p>
     */
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

    /**
     * Handles navigation events and determines the view mode based on URL parameters.
     * 
     * <p>This method processes the "token" query parameter to determine whether the view
     * should operate in registration mode (new subject) or update mode (existing subject):</p>
     * 
     * <h4>Token Parameter Processing:</h4>
     * <ul>
     *   <li><strong>Token Present:</strong> Attempts to find and load the subject associated with the token</li>
     *   <li><strong>Subject Found:</strong> Switches to update mode, populates form, shows Update button</li>
     *   <li><strong>Subject Not Found:</strong> Shows error notification, remains in registration mode</li>
     *   <li><strong>No Token:</strong> Operates in registration mode with Save button visible</li>
     * </ul>
     * 
     * <h4>UI State Management:</h4>
     * <ul>
     *   <li><strong>Registration Mode:</strong> Save button visible, Update button hidden</li>
     *   <li><strong>Update Mode:</strong> Update button visible, Save button hidden</li>
     * </ul>
     * 
     * <p>The method ensures proper form population and button visibility based on the
     * operational mode, providing a seamless experience for both new registrations
     * and subject updates.</p>
     * 
     * @param event the BeforeEnterEvent containing navigation information and query parameters
     * @see BeforeEnterObserver#beforeEnter(BeforeEnterEvent)
     */
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

    /**
     * Saves a new subject to the database with complete workflow processing.
     * 
     * <p>This method performs a comprehensive save operation that includes:</p>
     * 
     * <ol>
     *   <li><strong>Form Validation:</strong> Validates all form fields using the data binder</li>
     *   <li><strong>Token Generation:</strong> Creates a unique survey respondent token</li>
     *   <li><strong>Data Population:</strong> Sets respondent and survey ID from token service</li>
     *   <li><strong>Database Persistence:</strong> Saves the subject with immediate flush for constraint checking</li>
     *   <li><strong>Message Creation:</strong> Generates communication messages for the new subject</li>
     *   <li><strong>Form Reset:</strong> Clears the form for next entry</li>
     * </ol>
     * 
     * <h4>Error Handling:</h4>
     * <ul>
     *   <li><strong>Validation Errors:</strong> Shows field-specific error messages</li>
     *   <li><strong>Duplicate External ID:</strong> Handles constraint violations with specific messaging</li>
     *   <li><strong>Token Generation Errors:</strong> Manages token service failures</li>
     *   <li><strong>Database Errors:</strong> Catches and reports persistence exceptions</li>
     * </ul>
     * 
     * <p>The method uses immediate flush to detect constraint violations early,
     * allowing for specific error handling for duplicate external IDs within departments.</p>
     * 
     * @param binder the data binder containing form validation and data mapping
     * @throws ValidationException if form validation fails
     * @throws TokenGenerationError if token creation fails
     * @throws jakarta.persistence.PersistenceException if database constraints are violated
     */
    @Transactional
    public void saveSubject(Binder<Subject> binder) {
        try {
            binder.writeBean(subject);
            Respondent respondent = tokenService.getToken(1);
            subject.setRespondent(respondent);
            subject.setSurveyId(respondent.survey.id);
            // Optionally, flush to force exception now:
            subject.persistAndFlush();
            ArrayList<Message> messages = Message.createMessagesForSubject(subject);
            for(Message message : messages) {
                message.persistAndFlush();
            }
            Notification.show("Subject saved", 3000, Notification.Position.MIDDLE);
            subject = new Subject();
            binder.readBean(subject); // reset form
        } catch (ValidationException e) {
            Notification.show("Please fix validation errors", 3000, Notification.Position.MIDDLE);
        } catch (TokenGenerationError e) {
            Notification.show("Error generating new token. Please try again", 3000, Notification.Position.MIDDLE);
        }
    }

    /**
     * Updates an existing subject in the database.
     * 
     * <p>This method performs an update operation for existing subjects:</p>
     * 
     * <ol>
     *   <li><strong>Form Validation:</strong> Validates all form fields using the data binder</li>
     *   <li><strong>Data Binding:</strong> Writes form data to the subject entity</li>
     *   <li><strong>Database Merge:</strong> Merges changes with the existing database record</li>
     *   <li><strong>Immediate Flush:</strong> Ensures changes are persisted immediately</li>
     *   <li><strong>Navigation:</strong> Returns to the search view after successful update</li>
     * </ol>
     * 
     * <h4>Error Handling:</h4>
     * <ul>
     *   <li><strong>Validation Errors:</strong> Shows field-specific error messages without navigation</li>
     *   <li><strong>Database Errors:</strong> Catches and reports persistence exceptions</li>
     * </ul>
     * 
     * <p>Unlike the save operation, updates don't require token generation or message creation
     * since these are only needed for new subjects entering the system.</p>
     * 
     * @param binder the data binder containing form validation and data mapping
     * @throws ValidationException if form validation fails
     */
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

    /**
     * Creates and configures the department selection combo box.
     * 
     * <p>This method builds a department selector that is filtered based on the
     * current user's department permissions. Only departments that the user
     * has access to are available for selection.</p>
     * 
     * <p>The combo box is configured with:</p>
     * <ul>
     *   <li>Department names as display labels</li>
     *   <li>Filtered list based on user permissions</li>
     *   <li>Proper binding to the subject's department ID</li>
     * </ul>
     * 
     * @return a configured ComboBox for department selection
     */
    private ComboBox<Department> getDepartmentComboBox() {
        ComboBox<Department> departmentComboBox = new ComboBox<>("Deparments");
        departmentComboBox.setItems(user.getDepartments());
        departmentComboBox.setItemLabelGenerator(Department::getName);
        return departmentComboBox;
    }

    /**
     * Creates the instructional content for the registration methods.
     * 
     * <p>This method builds a content area that explains the different ways
     * users can register subjects:</p>
     * <ul>
     *   <li>Individual registration using the form</li>
     *   <li>REST API integration for programmatic access</li>
     *   <li>CSV bulk upload for batch processing</li>
     * </ul>
     * 
     * <p>The content includes a collapsible CSV structure guide that provides
     * detailed information about file format requirements and examples.</p>
     * 
     * @return a Div containing instructional content and CSV structure guide
     */
    private Div getRestfulInstructionsDiv() {
        Div div = new Div();

        // Create CSV Structure Accordion
        Details csvDetails = new Details("CSV File Structure", createCsvStructureContent());
        csvDetails.setOpened(false); // Collapsed by default

        div.add(
            new Paragraph("You can register subjects individually using the form, a rest API, or upload a CSV file with multiple subjects."),
            csvDetails
        );

        return div;
    }

    /**
     * Creates detailed CSV file structure documentation.
     * 
     * <p>This method generates comprehensive documentation for CSV file uploads including:</p>
     * 
     * <h4>File Format Specifications:</h4>
     * <ul>
     *   <li>Required column order and names</li>
     *   <li>Comment line handling (lines starting with '#')</li>
     *   <li>Data type requirements for each field</li>
     *   <li>Required vs. optional field designations</li>
     * </ul>
     * 
     * <h4>Field Validation Rules:</h4>
     * <ul>
     *   <li>Department ID must be valid for the user</li>
     *   <li>Email must be in proper format</li>
     *   <li>Phone numbers must follow ###-###-#### pattern</li>
     *   <li>Dates support multiple formats (yyyy-MM-dd, MM/dd/yyyy)</li>
     * </ul>
     * 
     * <h4>Example Data:</h4>
     * <p>Includes practical examples showing proper CSV formatting with various
     * data scenarios including optional fields and different date formats.</p>
     * 
     * @return a Div containing comprehensive CSV structure documentation
     */
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
    
    /**
     * Creates comprehensive REST API documentation for programmatic subject registration.
     * 
     * <p>This method generates detailed API documentation including:</p>
     * 
     * <h4>Endpoint Information:</h4>
     * <ul>
     *   <li>Full endpoint URL and HTTP method</li>
     *   <li>Authentication requirements and role permissions</li>
     *   <li>Required content type headers</li>
     * </ul>
     * 
     * <h4>Request Structure:</h4>
     * <ul>
     *   <li>Complete JSON request body example</li>
     *   <li>Field descriptions and requirements</li>
     *   <li>Data type and format specifications</li>
     * </ul>
     * 
     * <h4>Response Format:</h4>
     * <ul>
     *   <li>Successful response structure with generated IDs</li>
     *   <li>Error handling and response codes</li>
     *   <li>Token generation information</li>
     * </ul>
     * 
     * <p>The documentation provides developers with everything needed to integrate
     * subject registration into external systems and applications.</p>
     * 
     * @return a Div containing complete REST API documentation
     */
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
    
    /**
     * Provides the dynamic page title for the browser tab and navigation.
     * 
     * @return the page title string
     * @see HasDynamicTitle#getPageTitle()
     */
    @Override
    public String getPageTitle() {
        return "Elicit Register";
    }
}