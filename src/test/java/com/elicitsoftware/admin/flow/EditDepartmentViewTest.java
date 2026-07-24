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

import com.elicitsoftware.test.PostgresTestResource;
import com.vaadin.browserless.quarkus.QuarkusBrowserlessTest;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.component.textfield.TextField;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.enterprise.inject.spi.CDI;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Browserless UI test for {@link EditDepartmentView}'s Binder validation.
 *
 * <p>Traceability: UC-010 (Manage Departments). This view already used a
 * {@link com.vaadin.flow.data.binder.Binder}; code-review finding #7 moved its persistence into
 * {@code DepartmentService} (covered by {@code DepartmentServiceTest}). This test guards the
 * unchanged-but-critical UI contract that survived the refactor: the Save button tracks form
 * validity — enabled only when name, default message id, and a valid from-email are present,
 * and disabled when a required field is blank or the email is invalid.</p>
 *
 * <p>The view is obtained through CDI (it injects {@code DepartmentService}) and attached to the
 * test {@link UI}; fields are located by label and driven with {@code setValue} to run the real
 * server-side Binder status pipeline. Route navigation is unavailable under {@code @QuarkusTest}
 * (see the project's browserless-testing notes), so no {@code beforeEnter} bean is set here — the
 * assertions concern only field-level validity, which the binder evaluates independently.</p>
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class EditDepartmentViewTest extends QuarkusBrowserlessTest {

    private EditDepartmentView view;

    @BeforeEach
    void setUp() {
        view = CDI.current().select(EditDepartmentView.class).get();
        UI.getCurrent().add(view);
    }

    private TextField textField(String label) {
        return find(TextField.class, view).all().stream()
                .filter(f -> label.equals(f.getLabel()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No TextField labelled '" + label + "'"));
    }

    private EmailField fromEmail() {
        return find(EmailField.class, view).single();
    }

    private Button saveButton() {
        // The button text is "Save" until beforeEnter() relabels it; match on the primary action.
        return find(Button.class, view).all().stream()
                .filter(b -> !"Cancel".equals(b.getText()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No Save button"));
    }

    private void fillAllValid() {
        textField("Department Name").setValue("Cardiology");
        textField("Default Message ID").setValue("1");
        fromEmail().setValue("dept@example.org");
    }

    /** UC-010 (#7): a fully valid form enables the Save button. */
    @Test
    void saveEnabledWhenFormValid() {
        fillAllValid();
        assertTrue(saveButton().isEnabled(),
                "Save must be enabled once name, message id, and a valid email are set");
    }

    /** UC-010 (#7): a blank required name disables Save. */
    @Test
    void saveDisabledWhenNameBlank() {
        fillAllValid();
        textField("Department Name").setValue("");
        assertFalse(saveButton().isEnabled(),
                "Save must disable when the required department name is cleared");
    }

    /** UC-010 (#7): an invalid email address disables Save. */
    @Test
    void saveDisabledWhenEmailInvalid() {
        fillAllValid();
        fromEmail().setValue("not-an-email");
        assertFalse(saveButton().isEnabled(),
                "Save must disable when the from-email is not a valid address");
    }
}
