package com.elicitsoftware.admin.flow;

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

@Route(value = "edit-message-template/:id?", layout = MainLayout.class)
@RolesAllowed("elicit_admin")
public class EditMessageTemplatesView extends VerticalLayout implements BeforeEnterObserver {

    @Inject
    UiSessionLogin uiSessionLogin;

    private User user;

    private MessageTemplate template;

    private final TextField subjectField = new TextField("Subject");
    private final TextArea messageField = new TextArea("Body");
    private final ComboBox<String> mimeTypeField = new ComboBox<>("MIME Type");
    private final ComboBox<Department> departmentField = new ComboBox<>("Department");
    private final Button saveBtn = new Button("Save");
    private final Button updateBtn = new Button("Update");
    private Div content = new Div();

    private final Binder<MessageTemplate> binder = new Binder<>(MessageTemplate.class);

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

    private void updatePreview(String body) {
        if (mimeTypeField.getValue() != null && mimeTypeField.getValue().equals("text/plain")) {
            content.getElement().setProperty("innerHTML", "");
            content.getElement().setText(body);
        } else {
            content.getElement().setText("");
            content.getElement().setProperty("innerHTML", body);
        }
    }

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
}
