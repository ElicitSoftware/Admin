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
import com.elicitsoftware.test.PostgresTestResource;
import com.vaadin.browserless.quarkus.QuarkusBrowserlessTest;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.grid.Grid;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Browserless UI test for {@link MessageTemplatesView}.
 *
 * <p>Traceability: UC-007 (Manage Message Templates). The view has no injected dependencies, so
 * it is constructed directly with {@code new} and attached to the test {@link UI}. As with
 * {@link DepartmentsView}, a view attached this way has no initialized {@code Router}, so the
 * navigation calls are exercised for their own execution, not for an observable outcome (see
 * {@code UsersViewTest}).</p>
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class MessageTemplatesViewTest extends QuarkusBrowserlessTest {

    @SuppressWarnings("unchecked")
    private Grid<MessageTemplate> grid(MessageTemplatesView view) {
        return find(Grid.class, view).single();
    }

    /** UC-007: the template grid renders with its department/subject/MIME-type columns and data. */
    @Test
    @TestTransaction
    void gridRendersWithColumnsAndData() {
        persistTemplate();

        MessageTemplatesView view = new MessageTemplatesView();
        UI.getCurrent().add(view);

        // Edit + ID + Department + Subject + MIME Type = 5 columns.
        assertTrue(grid(view).getColumns().size() >= 5);
        assertTrue(test(grid(view)).size() >= 1, "the persisted template should appear");
    }

    /**
     * MessageType id=1 is seeded dev data (V0.0.3__POPULATE_DEV_DATA.sql); no department or
     * template is (UC-028 C-016), so each test creates its own department and one template
     * inside its test transaction.
     */
    private static void persistTemplate() {
        Department department = new Department();
        department.name = "UC-007 Dept";
        department.code = "UC007";
        department.defaultMessageId = "1";
        department.fromEmail = "uc007@example.org";
        department.persist();

        MessageTemplate template = new MessageTemplate();
        template.department = department;
        template.messageType = MessageType.findById(1L);
        template.subject = "UC-007 Subject";
        template.message = "UC-007 body";
        template.mimeType = "text/plain";
        template.persist();
    }

    /** UC-007: clicking a row's edit icon triggers navigation to that template's edit route. */
    @Test
    @TestTransaction
    void editButtonTriggersNavigation() {
        persistTemplate();
        MessageTemplatesView view = new MessageTemplatesView();
        UI.getCurrent().add(view);

        Component editCell = test(grid(view)).getCellComponent(0, 0);
        assertDoesNotThrow(() -> ((Button) editCell).click());
    }

    /** UC-007: clicking "New Message Template" triggers navigation to the create-mode edit route. */
    @Test
    void newTemplateButtonTriggersNavigation() {
        MessageTemplatesView view = new MessageTemplatesView();
        UI.getCurrent().add(view);

        Button newTemplateBtn = find(Button.class, view).all().stream()
                .filter(b -> view.getTranslation("messageTemplatesView.newTemplate").equals(b.getText()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No New Message Template button"));

        assertDoesNotThrow(newTemplateBtn::click);
    }
}
