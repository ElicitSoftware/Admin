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
import com.elicitsoftware.test.PostgresTestResource;
import com.vaadin.browserless.quarkus.QuarkusBrowserlessTest;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.html.ListItem;
import com.vaadin.flow.component.html.UnorderedList;
import com.vaadin.flow.server.VaadinSession;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.enterprise.inject.spi.CDI;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Browserless UI test for {@link RegisterView}'s CSV documentation.
 *
 * <p>Traceability: UC-003 (Register a Subject). Code-review finding #8 replaced a raw
 * {@code innerHTML} assignment describing the CSV upload columns with real Vaadin HTML
 * components ({@link UnorderedList}/{@link ListItem}). This test guards that the column
 * documentation still renders — as a component tree, not injected markup — with one list item
 * per documented CSV column, so the untrusted-markup regression cannot silently return.</p>
 *
 * <p>{@code RegisterView} injects {@code UiSessionLogin}/{@code TokenService}/{@code
 * SecurityIdentity} and reads the authenticated user from the Vaadin session, so the test seeds
 * a transient {@link User} (with one department) into the session and obtains the view through
 * CDI, then attaches it to the test {@link UI}. Route navigation is unavailable under
 * {@code @QuarkusTest} (see the project's browserless-testing notes).</p>
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class RegisterViewTest extends QuarkusBrowserlessTest {

    /** The CSV columns documented in {@code RegisterView.columnDescriptionsList()}. */
    private static final List<String> EXPECTED_COLUMNS = List.of(
            "departmentId", "firstName", "lastName", "middleName",
            "dob", "email", "phone", "xid");

    private RegisterView view;

    @BeforeEach
    @TestSecurity(user = "register.tester", roles = {"elicit_user"})
    void setUp() {
        User user = new User();
        user.setId(1);
        user.setUsername("register.tester");
        user.setActive(true);
        Department department = new Department();
        department.id = 1;
        department.name = "Register Dept";
        Set<Department> departments = new HashSet<>();
        departments.add(department);
        user.setDepartments(departments);
        VaadinSession.getCurrent().setAttribute("user", user);

        view = CDI.current().select(RegisterView.class).get();
        UI.getCurrent().add(view);
    }

    /** UC-003 (#8): the CSV column documentation renders as a real UnorderedList. */
    @Test
    @TestSecurity(user = "register.tester", roles = {"elicit_user"})
    void csvColumnDocsRenderAsComponentList() {
        List<UnorderedList> lists = find(UnorderedList.class, view).all();
        assertTrue(lists.stream().anyMatch(ul -> find(ListItem.class, ul).all().size() == EXPECTED_COLUMNS.size()),
                "expected a UnorderedList with one ListItem per documented CSV column");
    }

    /**
     * UC-003 (#8): every documented CSV column name appears in the list item text, and the raw
     * markup that {@code innerHTML} would have produced (e.g. {@code <li>}, {@code <strong>}) is
     * NOT present as literal text — proving the content is a component tree, not injected HTML.
     */
    @Test
    @TestSecurity(user = "register.tester", roles = {"elicit_user"})
    void csvColumnItemsUseTextNotMarkup() {
        UnorderedList columnList = find(UnorderedList.class, view).all().stream()
                .filter(ul -> find(ListItem.class, ul).all().size() == EXPECTED_COLUMNS.size())
                .findFirst()
                .orElseThrow(() -> new AssertionError("CSV column UnorderedList not found"));

        List<ListItem> items = find(ListItem.class, columnList).all();
        assertEquals(EXPECTED_COLUMNS.size(), items.size());

        for (int i = 0; i < EXPECTED_COLUMNS.size(); i++) {
            // Text lives in child Span components, so read the whole subtree's text.
            String text = items.get(i).getElement().getTextRecursively();
            assertTrue(text.contains(EXPECTED_COLUMNS.get(i)),
                    "list item " + i + " should mention column '" + EXPECTED_COLUMNS.get(i) + "' but was: " + text);
            assertTrue(!text.contains("<li>") && !text.contains("<strong>"),
                    "list item text must not contain raw HTML markup: " + text);
        }
    }
}
