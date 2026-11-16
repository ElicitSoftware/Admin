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
import com.elicitsoftware.model.ImportClient;
import com.elicitsoftware.model.User;
import com.elicitsoftware.security.ElicitRoles;
import com.elicitsoftware.test.DepartmentFixtures;
import com.elicitsoftware.test.PostgresTestResource;
import com.vaadin.browserless.quarkus.QuarkusBrowserlessTest;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.MultiSelectComboBox;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.Location;
import com.vaadin.flow.router.NavigationTrigger;
import com.vaadin.flow.router.RouteParameters;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.inject.spi.CDI;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Browserless UI tests for {@link ImportClientsView} and {@link EditImportClientView} (UC-032:
 * Manage Import Clients).
 *
 * <p>The views are obtained through CDI and attached to the test {@link UI}; route navigation is
 * unavailable under {@code @QuarkusTest} (see {@link UsersViewTest}), so the edit form's
 * {@code beforeEnter} is driven directly, as {@link EditUserViewRoleAssignmentTest} does. The
 * services commit their own transactions, so each test works on names made unique by a token.</p>
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
@TestSecurity(user = "uc032.tester", roles = "elicit_admin")
class ImportClientViewsTest extends QuarkusBrowserlessTest {

    private static String token() {
        return Long.toString(System.nanoTime(), 36);
    }

    private EditImportClientView editView(long id) {
        EditImportClientView view = CDI.current().select(EditImportClientView.class).get();
        UI ui = UI.getCurrent();
        ui.add(view);
        BeforeEnterEvent event = new BeforeEnterEvent(ui.getInternals().getRouter(),
                NavigationTrigger.PROGRAMMATIC, new Location(""), EditImportClientView.class,
                new RouteParameters("id", String.valueOf(id)), ui, Collections.emptyList());
        view.beforeEnter(event);
        return view;
    }

    private TextField field(EditImportClientView view, String labelKey) {
        String label = UI.getCurrent().getTranslation(labelKey);
        return find(TextField.class, view).all().stream()
                .filter(f -> label.equals(f.getLabel()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No TextField labeled '" + label + "'"));
    }

    private Button button(EditImportClientView view, String textKey) {
        String text = UI.getCurrent().getTranslation(textKey);
        return find(Button.class, view).all().stream()
                .filter(b -> text.equals(b.getText()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No button '" + text + "'"));
    }

    /** Whether Delete is offered; a hidden button is not found by the browserless query at all. */
    private boolean offersDelete(EditImportClientView view) {
        String text = UI.getCurrent().getTranslation("editImportClientView.delete");
        return find(Button.class, view).all().stream().anyMatch(b -> text.equals(b.getText()));
    }

    @SuppressWarnings("unchecked")
    private MultiSelectComboBox<Department> departments(EditImportClientView view) {
        return find(MultiSelectComboBox.class, view).single();
    }

    /** UC-032 A5: both routes are the administrator's alone. */
    @Test
    void given_theTwoRoutes_when_theirRolesAreRead_then_onlyTheAdministratorIsAllowed() {
        assertArrayEquals(new String[]{ElicitRoles.ADMIN}, ImportClientsView.class.getAnnotation(RolesAllowed.class).value());
        assertArrayEquals(new String[]{ElicitRoles.ADMIN}, EditImportClientView.class.getAnnotation(RolesAllowed.class).value());
    }

    /** UC-032 step 1: the list shows each import client's name, description, active flag and departments. */
    @Test
    @SuppressWarnings("unchecked")
    void given_anImportClient_when_theListOpens_then_itIsShownWithItsColumns() {
        String token = token();
        Department department = DepartmentFixtures.department(token);
        DepartmentFixtures.importClient("service-account-list-" + token, true, department);

        ImportClientsView view = CDI.current().select(ImportClientsView.class).get();
        UI.getCurrent().add(view);

        Grid<ImportClient> grid = find(Grid.class, view).single();
        // Edit + Name + Description + Active + Departments
        assertEquals(5, grid.getColumns().size());
        assertTrue(grid.getGenericDataView().getItems()
                .anyMatch(client -> ("service-account-list-" + token).equals(client.getName())
                        && client.getDepartments().size() == 1));
        assertTrue(find(Paragraph.class, view).single().getText().contains("elicit_importer"));
        assertEquals(UI.getCurrent().getTranslation("importClientsView.pageTitle"), view.getPageTitle());
    }

    /** UC-032 steps 2-5, BR-167: a new import client is active by default and is stored with its departments. */
    @Test
    void given_theAddForm_when_filledAndSaved_then_theImportClientIsStoredActiveWithItsDepartments() {
        String token = token();
        Department department = DepartmentFixtures.department(token);
        EditImportClientView view = editView(0);

        assertTrue(find(Checkbox.class, view).single().getValue(), "BR-167: active by default");
        assertFalse(offersDelete(view), "nothing to delete yet");

        field(view, "editImportClientView.name").setValue("  service-account-add-" + token + " ");
        field(view, "editImportClientView.description").setValue("Nightly feed");
        departments(view).setValue(Set.of((Department) Department.findById(department.id)));
        button(view, "common.save").click();

        ImportClient stored = ImportClient.findByName("service-account-add-" + token);
        assertNotNull(stored, "the name is stored trimmed");
        assertTrue(stored.isActive());
        assertEquals("Nightly feed", stored.getDescription());
        assertEquals(1, stored.getDepartments().size());
    }

    /** UC-032 A3, BR-167: clearing the active flag deactivates the client and keeps its record. */
    @Test
    void given_anExistingImportClient_when_deactivatedAndSaved_then_theRecordIsKeptInactive() {
        String token = token();
        Department department = DepartmentFixtures.department(token);
        ImportClient client = DepartmentFixtures.importClient("service-account-off-" + token, true, department);
        EditImportClientView view = editView(client.getId());

        assertEquals("service-account-off-" + token, field(view, "editImportClientView.name").getValue());
        assertEquals(1, departments(view).getValue().size());
        find(Checkbox.class, view).single().setValue(false);
        button(view, "common.save").click();

        ImportClient stored = QuarkusTransaction.requiringNew().call(() -> ImportClient.findById(client.getId()));
        assertFalse(stored.isActive());
        assertEquals(1, stored.getDepartments().size(), "its departments are kept");
    }

    /** UC-032 A2, BR-166: a name a user holds is refused, nothing is stored and the form says why. */
    @Test
    void given_aNameAUserHolds_when_saved_then_itIsRefused() {
        String token = token();
        DepartmentFixtures.consoleRecord("uc032.person." + token, true);
        EditImportClientView view = editView(0);

        TextField name = field(view, "editImportClientView.name");
        name.setValue("uc032.person." + token);
        button(view, "common.save").click();

        assertNull(ImportClient.findByName("uc032.person." + token));
        assertTrue(name.isInvalid());
        assertEquals(UI.getCurrent().getTranslation("editImportClientView.error.nameIsUser"), name.getErrorMessage());
    }

    /** UC-032 A2, BR-165: a name another import client holds is refused. */
    @Test
    void given_aNameAnotherImportClientHolds_when_saved_then_itIsRefused() {
        String token = token();
        DepartmentFixtures.importClient("service-account-dup-" + token, true);
        EditImportClientView view = editView(0);

        TextField name = field(view, "editImportClientView.name");
        name.setValue("service-account-dup-" + token);
        button(view, "common.save").click();

        assertEquals(1, ImportClient.count("name = ?1", "service-account-dup-" + token));
        assertEquals(UI.getCurrent().getTranslation("editImportClientView.error.nameExists"), name.getErrorMessage());
    }

    /** UC-032: a blank name, or one over 100 characters, keeps Save from storing anything. */
    @Test
    void given_aBlankOrOverlongName_when_saved_then_nothingIsStored() {
        long before = ImportClient.count();
        EditImportClientView view = editView(0);

        view.save();
        field(view, "editImportClientView.name").setValue("x".repeat(101));
        view.save();

        assertEquals(before, ImportClient.count());
    }

    /** UC-032 A4, BR-169: Delete is offered for a stored client and removes it with its assignments. */
    @Test
    void given_anExistingImportClient_when_deleted_then_itIsGone() {
        String token = token();
        Department department = DepartmentFixtures.department(token);
        ImportClient client = DepartmentFixtures.importClient("service-account-del-" + token, true, department);
        EditImportClientView view = editView(client.getId());

        assertTrue(offersDelete(view));
        // The button opens the confirmation; confirming it calls delete().
        view.delete();

        assertNull(ImportClient.findByName("service-account-del-" + token));
        assertNotNull(Department.findById(department.id));
    }

    /** UC-032 A1: an id that no longer exists is reported and nothing is loaded. */
    @Test
    void given_anUnknownId_when_theFormOpens_then_noRecordIsLoaded() {
        EditImportClientView view = editView(Long.MAX_VALUE);

        assertEquals("", field(view, "editImportClientView.name").getValue());
        assertFalse(offersDelete(view));
    }

    /** UC-008 A2, BR-166: the Users screen refuses a username an import client holds. */
    @Test
    void given_aNameAnImportClientHolds_when_enteredAsAUsername_then_theUsersScreenRefusesIt() {
        String token = token();
        DepartmentFixtures.importClient("service-account-user-" + token, true);
        EditUserView view = CDI.current().select(EditUserView.class).get();
        UI ui = UI.getCurrent();
        ui.add(view);
        view.beforeEnter(new BeforeEnterEvent(ui.getInternals().getRouter(),
                NavigationTrigger.PROGRAMMATIC, new Location(""), EditUserView.class,
                new RouteParameters("id", "0"), ui, Collections.emptyList()));

        TextField username = find(TextField.class, view).all().stream()
                .filter(f -> ui.getTranslation("editUserView.username").equals(f.getLabel())).findFirst().orElseThrow();
        username.setValue("service-account-user-" + token);
        find(TextField.class, view).all().stream()
                .filter(f -> f != username).forEach(f -> f.setValue("Machine"));
        view.saveUser();

        assertEquals(0, User.count("username = ?1", "service-account-user-" + token));
        assertEquals(ui.getTranslation("editUserView.error.usernameIsImportClient"), username.getErrorMessage());
    }
}
