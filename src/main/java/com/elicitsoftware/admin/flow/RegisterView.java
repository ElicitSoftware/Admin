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

import com.elicitsoftware.exception.AccessCodeGenerationError;
import com.elicitsoftware.model.*;
import io.quarkus.panache.common.Sort;
import com.elicitsoftware.response.AddResponse;
import com.elicitsoftware.service.CsvImportService;
import com.elicitsoftware.rest.AccessCodeService;
import com.elicitsoftware.service.SurveyDefinitionPresenceCheck;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.combobox.ComboBoxVariant;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.datepicker.DatePickerVariant;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.ModalityMode;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.textfield.TextFieldVariant;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.component.upload.UploadI18N;
import com.vaadin.flow.server.streams.UploadHandler;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.binder.ValidationException;
import com.vaadin.flow.data.validator.EmailValidator;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.theme.lumo.LumoUtility;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.persistence.PersistenceException;
import jakarta.transaction.Transactional;
import java.io.InputStream;
import java.text.DateFormatSymbols;
import java.util.Arrays;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
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
 *   <li><strong>Subject Updates:</strong> Edit existing subjects via the {@code accessCode} URL parameter</li>
 *   <li><strong>Department Integration:</strong> Automatic department filtering based on user permissions</li>
 *   <li><strong>Access Code Generation:</strong> Automatic creation of unique access codes for survey access</li>
 *   <li><strong>Message Creation:</strong> Automatic generation of communication messages for new subjects</li>
 * </ul>
 *
 * <p>The view supports two operational modes:</p>
 * <ul>
 *   <li><strong>Registration Mode:</strong> Default mode for creating new subjects</li>
 *   <li><strong>Update Mode:</strong> Activated when accessing with a valid subject access code parameter</li>
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
 * @see Subject
 * @see CsvImportService
 * @see AccessCodeService
 * @since 1.0
 */
@Route(value = "register", layout = MainLayout.class)
@RolesAllowed({"elicit_admin", "elicit_user"})
public class RegisterView extends HorizontalLayout implements HasDynamicTitle, BeforeEnterObserver {

    /**
     * Injected service for handling user session and authentication.
     */
    @Inject
    UiSessionLogin uiSessionLogin;

    /** Reports whether this deployment has a survey installed (UC-019). */
    @Inject
    SurveyDefinitionPresenceCheck surveyPresence;

    /** The missing-survey explanation currently shown, if any (UC-019). */
    private Component missingSurveyNotice;

    /**
     * Injected service for generating and managing survey access codes.
     */
    @Inject
    AccessCodeService accessCodeService;

    /**
     * Security identity for user authentication and role checking.
     */
    @Inject
    SecurityIdentity identity;

    /**
     * The current authenticated user.
     */
    private User user;

    /**
     * The subject entity being registered or updated.
     */
    private Subject subject = new Subject();

    /**
     * Data binder for form validation and data binding.
     */
    private Binder<Subject> binder;

    /**
     * Survey selector: the survey a new subject's access code is generated for. Read-only when
     * editing an existing subject, whose respondent already belongs to a survey.
     */
    private ComboBox<Survey> surveyComboBox;

    /**
     * Button for saving new subjects.
     */
    private Button saveButton;

    /**
     * Button for updating existing subjects.
     */
    private Button updateButton;

    /**
     * Left column layout containing the registration form.
     */
    private VerticalLayout leftLayout = new VerticalLayout();

    /**
     * Right column layout containing CSV upload and documentation.
     */
    private VerticalLayout rightLayout = new VerticalLayout();

    /**
     * Default constructor for Vaadin UI component instantiation.
     * <p>
     * Creates a new RegisterView instance for the Vaadin framework.
     * This constructor is called by Vaadin during route navigation
     * and component initialization.
     */
    public RegisterView() {
        // Default constructor for Vaadin
    }

    /**
     * Initializes the registration view components and layout after dependency injection.
     *
     * <p>This method sets up the complete user interface including:</p>
     *
     * <h4>Form Configuration:</h4>
     * <ul>
     *   <li>Responsive single-column form layout</li>
     *   <li>Survey selection (auto-populated if exactly one survey is installed)</li>
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
        if (user == null) {
            // UC-001 A1: no console record for this principal. SearchView explains the same
            // condition; without this guard the view fails on the first user access below.
            add(noUserNotice());
            return;
        }

        FormLayout formLayout = new FormLayout();
        formLayout.setResponsiveSteps(
                new FormLayout.ResponsiveStep("0", 1)
        );
        formLayout.setWidth("100%");
        ComboBox<Department> departmentComboBox = getDepartmentComboBox();
        departmentComboBox.setId("register-department");
        if (user.getDepartments().size() == 1) {
            departmentComboBox.setValue(user.getDepartments().iterator().next());
            subject.setDepartmentId(user.getDepartments().iterator().next().id);
        }

        List<Survey> surveys = Survey.findAll(Sort.by("displayOrder")).list();
        surveyComboBox = getSurveyComboBox(surveys);
        surveyComboBox.setId("register-survey");
        if (surveys.size() == 1) {
            surveyComboBox.setValue(surveys.get(0));
            subject.setSurveyId(surveys.get(0).id);
        }

        TextField firstName = new TextField(getTranslation("registerView.firstName"));
        firstName.setId("register-first-name");
        firstName.addThemeVariants(TextFieldVariant.LUMO_SMALL);
        TextField lastName = new TextField(getTranslation("registerView.lastName"));
        lastName.setId("register-last-name");
        lastName.addThemeVariants(TextFieldVariant.LUMO_SMALL);
        TextField middleName = new TextField(getTranslation("registerView.middleName"));
        middleName.setId("register-middle-name");
        middleName.addThemeVariants(TextFieldVariant.LUMO_SMALL);
        DatePicker dob = new DatePicker(getTranslation("registerView.dob"));
        dob.setId("register-dob");
        dob.setLocale(getLocale());
        dob.setI18n(datePickerI18n());
        dob.addThemeVariants(DatePickerVariant.LUMO_SMALL);
        EmailField email = new EmailField(getTranslation("registerView.email"));
        email.setId("register-email");
        TextField phone = new TextField(getTranslation("registerView.phone"));
        phone.setId("register-phone");
        phone.setPlaceholder("123-456-7890");
        phone.addThemeVariants(TextFieldVariant.LUMO_SMALL);
        TextField xid = new TextField(getTranslation("registerView.xid"));
        xid.setId("register-xid");
        xid.addThemeVariants(TextFieldVariant.LUMO_SMALL);

        formLayout.add(surveyComboBox, departmentComboBox, firstName, lastName, middleName, dob, email, phone, xid);

        binder = new Binder<>(Subject.class);

        // Add this binder for departmentComboBox
        binder.forField(departmentComboBox)
                .asRequired(getTranslation("registerView.error.departmentRequired"))
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

        binder.forField(surveyComboBox)
                .asRequired(getTranslation("registerView.error.surveyRequired"))
                .bind(
                        s -> {
                            if (s.getSurveyId() == 0) return null;
                            return surveys.stream()
                                    .filter(survey -> survey.id == s.getSurveyId())
                                    .findFirst()
                                    .orElse(null);
                        },
                        (s, survey) -> s.setSurveyId(survey == null ? 0 : survey.id)
                );

        binder.forField(xid)
                .withConverter(
                        str -> str == null || str.trim().isEmpty() ? null : str,
                        obj -> obj == null ? "" : obj
                )
                .bind("xid");

        binder.forField(firstName)
                .asRequired(getTranslation("registerView.error.firstNameRequired"))
                .bind("firstName");

        binder.forField(lastName)
                .asRequired(getTranslation("registerView.error.lastNameRequired"))
                .bind("lastName");

        binder.forField(middleName)
                .bind("middleName");

        binder.forField(dob)
                .withValidator(date -> date == null || date.isBefore(LocalDate.now()), getTranslation("registerView.error.dobPast"))
                .bind(
                        s -> s.getDob() == null ? null : s.getDob(),
                        (s, value) -> s.setDob(value == null ? null : LocalDate.from(value))
                );

        binder.forField(email)
                .withValidator(new EmailValidator(getTranslation("registerView.error.emailInvalid")))
                .bind("email");

        binder.forField(phone)
                .withConverter(
                        str -> (str == null || str.trim().isEmpty()) ? null : str,
                        obj -> obj == null ? "" : obj
                )
                .withValidator(
                        phoneVal -> phoneVal == null || phoneVal.matches("^\\d{3}-\\d{3}-\\d{4}$"),
                        getTranslation("registerView.error.phoneFormat")
                )
                .bind("phone");

        saveButton = new Button(getTranslation("common.save"), event -> {
            try {
                saveSubject(binder);
            } catch (PersistenceException e) {
                Notification.show(getTranslation("registerView.error.duplicateXid", subject.getXid()), 5000, Notification.Position.MIDDLE);
                subject = new Subject();
            } catch (Exception e) {
                showErrorDialog(getTranslation("registerView.error.databaseTitle"), getTranslation("registerView.error.database", e.getMessage()));
                subject = new Subject();
            }
        });
        saveButton.setId("register-save-button");
        saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        updateButton = new Button(getTranslation("registerView.btnUpdate"), event -> {
            try {
                updateSubject(binder);
                // Navigate back to the search view after update
                getUI().ifPresent(ui -> ui.navigate(""));
            } catch (Exception e) {
                showErrorDialog(getTranslation("registerView.error.databaseTitle"), getTranslation("registerView.error.database", e.getMessage()));
            }
        });
        updateButton.setId("register-update-button");
        updateButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        // By default, show only saveButton
        saveButton.setVisible(true);
        updateButton.setVisible(false);

        leftLayout.add(formLayout, saveButton, updateButton);

        // Set the layouts to take 50% width
        leftLayout.setWidth("50%");
        rightLayout.setWidth("50%");

        // Create CSV upload button using modern UploadHandler API
        Upload csvUpload = new Upload();
        csvUpload.setId("register-csv-upload");
        csvUpload.setAcceptedFileTypes(".csv");
        csvUpload.setMaxFiles(1);
        csvUpload.setMaxFileSize(5 * 1024 * 1024); // 5MB limit
        csvUpload.setI18n(uploadI18n());

        Button uploadButton = new Button(getTranslation("registerView.btnUploadCsv"));
        uploadButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        csvUpload.setUploadButton(uploadButton);

        // Use modern InMemoryUploadHandler instead of deprecated MemoryBuffer
        csvUpload.setUploadHandler(UploadHandler.inMemory((metadata, data) -> {
            try {
                CsvImportService importService = new CsvImportService(accessCodeService);
                InputStream inputStream = new java.io.ByteArrayInputStream(data);
                AddResponse response = importService.importSubjects(inputStream);
                showSuccessDialog(getTranslation("registerView.csvImport.successTitle"),
                        getTranslation("registerView.csvImport.success", response.toString()));
            } catch (Exception e) {
                showErrorDialog(getTranslation("registerView.csvImport.errorTitle"), e.getMessage());
            }
        }));

        // Create REST API instructions accordion
        Details restApiDetails = new Details(getTranslation("registerView.apiDoc.title"), createRestApiContent());
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
     * <p>This method processes the "accessCode" query parameter to determine whether the view
     * should operate in registration mode (new subject) or update mode (existing subject):</p>
     *
     * <h4>Access Code Parameter Processing:</h4>
     * <ul>
     *   <li><strong>Access Code Present:</strong> Attempts to find and load the subject associated with the access code</li>
     *   <li><strong>Subject Found:</strong> Switches to update mode, populates form, shows Update button</li>
     *   <li><strong>Subject Not Found:</strong> Shows error notification, remains in registration mode</li>
     *   <li><strong>No Access Code:</strong> Operates in registration mode with Save button visible</li>
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
        if (user == null) {
            return; // no console record: init() already showed the explanation (UC-001 A1)
        }
        // Authorization is now handled by @RolesAllowed annotation
        refreshMissingSurveyNotice();

        Optional<String> accessCodeOpt = event.getLocation().getQueryParameters().getParameters().getOrDefault("accessCode", List.of()).stream().findFirst();
        if (accessCodeOpt.isPresent()) {
            String accessCode = accessCodeOpt.get();
            // Fetch the subject by access code (implement this in your StatusDataSource or Subject repository)
            Subject found = Subject.findSubjectByAccessCode(accessCode);
            if (found != null) {
                this.subject = found;
                if (binder != null) {
                    binder.readBean(subject);
                }
                // Editing: show update, hide save; the survey is fixed by the existing respondent
                if (updateButton != null && saveButton != null) {
                    updateButton.setVisible(true);
                    saveButton.setVisible(false);
                }
                if (surveyComboBox != null) {
                    surveyComboBox.setReadOnly(true);
                }
            } else {
                Notification.show(getTranslation("registerView.error.subjectNotFound", accessCode), 3000, Notification.Position.MIDDLE);
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
     *   <li><strong>Access Code Generation:</strong> Creates a unique survey respondent access code</li>
     *   <li><strong>Data Population:</strong> Sets respondent and survey ID from access code service</li>
     *   <li><strong>Database Persistence:</strong> Saves the subject with immediate flush for constraint checking</li>
     *   <li><strong>Message Creation:</strong> Generates communication messages for the new subject</li>
     *   <li><strong>Form Reset:</strong> Clears the form for next entry</li>
     * </ol>
     *
     * <h4>Error Handling:</h4>
     * <ul>
     *   <li><strong>Validation Errors:</strong> Shows field-specific error messages</li>
     *   <li><strong>Duplicate External ID:</strong> Handles constraint violations with specific messaging</li>
     *   <li><strong>Access Code Generation Errors:</strong> Manages access code service failures</li>
     *   <li><strong>Database Errors:</strong> Catches and reports persistence exceptions</li>
     * </ul>
     *
     * <p>The method uses immediate flush to detect constraint violations early,
     * allowing for specific error handling for duplicate external IDs within departments.</p>
     *
     * @param binder the data binder containing form validation and data mapping
     * @throws ValidationException  if form validation fails
     * @throws AccessCodeGenerationError if access code creation fails
     * @throws PersistenceException if database constraints are violated
     */
    @Transactional
    public void saveSubject(Binder<Subject> binder) throws ValidationException, AccessCodeGenerationError, PersistenceException {
        try {
            // Write form values to subject first to get current values
            binder.writeBean(subject);

            boolean isExluded = ExcludedXid.isExcluded(this.subject.getXid(), (int) this.subject.getDepartmentId());
            if (isExluded) {
                // Find the department name from the user's departments by ID
                String departmentName = user.getDepartments().stream()
                    .filter(dept -> dept.id == this.subject.getDepartmentId())
                    .map(Department::getName)
                    .findFirst()
                    .orElse(getTranslation("registerView.unknownDepartment"));

                Notification.show(getTranslation("registerView.error.excludedXid", this.subject.getXid(), departmentName), 3000, Notification.Position.MIDDLE);
                return; // Exit early if excluded
            }

            Respondent respondent = accessCodeService.generateAccessCode((int) subject.getSurveyId());
            subject.setRespondent(respondent);
            subject.setSurveyId(respondent.survey.id);
            // Optionally, flush to force exception now:
            subject.persistAndFlush();
            ArrayList<Message> messages = Message.createMessagesForSubject(subject);
            for (Message message : messages) {
                message.persistAndFlush();
            }
            Notification.show(getTranslation("registerView.subjectSaved"), 3000, Notification.Position.MIDDLE);
            subject = new Subject();
            binder.readBean(subject); // reset form
        } catch (ValidationException e) {
            Notification.show(getTranslation("registerView.error.fixValidation"), 3000, Notification.Position.MIDDLE);
        } catch (AccessCodeGenerationError e) {
            Notification.show(getTranslation("registerView.error.accessCode"), 3000, Notification.Position.MIDDLE);
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
     * <p>Unlike the save operation, updates don't require access code generation or message creation
     * since these are only needed for new subjects entering the system.</p>
     *
     * @param binder the data binder containing form validation and data mapping
     * @throws ValidationException if form validation fails
     */
    @Transactional
    public void updateSubject(Binder<Subject> binder) throws ValidationException {
        try {
            binder.writeBean(subject);
            subject = Subject.getEntityManager().merge(subject);
            Subject.getEntityManager().flush();
            Notification.show(getTranslation("registerView.subjectUpdated"), 3000, Notification.Position.MIDDLE);
        } catch (ValidationException e) {
            Notification.show(getTranslation("registerView.error.fixValidation"), 3000, Notification.Position.MIDDLE);
        }
    }

    /**
     * Registers a new user account in the system.
     *
     * <p>This method performs comprehensive user registration including:</p>
     * <ol>
     *   <li>Form validation using the configured binder</li>
     *   <li>Password encryption using BCrypt</li>
     *   <li>User entity creation and persistence</li>
     *   <li>Success notification display</li>
     *   <li>Navigation to login page</li>
     * </ol>
     *
     * <p>If validation fails, appropriate error messages are displayed
     * and the registration process is halted.</p>
     */
    @Transactional
    public void register() {
        // ...existing code...
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
    /**
     * Creates the survey selection combo box (UC-003): the survey the new subject's access code
     * is generated for. Lists every installed survey in display order; when only one survey is
     * installed the caller pre-selects it so the form behaves as it did before this selector
     * existed.
     *
     * @param surveys the installed surveys, in display order
     * @return a configured ComboBox for survey selection
     */
    private ComboBox<Survey> getSurveyComboBox(List<Survey> surveys) {
        ComboBox<Survey> surveyComboBox = new ComboBox<>(getTranslation("registerView.survey"));
        surveyComboBox.setItems(surveys);
        surveyComboBox.setItemLabelGenerator(survey -> survey.name);
        surveyComboBox.addThemeVariants(ComboBoxVariant.LUMO_SMALL);
        return surveyComboBox;
    }

    private ComboBox<Department> getDepartmentComboBox() {
        ComboBox<Department> departmentComboBox = new ComboBox<>(getTranslation("registerView.department"));
        departmentComboBox.setItems(user.getDepartments());
        departmentComboBox.setItemLabelGenerator(Department::getName);
        departmentComboBox.addThemeVariants(ComboBoxVariant.LUMO_SMALL);
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
        Details csvDetails = new Details(getTranslation("registerView.csvDoc.title"), createCsvStructureContent());
        csvDetails.setOpened(false); // Open by default so users can see the format

        div.add(
                new Paragraph(getTranslation("registerView.intro.methods")),
                new Paragraph(getTranslation("registerView.intro.clickBelow")),
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
                new Paragraph(getTranslation("registerView.csvDoc.columnsIntro")),
                new Paragraph("#departmentId, firstName, lastName, middleName, dob, email, phone, xid"), // i18n:ignore CSV header line
                new Paragraph(getTranslation("registerView.csvDoc.comments")),

                new H4(getTranslation("registerView.csvDoc.columnDescriptions")),
                columnDescriptionsList(),

                new H4(getTranslation("registerView.csvDoc.example")),
                // i18n:ignore-start (sample CSV data)
                new Pre("#departmentId,firstName,lastName,middleName,dob,email,phone,xid\n" +
                        "1,John,Doe,Michael,1990-01-15,john.doe@email.com,123-456-7890,EXT001\n" +
                        "2,Jane,Smith,,1985-03-22,jane.smith@email.com,555-123-4567,EXT002\n" +
                        "1,Bob,Johnson,Robert,12/10/1992,bob.johnson@email.com,999-888-7777,EXT003")
                // i18n:ignore-end
        );

        return content;
    }

    /**
     * Builds the CSV column-description list using Vaadin HTML components.
     *
     * <p>This replaces a raw {@code innerHTML} assignment: each item is composed from an
     * {@link Anchor}-free {@link ListItem} containing a bold {@link Span} label and a plain
     * text description, so no markup string is injected into the DOM.</p>
     *
     * @return an {@link UnorderedList} describing each CSV column
     */
    private UnorderedList columnDescriptionsList() {
        UnorderedList list = new UnorderedList();
        list.add(columnDescription("departmentId", getTranslation("registerView.csvDoc.col.departmentId")));
        list.add(columnDescription("firstName", getTranslation("registerView.csvDoc.col.firstName")));
        list.add(columnDescription("lastName", getTranslation("registerView.csvDoc.col.lastName")));
        list.add(columnDescription("middleName", getTranslation("registerView.csvDoc.col.middleName")));
        list.add(columnDescription("dob", getTranslation("registerView.csvDoc.col.dob")));
        list.add(columnDescription("email", getTranslation("registerView.csvDoc.col.email")));
        list.add(columnDescription("phone", getTranslation("registerView.csvDoc.col.phone")));
        list.add(columnDescription("xid", getTranslation("registerView.csvDoc.col.xid")));
        return list;
    }

    /**
     * Creates a single CSV column-description list item with a bold field name.
     *
     * @param column      the technical CSV column name, rendered in bold (never translated)
     * @param description the translated, human-readable description text
     * @return a {@link ListItem} for the column
     */
    private ListItem columnDescription(String column, String description) {
        Span name = new Span(column + ":");
        name.addClassName(LumoUtility.FontWeight.BOLD);
        return new ListItem(name, new Span(" " + description));
    }

    /**
     * Month and weekday names for the date picker in the current locale (UC-026), with the
     * button labels from the translation bundle.
     */
    private DatePicker.DatePickerI18n datePickerI18n() {
        DateFormatSymbols symbols = DateFormatSymbols.getInstance(getLocale());
        DatePicker.DatePickerI18n i18n = new DatePicker.DatePickerI18n();
        i18n.setMonthNames(Arrays.asList(symbols.getMonths()).subList(0, 12));
        i18n.setWeekdays(Arrays.asList(symbols.getWeekdays()).subList(1, 8));
        i18n.setWeekdaysShort(Arrays.asList(symbols.getShortWeekdays()).subList(1, 8));
        i18n.setToday(getTranslation("registerView.datePicker.today"));
        i18n.setCancel(getTranslation("common.cancel"));
        return i18n;
    }

    /** Upload component texts in the current locale (UC-026). */
    private UploadI18N uploadI18n() {
        UploadI18N i18n = new UploadI18N();
        i18n.setAddFiles(new UploadI18N.AddFiles()
                .setOne(getTranslation("registerView.btnUploadCsv"))
                .setMany(getTranslation("registerView.btnUploadCsv")));
        i18n.setDropFiles(new UploadI18N.DropFiles()
                .setOne(getTranslation("registerView.upload.dropFile"))
                .setMany(getTranslation("registerView.upload.dropFile")));
        i18n.setError(new UploadI18N.Error()
                .setFileIsTooBig(getTranslation("registerView.upload.error.tooBig"))
                .setIncorrectFileType(getTranslation("registerView.upload.error.wrongType"))
                .setTooManyFiles(getTranslation("registerView.upload.error.tooMany")));
        return i18n;
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
     *   <li>Access code generation information</li>
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
                new Paragraph(getTranslation("registerView.apiDoc.intro")),

                new H3(getTranslation("registerView.apiDoc.single.h")),
                new H4(getTranslation("registerView.apiDoc.endpoint")),
                new Pre("POST /api/secured/add/subject"), // i18n:ignore

                new H4(getTranslation("registerView.apiDoc.authentication")),
                new Paragraph(getTranslation("registerView.apiDoc.auth.anyRole")),

                new H4(getTranslation("registerView.apiDoc.contentType")),
                new Pre("application/json"),

                new H4(getTranslation("registerView.apiDoc.requestBodyExample")),
                // i18n:ignore-start (sample request body)
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
                // i18n:ignore-end

                new H3(getTranslation("registerView.apiDoc.bulk.h")),
                new H4(getTranslation("registerView.apiDoc.endpoint")),
                new Pre("POST /api/secured/add/subjects"), // i18n:ignore

                new H4(getTranslation("registerView.apiDoc.authentication")),
                new Paragraph(getTranslation("registerView.apiDoc.auth.anyRole")),

                new H4(getTranslation("registerView.apiDoc.contentType")),
                new Pre("application/json"),

                new H4(getTranslation("registerView.apiDoc.requestBodyArray")),
                // i18n:ignore-start (sample request body)
                new Pre("[\n" +
                        "  {\n" +
                        "    \"surveyId\": 1,\n" +
                        "    \"departmentId\": 1,\n" +
                        "    \"firstName\": \"John\",\n" +
                        "    \"lastName\": \"Doe\",\n" +
                        "    \"middleName\": \"Michael\",\n" +
                        "    \"dob\": \"1990-01-15\",\n" +
                        "    \"email\": \"john.doe@email.com\",\n" +
                        "    \"phone\": \"123-456-7890\",\n" +
                        "    \"xid\": \"EXT001\"\n" +
                        "  },\n" +
                        "  {\n" +
                        "    \"surveyId\": 1,\n" +
                        "    \"departmentId\": 1,\n" +
                        "    \"firstName\": \"Jane\",\n" +
                        "    \"lastName\": \"Smith\",\n" +
                        "    \"email\": \"jane.smith@email.com\",\n" +
                        "    \"xid\": \"EXT002\"\n" +
                        "  }\n" +
                        "]"),
                // i18n:ignore-end

                new H3(getTranslation("registerView.apiDoc.csvUpload.h")),
                new H4(getTranslation("registerView.apiDoc.endpoint")),
                new Pre("POST /api/secured/add/csv"), // i18n:ignore

                new H4(getTranslation("registerView.apiDoc.authentication")),
                new Paragraph(getTranslation("registerView.apiDoc.auth.importerRole")),

                new H4(getTranslation("registerView.apiDoc.contentType")),
                new Pre("multipart/form-data"),

                new H4(getTranslation("registerView.apiDoc.requestBody")),
                new Paragraph(getTranslation("registerView.apiDoc.requestBodyFile")),

                new H4(getTranslation("registerView.apiDoc.csvFormat")),
                new Paragraph(getTranslation("registerView.csvDoc.columnsIntro")),
                new Pre("departmentId,firstName,lastName,middleName,dob,email,phone,xid"),

                new Paragraph(getTranslation("registerView.apiDoc.columnRequirements")),
                new Pre(getTranslation("registerView.apiDoc.columnRequirementsList")),

                new H4(getTranslation("registerView.apiDoc.csvExample")),
                // i18n:ignore-start (sample CSV data and API responses)
                new Pre("departmentId,firstName,lastName,middleName,dob,email,phone,xid\n" +
                        "1,John,Doe,Michael,1990-01-15,john.doe@email.com,123-456-7890,EXT001\n" +
                        "2,Jane,Smith,,1985-03-22,jane.smith@email.com,555-123-4567,EXT002"),
                // i18n:ignore-end

                new H3(getTranslation("registerView.apiDoc.webUpload.h")),
                new Paragraph(getTranslation("registerView.apiDoc.webUpload.p")),

                new H3(getTranslation("registerView.apiDoc.response.h")),

                new H4(getTranslation("registerView.apiDoc.response.single")),
                // i18n:ignore-start (sample API response, message values are literal API output)
                new Pre("""
                        {
                            "statuses": [
                                {
                                    "status": {
                                        "id": 123,
                                        "xid": "EXT001",
                                        "departmentId": 1,
                                        "surveyId": 1,
                                        "firstName": "John",
                                        "lastName": "Doe",
                                        "email": "john.doe@email.com",
                                        "accessCode": "ABC123DEF",
                                        "created": "2025-10-13"
                                    },
                                    "message": "New Subject"
                                }
                            ]
                        }
                        """),
                // i18n:ignore-end

                new H4(getTranslation("registerView.apiDoc.response.bulk")),
                // i18n:ignore-start (sample API response, message values are literal API output)
                new Pre("""
                        {
                            "statuses": [
                                {
                                    "status": {...},
                                    "message": "New Subject: EXT001"
                                },
                                {
                                    "status": {...},
                                    "message": "Existing Subject: EXT002"
                                },
                                {
                                    "status": {...},
                                    "message": "Excluded Subject: EXT003"
                                }
                            ]
                        }
                        """),
                // i18n:ignore-end

                new H4(getTranslation("registerView.apiDoc.notes.h")),
                new Paragraph(getTranslation("registerView.apiDoc.notes.exclusion")),
                new Paragraph(getTranslation("registerView.apiDoc.notes.duplicates")),
                new Paragraph(getTranslation("registerView.apiDoc.notes.individual")),
                new Paragraph(getTranslation("registerView.apiDoc.notes.auth"))
        );

        return content;
    }

    /**
     * Displays an error dialog with proper formatting for line breaks and detailed error messages.
     *
     * <p>This method creates a modal dialog that can properly display multi-line error messages,
     * including formatted toString() output from response objects. The dialog uses pre-wrap
     * white-space styling to preserve line breaks and formatting.</p>
     *
     * @param title   the title to display in the dialog header
     * @param message the error message to display, which may contain line breaks
     */
    private void showErrorDialog(String title, String message) {
        Dialog errorDialog = new Dialog();
        errorDialog.setHeaderTitle(title);

        Span errorMessage = new Span(message);
        errorMessage.addClassName(LumoUtility.Whitespace.PRE_WRAP);

        Button closeButton = new Button(getTranslation("common.close"), evt -> errorDialog.close());
        closeButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        closeButton.addClassName(LumoUtility.Margin.Top.MEDIUM);

        VerticalLayout dialogLayout = new VerticalLayout(errorMessage, closeButton);
        dialogLayout.setAlignItems(Alignment.CENTER);
        dialogLayout.setSpacing(true);

        errorDialog.add(dialogLayout);
        errorDialog.setModality(ModalityMode.STRICT);
        errorDialog.setDraggable(false);
        errorDialog.setResizable(true);
        errorDialog.setWidth("600px");
        errorDialog.setMaxWidth("90vw");

        errorDialog.open();
    }

    /**
     * Displays a success dialog with proper formatting for line breaks and detailed success messages.
     *
     * <p>This method creates a modal dialog that can properly display multi-line success messages,
     * including formatted toString() output from response objects. The dialog uses pre-wrap
     * white-space styling to preserve line breaks and formatting.</p>
     *
     * @param title   the title to display in the dialog header
     * @param message the success message to display, which may contain line breaks
     */
    private void showSuccessDialog(String title, String message) {
        Dialog successDialog = new Dialog();
        successDialog.setHeaderTitle(title);

        Span successMessage = new Span(message);
        successMessage.addClassNames(LumoUtility.Whitespace.PRE_WRAP, LumoUtility.TextColor.SUCCESS);

        Button closeButton = new Button(getTranslation("common.close"), evt -> successDialog.close());
        closeButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        closeButton.addClassName(LumoUtility.Margin.Top.MEDIUM);

        VerticalLayout dialogLayout = new VerticalLayout(successMessage, closeButton);
        dialogLayout.setAlignItems(Alignment.CENTER);
        dialogLayout.setSpacing(true);

        successDialog.add(dialogLayout);
        successDialog.setModality(ModalityMode.STRICT);
        successDialog.setDraggable(false);
        successDialog.setResizable(true);
        successDialog.setWidth("600px");
        successDialog.setMaxWidth("90vw");

        successDialog.open();
    }

    /**
     * Provides the dynamic page title for the browser tab and navigation.
     *
     * @return the page title string
     * @see HasDynamicTitle#getPageTitle()
     */
    @Override
    public String getPageTitle() {
        return getTranslation("registerView.pageTitle");
    }

    /**
     * Shows or clears this view's missing-survey explanation (UC-019).
     * <p>
     * Re-evaluated on every entry rather than once at construction, so the explanation
     * disappears as soon as a definition is applied (BR-076). The view stays usable
     * either way -- the explanation is added to it, not put in front of it (BR-077).
     * <p>
     * Package-private so same-package tests can drive it directly: under
     * {@code @QuarkusTest} there is no route navigation to deliver a real enter event.
     */
    void refreshMissingSurveyNotice() {
        if (missingSurveyNotice != null) {
            remove(missingSurveyNotice);
            missingSurveyNotice = null;
        }
        if (!surveyPresence.isSurveyInstalled()) {
            missingSurveyNotice = MissingSurveyNotice.emptyState(identity.hasRole("elicit_admin"));
            addComponentAsFirst(missingSurveyNotice);
        }
    }
    /**
     * The explanation shown instead of the form when the signed-in principal has no console
     * record (UC-001 A1), worded exactly as SearchView words it.
     */
    private Div noUserNotice() {
        Div errorDiv = new Div();
        errorDiv.add(new Paragraph(getTranslation("searchView.noUser.loggedIn")));
        Span principal = new Span(identity.getPrincipal().getName());
        principal.addClassName(LumoUtility.FontWeight.BOLD);
        errorDiv.add(new Paragraph(new Span(getTranslation("searchView.noUser.before")), principal,
                new Span(getTranslation("searchView.noUser.after"))));
        errorDiv.add(new Paragraph(getTranslation("searchView.noUser.help")));
        return errorDiv;
    }
}
