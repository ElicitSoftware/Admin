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
 * Browserless UI test for {@link DepartmentsView}.
 *
 * <p>Traceability: UC-006 (Manage Departments). The view has no injected dependencies, so it is
 * constructed directly with {@code new} rather than through CDI, and attached to the test
 * {@link UI}. A view attached this way has no initialized {@code Router}, so the edit/"New
 * Department" navigation calls are exercised for their own execution (and id formatting), not
 * for an observable navigation outcome — see {@code UsersViewTest}.</p>
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class DepartmentsViewTest extends QuarkusBrowserlessTest {

    @SuppressWarnings("unchecked")
    private Grid<Department> grid(DepartmentsView view) {
        return find(Grid.class, view).single();
    }

    /** UC-006: the department grid renders its full set of columns. */
    @Test
    @TestTransaction
    void gridRendersWithColumnsAndData() {
        Department department = new Department();
        department.name = "Cardiology";
        department.code = "CARD";
        department.defaultMessageId = "1";
        department.fromEmail = "cardiology@example.org";
        department.persist();

        DepartmentsView view = new DepartmentsView();
        UI.getCurrent().add(view);

        // Edit + Department Name + Code + Default Message ID + From Email = 5 columns.
        assertTrue(grid(view).getColumns().size() >= 5);
        assertTrue(test(grid(view)).size() >= 1, "the persisted department should appear in the grid");
    }

    /** UC-006: clicking a row's edit icon triggers navigation to that department's edit route. */
    @Test
    @TestTransaction
    void editButtonTriggersNavigation() {
        Department department = new Department();
        department.name = "Radiology";
        department.code = "RAD";
        department.defaultMessageId = "1";
        department.fromEmail = "radiology@example.org";
        department.persist();

        DepartmentsView view = new DepartmentsView();
        UI.getCurrent().add(view);

        Component editCell = test(grid(view)).getCellComponent(0, 0);
        assertDoesNotThrow(() -> ((Button) editCell).click());
    }

    /** UC-006: clicking "New Department" triggers navigation to the create-mode edit route. */
    @Test
    void newDepartmentButtonTriggersNavigation() {
        DepartmentsView view = new DepartmentsView();
        UI.getCurrent().add(view);

        Button newDepartmentBtn = find(Button.class, view).all().stream()
                .filter(b -> view.getTranslation("departmentsView.newDepartment").equals(b.getText()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No New Department button"));

        assertDoesNotThrow(newDepartmentBtn::click);
    }
}
