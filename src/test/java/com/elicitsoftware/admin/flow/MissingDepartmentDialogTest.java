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
import com.elicitsoftware.model.User;
import com.elicitsoftware.service.DefaultAccountCheck;
import com.elicitsoftware.service.SurveyDefinitionPresenceCheck;
import com.elicitsoftware.test.PostgresTestResource;
import com.vaadin.browserless.quarkus.QuarkusBrowserlessTest;
import com.vaadin.flow.component.ModalityMode;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.server.VaadinSession;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.enterprise.inject.spi.CDI;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UC-028 (Require a Department Assignment): the layout blocks a console user who has no
 * department with a modal dialog, offers an administrator the Departments screen, offers a
 * user only Logout, and clears the dialog once a department is assigned.
 * <p>
 * The gate re-reads the console record from the database when the session copy shows no
 * department (BR-114), so the tests that expect the dialog persist a real {@code survey.users}
 * row for the principal; the ones that expect no dialog seed the session directly, as the other
 * layout tests do. Route navigation is unavailable under {@code @QuarkusTest}, so the layout's
 * {@code showRouterLayoutContent} is driven directly, followed by a client round trip because a
 * dialog is attached to the UI only then.
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class MissingDepartmentDialogTest extends QuarkusBrowserlessTest {

    /** A survey is installed, so the missing-survey banner stays out of the way (UC-019 is tested separately). */
    static final class SurveyPresent extends SurveyDefinitionPresenceCheck {
        @Override
        public boolean isSurveyInstalled() {
            return true;
        }
    }

    /** No seeded default accounts, so the default-account banner stays out of the way (UC-021). */
    static final class NoDefaultAccounts extends DefaultAccountCheck {
        @Override
        public List<String> findDefaultAccounts() {
            return List.of();
        }
    }

    @BeforeEach
    void quietTheBanners() {
        QuarkusMock.installMockForType(new SurveyPresent(), SurveyDefinitionPresenceCheck.class);
        QuarkusMock.installMockForType(new NoDefaultAccounts(), DefaultAccountCheck.class);
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

    /**
     * A real console record with no department, loaded into the session as sign-in would load
     * it, so the gate's re-read finds it too (BR-114). The UI-scoped {@code UiSessionLogin}
     * initialises once per test run, so the load is explicit rather than left to its
     * {@code @PostConstruct}.
     */
    private static void persistUserWithoutDepartment(String username) {
        QuarkusTransaction.requiringNew().run(() -> {
            User user = new User();
            user.setUsername(username);
            user.setFirstName("No");
            user.setLastName("Department");
            user.setActive(true);
            user.persist();
        });
        CDI.current().select(UiSessionLogin.class).get().refresh();
    }

    private static void assignADepartment(String username) {
        QuarkusTransaction.requiringNew().run(() -> {
            Department department = new Department();
            department.name = "UC028 Dept " + username;
            department.code = "UC028-" + username.substring(username.indexOf('.') + 1);
            department.defaultMessageId = "1";
            department.fromEmail = "uc028@example.org";
            department.persist();
            User user = User.find("username = ?1", username).firstResult();
            user.getDepartments().add(department);
        });
    }

    /** Forces the UI-scoped lookup to run first so it cannot overwrite a session user seeded here. */
    private static void seedSessionUser(String username, boolean withDepartment) {
        CDI.current().select(UiSessionLogin.class).get().getUser();
        User user = new User();
        user.setId(1);
        user.setUsername(username);
        user.setActive(true);
        Set<Department> departments = new HashSet<>();
        if (withDepartment) {
            Department department = new Department();
            department.id = 1;
            department.name = "Seeded Dept";
            departments.add(department);
        }
        user.setDepartments(departments);
        VaadinSession.getCurrent().setAttribute("user", user);
    }

    private MainLayout attachLayout() {
        MainLayout layout = CDI.current().select(MainLayout.class).get();
        UI.getCurrent().add(layout);
        return layout;
    }

    private static Div routedContent() {
        Div content = new Div();
        content.setId("routed-content");
        return content;
    }

    private static Optional<Dialog> openDialog() {
        return UI.getCurrent().getChildren()
                .filter(Dialog.class::isInstance)
                .map(Dialog.class::cast)
                .filter(d -> d.getId().filter(MissingDepartmentDialog.DIALOG_ID::equals).isPresent())
                .filter(Dialog::isOpened)
                .findFirst();
    }

    private List<Button> buttons(Dialog dialog) {
        return find(Button.class, dialog).all();
    }

    private static boolean hasButton(List<Button> buttons, String id) {
        return buttons.stream().anyMatch(b -> b.getId().filter(id::equals).isPresent());
    }

    // --- The dialog ------------------------------------------------------------------------

    /** UC-028 steps 2-3 / BR-110: an administrator with no department is blocked and offered the remedy. */
    @Test
    @TestSecurity(user = "uc028.admin", roles = {"elicit_admin"})
    void administratorWithoutADepartmentIsBlockedAndOfferedDepartments() {
        persistUserWithoutDepartment("uc028.admin");
        MainLayout layout = attachLayout();

        layout.showRouterLayoutContent(routedContent());
        roundTrip();

        Dialog dialog = openDialog().orElseThrow(() -> new AssertionError("the blocking dialog should be open"));
        assertEquals(ModalityMode.STRICT, dialog.getModality(), "nothing behind the dialog may be reachable (BR-110)");
        assertFalse(dialog.isCloseOnEsc(), "Escape must not dismiss it (BR-110)");
        assertFalse(dialog.isCloseOnOutsideClick(), "clicking outside must not dismiss it (BR-110)");
        assertEquals("alertdialog", dialog.getRole(), "announced as an alert dialog (NFR-016)");
        List<Button> buttons = buttons(dialog);
        assertTrue(hasButton(buttons, MissingDepartmentDialog.ADD_BUTTON_ID), "the administrator is offered Add a department");
        assertTrue(hasButton(buttons, MissingDepartmentDialog.LOGOUT_BUTTON_ID), "Logout is always offered (BR-112)");
        assertEquals(2, buttons.size(), "no close button or other way out");
    }

    /** UC-028 A1: a user with no department is told to ask an administrator and can only log out. */
    @Test
    @TestSecurity(user = "uc028.user", roles = {"elicit_user"})
    void userWithoutADepartmentIsBlockedWithLogoutOnly() {
        persistUserWithoutDepartment("uc028.user");
        MainLayout layout = attachLayout();

        layout.showRouterLayoutContent(routedContent());
        roundTrip();

        Dialog dialog = openDialog().orElseThrow(() -> new AssertionError("the blocking dialog should be open"));
        List<Button> buttons = buttons(dialog);
        assertFalse(hasButton(buttons, MissingDepartmentDialog.ADD_BUTTON_ID), "a user cannot create departments");
        assertTrue(hasButton(buttons, MissingDepartmentDialog.LOGOUT_BUTTON_ID), "Logout is the only action (BR-112)");
        assertEquals(1, buttons.size());
    }

    /** UC-028 BR-111: the Departments and Edit Department screens stay usable for an administrator. */
    @Test
    @TestSecurity(user = "uc028.remedy", roles = {"elicit_admin"})
    void remedyScreensAreNeverBlockedForAnAdministrator() {
        persistUserWithoutDepartment("uc028.remedy");
        MainLayout layout = attachLayout();

        layout.showRouterLayoutContent(CDI.current().select(DepartmentsView.class).get());
        roundTrip();
        assertTrue(openDialog().isEmpty(), "Departments is where the remedy lives; it must not be blocked");

        layout.showRouterLayoutContent(CDI.current().select(EditDepartmentView.class).get());
        roundTrip();
        assertTrue(openDialog().isEmpty(), "Edit Department is the remedy itself; it must not be blocked");

        layout.showRouterLayoutContent(routedContent());
        roundTrip();
        assertTrue(openDialog().isPresent(), "every other screen is blocked until a department exists");
    }

    /** UC-028 A2/step 6/BR-114: an assignment made elsewhere clears the dialog on the next navigation. */
    @Test
    @TestSecurity(user = "uc028.assigned", roles = {"elicit_admin"})
    void dialogClearsOnTheNextNavigationOnceADepartmentIsAssigned() {
        persistUserWithoutDepartment("uc028.assigned");
        MainLayout layout = attachLayout();
        layout.showRouterLayoutContent(routedContent());
        roundTrip();
        Dialog dialog = openDialog().orElseThrow(() -> new AssertionError("the blocking dialog should be open"));

        assignADepartment("uc028.assigned");
        layout.showRouterLayoutContent(routedContent());
        roundTrip();

        assertFalse(dialog.isOpened(), "the dialog should close without a re-login (BR-114)");
        assertTrue(openDialog().isEmpty());
        assertEquals("routed-content", layout.getContent().getId().orElse(null),
                "with a department assigned the routed view is shown as usual");
    }

    /** UC-028 (ordinary case): a user with a department sees no dialog and no wrapper. */
    @Test
    @TestSecurity(user = "uc028.fine", roles = {"elicit_user"})
    void userWithADepartmentIsNotBlocked() {
        seedSessionUser("uc028.fine", true);
        MainLayout layout = attachLayout();

        layout.showRouterLayoutContent(routedContent());
        roundTrip();

        assertTrue(openDialog().isEmpty(), "a user with a department is never blocked");
        assertEquals("routed-content", layout.getContent().getId().orElse(null));
    }

    /** UC-028 A4: a principal with no console record is left to UC-001's handling, not blocked. */
    @Test
    @TestSecurity(user = "uc028.norecord", roles = {"elicit_user"})
    void principalWithNoConsoleRecordIsNotBlocked() {
        seedSessionUser("uc028.norecord", false);
        VaadinSession.getCurrent().setAttribute("user", null);
        MainLayout layout = attachLayout();

        layout.showRouterLayoutContent(routedContent());
        roundTrip();

        assertTrue(openDialog().isEmpty(), "no record means UC-001's explanation, not this dialog");
    }
}
