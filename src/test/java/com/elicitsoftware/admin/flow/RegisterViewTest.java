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
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.ListItem;
import com.vaadin.flow.component.html.UnorderedList;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.server.VaadinSession;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.enterprise.inject.spi.CDI;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.elicitsoftware.admin.flow.ResultDialogSupport.detailsIn;
import static com.elicitsoftware.admin.flow.ResultDialogSupport.errorsIn;
import static com.elicitsoftware.admin.flow.ResultDialogSupport.headingsIn;
import static com.elicitsoftware.admin.flow.ResultDialogSupport.messagesIn;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
 * <p>{@code RegisterView} injects {@code UiSessionLogin}/{@code AccessCodeService}/{@code
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

    /**
     * Persists a department the CSV rows can be imported into. The import hard-codes survey 1,
     * which {@code CsvImportServiceTest} relies on too.
     */
    private Department persistDepartment(String token) {
        Department department = new Department();
        department.name = "Register CSV Dept " + token;
        department.code = "RCSV-" + token;
        // "1" need not resolve to a real MessageTemplate row: Message.createMessagesForSubject
        // just logs and skips an unresolvable template id rather than failing the import.
        department.defaultMessageId = "1";
        department.fromEmail = "register-csv@example.org";
        department.persist();
        return department;
    }

    /**
     * UC-003 A6 (#85): a CSV import reports one line per subject rather than one run-on
     * paragraph. The summary used to be {@code AddResponse.toString()} in a single {@code Span}
     * relying on {@code LumoUtility.Whitespace.PRE_WRAP}, which is inert in Admin, so every line
     * collapsed into the next and the administrator read a Java debug dump.
     */
    @Test
    @TestSecurity(user = "register.tester", roles = {"elicit_user"})
    @TestTransaction
    void csvImportSummaryIsOneLinePerSubject() {
        Department dept = persistDepartment("SUCCESS");
        String csv = String.join("\n",
                dept.id + ",Alice,Anderson,,1990-01-01,alice@example.org,555-010-0100,RV-XID-1",
                dept.id + ",Bob,Baker,,1985-02-15,bob@example.org,555-010-0101,RV-XID-2");

        view.handleCsvUpload(csv.getBytes(StandardCharsets.UTF_8));

        Dialog dialog = find(Dialog.class).single();
        assertEquals("CSV Import Success", dialog.getHeaderTitle());
        assertEquals(List.of("Successfully imported 2 subjects:"), headingsIn(dialog));

        List<String> details = detailsIn(dialog);
        assertEquals(2, details.size(), "expected one list item per imported subject: " + details);
        assertTrue(details.stream().anyMatch(line -> line.contains("Alice Anderson")), details.toString());
        assertTrue(details.stream().anyMatch(line -> line.contains("RV-XID-2")), details.toString());
        assertTrue(details.stream().allMatch(line -> line.startsWith("New Subject")), details.toString());
    }

    /**
     * UC-003 A6 (#85): nothing in the summary is a pre-formatted blob any more — no element
     * carries an embedded newline, and the {@code AddResponse} debug dump is gone.
     */
    @Test
    @TestSecurity(user = "register.tester", roles = {"elicit_user"})
    @TestTransaction
    void csvImportSummaryCarriesNoPreformattedText() {
        Department dept = persistDepartment("NOBLOB");
        String csv = dept.id + ",Carla,Cruz,,1992-03-04,carla@example.org,555-010-0102,RV-XID-3";

        view.handleCsvUpload(csv.getBytes(StandardCharsets.UTF_8));

        Dialog dialog = find(Dialog.class).single();
        assertEquals(List.of("Successfully imported 1 subject:"), headingsIn(dialog));

        List<String> lines = new java.util.ArrayList<>(messagesIn(dialog));
        lines.addAll(headingsIn(dialog));
        lines.addAll(detailsIn(dialog));
        assertFalse(lines.isEmpty());
        assertTrue(lines.stream().noneMatch(line -> line.contains("\n")),
                "no element may carry an embedded newline: " + lines);
        assertTrue(lines.stream().noneMatch(line -> line.contains("AddResponse")),
                "the Java debug dump must not reach the administrator: " + lines);

        // The whole dialog, not just the summary: no Span is left holding the old blob.
        assertTrue(find(Span.class, dialog).all().stream()
                        .noneMatch(span -> span.getText() != null && span.getText().contains("\n")),
                "no Span may carry an embedded newline");
    }

    /**
     * UC-003 A6 (#85): each rejected row is its own list item, named by its line number, rather
     * than the aggregated message run together on one line.
     */
    @Test
    @TestSecurity(user = "register.tester", roles = {"elicit_user"})
    @TestTransaction
    void csvImportErrorsAreOneItemPerRejectedLine() {
        Department dept = persistDepartment("ERRORS");
        String csv = String.join("\n",
                dept.id + ",NoLastName,,,1991-05-05,nolast@example.org,555-010-0104,RV-XID-4",
                dept.id + ",NoEmail,Nemo,,1991-05-05,,555-010-0105,RV-XID-5");

        view.handleCsvUpload(csv.getBytes(StandardCharsets.UTF_8));

        Dialog dialog = find(Dialog.class).single();
        assertEquals("CSV Import Error", dialog.getHeaderTitle());
        assertEquals(List.of("Import completed with errors:"), headingsIn(dialog));

        List<String> errors = errorsIn(dialog);
        assertEquals(2, errors.size(), "expected one list item per rejected line: " + errors);
        assertTrue(errors.get(0).startsWith("Line 1: "), errors.toString());
        assertTrue(errors.get(1).startsWith("Line 2: "), errors.toString());
        assertTrue(errors.stream().noneMatch(line -> line.contains("\n")), errors.toString());
    }

    /** UC-003 (#85): the bold CSV column name is styled by a real class, not an inert Lumo one. */
    @Test
    @TestSecurity(user = "register.tester", roles = {"elicit_user"})
    void csvColumnNamesUseTheInlineNameClass() {
        assertEquals(EXPECTED_COLUMNS.size(),
                find(Span.class, view).withClassName("inline-name").all().size(),
                "each documented CSV column name should carry the inline-name class");
    }
}
