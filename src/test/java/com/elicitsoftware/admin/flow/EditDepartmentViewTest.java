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
import com.elicitsoftware.model.User;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.security.TestSecurity;
import org.junit.jupiter.api.AfterEach;
import com.elicitsoftware.test.PostgresTestResource;
import com.vaadin.browserless.quarkus.QuarkusBrowserlessTest;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.Location;
import com.vaadin.flow.router.NavigationTrigger;
import com.vaadin.flow.router.RouteParameters;
import java.util.Collections;
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

    private TextField textField(String labelKey) {
        String label = UI.getCurrent().getTranslation(labelKey);
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
                .filter(b -> !UI.getCurrent().getTranslation("common.cancel").equals(b.getText()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No Save button"));
    }

    private void fillAllValid() {
        textField("editDepartmentView.name").setValue("Cardiology");
        textField("editDepartmentView.defaultMessageId").setValue("1");
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
        textField("editDepartmentView.name").setValue("");
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
    @AfterEach
    void cleanup() {
        QuarkusTransaction.requiringNew().run(() -> {
            User.<User>list("username like ?1", "uc028.%").forEach(u -> {
                if (u.getDepartments() != null) {
                    u.getDepartments().clear();
                }
                u.delete();
            });
            // Flush the entity deletes (and their join rows) before the bulk delete below runs.
            User.getEntityManager().flush();
            Department.delete("code like ?1", "UC028%");
        });
    }

    /** UC-028 BR-113 / step 5: saving a new department assigns it to the administrator who created it. */
    @Test
    @TestSecurity(user = "uc028.deptadmin", roles = {"elicit_admin"})
    void savingANewDepartmentAssignsItToTheCreator() {
        QuarkusTransaction.requiringNew().run(() -> {
            User user = new User();
            user.setUsername("uc028.deptadmin");
            user.setFirstName("Dee");
            user.setLastName("Admin");
            user.setActive(true);
            user.persist();
        });
        // Route navigation is unavailable here, so enter create mode the way the router would.
        UI ui = UI.getCurrent();
        view.beforeEnter(new BeforeEnterEvent(ui.getInternals().getRouter(), NavigationTrigger.PROGRAMMATIC,
                new Location(""), EditDepartmentView.class, new RouteParameters("id", "0"), ui, Collections.emptyList()));
        textField("editDepartmentView.name").setValue("UC028 Created Dept");
        textField("editDepartmentView.code").setValue("UC028-C");
        textField("editDepartmentView.defaultMessageId").setValue("1");
        fromEmail().setValue("uc028@example.org");

        view.saveDepartment();

        QuarkusTransaction.requiringNew().run(() -> {
            assertTrue(Department.count("code = ?1", "UC028-C") == 1, "the department should have been saved");
            User reloaded = User.find("username = ?1", "uc028.deptadmin").firstResult();
            assertTrue(reloaded.getDepartments().stream().anyMatch(d -> "UC028-C".equals(d.code)),
                    "the creator should be assigned to the department they just created");
        });
        User sessionUser = CDI.current().select(UiSessionLogin.class).get().getUser();
        assertTrue(sessionUser != null && sessionUser.hasDepartments(),
                "the session record is refreshed so the console notices without a re-login (BR-114)");
    }
}
