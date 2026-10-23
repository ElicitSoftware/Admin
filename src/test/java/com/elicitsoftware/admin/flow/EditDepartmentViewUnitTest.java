package com.elicitsoftware.admin.flow;

/*-
 * ***LICENSE_START***
 * Elicit Survey
 * %%
 * Copyright (C) 2025 - 2026 The Regents of the University of Michigan - Rogel Cancer Center
 * %%
 * PolyForm Noncommercial License 1.0.0
 * <https://polyformproject.org/licenses/noncommercial/1.0.0>
 * ***LICENSE_END***
 */

import com.elicitsoftware.model.Department;
import com.elicitsoftware.service.DepartmentService;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.textfield.TextFieldVariant;
import com.vaadin.flow.router.BeforeEnterEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UC-006: Manage Departments — {@link EditDepartmentView} in a plain browserless UI with stand-ins
 * for the department service, the identity and the session login. Covers the form and its
 * validation (BR-022, BR-024, BR-025), the create path that assigns the new department to its
 * creator and refreshes the session copy (UC-028 BR-113), the update path, and the wording of
 * every save failure including the unique-name and unique-code violations (BR-022, BR-023).
 *
 * <p>Editing an existing department starts with {@code Department.findById}, a Panache static
 * call, so that branch of {@code beforeEnter} is not reached here.</p>
 */
class EditDepartmentViewUnitTest extends PlainSmallViewTestSupport {

    private final List<String> calls = new ArrayList<>();
    private RuntimeException saveFailure;
    private EditDepartmentView view;

    @BeforeEach
    void build() {
        view = new EditDepartmentView();
        view.departmentService = new DepartmentService() {
            @Override
            public Department save(Department department) {
                calls.add("save:" + department.name);
                if (saveFailure != null) {
                    throw saveFailure;
                }
                return department;
            }

            @Override
            public Department create(Department department, String principalName) {
                calls.add("create:" + department.name + " for " + principalName);
                if (saveFailure != null) {
                    throw saveFailure;
                }
                return department;
            }
        };
        view.identity = identity("ada");
        view.uiSessionLogin = new UiSessionLogin() {
            @Override
            public void refresh() {
                calls.add("refresh");
            }
        };
        UI.getCurrent().add(view);
    }

    private List<Component> parts() {
        return view.getChildren().toList();
    }

    private TextField name() {
        return (TextField) parts().get(0);
    }

    private TextField code() {
        return (TextField) parts().get(1);
    }

    private TextField defaultMessageId() {
        return (TextField) parts().get(2);
    }

    private EmailField fromEmail() {
        return (EmailField) parts().get(3);
    }

    private Button save() {
        return (Button) ((HorizontalLayout) parts().get(4)).getComponentAt(0);
    }

    private Button cancel() {
        return (Button) ((HorizontalLayout) parts().get(4)).getComponentAt(1);
    }

    private void enterNew() {
        view.beforeEnter(beforeEnter(EditDepartmentView.class, Map.of("id", "0")));
    }

    private void fillValid() {
        name().setValue("Cardiology");
        code().setValue("CARD");
        fromEmail().setValue("card@example.org");
    }

    /** The department the form edits; the view keeps it private and offers no accessor. */
    private Department department() throws ReflectiveOperationException {
        Field field = EditDepartmentView.class.getDeclaredField("department");
        field.setAccessible(true);
        return (Department) field.get(view);
    }

    private Notification onlyNotification() {
        return $(Notification.class).single();
    }

    @Test
    void given_newView_when_built_then_formFieldsAreLabelledAndConfigured() {
        assertTrue(view.isSpacing());
        assertTrue(view.isPadding());
        assertEquals(t("editDepartmentView.name"), name().getLabel());
        assertTrue(name().isRequired());
        assertTrue(name().isRequiredIndicatorVisible());
        assertEquals("300px", name().getWidth());
        assertTrue(name().getThemeNames().contains(TextFieldVariant.LUMO_SMALL.getVariantName()));
        assertEquals(t("editDepartmentView.code"), code().getLabel());
        assertEquals(t("editDepartmentView.code.helper"), code().getHelperText());
        assertEquals("300px", code().getWidth());
        assertTrue(code().getThemeNames().contains(TextFieldVariant.LUMO_SMALL.getVariantName()));
        assertEquals(t("editDepartmentView.defaultMessageId"), defaultMessageId().getLabel());
        assertEquals("1", defaultMessageId().getValue());
        assertTrue(defaultMessageId().isRequired());
        assertTrue(defaultMessageId().isRequiredIndicatorVisible());
        assertEquals(t("editDepartmentView.defaultMessageId.helper"), defaultMessageId().getHelperText());
        assertEquals("300px", defaultMessageId().getWidth());
        assertTrue(defaultMessageId().getThemeNames().contains(TextFieldVariant.LUMO_SMALL.getVariantName()));
        assertEquals(t("editDepartmentView.fromEmail"), fromEmail().getLabel());
        assertTrue(fromEmail().isRequired());
        assertTrue(fromEmail().isRequiredIndicatorVisible());
        assertEquals(t("editDepartmentView.fromEmail.helper"), fromEmail().getHelperText());
        assertEquals("300px", fromEmail().getWidth());
        assertEquals(t("common.save"), save().getText());
        assertTrue(save().getThemeNames().contains(ButtonVariant.LUMO_PRIMARY.getVariantName()));
        assertEquals(t("common.cancel"), cancel().getText());
        assertTrue(cancel().getThemeNames().contains(ButtonVariant.LUMO_TERTIARY.getVariantName()));
        assertTrue(((HorizontalLayout) parts().get(4)).isSpacing());
    }

    private static String pendingJs() {
        StringBuilder sb = new StringBuilder();
        UI.getCurrent().getInternals().dumpPendingJavaScriptInvocations().forEach(i ->
                sb.append(i.getInvocation().getExpression()).append(' ')
                        .append(i.getInvocation().getParameters()).append('\n'));
        return sb.toString();
    }

    @Test
    void given_idZero_when_entered_then_createModeWithDefaultMessageId() throws Exception {
        defaultMessageId().setValue("7");
        pendingJs();

        enterNew();

        assertTrue(pendingJs().contains(t("editDepartmentView.title.create")), "the page title names the action");

        assertEquals(t("editDepartmentView.btnCreate"), save().getText());
        assertEquals("1", department().defaultMessageId);
        assertEquals("1", defaultMessageId().getValue(), "the new bean is bound to the form");
        assertEquals(0, department().id);
    }

    @Test
    void given_noId_when_entered_then_createModeToo() throws Exception {
        view.beforeEnter(beforeEnter(EditDepartmentView.class, Map.of()));

        assertEquals(t("editDepartmentView.btnCreate"), save().getText());
        assertEquals(0, department().id);
    }

    @Test
    void given_nonNumericId_when_entered_then_invalidIdIsShownAndDepartmentsIsForwardedTo() {
        BeforeEnterEvent event = beforeEnter(EditDepartmentView.class, Map.of("id", "abc"));

        view.beforeEnter(event);

        assertEquals(DepartmentsView.class, event.getForwardTargetType());
        Notification n = onlyNotification();
        assertEquals(t("editDepartmentView.error.invalidId"), test(n).getText());
        assertEquals(3000, n.getDuration());
        assertEquals(Notification.Position.MIDDLE, n.getPosition());
    }

    @Test
    void given_newDepartment_when_editedValidAndInvalid_then_saveIsEnabledOnlyWhenValid() {
        enterNew();

        fillValid();
        assertTrue(save().isEnabled());

        name().setValue("");
        assertFalse(save().isEnabled(), "a name is required (BR-022)");
        name().setValue("x".repeat(256));
        assertFalse(save().isEnabled());
        name().setValue("Cardiology");
        assertTrue(save().isEnabled());

        code().setValue("c".repeat(101));
        assertFalse(save().isEnabled());
        code().setValue("");
        assertTrue(save().isEnabled(), "the code is optional");

        defaultMessageId().setValue("");
        assertFalse(save().isEnabled(), "a default message is required (BR-024)");
        defaultMessageId().setValue("9".repeat(101));
        assertFalse(save().isEnabled());
        defaultMessageId().setValue("2");
        assertTrue(save().isEnabled());

        fromEmail().setValue("not-an-email");
        assertFalse(save().isEnabled(), "the sender must be an address (BR-025)");
        fromEmail().setValue("a".repeat(40) + "@example.org");
        assertFalse(save().isEnabled(), "at most 50 characters");
        fromEmail().setValue("");
        assertFalse(save().isEnabled());
    }

    @Test
    void given_invalidForm_when_saved_then_validationNoticeAndNothingSaved() {
        enterNew();

        view.saveDepartment();

        assertEquals(List.of(), calls);
        Notification n = onlyNotification();
        assertEquals(t("editDepartmentView.error.fixValidation"), test(n).getText());
        assertEquals(3000, n.getDuration());
        assertEquals(Notification.Position.MIDDLE, n.getPosition());
    }

    @Test
    void given_validNewDepartment_when_saved_then_createdForCreatorSessionRefreshedAndListShown() throws Exception {
        enterNew();
        fillValid();

        view.saveDepartment();

        assertEquals(List.of("create:Cardiology for ada", "refresh"), calls, "assigned, then re-read (BR-113)");
        assertEquals("CARD", department().code);
        assertEquals("card@example.org", department().fromEmail);
        assertEquals(List.of(t("editDepartmentView.created")), notifications());
        Notification n = $(Notification.class).all().get(0);
        assertEquals(3000, n.getDuration());
        assertEquals(Notification.Position.MIDDLE, n.getPosition());
        assertEquals(List.of("departments"), attemptedPaths);
    }

    @Test
    void given_existingDepartment_when_saved_then_itIsSavedWithoutRefresh() throws Exception {
        enterNew();
        department().id = 12;
        fillValid();

        view.updateDepartment();

        assertEquals(List.of("save:Cardiology"), calls);
        assertEquals(List.of(t("editDepartmentView.updated")), notifications());
        assertEquals(List.of("departments"), attemptedPaths);
    }

    private String failWith(String message) {
        saveFailure = new IllegalStateException(message);
        enterNew();
        fillValid();
        view.saveDepartment();
        Notification n = onlyNotification();
        assertEquals(5000, n.getDuration());
        assertEquals(Notification.Position.MIDDLE, n.getPosition());
        assertEquals(List.of(), attemptedPaths, "a failed save stays on the form");
        return test(n).getText();
    }

    @Test
    void given_duplicateName_when_saved_then_nameExistsIsShown() {
        assertEquals(t("editDepartmentView.error.nameExists"),
                failWith("duplicate key violates unique constraint \"department_name_un\""));
    }

    @Test
    void given_duplicateCode_when_saved_then_codeExistsIsShown() {
        assertEquals(t("editDepartmentView.error.codeExists"),
                failWith("duplicate key violates unique constraint \"department_code_un\""));
    }

    @Test
    void given_otherUniqueViolation_when_saved_then_nameOrCodeExistsIsShown() {
        assertEquals(t("editDepartmentView.error.nameOrCodeExists"),
                failWith("violates unique constraint \"something_else\""));
    }

    @Test
    void given_otherFailure_when_saved_then_itsMessageIsShown() {
        assertEquals(t("editDepartmentView.error.save", "connection lost"), failWith("connection lost"));
    }

    @Test
    void given_failureWithoutMessage_when_saved_then_genericSaveErrorIsShown() {
        saveFailure = new IllegalStateException();
        enterNew();
        fillValid();

        view.saveDepartment();

        assertEquals(List.of(t("editDepartmentView.error.save", (Object) null)), notifications());
    }

    @Test
    void given_form_when_cancelClicked_then_departmentsIsEntered() {
        test(cancel()).click();

        assertEquals(List.of("departments"), attemptedPaths);
        assertEquals(List.of(), calls);
    }
}
