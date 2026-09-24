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
import com.elicitsoftware.test.PostgresTestResource;
import com.vaadin.browserless.quarkus.QuarkusBrowserlessTest;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.html.Div;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.enterprise.inject.spi.CDI;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Browserless UI test for {@link UiSessionLogin}, the UI-scoped bridge between Quarkus security
 * and the Vaadin session.
 *
 * <p>Traceability: UC-001 (Authenticate and Access the Admin Console). {@code @PostConstruct}
 * looks up an active {@link User} by the authenticated principal's name and stores it (or
 * {@code null}) in the Vaadin session. This test resolves the {@code @UIScoped} bean directly
 * through CDI (its {@code @PostConstruct} fires on first method invocation through the proxy)
 * rather than bypassing it the way other view tests do (see {@code SearchViewTest}).</p>
 *
 * <p>Only the "not found" branch is exercised here. The UI-scoped bean's persistence context
 * appears to hold a snapshot from before the test method body runs (predating whatever the test
 * itself commits via {@code @TestTransaction} or an explicit {@code QuarkusTransaction}), so a
 * row inserted by the test is not visible to {@code init()}'s own query — a "found" test would
 * pass or fail on that visibility quirk rather than on {@code UiSessionLogin}'s actual logic.
 * The "no active user at all" case doesn't depend on that visibility and is safe to assert.</p>
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class UiSessionLoginTest extends QuarkusBrowserlessTest {

    @AfterEach
    void cleanup() {
        QuarkusTransaction.requiringNew().run(() -> {
            // Join rows first: a bulk delete of the users would trip user_departments' FK.
            User.<User>list("username like ?1", "uisessionlogin.%").forEach(u -> {
                if (u.getDepartments() != null) {
                    u.getDepartments().clear();
                }
                u.delete();
            });
            // Flush the entity deletes (and their join rows) before the bulk delete below runs.
            User.getEntityManager().flush();
            Department.delete("code like ?1", "UISL%");
        });
    }

    private UiSessionLogin sessionLogin() {
        UI.getCurrent().add(new Div()); // ensure a component is attached before resolving the UI-scoped bean
        return CDI.current().select(UiSessionLogin.class).get();
    }

    /** UC-001: an inactive user matching the principal is treated as not found. */
    @Test
    @TestSecurity(user = "uisessionlogin.inactive", roles = {"elicit_user"})
    void inactiveUserIsTreatedAsNotFound() {
        QuarkusTransaction.requiringNew().run(() -> {
            User user = new User();
            user.setUsername("uisessionlogin.inactive");
            user.setFirstName("Test");
            user.setLastName("Tester");
            user.setActive(false);
            user.persist();
        });

        assertNull(sessionLogin().getUser());
    }

    /** UC-001 A1: no matching user at all results in a null session user, not an error. */
    @Test
    @TestSecurity(user = "uisessionlogin.unknown", roles = {})
    void noMatchingUserResultsInNullSessionUser() {
        assertNull(sessionLogin().getUser());
    }
    /** UC-028 BR-114: refresh() re-reads the record, so a department assigned after sign-in is seen. */
    @Test
    @TestSecurity(user = "uisessionlogin.refresh", roles = {"elicit_admin"})
    void refreshRereadsTheUserFromTheDatabase() {
        QuarkusTransaction.requiringNew().run(() -> {
            User user = new User();
            user.setUsername("uisessionlogin.refresh");
            user.setFirstName("Re");
            user.setLastName("Fresh");
            user.setActive(true);
            user.persist();
        });
        // The UI-scoped bean initialises once per test run, so load this principal's record explicitly.
        UiSessionLogin login = sessionLogin();
        login.refresh();
        assertFalse(login.getUser().hasDepartments(), "signed in with no department");

        QuarkusTransaction.requiringNew().run(() -> {
            Department department = new Department();
            department.name = "UISL Refresh Dept";
            department.code = "UISL-R";
            department.defaultMessageId = "1";
            department.fromEmail = "uisl@example.org";
            department.persist();
            User user = User.find("username = ?1", "uisessionlogin.refresh").firstResult();
            user.getDepartments().add(department);
        });
        assertFalse(login.getUser().hasDepartments(), "the session copy is stale until refreshed");

        login.refresh();

        assertTrue(login.getUser().hasDepartments(), "refresh() should pick up the new assignment");
    }

    /** UC-028 BR-114: refresh() applies the same rule as sign-in, so a deactivated record becomes null. */
    @Test
    @TestSecurity(user = "uisessionlogin.gone", roles = {"elicit_user"})
    void refreshStoresNullWhenTheRecordIsDeactivated() {
        QuarkusTransaction.requiringNew().run(() -> {
            User user = new User();
            user.setUsername("uisessionlogin.gone");
            user.setFirstName("Go");
            user.setLastName("Ne");
            user.setActive(true);
            user.persist();
        });
        UiSessionLogin login = sessionLogin();
        login.refresh();
        assertTrue(login.getUser() != null, "signed in with an active record");

        QuarkusTransaction.requiringNew().run(() -> {
            User user = User.find("username = ?1", "uisessionlogin.gone").firstResult();
            user.setActive(false);
        });
        login.refresh();

        assertNull(login.getUser(), "an inactive record is treated as not found, as at sign-in");
    }
}
