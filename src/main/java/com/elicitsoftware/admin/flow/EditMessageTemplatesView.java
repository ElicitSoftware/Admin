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
import com.elicitsoftware.model.MessageTemplate;
import com.elicitsoftware.model.MessageType;
import com.elicitsoftware.model.User;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.binder.ValidationException;
import com.vaadin.flow.data.validator.StringLengthValidator;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.Route;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/**
 * A Vaadin Flow view for editing and creating message templates.
 * This view provides a comprehensive interface for managing message templates
 * with real-time preview functionality.
 *
 * <p>The view features a two-column layout:</p>
 * <ul>
 *   <li>Left column: Form with fields for subject, body, MIME type, and department</li>
 *   <li>Right column: Live preview of the message content</li>
 * </ul>
 *
 * <p>The view supports both HTML and plain text MIME types with appropriate
 * preview rendering. Form validation ensures data integrity before saving.</p>
 *
 * <p>Route patterns:</p>
 * <ul>
 *   <li>/edit-message-template - Create a new message template</li>
 *   <li>/edit-message-template/123 - Edit message template with ID 123</li>
 * </ul>
 *
 * @author Elicit Software
 * @version 1.0
 * @since 1.0
 */
@Route(value = "edit-message-template/:id?", layout = MainLayout.class)
@RolesAllowed("elicit_admin")
public class EditMessageTemplatesView extends VerticalLayout implements BeforeEnterObserver {

    /** Injected service for handling user session and login information. */
    @Inject
    UiSessionLogin uiSessionLogin;

    /** The current logged-in user. */
    private User user;

    /** The message template being edited or created. */
    private MessageTemplate template;

    /** Text field for the message subject. */
    private final TextField subjectField = new TextField("Subject");

    /** Text area for the message body content. */
    private final TextArea messageField = new TextArea("Body");

    /** Combo box for selecting MIME type (HTML or plain text). */
    private final ComboBox<String> mimeTypeField = new ComboBox<>("MIME Type");

    /** Combo box for selecting the department. */
    private final ComboBox<Department> departmentField = new ComboBox<>("Department");

    /** Button for saving new message templates. */
    private final Button saveBtn = new Button("Save");

    /** Button for updating existing message templates. */
    private final Button updateBtn = new Button("Update");

    /** Container div for displaying the message preview. */
    private Div content = new Div();

    /** Data binder for form validation and data binding. */
    private final Binder<MessageTemplate> binder = new Binder<>(MessageTemplate.class);

    /**
     * Initializes the view components and layout after dependency injection.
     *
     * <p>This method sets up:</p>
     * <ul>
     *   <li>Form fields with validation rules</li>
     *   <li>Two-column layout with form and preview</li>
     *   <li>Data binding between form fields and the MessageTemplate entity</li>
     *   <li>Real-time preview updates as the user types</li>
     *   <li>Button click handlers for save and update operations</li>
     * </ul>
     *
     * <p>The preview column automatically updates when the message content
     * or MIME type changes, providing immediate visual feedback.</p>
     */
    @PostConstruct
    public void init() {
        user = uiSessionLogin.getUser();

        // Set options for MIME type
        mimeTypeField.setItems("text/html","text/plain");
        mimeTypeField.setAllowCustomValue(false);

        // Set options for Department
        departmentField.setItems(user.getDepartments());

        departmentField.setItemLabelGenerator(Department::getName);
        departmentField.setRequiredIndicatorVisible(true);
        departmentField.setAllowCustomValue(false);

        // Set the main layout to horizontal, with two columns: form and preview
        HorizontalLayout mainLayout = new HorizontalLayout();
        mainLayout.setWidthFull();

        // Form column (50%)
        FormLayout form = new FormLayout();
        form.add(mimeTypeField, departmentField, subjectField, messageField, saveBtn, updateBtn);
        form.setWidthFull();
        form.getStyle().set("flex", "1 1 50%");
        form.setResponsiveSteps(
                new FormLayout.ResponsiveStep("0", 1) // 1 column at all widths
        );

        // Preview column (50%)
        VerticalLayout preview = new VerticalLayout();
        preview.setWidthFull();
        preview.getStyle().set("flex", "1 1 50%");
        H3 title = new H3("Message Preview");
        content = new Div();
        content.setWidth("100%");
        content.setHeight("100%");
        // Update content
        updatePreview(messageField.getValue());

        preview.add(title, content);

        // Set value change mode and debounce for messageField
        messageField.setValueChangeMode(com.vaadin.flow.data.value.ValueChangeMode.TIMEOUT);
        messageField.setValueChangeTimeout(300);

        // Update content when messageField changes (debounced)
        messageField.addValueChangeListener(event -> updatePreview(event.getValue()));

        // Add change listener to mimeTypeField and update preview when it changes
        mimeTypeField.addValueChangeListener(event -> updatePreview(messageField.getValue()));

        // Add both columns to the main layout
        mainLayout.add(form, preview);
        mainLayout.setFlexGrow(1, form, preview);

        add(mainLayout);

        // --- Binder and Validation ---
        binder.forField(subjectField)
                .asRequired("Subject is required")
                .withValidator(new StringLengthValidator(
                        "Subject must be 3-100 characters", 1, 255))
                .bind("subject");

        binder.forField(messageField)
                .asRequired("Body is required")
                .withValidator(new StringLengthValidator(
                        "Body must be at least 5 characters", 1, 6000))
                .bind("message");


        binder.forField(mimeTypeField)
                .asRequired("MIME Type is required")
                .bind("mimeType");

        binder.forField(departmentField)
                .asRequired("Department is required")
                .bind("department");

        // Enable/disable buttons based on validation
        binder.addStatusChangeListener(event -> {
            saveBtn.setEnabled(binder.isValid());
            updateBtn.setEnabled(binder.isValid());
        });

        saveBtn.addClickListener(e -> saveTemplate());
        updateBtn.addClickListener(e -> updateTemplate());

        // Hide both buttons initially
        saveBtn.setVisible(false);
        updateBtn.setVisible(false);
    }

    /**
     * Called before the user enters this view to handle route parameters.
     *
     * <p>This method determines whether the view is in create or edit mode
     * based on the presence and value of the ID parameter:</p>
     * <ul>
     *   <li>If ID is null or "0": Create mode - initializes a new MessageTemplate</li>
     *   <li>If ID is a valid number: Edit mode - loads the existing template</li>
     * </ul>
     *
     * <p>In edit mode, the form fields are populated with the existing template
     * data and the Update button is shown. In create mode, default values are
     * set and the Save button is shown.</p>
     *
     * @param event the BeforeEnterEvent containing navigation information and route parameters
     * @see BeforeEnterObserver#beforeEnter(BeforeEnterEvent)
     */
    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        String idStr = event.getRouteParameters().get("id").orElse(null);
        if (idStr != null && !idStr.equals("0")) {
            // Load existing template
            template = MessageTemplate.findById(Long.parseLong(idStr));
            if (template != null) {
                subjectField.setValue(template.subject != null ? template.subject : "");
                messageField.setValue(template.message != null ? template.message : "");
                mimeTypeField.setValue(template.mimeType != null ? template.mimeType : "text/html");
                departmentField.setValue(template.department);
                saveBtn.setVisible(false);
                updateBtn.setVisible(true);
            }
        } else {
            template = new MessageTemplate();
            //Default to email. Later we may add more types like Reminder email
            template.messageType = MessageType.findById(1);
            mimeTypeField.setValue("text/html");
            saveBtn.setVisible(true);
            updateBtn.setVisible(false);
        }
    }

    /**
     * Updates the message preview content based on the provided body text and current MIME type.
     *
     * <p>The preview rendering depends on the selected MIME type:</p>
     * <ul>
     *   <li>text/plain: Displays content as plain text</li>
     *   <li>text/html: Renders content as HTML</li>
     * </ul>
     *
     * <p>This method is called automatically when the message body or MIME type
     * fields change, providing real-time feedback to the user.</p>
     *
     * @param body the message body content to preview
     */
    private void updatePreview(String body) {
        if (mimeTypeField.getValue() != null && mimeTypeField.getValue().equals("text/plain")) {
            content.getElement().setProperty("innerHTML", "");
            content.getElement().setText(body);
        } else {
            content.getElement().setText("");
            content.getElement().setProperty("innerHTML", body);
        }
    }

    /**
     * Saves a new message template to the database.
     *
     * <p>This method performs the following operations:</p>
     * <ol>
     *   <li>Validates all form fields using the data binder</li>
     *   <li>Writes the form data to the MessageTemplate entity</li>
     *   <li>Persists the entity to the database</li>
     *   <li>Flushes the changes to ensure immediate persistence</li>
     *   <li>Shows a success notification</li>
     *   <li>Navigates back to the message templates list view</li>
     * </ol>
     *
     * <p>If validation fails, an error notification is displayed and the
     * save operation is aborted.</p>
     */
    @Transactional
    public void saveTemplate() {
        try {
            binder.writeBean(template);
            template.persistAndFlush();
            Notification.show("Message Template saved", 1000, Notification.Position.MIDDLE);
            getUI().ifPresent(ui ->
                    ui.navigate("/message-templates")
            );
        } catch (ValidationException e) {
            Notification.show("Please fix validation errors", 2000, Notification.Position.MIDDLE);
        }
    }

    /**
     * Updates an existing message template in the database.
     *
     * <p>This method performs validation and persistence operations
     * for template modifications, ensuring data integrity and
     * providing user feedback on the operation status.</p>
     */
    @Transactional
    public void updateTemplate() {
        try {
            binder.writeBean(template);
            MessageTemplate.getEntityManager().merge(template);
            MessageTemplate.getEntityManager().flush();
            Notification.show("Message Template updated", 1000, Notification.Position.MIDDLE);
            getUI().ifPresent(ui ->
                    ui.navigate("/message-templates")
            );
        } catch (ValidationException e) {
            Notification.show("Please fix validation errors", 2000, Notification.Position.MIDDLE);
        }
    }

    /**
     * Default constructor for EditMessageTemplatesView.
     *
     * <p>Creates a new EditMessageTemplatesView instance and initializes the Vaadin UI
     * components for message template editing. This constructor sets up the form layout,
     * data binding, validation, and event handlers needed for template management.</p>
     *
     * <p><strong>Initialization Process:</strong></p>
     * <ol>
     *   <li>Calls parent VerticalLayout constructor</li>
     *   <li>Initializes form components (text fields, text areas, buttons)</li>
     *   <li>Sets up data binder for MessageTemplate entity</li>
     *   <li>Configures validation rules and error handling</li>
     *   <li>Establishes event listeners for save/cancel actions</li>
     *   <li>Applies styling and layout configuration</li>
     * </ol>
     *
     * <p><strong>UI Components Initialized:</strong></p>
     * <ul>
     *   <li><strong>Form Fields:</strong> Template name, subject, body content</li>
     *   <li><strong>Action Buttons:</strong> Save, cancel, and navigation controls</li>
     *   <li><strong>Validation:</strong> Real-time field validation and error display</li>
     *   <li><strong>Layout:</strong> Responsive form layout with proper spacing</li>
     * </ul>
     *
     * <p><strong>Framework Integration:</strong></p>
     * <ul>
     *   <li><strong>Vaadin:</strong> Integrated with Vaadin component lifecycle</li>
     *   <li><strong>CDI:</strong> Prepared for dependency injection of services</li>
     *   <li><strong>JPA:</strong> Ready for MessageTemplate entity operations</li>
     *   <li><strong>Routing:</strong> Configured for Vaadin navigation</li>
     * </ul>
     *
     * <p><strong>Usage:</strong></p>
     * <p>This constructor is automatically called by Vaadin when navigating to
     * the message template editing route. Manual instantiation is not required.</p>
     */
    public EditMessageTemplatesView() {
        super();
        // Additional initialization will be performed by init methods
    }
}
