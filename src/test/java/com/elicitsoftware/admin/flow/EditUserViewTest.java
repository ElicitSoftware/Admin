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
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.textfield.TextField;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.enterprise.inject.spi.CDI;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Browserless UI test for {@link EditUserView}'s Binder validation.
 *
 * <p>Traceability: UC-009 (Manage Users). Code-review finding #6 converted this view to use a
 * {@link com.vaadin.flow.data.binder.Binder} with required/length validators and made the Save
 * button track validity. This test exercises that behaviour from the user's perspective:
 * required fields left blank keep Save disabled; filling them all in enables it; clearing one
 * disables it again.</p>
 *
 * <p>The view injects {@code UserService} and loads departments in its constructor, so it is
 * obtained through CDI (rather than {@code new}) and attached to the test {@link UI} — under
 * {@code @QuarkusTest} the Vaadin route registry is not populated, so route navigation is
 * unavailable (see the project's browserless-testing notes). Fields are located by their label
 * via the framework {@code find(...)} API and driven with {@code setValue}, which runs the real
 * server-side Binder status pipeline.</p>
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class EditUserViewTest extends QuarkusBrowserlessTest {

    private EditUserView view;

    @BeforeEach
    void setUp() {
        view = CDI.current().select(EditUserView.class).get();
        UI.getCurrent().add(view);
    }

    private TextField field(String label) {
        return find(TextField.class, view).all().stream()
                .filter(f -> label.equals(f.getLabel()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No TextField labelled '" + label + "'"));
    }

    private Button saveButton() {
        return find(Button.class, view).all().stream()
                .filter(b -> "Save".equals(b.getText()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No Save button"));
    }

    private void fillAllValid() {
        field("Username").setValue("jane.doe@example.org");
        field("First Name").setValue("Jane");
        field("Last Name").setValue("Doe");
    }

    /** UC-009 (#6): with required fields blank, the Save button is disabled. */
    @Test
    void saveDisabledWhenRequiredFieldsBlank() {
        // Touch a required field and clear it to force a validation status update.
        field("Username").setValue("temp");
        field("Username").setValue("");
        assertFalse(saveButton().isEnabled(),
                "Save must be disabled while a required field is blank");
    }

    /** UC-009 (#6): filling every required field enables Save. */
    @Test
    void saveEnabledWhenAllRequiredFieldsValid() {
        fillAllValid();
        assertTrue(saveButton().isEnabled(),
                "Save must be enabled once all required fields are valid");
    }

    /** UC-009 (#6): clearing a required field after a valid form disables Save again. */
    @Test
    void clearingRequiredFieldDisablesSave() {
        fillAllValid();
        assertTrue(saveButton().isEnabled());

        field("Last Name").setValue("");
        assertFalse(saveButton().isEnabled(),
                "Save must disable again when a required field is cleared");
    }

    /**
     * UC-016: in the default OIDC authorization mode (this class runs without
     * {@code DatabaseAuthorizationTestProfile}), the Database Role Assignment section --
     * and its role dropdown -- is not visible in the rendered view.
     */
    @Test
    void roleDropdownHiddenInOidcMode() {
        boolean visible = find(ComboBox.class, view).all().stream()
                .anyMatch(box -> "Role".equals(box.getLabel()) && box.isVisible());
        assertFalse(visible, "Role dropdown must not be visible when elicit.authorization.mode=OIDC");
    }
}
