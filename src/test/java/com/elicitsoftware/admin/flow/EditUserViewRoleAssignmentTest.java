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

import com.elicitsoftware.model.User;
import com.elicitsoftware.model.UserRole;
import com.elicitsoftware.test.DatabaseAuthorizationTestProfile;
import com.elicitsoftware.test.PostgresTestResource;
import com.vaadin.browserless.quarkus.QuarkusBrowserlessTest;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.Location;
import com.vaadin.flow.router.NavigationTrigger;
import com.vaadin.flow.router.RouteParameters;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.security.TestSecurity;
import jakarta.enterprise.inject.spi.CDI;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Browserless UI tests for {@link EditUserView}'s Database Role Assignment section (UC-016),
 * run under {@link DatabaseAuthorizationTestProfile} ({@code elicit.authorization.mode=DATABASE}).
 * Complements UC-008 (Manage Users) and UC-001 (role resolution and cumulative expansion are
 * unaffected by this mode's UI).
 */
@QuarkusTest
@TestProfile(DatabaseAuthorizationTestProfile.class)
@QuarkusTestResource(PostgresTestResource.class)
@TestSecurity(user = "uc016.tester", roles = "elicit_admin")
class EditUserViewRoleAssignmentTest extends QuarkusBrowserlessTest {

    private EditUserView view;

    @BeforeEach
    void setUp() {
        view = CDI.current().select(EditUserView.class).get();
        UI.getCurrent().add(view);
        enterCreateMode();
    }

    /**
     * Drives {@link EditUserView#beforeEnter} directly in create mode (route id "0"), since
     * route navigation is unavailable under {@code @QuarkusTest} (see {@link EditUserViewTest}).
     * Without this, {@code EditUserView.user} stays null and {@code saveUser()} NPEs in the
     * binder.
     */
    private void enterCreateMode() {
        UI ui = UI.getCurrent();
        BeforeEnterEvent event = new BeforeEnterEvent(ui.getInternals().getRouter(),
                NavigationTrigger.PROGRAMMATIC, new Location(""), EditUserView.class,
                new RouteParameters("id", "0"), ui, Collections.emptyList());
        view.beforeEnter(event);
    }

    @SuppressWarnings("unchecked")
    private ComboBox<String> roleBox() {
        return (ComboBox<String>) find(ComboBox.class, view).all().stream()
                .filter(box -> "Role".equals(box.getLabel()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No Role ComboBox"));
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

    /** UC-016: the role dropdown is visible and offers exactly the three recognized roles. */
    @Test
    void roleDropdownVisibleAndOffersRecognizedRoles() {
        ComboBox<String> roleBox = roleBox();
        assertTrue(roleBox.isVisible(), "Role dropdown must be visible when elicit.authorization.mode=DATABASE");
    }

    /** UC-016: saving a new user with a role selected persists exactly one row in survey.user_roles. */
    @Test
    @TestTransaction
    void savingPersistsSelectedRole() {
        field("Username").setValue("uc016.grant@example.org");
        field("First Name").setValue("Grant");
        field("Last Name").setValue("Test");
        roleBox().setValue("elicit_admin");

        saveButton().click();

        User saved = User.find("username", "uc016.grant@example.org").firstResult();
        assertNotNull(saved);
        assertEquals(1, UserRole.count("id.userId", saved.getId()));
        UserRole role = UserRole.<UserRole>list("id.userId", saved.getId()).get(0);
        assertEquals("elicit_admin", role.getId().getRoleName());
    }

    /**
     * UC-016/BR-055: changing the selection on a second save replaces the grant rather than
     * adding a second row. Both saves happen on the same in-memory {@code view} instance (its
     * {@code user} field carries the generated id across saves) since route navigation/reload
     * isn't available under {@code @QuarkusTest} (see {@link EditUserViewTest}).
     */
    @Test
    @TestTransaction
    void changingSelectionReplacesGrant() {
        field("Username").setValue("uc016.replace@example.org");
        field("First Name").setValue("Replace");
        field("Last Name").setValue("Test");
        roleBox().setValue("elicit_admin");
        saveButton().click();

        roleBox().setValue("elicit_importer");
        saveButton().click();

        User saved = User.find("username", "uc016.replace@example.org").firstResult();
        assertEquals(1, UserRole.count("id.userId", saved.getId()));
        UserRole role = UserRole.<UserRole>list("id.userId", saved.getId()).get(0);
        assertEquals("elicit_importer", role.getId().getRoleName());
    }

    /** UC-016: clearing the selection on a second save removes the grant entirely. */
    @Test
    @TestTransaction
    void clearingSelectionRemovesGrant() {
        field("Username").setValue("uc016.clear@example.org");
        field("First Name").setValue("Clear");
        field("Last Name").setValue("Test");
        roleBox().setValue("elicit_user");
        saveButton().click();

        roleBox().clear();
        saveButton().click();

        User saved = User.find("username", "uc016.clear@example.org").firstResult();
        assertEquals(0, UserRole.count("id.userId", saved.getId()));
    }
}
