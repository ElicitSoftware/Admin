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
import com.elicitsoftware.response.AddResponseStatus;
import com.elicitsoftware.service.CsvImportException;
import com.elicitsoftware.service.CsvImportService;
import com.elicitsoftware.service.CsvSurveyMissingException;
import com.elicitsoftware.service.DepartmentService;
import com.elicitsoftware.rest.AccessCodeService;
import com.elicitsoftware.security.DepartmentScope;
import com.elicitsoftware.service.SurveyDefinitionPresenceCheck;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
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
import io.quarkus.logging.Log;
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
import java.util.Map;
import java.util.Set;
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
 *   <li><strong>Subject Updates:</strong> Edit existing subjects, addressed by record id ({@code register/<id>})</li>
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
 * @see Subject
 * @see CsvImportService
 * @see AccessCodeService
 */
@Route(value = "register/:id?", layout = MainLayout.class)
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

    /** Where the view reads a stored subject and writes an edited one. */
    SubjectStore subjects = new SubjectStore();

    /** The optional route parameter that names the subject to edit by its record id (UC-003 A5). */
    static final String ID_PARAMETER = "id";

    /** Decides whether a subject's department is one of the user's (UC-003 BR-138). */
    @Inject
    DepartmentScope departmentScope;

    /** The surveys each department registers onto (UC-006 BR-172, UC-003 BR-174). */
    @Inject
    DepartmentService departmentService;

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
        // UC-003 BR-174: the survey choice offers the chosen department's surveys only.
        departmentComboBox.addValueChangeListener(e -> offerSurveysOf(e.getValue(), surveys));
        offerSurveysOf(departmentComboBox.getValue(), surveys);

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
                new ResultDialog(getTranslation("registerView.error.databaseTitle"),
                        getTranslation("registerView.error.database", e.getMessage()), true).open();
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
                new ResultDialog(getTranslation("registerView.error.databaseTitle"),
                        getTranslation("registerView.error.database", e.getMessage()), true).open();
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
        csvUpload.setAcceptedFileExtensions(".csv");
        csvUpload.setMaxFiles(1);
        csvUpload.setMaxFileSize(5 * 1024 * 1024); // 5MB limit
        csvUpload.setI18n(uploadI18n());

        Button uploadButton = new Button(getTranslation("registerView.btnUploadCsv"));
        uploadButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        csvUpload.setUploadButton(uploadButton);

        // Use modern InMemoryUploadHandler instead of deprecated MemoryBuffer
        csvUpload.setUploadHandler(UploadHandler.inMemory((metadata, data) -> handleCsvUpload(data)));

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
     * Decides between registration mode and update mode from the route (UC-003 A5, A7).
     *
     * <p>{@code register} registers a new subject: Save is shown, Update hidden.
     * {@code register/<id>} edits the subject with that record id: the form is filled, Update is
     * shown and the survey is fixed. An id that is not a number, names no subject, or names a
     * subject in a department the user is not assigned to (BR-138) is answered alike, with the
     * not-found notice and the form left in registration mode. The address never carries an
     * access code (NFR-022).</p>
     *
     * @param event the BeforeEnterEvent carrying the route parameters
     * @see BeforeEnterObserver#beforeEnter(BeforeEnterEvent)
     */
    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        if (user == null) {
            return; // no console record: init() already showed the explanation (UC-001 A1)
        }
        refreshMissingSurveyNotice();

        Optional<String> idOpt = event.getRouteParameters().get(ID_PARAMETER);
        if (idOpt.isPresent()) {
            Subject found = editableSubject(idOpt.get());
            if (found != null) {
                this.subject = found;
                // Read-only before the bean is read: an existing subject's survey is shown as it
                // is, whatever the department lists now (UC-003 BR-174 governs new subjects).
                if (surveyComboBox != null) {
                    surveyComboBox.setReadOnly(true);
                }
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
                Notification.show(getTranslation("registerView.error.subjectNotFound"), 3000, Notification.Position.MIDDLE);
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
     * The subject a route id names, when the user may edit it (UC-003 A5, BR-138).
     *
     * @param idParameter the {@code id} route parameter as written in the address
     * @return the subject, or null when the id is not a number, names no subject, or names one
     *         in a department the user is not assigned to
     */
    private Subject editableSubject(String idParameter) {
        long id;
        try {
            id = Long.parseLong(idParameter);
        } catch (NumberFormatException e) {
            return null;
        }
        Subject found = findSubject(id);
        if (found != null && !departmentScope.permits(found.getDepartmentId())) {
            Log.infof("Subject %d is not in a department of %s; not opened for editing", id, identity.getPrincipal().getName());
            return null;
        }
        return found;
    }

    /** The subject the form currently registers or edits. */
    Subject currentSubject() {
        return subject;
    }

    /** The stored subject with this record id, or null. */
    private Subject findSubject(long id) {
        return subjects.find(id);
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

            // UC-003 BR-138: the department is checked here, not left to what the selector offers.
            if (!departmentScope.permits(subject.getDepartmentId())) {
                Notification.show(getTranslation("registerView.error.notYourDepartment"), 5000, Notification.Position.MIDDLE);
                return;
            }
            // UC-003 BR-174: and so is the department's survey.
            if (!departmentService.offersSurvey(subject.getDepartmentId(), subject.getSurveyId())) {
                Notification.show(getTranslation("registerView.error.surveyNotOffered"), 5000, Notification.Position.MIDDLE);
                return;
            }

            boolean isExcluded = ExcludedXid.isExcluded(this.subject.getXid(), (int) this.subject.getDepartmentId());
            if (isExcluded) {
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
            // UC-003 BR-138: the subject as it is stored, and the department now chosen for it,
            // must both be the user's; the stored row is re-read rather than trusted from the form.
            Subject stored = findSubject(subject.getId());
            if (stored == null || !departmentScope.permits(stored.getDepartmentId())) {
                Notification.show(getTranslation("registerView.error.notYourDepartment"), 5000, Notification.Position.MIDDLE);
                return;
            }
            binder.writeBean(subject);
            if (!departmentScope.permits(subject.getDepartmentId())) {
                Notification.show(getTranslation("registerView.error.notYourDepartment"), 5000, Notification.Position.MIDDLE);
                return;
            }
            mergeSubject();
            Notification.show(getTranslation("registerView.subjectUpdated"), 3000, Notification.Position.MIDDLE);
        } catch (ValidationException e) {
            Notification.show(getTranslation("registerView.error.fixValidation"), 3000, Notification.Position.MIDDLE);
        }
    }

    /** Writes the edited subject over its stored row. */
    private void mergeSubject() {
        subject = subjects.merge(subject);
    }

    /**
     * Reads a stored subject and writes an edited one back. Kept apart from the view so that the
     * view's handling of a subject can be tested without a database.
     */
    static class SubjectStore {

        /** The stored subject with this record id, or null. */
        Subject find(long id) {
            return Subject.findById(id);
        }

        /** Writes the edited subject over its stored row and returns the managed instance. */
        Subject merge(Subject edited) {
            Subject managed = Subject.getEntityManager().merge(edited);
            Subject.getEntityManager().flush();
            return managed;
        }
    }

    /**
     * Narrows the survey choice to the department's surveys (UC-003 BR-174), keeping the current
     * survey when it is still offered and choosing the only one when there is one. A read-only
     * choice, an existing subject's, offers every survey, so it shows the subject's as it is.
     *
     * @param department the department chosen in the form, or null
     * @param surveys    every installed survey, in display order
     */
    void offerSurveysOf(Department department, List<Survey> surveys) {
        if (surveyComboBox.isReadOnly()) {
            surveyComboBox.setItems(surveys);
            return;
        }
        Set<Integer> offered = department == null ? Set.of() : departmentService.surveyIdsOf(department.id);
        Survey current = surveyComboBox.getValue();
        List<Survey> items = surveys.stream()
                .filter(survey -> offered.contains(survey.id))
                .toList();
        surveyComboBox.setItems(items);
        Survey keep = current == null ? null
                : items.stream().filter(survey -> survey.id.equals(current.id)).findFirst().orElse(null);
        if (keep != null) {
            surveyComboBox.setValue(keep);
        } else if (items.size() == 1) {
            surveyComboBox.setValue(items.get(0));
        } else {
            surveyComboBox.clear();
        }
    }

    /**
     * Creates the survey selection combo box (UC-003): the survey the new subject's access code is
     * generated for, labelled by survey name.
     *
     * @param surveys the surveys to offer, in display order
     * @return a configured ComboBox for survey selection
     */
    private ComboBox<Survey> getSurveyComboBox(List<Survey> surveys) {
        ComboBox<Survey> surveyComboBox = new ComboBox<>(getTranslation("registerView.survey"));
        surveyComboBox.setItems(surveys);
        surveyComboBox.setItemLabelGenerator(survey -> survey.name);
        surveyComboBox.addThemeVariants(ComboBoxVariant.LUMO_SMALL);
        return surveyComboBox;
    }

    /**
     * Creates the department selection combo box, offering only the departments the signed-in user is
     * assigned to (NFR-021), labelled by department name.
     *
     * @return a configured ComboBox for department selection
     */
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
                new Paragraph("#surveyId, departmentId, xid, firstName, lastName, middleName, dob, email, phone"), // i18n:ignore CSV header line
                new Paragraph(getTranslation("registerView.csvDoc.comments")),

                new H4(getTranslation("registerView.csvDoc.columnDescriptions")),
                columnDescriptionsList(),

                new H4(getTranslation("registerView.csvDoc.example")),
                // i18n:ignore-start (sample CSV data)
                new Pre("#surveyId,departmentId,xid,firstName,lastName,middleName,dob,email,phone\n" +
                        "1,1,EXT001,John,Doe,Michael,1990-01-15,john.doe@email.com,123-456-7890\n" +
                        "1,2,,Jane,Smith,,1985-03-22,jane.smith@email.com\n" +
                        "2,1,EXT003,Bob,Johnson,Robert,12/10/1992,bob.johnson@email.com,999-888-7777")
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
        list.add(columnDescription("surveyId", getTranslation("registerView.csvDoc.col.surveyId")));
        list.add(columnDescription("departmentId", getTranslation("registerView.csvDoc.col.departmentId")));
        list.add(columnDescription("xid", getTranslation("registerView.csvDoc.col.xid")));
        list.add(columnDescription("firstName", getTranslation("registerView.csvDoc.col.firstName")));
        list.add(columnDescription("lastName", getTranslation("registerView.csvDoc.col.lastName")));
        list.add(columnDescription("middleName", getTranslation("registerView.csvDoc.col.middleName")));
        list.add(columnDescription("dob", getTranslation("registerView.csvDoc.col.dob")));
        list.add(columnDescription("email", getTranslation("registerView.csvDoc.col.email")));
        list.add(columnDescription("phone", getTranslation("registerView.csvDoc.col.phone")));
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
        name.addClassName("inline-name");
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
                new Paragraph(getTranslation("registerView.apiDoc.auth.importerRole")),

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
                new Paragraph(getTranslation("registerView.apiDoc.auth.importerRole")),

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
                new Pre("surveyId,departmentId,xid,firstName,lastName,middleName,dob,email,phone"),

                new Paragraph(getTranslation("registerView.apiDoc.columnRequirements")),
                new Pre(getTranslation("registerView.apiDoc.columnRequirementsList")),

                new H4(getTranslation("registerView.apiDoc.csvExample")),
                // i18n:ignore-start (sample CSV data and API responses)
                new Pre("surveyId,departmentId,xid,firstName,lastName,middleName,dob,email,phone\n" +
                        "1,1,EXT001,John,Doe,Michael,1990-01-15,john.doe@email.com,123-456-7890\n" +
                        "2,2,EXT002,Jane,Smith,,1985-03-22,jane.smith@email.com,555-123-4567"),
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
                new Paragraph(getTranslation("registerView.apiDoc.notes.auth")),
                new Paragraph(getTranslation("registerView.apiDoc.notes.departments"))
        );

        return content;
    }

    /**
     * Reads an uploaded CSV and asks the user to confirm what it will do before anyone is
     * registered (UC-003 A6, BR-176). A file with a row that names no survey is rejected with a
     * warning and nothing is imported (BR-175); the survey chosen in the form plays no part.
     * Package-private so tests can drive it directly without simulating a real file upload,
     * the same way {@code RespondentImportView.handleUpload} is.
     *
     * @param data the raw bytes of the uploaded file
     */
    void handleCsvUpload(byte[] data) {
        try {
            CsvImportService importService = new CsvImportService(accessCodeService);
            CsvImportService.CsvImportPlan plan = importService.plan(new java.io.ByteArrayInputStream(data));
            if (plan.rejected()) {
                new ResultDialog(getTranslation("registerView.csvImport.errorTitle"),
                        new CsvSurveyMissingException(plan.linesWithoutSurvey()).getMessage(), true).open();
                return;
            }
            if (plan.accepted().isEmpty()) {
                // Nothing to confirm: the file holds no rows, or every row would be refused.
                if (plan.refused().isEmpty()) {
                    new ResultDialog(getTranslation("registerView.csvImport.successTitle"),
                            importSummary(new AddResponse()), false).open();
                } else {
                    new ResultDialog(getTranslation("registerView.csvImport.errorTitle"),
                            rejectedLines(new CsvImportException(plan.refused())), true).open();
                }
                return;
            }
            confirmCsvImport(importService, plan);
        } catch (Exception e) {
            // The file could not be read: the cause goes to the log, the dialog says only that
            // it failed (UC-010 BR-139).
            Log.error("CSV import failed", e);
            new ResultDialog(getTranslation("registerView.csvImport.errorTitle"),
                    getTranslation("registerView.csvImport.failed"), true).open();
        }
    }

    /**
     * The confirmation of UC-003 A6 step 2: how many subjects the upload will register on each
     * survey and in each department, and each row it will refuse. Confirming imports the
     * accepted rows (step 4); canceling imports nothing (step 3).
     */
    private void confirmCsvImport(CsvImportService importService, CsvImportService.CsvImportPlan plan) {
        ConfirmDialog dialog = new ConfirmDialog();
        csvConfirmation = dialog;
        dialog.setId("register-csv-confirm");
        dialog.setHeader(getTranslation("registerView.csvConfirm.title"));
        dialog.setText(csvPlanSummary(plan));
        dialog.setCancelable(true);
        dialog.setCancelText(getTranslation("common.cancel"));
        dialog.setConfirmText(getTranslation("registerView.csvConfirm.import"));
        dialog.addConfirmListener(e -> runCsvImport(importService, plan));
        dialog.open();
    }

    /**
     * The last upload's confirmation. Package-private so tests can confirm or cancel it: an
     * opened ConfirmDialog joins the UI only when the response is written.
     */
    ConfirmDialog csvConfirmation;

    /** The confirmation's body: one line per survey and department, then the refused rows. */
    Div csvPlanSummary(CsvImportService.CsvImportPlan plan) {
        Map<Integer, Map<Long, Long>> counts = new java.util.TreeMap<>();
        for (CsvImportService.CsvRow row : plan.accepted()) {
            counts.computeIfAbsent(row.request().surveyId, k -> new java.util.TreeMap<>())
                    .merge((long) row.request().departmentId, 1L, Long::sum);
        }
        Div body = new Div();
        body.add(new Paragraph(getTranslation("registerView.csvConfirm.intro", plan.accepted().size())));
        // Accepted rows name installed surveys and the user's own departments only (BR-137, BR-172).
        Map<Integer, String> surveyNames = new java.util.HashMap<>();
        departmentService.installedSurveys().forEach(survey -> surveyNames.put(survey.id, survey.name));
        Map<Long, String> departmentNames = new java.util.HashMap<>();
        user.getDepartments().forEach(department -> departmentNames.put(department.id, department.getName()));
        UnorderedList groups = new UnorderedList();
        counts.forEach((surveyId, byDepartment) -> byDepartment.forEach((departmentId, count) ->
                groups.add(new ListItem(getTranslation("registerView.csvConfirm.group",
                        surveyNames.getOrDefault(surveyId, String.valueOf(surveyId)),
                        departmentNames.getOrDefault(departmentId, String.valueOf(departmentId)),
                        count)))));
        body.add(groups);
        if (!plan.refused().isEmpty()) {
            body.add(new Paragraph(getTranslation("registerView.csvConfirm.refused", plan.refused().size())));
            UnorderedList refused = new UnorderedList();
            plan.refused().forEach(line -> refused.add(new ListItem(line)));
            body.add(refused);
        }
        return body;
    }

    /** Imports the rows the user confirmed and shows the outcome (UC-003 A6 step 4). */
    private void runCsvImport(CsvImportService importService, CsvImportService.CsvImportPlan plan) {
        try {
            AddResponse response = importService.importAccepted(plan);
            new ResultDialog(getTranslation("registerView.csvImport.successTitle"),
                    importSummary(response), false).open();
        } catch (CsvImportException e) {
            new ResultDialog(getTranslation("registerView.csvImport.errorTitle"),
                    rejectedLines(e), true).open();
        } catch (Exception e) {
            Log.error("CSV import failed", e);
            new ResultDialog(getTranslation("registerView.csvImport.errorTitle"),
                    getTranslation("registerView.csvImport.failed"), true).open();
        }
    }

    /**
     * Lays a finished CSV import out as the {@link ResultDialog} sections an administrator reads
     * (UC-003 A6): a heading, then one line per row saying what became of it.
     *
     * <p>This used to hand {@code AddResponse.toString()} — a Java debug dump — to a single
     * {@code Span} that relied on {@code LumoUtility.Whitespace.PRE_WRAP} to break its lines.
     * Admin's Lumo utility classes are inert (issue #85), so the whole summary collapsed into one
     * run-on paragraph. Structure survives a missing stylesheet; a pre-formatted string does
     * not.</p>
     *
     * @param response the import's outcome
     * @return the summary, in the order it should read
     */
    private List<ResultDialog.Section> importSummary(AddResponse response) {
        List<AddResponseStatus> subjects = response.getSubjects() != null
                ? response.getSubjects()
                : List.of();

        List<ResultDialog.Line> lines = new ArrayList<>();
        lines.add(ResultDialog.Line.heading(subjects.size() == 1
                ? getTranslation("registerView.csvImport.summary.headingOne")
                : getTranslation("registerView.csvImport.summary.heading", subjects.size())));
        for (AddResponseStatus subject : subjects) {
            lines.add(ResultDialog.Line.detail(describe(subject)));
        }

        List<ResultDialog.Section> sections = new ArrayList<>();
        sections.add(new ResultDialog.Section(lines));

        List<String> errors = response.getErrors() != null ? response.getErrors() : List.of();
        if (!errors.isEmpty()) {
            List<ResultDialog.Line> errorLines = new ArrayList<>();
            errorLines.add(ResultDialog.Line.heading(getTranslation("registerView.csvImport.errorsHeading")));
            errors.forEach(error -> errorLines.add(ResultDialog.Line.error(error)));
            sections.add(new ResultDialog.Section(errorLines));
        }
        return sections;
    }

    /** The import service's outcome word in the administrator's language; an unknown one is shown as sent. */
    private String outcomeLabel(String importStatus) {
        if (importStatus == null) {
            return "";
        }
        return switch (importStatus) {
            case "New Subject" -> getTranslation("registerView.csvImport.outcome.new"); // i18n:ignore
            case "Existing Subject" -> getTranslation("registerView.csvImport.outcome.existing"); // i18n:ignore
            case "Excluded Subject" -> getTranslation("registerView.csvImport.outcome.excluded"); // i18n:ignore
            default -> importStatus;
        };
    }

    /**
     * Describes one imported row: what became of it, and enough of the subject to recognize.
     *
     * <p>The outcome itself ({@code New Subject}, {@code Existing Subject}, ...) is the import
     * service's own wording and travels in its REST payload, which stays as it is; it is put into
     * the administrator's language here, where it is shown. An excluded row carries an empty
     * {@code Status}, so every field is optional and the line falls back to the outcome on its own.</p>
     *
     * @param subject one row's outcome
     * @return a single line of text
     */
    private String describe(AddResponseStatus subject) {
        String outcome = outcomeLabel(subject.getImportStatus());
        Status status = subject.getStatus();
        if (status == null) {
            return outcome;
        }

        StringBuilder who = new StringBuilder();
        String name = ((status.getFirstName() != null ? status.getFirstName() : "") + " "
                + (status.getLastName() != null ? status.getLastName() : "")).trim();
        if (!name.isEmpty()) {
            who.append(name);
        }
        if (status.getXid() != null && !status.getXid().isBlank()) {
            who.append(who.isEmpty() ? "" : " ").append("(")
                    .append(getTranslation("registerView.csvImport.summary.xid", status.getXid())).append(")");
        }
        if (status.getAccessCode() != null && !status.getAccessCode().isBlank()) {
            who.append(who.isEmpty() ? "" : ", ")
                    .append(getTranslation("registerView.csvImport.summary.accessCode", status.getAccessCode()));
        }
        if (who.isEmpty()) {
            return outcome;
        }
        return getTranslation("registerView.csvImport.summary.subject", outcome, who.toString());
    }

    /**
     * Lays the rows a CSV import rejected out as one list item each (UC-003 A6).
     *
     * @param failure the import failure, which carries one message per rejected line
     * @return the summary, in the order it should read
     */
    private List<ResultDialog.Section> rejectedLines(CsvImportException failure) {
        List<ResultDialog.Line> lines = new ArrayList<>();
        lines.add(ResultDialog.Line.heading(getTranslation("registerView.csvImport.lineErrorsHeading")));
        failure.getLineErrors().forEach(error -> lines.add(ResultDialog.Line.error(error)));
        return List.of(new ResultDialog.Section(lines));
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
        principal.addClassName("inline-name");
        errorDiv.add(new Paragraph(new Span(getTranslation("searchView.noUser.before")), principal,
                new Span(getTranslation("searchView.noUser.after"))));
        errorDiv.add(new Paragraph(getTranslation("searchView.noUser.help")));
        return errorDiv;
    }
}
