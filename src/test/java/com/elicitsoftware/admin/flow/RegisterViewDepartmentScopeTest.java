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
import com.elicitsoftware.model.Respondent;
import com.elicitsoftware.model.Subject;
import com.elicitsoftware.model.User;
import com.elicitsoftware.rest.AccessCodeService;
import com.elicitsoftware.test.DepartmentFixtures;
import com.elicitsoftware.test.PostgresTestResource;
import com.vaadin.browserless.quarkus.QuarkusBrowserlessTest;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.Location;
import com.vaadin.flow.router.NavigationTrigger;
import com.vaadin.flow.router.RouteParameters;
import com.vaadin.flow.server.VaadinSession;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UC-003: Register or Update a Subject — A5 the edit screen addressed by the subject's record id,
 * A7 and BR-138 a subject is edited only within the user's departments (NFR-021), against a real
 * database: the departments come from the user's stored console record, not from the session.
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class RegisterViewDepartmentScopeTest extends QuarkusBrowserlessTest {

    private static final String USERNAME = "register.scope";

    @Inject
    AccessCodeService accessCodes;

    private Department own;
    private Department other;
    private RegisterView view;
    private String token;

    @BeforeEach
    @TestSecurity(user = USERNAME, roles = {"elicit_user"})
    void setUp() {
        token = Long.toString(System.nanoTime(), 36);
        own = DepartmentFixtures.department(token + "A");
        other = DepartmentFixtures.department(token + "B");
        User user = DepartmentFixtures.consoleRecord(USERNAME, true, own);
        VaadinSession.getCurrent().setAttribute("user", user);

        view = CDI.current().select(RegisterView.class).get();
        UI.getCurrent().add(view);
    }

    private long persistSubject(Department department, String firstName) {
        return QuarkusTransaction.requiringNew().call(() -> {
            Respondent respondent = accessCodes.generateAccessCode(1);
            Subject subject = new Subject("RS-" + token + "-" + firstName, 1, department.id, firstName, "Lee", null, null,
                    firstName.toLowerCase() + "." + token + "@example.org", null);
            subject.setRespondent(respondent);
            subject.persistAndFlush();
            return subject.getId();
        });
    }

    private void enterToEdit(long id) {
        UI ui = UI.getCurrent();
        BeforeEnterEvent event = new BeforeEnterEvent(ui.getInternals().getRouter(),
                NavigationTrigger.PROGRAMMATIC, new Location("register/" + id), RegisterView.class,
                new RouteParameters(RegisterView.ID_PARAMETER, Long.toString(id)), ui, Collections.emptyList());
        view.beforeEnter(event);
    }

    private TextField firstName() {
        return find(TextField.class, view).all().stream()
                .filter(field -> "register-first-name".equals(field.getId().orElse(null)))
                .findFirst().orElseThrow();
    }

    private Button button(String id) {
        return find(Button.class, view).all().stream()
                .filter(button -> id.equals(button.getId().orElse(null)))
                .findFirst().orElse(null);
    }

    private List<String> notifications() {
        return find(Notification.class).all().stream().map(n -> test(n).getText()).toList();
    }

    @SuppressWarnings("unchecked")
    private Binder<Subject> binder() throws Exception {
        Field field = RegisterView.class.getDeclaredField("binder");
        field.setAccessible(true);
        return (Binder<Subject>) field.get(view);
    }

    private String storedFirstName(long id) {
        return QuarkusTransaction.requiringNew().call(() -> Subject.<Subject>findById(id).getFirstName());
    }

    private long storedDepartment(long id) {
        return QuarkusTransaction.requiringNew().call(() -> Subject.<Subject>findById(id).getDepartmentId());
    }

    @Test
    @TestSecurity(user = USERNAME, roles = {"elicit_user"})
    void given_aSubjectOfTheUsersDepartment_when_openedByItsId_then_theFormHoldsItInUpdateMode() {
        long id = persistSubject(own, "Ann");

        enterToEdit(id);

        assertEquals(id, view.currentSubject().getId());
        assertEquals("Ann", firstName().getValue());
        assertTrue(button("register-update-button").isVisible());
        assertTrue(notifications().isEmpty());
    }

    @Test
    @TestSecurity(user = USERNAME, roles = {"elicit_user"})
    void given_aSubjectOfAnotherDepartment_when_openedByItsId_then_itIsAnsweredAsNotFoundAndTheFormStaysEmpty() {
        long id = persistSubject(other, "Bob");

        enterToEdit(id);

        assertEquals(List.of("Subject not found."), notifications(), "A7: exactly as for a subject that does not exist");
        assertEquals(0, view.currentSubject().getId());
        assertEquals("", firstName().getValue(), "BR-138: no field of the subject is shown");
        assertTrue(button("register-save-button").isVisible(), "the form stays in registration mode");
        Button update = button("register-update-button");
        assertTrue(update == null || !update.isVisible());
    }

    @Test
    @TestSecurity(user = USERNAME, roles = {"elicit_user"})
    void given_theUsersDepartmentWasRemovedWhileEditing_when_updating_then_theStoredSubjectIsUnchanged() throws Exception {
        long id = persistSubject(own, "Cleo");
        enterToEdit(id);
        firstName().setValue("Changed");
        // Another administrator takes the department away; the session still holds the old record.
        DepartmentFixtures.consoleRecord(USERNAME, true);

        view.updateSubject(binder());

        assertEquals(List.of("This subject belongs to a department you are not assigned to. Nothing was changed."), notifications());
        assertEquals("Cleo", storedFirstName(id), "BR-138: the departments are read from the database at that moment");
        assertEquals(own.id, storedDepartment(id));
    }

    @Test
    @TestSecurity(user = USERNAME, roles = {"elicit_user"})
    void given_aSubjectOfTheUsersDepartment_when_updating_then_theChangeIsStored() throws Exception {
        long id = persistSubject(own, "Dana");
        enterToEdit(id);
        firstName().setValue("Dinah");

        view.updateSubject(binder());

        assertEquals("Dinah", storedFirstName(id));
        assertFalse(notifications().contains("This subject belongs to a department you are not assigned to. Nothing was changed."));
    }
}
