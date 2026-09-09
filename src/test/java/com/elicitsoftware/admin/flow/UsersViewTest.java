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
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Paragraph;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.enterprise.inject.spi.CDI;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Browserless UI test for {@link UsersView} in the default OIDC authorization mode.
 *
 * <p>Traceability: UC-008 (Manage Users). The view has no injected dependencies beyond
 * {@code AuthorizationModeConfig}, so it is obtained through CDI (rather than {@code new}) to
 * let {@code @PostConstruct} run, and attached to the test {@link UI}.</p>
 *
 * <p>A view attached directly to the test {@link UI} (rather than reached by router
 * navigation) has no initialized {@code Router} on that {@code UI} instance, so
 * {@code ui.navigate(...)} is a silent no-op here rather than swapping in the target view (see
 * the project's browserless-testing notes) — the Edit/Add-User click handlers are exercised for
 * the navigation call itself (and its id-parameter formatting), not for an observable
 * navigation outcome.</p>
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class UsersViewTest extends QuarkusBrowserlessTest {

    private UsersView view;

    @BeforeEach
    void setUp() {
        view = CDI.current().select(UsersView.class).get();
        UI.getCurrent().add(view);
    }

    @SuppressWarnings("unchecked")
    private Grid<User> grid() {
        return find(Grid.class, view).single();
    }

    /** UC-008: the user grid renders its full set of columns. */
    @Test
    void gridRendersWithColumns() {
        // Edit + Username + First Name + Last Name + Active + Department = 6 columns.
        assertTrue(grid().getColumns().size() >= 6);
    }

    /** UC-008: in the default OIDC mode, the info text explains OIDC-managed roles. */
    @Test
    void infoTextExplainsOidcMode() {
        Paragraph info = find(Paragraph.class, view).single();
        assertTrue(info.getText().contains("OpenID Connect"),
                "OIDC mode should explain that roles are configured in the OIDC provider");
    }

    /** UC-008: clicking "Add User" triggers navigation to the create-mode EditUserView route. */
    @Test
    void addUserButtonTriggersNavigation() {
        Button addUserBtn = find(Button.class, view).all().stream()
                .filter(b -> "Add User".equals(b.getText()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No Add User button"));

        assertDoesNotThrow(addUserBtn::click);
    }

    /** UC-008: clicking a row's edit icon triggers navigation to that user's edit route. */
    @Test
    @TestTransaction
    void editButtonTriggersNavigation() {
        Department department = new Department();
        department.name = "UC-008 Dept";
        department.code = "UC008";
        department.defaultMessageId = "1";
        department.fromEmail = "uc008@example.org";
        department.persist();

        User user = new User();
        user.setUsername("uc008.grid@example.org");
        user.setFirstName("Grid");
        user.setLastName("User");
        user.setActive(true);
        Set<Department> departments = new HashSet<>();
        departments.add(department);
        user.setDepartments(departments);
        user.persist();

        // Refresh the grid to pick up the freshly persisted user.
        view = CDI.current().select(UsersView.class).get();
        UI.getCurrent().add(view);

        Component editCell = test(grid()).getCellComponent(0, 0);
        assertDoesNotThrow(() -> ((Button) editCell).click());
    }
}
