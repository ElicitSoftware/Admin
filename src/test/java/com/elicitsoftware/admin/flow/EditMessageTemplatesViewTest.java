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
import com.elicitsoftware.test.PostgresTestResource;
import com.vaadin.browserless.quarkus.QuarkusBrowserlessTest;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.Location;
import com.vaadin.flow.router.NavigationTrigger;
import com.vaadin.flow.router.RouteParameters;
import com.vaadin.flow.server.VaadinSession;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.enterprise.inject.spi.CDI;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Browserless UI test for {@link EditMessageTemplatesView}.
 *
 * <p>Traceability: UC-007 (Manage Message Templates). The view injects {@code UiSessionLogin}
 * and reads the authenticated user's departments in its {@code @PostConstruct}, so the test
 * seeds a transient {@link User} (with one department) into the Vaadin session the same way
 * {@code SearchViewTest} does, then obtains the view through CDI. Route navigation is
 * unavailable/a no-op when a view is attached directly rather than reached by router navigation
 * (see {@code UsersViewTest}), so {@code beforeEnter} is driven directly with a constructed
 * {@code BeforeEnterEvent} carrying the {@code id} route parameter (see
 * {@code EditUserViewRoleAssignmentTest}).</p>
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
@TestSecurity(user = "template.tester", roles = {"elicit_admin"})
class EditMessageTemplatesViewTest extends QuarkusBrowserlessTest {

    private Department department;
    private EditMessageTemplatesView view;

    private void setUpWithDepartment() {
        // No department is seeded (UC-028 C-016); every test here runs in a test transaction,
        // so the one created here is rolled back with everything else.
        department = new Department();
        department.name = "UC-007 Edit Dept";
        department.code = "UC007E";
        department.defaultMessageId = "1";
        department.fromEmail = "uc007e@example.org";
        department.persist();

        // UiSessionLogin's own @PostConstruct (triggered by its first method call anywhere in
        // this test run) overwrites the "user" session attribute with its own DB lookup. Force
        // that one-time initialization now, before seeding our own value, so the view's later
        // uiSessionLogin.getUser() call (which only re-reads the live session attribute, not
        // re-running init()) sees what we set rather than being clobbered by it.
        CDI.current().select(UiSessionLogin.class).get().getUser();

        User user = new User();
        user.setId(1);
        user.setUsername("template.tester");
        user.setActive(true);
        Set<Department> departments = new HashSet<>();
        departments.add(department);
        user.setDepartments(departments);
        VaadinSession.getCurrent().setAttribute("user", user);

        view = CDI.current().select(EditMessageTemplatesView.class).get();
        UI.getCurrent().add(view);
    }

    private void enterMode(String idParam) {
        UI ui = UI.getCurrent();
        BeforeEnterEvent event = new BeforeEnterEvent(ui.getInternals().getRouter(),
                NavigationTrigger.PROGRAMMATIC, new Location(""), EditMessageTemplatesView.class,
                new RouteParameters("id", idParam), ui, Collections.emptyList());
        view.beforeEnter(event);
    }

    private TextField subjectField() {
        return find(TextField.class, view).single();
    }

    private TextArea bodyField() {
        return find(TextArea.class, view).single();
    }

    @SuppressWarnings("unchecked")
    private ComboBox<String> mimeTypeField() {
        return (ComboBox<String>) find(ComboBox.class, view).all().stream()
                .filter(box -> UI.getCurrent().getTranslation("editMessageTemplatesView.mimeType").equals(box.getLabel()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No MIME Type ComboBox"));
    }

    @SuppressWarnings("unchecked")
    private ComboBox<Department> departmentField() {
        return (ComboBox<Department>) find(ComboBox.class, view).all().stream()
                .filter(box -> UI.getCurrent().getTranslation("editMessageTemplatesView.department").equals(box.getLabel()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No Department ComboBox"));
    }

    /**
     * {@code find()} only returns currently-visible components, matching real-browser query
     * semantics; {@code saveBtn}/{@code updateBtn} toggle visibility rather than existing/not
     * existing, so a hidden button is simply not findable by label.
     */
    private Button buttonLabeled(String textKey) {
        String text = UI.getCurrent().getTranslation(textKey);
        return find(Button.class, view).all().stream()
                .filter(b -> text.equals(b.getText()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No visible button labelled '" + text + "'"));
    }

    private Div previewContent() {
        return find(Div.class, view).all().stream()
                .filter(d -> "100%".equals(d.getWidth()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No preview content Div"));
    }

    /** UC-007: create mode (id "0") shows Save (and only Save), and defaults MIME type to HTML. */
    @Test
    @TestTransaction
    void createModeShowsSaveAndDefaultsMimeType() {
        setUpWithDepartment();
        enterMode("0");

        assertTrue(buttonLabeled("common.save").isVisible());
        assertEquals(1, find(Button.class, view).all().size(), "Update must be hidden in create mode");
        assertEquals("text/html", mimeTypeField().getValue());
    }

    /** UC-007: edit mode loads the existing template's fields and shows Update instead of Save. */
    @Test
    @TestTransaction
    void editModePopulatesFieldsFromExistingTemplate() {
        setUpWithDepartment();
        MessageTemplate template = new MessageTemplate();
        template.department = department;
        template.messageType = MessageType.findById(1L);
        template.subject = "Existing Subject";
        template.message = "Existing body";
        template.mimeType = "text/plain";
        template.persist();

        enterMode(String.valueOf(template.id));

        assertEquals("Existing Subject", subjectField().getValue());
        assertEquals("Existing body", bodyField().getValue());
        assertEquals("text/plain", mimeTypeField().getValue());
        assertTrue(buttonLabeled("editMessageTemplatesView.btnUpdate").isVisible());
        assertEquals(1, find(Button.class, view).all().size(), "Save must be hidden in edit mode");
    }

    /** UC-007: the preview renders plain text as text, not interpreted markup. */
    @Test
    @TestTransaction
    void plainTextMimeTypeRendersBodyAsText() {
        setUpWithDepartment();
        enterMode("0");

        mimeTypeField().setValue("text/plain");
        bodyField().setValue("<b>hello</b>");

        assertEquals("<b>hello</b>", previewContent().getElement().getText());
        assertEquals("", previewContent().getElement().getProperty("innerHTML"));
    }

    /** UC-007: the preview renders HTML mime-type content via innerHTML, not as literal text. */
    @Test
    @TestTransaction
    void htmlMimeTypeRendersBodyAsMarkup() {
        setUpWithDepartment();
        enterMode("0");

        mimeTypeField().setValue("text/html");
        bodyField().setValue("<b>hello</b>");

        assertEquals("<b>hello</b>", previewContent().getElement().getProperty("innerHTML"));
    }

    /** UC-007: Save is disabled while the subject is blank, and enables once the form is valid. */
    @Test
    @TestTransaction
    void saveButtonTracksFormValidity() {
        setUpWithDepartment();
        enterMode("0");

        departmentField().setValue(department);
        subjectField().setValue("");
        assertFalse(buttonLabeled("common.save").isEnabled());

        subjectField().setValue("A Subject");
        bodyField().setValue("A body");
        assertTrue(buttonLabeled("common.save").isEnabled());
    }

    /** UC-007: saving a new template in create mode persists it and returns to the list route. */
    @Test
    @TestTransaction
    void savingCreateModePersistsNewTemplate() {
        setUpWithDepartment();
        enterMode("0");

        departmentField().setValue(department);
        subjectField().setValue("New UC-007 Subject");
        bodyField().setValue("New UC-007 body");

        buttonLabeled("common.save").click();

        MessageTemplate saved = MessageTemplate.find("subject", "New UC-007 Subject").firstResult();
        assertTrue(saved != null && "New UC-007 body".equals(saved.message));
    }

    /** UC-007: updating an existing template in edit mode persists the change. */
    @Test
    @TestTransaction
    void updatingEditModePersistsChange() {
        setUpWithDepartment();
        MessageTemplate template = new MessageTemplate();
        template.department = department;
        template.messageType = MessageType.findById(1L);
        template.subject = "Before Update";
        template.message = "Before body";
        template.mimeType = "text/html";
        template.persist();

        enterMode(String.valueOf(template.id));
        subjectField().setValue("After Update");

        buttonLabeled("editMessageTemplatesView.btnUpdate").click();

        MessageTemplate updated = MessageTemplate.findById(template.id);
        assertEquals("After Update", updated.subject);
    }
}
