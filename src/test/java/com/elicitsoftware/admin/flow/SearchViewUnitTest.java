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
import com.elicitsoftware.model.Status;
import com.elicitsoftware.service.EmailService;
import com.elicitsoftware.service.ReportingService;
import com.elicitsoftware.service.StatusDataSource;
import com.elicitsoftware.service.StatusQuery;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.combobox.MultiSelectComboBox;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H5;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.provider.SortDirection;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.flow.server.VaadinSession;
import io.quarkus.panache.common.Sort;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UC-002: Search and Monitor Subject Progress (BR-005 department-scoped visibility, BR-006 status
 * labels, BR-007 single active row action, BR-008 text filter matching), with UC-004 and UC-005's
 * row actions and UC-019's empty state (BR-076 to BR-078) — {@link SearchView} built with
 * {@code new} in a plain browserless UI. The view's own {@link StatusDataSource}, which would run
 * Panache queries, is swapped for a recording stand-in, so the parameterized query the filters
 * build, the sort the grid asks for and the paging window can all be read back.
 */
class SearchViewUnitTest extends PlainAdminViewTestSupport {

    private static final int COL_STATUS = 8;
    private static final int COL_EDIT = 9;
    private static final int COL_ACTION = 10;

    private Login login;
    private Presence presence;
    private RecordingDataSource data;
    private final List<String> emailed = new ArrayList<>();
    private final List<String> printed = new ArrayList<>();
    private Boolean emailAnswer;
    private RuntimeException emailFailure;
    private RuntimeException reportFailure;
    private SearchView view;

    private final Department oncology = department(3, "Oncology");
    private final Department clinic = department(7, "Clinic");

    /**
     * Answers with fixed rows and records every query it is handed. The view hands it the
     * pagination window, not the grid's own range, so a tester asking for one row would get the
     * whole page back and be refused; {@link #only} plays the part of that one-row request.
     */
    static final class RecordingDataSource extends StatusDataSource {
        List<Status> rows = new ArrayList<>();
        Integer only;
        int total;
        final List<StatusQuery> counted = new ArrayList<>();
        final List<StatusQuery> fetched = new ArrayList<>();
        final List<int[]> windows = new ArrayList<>();

        @Override
        public List<Status> fetch(StatusQuery query, int offset, int limit) {
            fetched.add(query);
            windows.add(new int[] {offset, limit});
            if (only != null) {
                return List.of(rows.get(only));
            }
            return rows.subList(Math.min(offset, rows.size()), Math.min(offset + limit, rows.size()));
        }

        @Override
        public int count(StatusQuery query) {
            counted.add(query);
            return total;
        }
    }

    @BeforeEach
    void stubs() {
        login = new Login();
        presence = new Presence();
        data = new RecordingDataSource();
        emailAnswer = true;
        emailFailure = null;
        reportFailure = null;
    }

    @AfterEach
    void stopScheduler() {
        if (view != null) {
            ScheduledExecutorService scheduler = field(view, "scheduler");
            scheduler.shutdownNow();
        }
    }

    private SearchView build(String... roles) {
        view = new SearchView();
        view.uiSessionLogin = login(login);
        view.identity = identity("search.tester", roles);
        view.surveyPresence = presence(presence);
        view.emailService = new EmailService() {
            @Override
            public boolean sendEmail(Status status) {
                emailed.add(status.getAccessCode());
                if (emailFailure != null) {
                    throw emailFailure;
                }
                return emailAnswer;
            }
        };
        view.reportingService = new ReportingService() {
            @Override
            public void printReports(Status status) {
                printed.add(status.getAccessCode());
                if (reportFailure != null) {
                    throw reportFailure;
                }
            }
        };
        setField(view, "dataSource", data);
        view.init();
        UI.getCurrent().add(view);
        return view;
    }

    private SearchView buildWithRows(String... roles) {
        login.user = user("search.tester", oncology, clinic);
        data.rows = new ArrayList<>(List.of(status(1, "AAA111", "Finished"), status(2, "BBB222", "In Progress")));
        data.total = 2;
        return build(roles);
    }

    private static Status status(long id, String accessCode, String state) {
        Status status = new Status();
        status.setId(id);
        status.setRespondentId(100 + id);
        status.setAccessCode(accessCode);
        status.setFirstName("First" + id);
        status.setLastName("Last" + id);
        status.setStatus(state);
        return status;
    }

    @SuppressWarnings("unchecked")
    private MultiSelectComboBox<Department> departments() {
        return byId(view, "department-filter", MultiSelectComboBox.class);
    }

    private TextField filter(String id) {
        return byId(view, id, TextField.class);
    }

    @SuppressWarnings("unchecked")
    private Grid<Status> grid() {
        return byId(view, "subject-grid", Grid.class);
    }

    private void search() {
        test(byId(view, "search-button", Button.class)).click();
        data.counted.clear();
        test(grid()).size();
    }

    private StatusQuery lastCount() {
        assertFalse(data.counted.isEmpty(), "the grid must count through the data source");
        return data.counted.get(data.counted.size() - 1);
    }

    private HorizontalLayout actionCell(int row) {
        return assertInstanceOf(HorizontalLayout.class, cellComponent(row, COL_ACTION));
    }

    private String cellText(int row, int column) {
        data.only = row;
        try {
            return test(grid()).getCellText(row, column);
        } finally {
            data.only = null;
        }
    }

    /**
     * A cell's component, rendered for the row's item and attached to the UI so it can be
     * clicked. The grid's own rendered copy cannot be looked up: its columns carry no key.
     */
    private Component cellComponent(int row, int column) {
        Component cell = renderCell(row, column);
        UI.getCurrent().add(cell);
        return cell;
    }

    private Component renderCell(int row, int column) {
        data.only = row;
        try {
            return test(grid()).renderCellComponent(row, column);
        } finally {
            data.only = null;
        }
    }

    @SuppressWarnings("unchecked")
    private static ComboBox<String> actionBox(HorizontalLayout cell) {
        return (ComboBox<String>) cell.getComponentAt(0);
    }

    private static Button submit(HorizontalLayout cell) {
        assertEquals(2, cell.getComponentCount(), "a chosen action shows the submit button");
        return assertInstanceOf(Button.class, cell.getComponentAt(1));
    }

    // ---- no console record (UC-001 A1) -----------------------------------------------------------

    @Test
    void given_noConsoleRecord_when_init_then_theExplanationNamesThePrincipalAsText() {
        build("elicit_user");

        List<Paragraph> paragraphs = find(Paragraph.class, view).all();
        assertEquals(3, paragraphs.size());
        assertEquals(t("searchView.noUser.loggedIn"), paragraphs.get(0).getText());
        assertEquals(t("searchView.noUser.help"), paragraphs.get(2).getText());
        List<Span> spans = find(Span.class, paragraphs.get(1)).all();
        assertEquals(List.of(t("searchView.noUser.before"), "search.tester", t("searchView.noUser.after")),
                spans.stream().map(Span::getText).toList());
        assertTrue(spans.get(1).hasClassName("inline-name"));
        assertNull(grid(), "no grid without a console record");
        assertTrue(view.hasClassName("search-view"));
        assertEquals("100%", view.getWidth());
    }

    // ---- search bar (UC-002 steps 1 and 2) -------------------------------------------------------

    @Test
    void given_consoleUser_when_init_then_theSearchBarOffersEveryFilterWithAllDepartmentsChosen() {
        buildWithRows("elicit_user");

        assertEquals(t("searchView.heading"), find(H5.class, view).single().getText());
        assertEquals(t("searchView.filter.accessCode"), filter("access-code-filter").getLabel());
        assertEquals(t("searchView.filter.firstName"), filter("first-name-filter").getLabel());
        assertEquals(t("searchView.filter.lastName"), filter("last-name-filter").getLabel());
        assertEquals(t("searchView.filter.email"), filter("email-filter").getLabel());
        assertEquals(t("searchView.filter.phone"), filter("phone-filter").getLabel());
        assertEquals(t("common.search"), byId(view, "search-button", Button.class).getText());

        MultiSelectComboBox<Department> departments = departments();
        assertEquals(t("searchView.filter.departments"), departments.getLabel());
        assertTrue(departments.isRequiredIndicatorVisible());
        List<Department> items = departments.getListDataView().getItems().toList();
        assertEquals(3, items.size(), "All departments, then the user's own");
        assertEquals(-1, items.get(0).id);
        assertEquals(t("searchView.filter.allDepartments"), departments.getItemLabelGenerator().apply(items.get(0)));
        assertEquals("Oncology", departments.getItemLabelGenerator().apply(oncology));
        assertEquals(Set.of(items.get(0)), departments.getValue());
    }

    @Test
    void given_allDepartmentsAndBlankFilters_when_searching_then_theQueryCoversEveryOwnDepartmentOnly() {
        buildWithRows("elicit_user");
        filter("first-name-filter").setValue("   ");

        search();

        StatusQuery query = lastCount();
        assertEquals("departmentId in :departments", query.whereClause());
        assertEquals(Map.of("departments", List.of(3L, 7L)), query.params());
        assertNull(query.sort());
    }

    @Test
    void given_everyTextFilter_when_searching_then_eachIsBoundAsALowerCasedWildcardParameter() {
        buildWithRows("elicit_user");
        filter("access-code-filter").setValue("AbC");
        filter("first-name-filter").setValue("Ann");
        filter("last-name-filter").setValue("Lee");
        filter("email-filter").setValue("A@X.org");
        filter("phone-filter").setValue("555-1X");

        search();

        StatusQuery query = lastCount();
        assertEquals("departmentId in :departments"
                + " and lower(accessCode) like :accessCode"
                + " and lower(firstName) like :firstName"
                + " and lower(lastName) like :lastName"
                + " and lower(email) like :email"
                + " and phone like :phone", query.whereClause());
        assertEquals(Map.of("departments", List.of(3L, 7L), "accessCode", "%abc%", "firstName", "%ann%",
                "lastName", "%lee%", "email", "%a@x.org%", "phone", "%555-1X%"), query.params());
    }

    @Test
    void given_oneDepartmentChosen_when_searching_then_onlyItsIdIsBound() {
        buildWithRows("elicit_user");
        departments().setValue(Set.of(clinic));

        search();

        assertEquals(List.of(7L), lastCount().params().get("departments"));
    }

    @Test
    void given_noDepartmentChosen_when_searching_then_theUserIsToldAndNothingMatches() {
        buildWithRows("elicit_user");
        departments().setValue(Set.of());

        search();

        assertEquals(List.of(), lastCount().params().get("departments"));
        assertTrue(notifications().contains(t("searchView.error.selectDepartments")));
    }

    @Test
    void given_allDepartmentsChosen_when_addingADepartment_then_allIsDropped() {
        buildWithRows("elicit_user");
        Department all = departments().getValue().iterator().next();

        departments().setValue(Set.of(all, oncology));

        assertEquals(Set.of(oncology), departments().getValue());
    }

    @Test
    void given_aDepartmentChosen_when_addingAllDepartments_then_onlyAllRemains() {
        buildWithRows("elicit_user");
        Department all = departments().getValue().iterator().next();
        departments().setValue(Set.of(oncology));

        departments().setValue(Set.of(oncology, all));

        assertEquals(Set.of(all), departments().getValue());
    }

    @Test
    void given_twoDepartmentsChosen_when_droppingOne_then_theOtherStays() {
        buildWithRows("elicit_user");
        departments().setValue(Set.of(oncology, clinic));

        departments().setValue(Set.of(clinic));

        assertEquals(Set.of(clinic), departments().getValue());
    }

    @Test
    void given_aDepartmentChosen_when_choosingAllAlone_then_allIsKept() {
        buildWithRows("elicit_user");
        Department all = departments().getListDataView().getItems().findFirst().orElseThrow();
        departments().setValue(Set.of(oncology));
        departments().setValue(Set.of(all));

        assertEquals(Set.of(all), departments().getValue());
        search();
        assertEquals(List.of(3L, 7L), lastCount().params().get("departments"));
    }

    // ---- grid (UC-002 steps 3 and 4) -------------------------------------------------------------

    @Test
    void given_consoleUser_when_init_then_theGridHasTranslatedSortableColumns() {
        buildWithRows("elicit_user");

        Grid<Status> grid = grid();
        assertTrue(grid.isAllRowsVisible(), "the page scrolls, not the grid (#26)");
        assertEquals(List.of(Status.PROP_ACCESS_CODE, Status.PROP_DEPARTMENT_NAME, Status.PROP_FIRST_NAME,
                Status.PROP_MIDDLE_NAME, Status.PROP_LAST_NAME, Status.PROP_EMAIL, Status.PROP_PHONE,
                Status.PROP_CREATED_DT, Status.PROP_STATUS),
                grid.getColumns().subList(0, 9).stream()
                        .map(c -> c.getSortOrder(SortDirection.ASCENDING).findFirst().orElseThrow().getSorted()).toList());
        // The first header row carries the titles; the view appends a second, empty one.
        assertEquals(2, grid.getHeaderRows().size());
        List<String> headers = grid.getColumns().stream()
                .map(c -> grid.getHeaderRows().get(0).getCell(c).getText()).toList();
        assertEquals(List.of(t("searchView.grid.accessCode"), t("searchView.grid.department"), t("searchView.grid.firstName"),
                t("searchView.grid.middleName"), t("searchView.grid.lastName"), t("searchView.grid.email"),
                t("searchView.grid.phone"), t("searchView.grid.created"), t("searchView.grid.status"),
                t("common.edit"), t("searchView.grid.action")), headers);
        assertTrue(grid.isMultiSort());
    }

    @Test
    void given_rows_when_rendering_then_eachCellShowsItsStatusField() {
        buildWithRows("elicit_user");

        assertEquals(2, test(grid()).size());
        assertEquals("AAA111", cellText(0, 0));
        assertEquals("First1", cellText(0, 2));
        assertEquals("Last2", cellText(1, 4));
        int[] window = data.windows.get(data.windows.size() - 1);
        assertEquals(0, window[0]);
        assertEquals(10, window[1], "the default page size");
    }

    @Test
    void given_moreRowsThanAPage_when_counting_then_theGridIsToldOnlyAPageRemains() {
        login.user = user("search.tester", oncology);
        for (int i = 1; i <= 12; i++) {
            data.rows.add(status(i, "C" + i, "Not Started"));
        }
        data.total = 12;
        build("elicit_user");

        assertEquals(10, test(grid()).size(), "a page holds ten rows by default");
    }

    @Test
    void given_englishStatuses_when_rendering_then_unknownAndMissingOnesAreShownAsSent() {
        login.user = user("search.tester", oncology);
        data.rows = new ArrayList<>(List.of(status(1, "A", "Paused"), status(2, "B", null)));
        data.total = 2;
        build("elicit_user");

        assertEquals("Paused", cellText(0, COL_STATUS));
        assertEquals("", cellText(1, COL_STATUS));
    }

    @Test
    void given_spanishLocale_when_rendering_then_theThreeKnownStatusesAreTranslated() {
        UI.getCurrent().setLocale(Locale.forLanguageTag("es-419"));
        login.user = user("search.tester", oncology);
        data.rows = new ArrayList<>(List.of(status(1, "A", "Not Started"), status(2, "B", "In Progress"),
                status(3, "C", "Finished")));
        data.total = 3;
        build("elicit_user");

        assertEquals(spanish("searchView.status.notStarted"), cellText(0, COL_STATUS));
        assertEquals(spanish("searchView.status.inProgress"), cellText(1, COL_STATUS));
        assertEquals(spanish("searchView.status.finished"), cellText(2, COL_STATUS));
    }

    @Test
    void given_sortedColumns_when_fetching_then_theSortIsHandedToTheDataSourceInOrder() {
        buildWithRows("elicit_user");

        test(grid()).sortByColumn(4, SortDirection.DESCENDING);
        test(grid()).sortByColumn(0, SortDirection.ASCENDING);
        data.fetched.clear();
        data.only = 0;
        test(grid()).getRow(0);

        Sort sort = data.fetched.get(data.fetched.size() - 1).sort();
        assertNotNull(sort);
        assertEquals(List.of(Status.PROP_LAST_NAME, Status.PROP_ACCESS_CODE),
                sort.getColumns().stream().map(Sort.Column::getName).toList());
        assertEquals(List.of(Sort.Direction.Descending, Sort.Direction.Ascending),
                sort.getColumns().stream().map(Sort.Column::getDirection).toList());
    }

    @Test
    void given_noSort_when_fetching_then_theDataSourceGetsNone() {
        buildWithRows("elicit_user");

        data.fetched.clear();
        data.only = 0;
        test(grid()).getRow(0);

        assertNull(data.fetched.get(data.fetched.size() - 1).sort());
    }

    @Test
    void given_aRow_when_editing_then_theRegisterViewOpensWithItsAccessCode() {
        buildWithRows("elicit_user");

        Button edit = assertInstanceOf(Button.class, cellComponent(1, COL_EDIT));
        assertEquals(t("common.edit"), edit.getElement().getProperty("title"));
        test(edit).click();

        assertEquals("register", activePath());
        assertEquals(List.of("BBB222"), UI.getCurrent().getInternals().getActiveViewLocation()
                .getQueryParameters().getParameters().get("accessCode"));
    }

    // ---- row actions (UC-002 BR-007, UC-004, UC-005, UC-011) ------------------------------------

    @Test
    void given_finishedRowForAnAdministrator_when_rendering_then_everyActionIsOffered() {
        buildWithRows("elicit_admin");

        HorizontalLayout cell = actionCell(0);
        assertEquals(1, cell.getComponentCount(), "no submit button before an action is chosen");
        ComboBox<String> box = actionBox(cell);
        assertEquals(t("searchView.action.placeholder"), box.getPlaceholder());
        assertEquals(List.of(t("searchView.action.sendEmail"), t("searchView.action.printReports"), t("searchView.action.export")),
                test(box).getSuggestions());
    }

    @Test
    void given_buildActionOptions_when_statusAndRoleVary_then_onlyTheAllowedActionsAreListed() {
        buildWithRows("elicit_user");

        assertEquals(List.of(SearchView.ACTION_SEND_EMAIL, SearchView.ACTION_PRINT_REPORTS),
                view.buildActionOptions(status(9, "X", "Finished")));
        assertEquals(List.of(SearchView.ACTION_SEND_EMAIL), view.buildActionOptions(status(9, "X", "In Progress")));
        assertEquals("/api/secured/respondent/export?id=109", view.buildExportUrl(status(9, "X", "Finished")));
    }

    @Test
    void given_anActionChosen_when_clearingIt_then_theSubmitButtonGoesAndNoRowIsActive() {
        buildWithRows("elicit_user");
        HorizontalLayout cell = actionCell(0);
        ComboBox<String> box = actionBox(cell);

        box.setValue(SearchView.ACTION_SEND_EMAIL);
        assertEquals(t("searchView.action.btnSubmit"), submit(cell).getText());
        assertEquals("AAA111", field(view, "activeActionAccessCode"));
        assertEquals(SearchView.ACTION_SEND_EMAIL, field(view, "activeActionValue"));

        box.setValue(SearchView.ACTION_SEND_EMAIL);
        assertEquals(2, cell.getComponentCount(), "choosing again does not add a second button");

        box.clear();
        assertEquals(1, cell.getComponentCount());
        assertNull(field(view, "activeActionAccessCode"));
        assertNull(field(view, "activeActionValue"));
    }

    @Test
    void given_oneRowActive_when_choosingOnAnother_then_onlyTheSecondStaysActive() {
        buildWithRows("elicit_user");
        HorizontalLayout first = actionCell(0);
        HorizontalLayout second = actionCell(1);
        actionBox(first).setValue(SearchView.ACTION_SEND_EMAIL);

        actionBox(second).setValue(SearchView.ACTION_SEND_EMAIL);

        assertEquals("BBB222", field(view, "activeActionAccessCode"));

        actionBox(first).clear();
        assertEquals("BBB222", field(view, "activeActionAccessCode"), "clearing another row leaves the active one alone");
        assertEquals(SearchView.ACTION_SEND_EMAIL, field(view, "activeActionValue"));
    }

    @Test
    void given_theActiveRow_when_itIsRenderedAgain_then_itKeepsItsChoiceAndSubmitButton() {
        buildWithRows("elicit_user");
        actionBox(actionCell(1)).setValue(SearchView.ACTION_PRINT_REPORTS);

        HorizontalLayout again = assertInstanceOf(HorizontalLayout.class, renderCell(1, COL_ACTION));
        HorizontalLayout other = assertInstanceOf(HorizontalLayout.class, renderCell(0, COL_ACTION));

        assertEquals(SearchView.ACTION_PRINT_REPORTS, actionBox(again).getValue());
        assertEquals(2, again.getComponentCount());
        assertNull(actionBox(other).getValue());
        assertEquals(1, other.getComponentCount());
    }

    @Test
    void given_sendEmailChosen_when_submitting_then_theEmailIsSentAndTheRowIsReset() {
        buildWithRows("elicit_user");
        HorizontalLayout cell = actionCell(0);
        actionBox(cell).setValue(SearchView.ACTION_SEND_EMAIL);

        test(submit(cell)).click();

        assertEquals(List.of("AAA111"), emailed);
        assertEquals(List.of(t("searchView.action.emailSent")), notifications());
        assertNull(actionBox(cell).getValue());
        assertEquals(1, cell.getComponentCount());
        assertNull(field(view, "activeActionAccessCode"));
    }

    @Test
    void given_theMailServerRefuses_when_submitting_then_theFailureIsReported() {
        emailAnswer = false;
        buildWithRows("elicit_user");
        HorizontalLayout cell = actionCell(0);
        actionBox(cell).setValue(SearchView.ACTION_SEND_EMAIL);

        test(submit(cell)).click();

        assertEquals(List.of(t("searchView.action.emailFailed")), notifications());
    }

    @Test
    void given_sendingThrows_when_submitting_then_theErrorIsReportedWithItsMessage() {
        emailFailure = new IllegalStateException("smtp down");
        buildWithRows("elicit_user");
        HorizontalLayout cell = actionCell(0);
        actionBox(cell).setValue(SearchView.ACTION_SEND_EMAIL);

        test(submit(cell)).click();

        assertEquals(List.of(t("searchView.action.emailError", "smtp down")), notifications());
    }

    @Test
    void given_printReportsChosen_when_submitting_then_theReportsAreGenerated() {
        buildWithRows("elicit_user");
        HorizontalLayout cell = actionCell(0);
        actionBox(cell).setValue(SearchView.ACTION_PRINT_REPORTS);

        test(submit(cell)).click();

        assertEquals(List.of("AAA111"), printed);
        assertTrue(emailed.isEmpty());
        assertEquals(List.of(t("searchView.action.reportsGenerated")), notifications());
    }

    @Test
    void given_reportGenerationThrows_when_submitting_then_theErrorIsReported() {
        reportFailure = new IllegalStateException("no report");
        buildWithRows("elicit_user");
        HorizontalLayout cell = actionCell(0);
        actionBox(cell).setValue(SearchView.ACTION_PRINT_REPORTS);

        test(submit(cell)).click();

        assertEquals(List.of(t("searchView.action.reportsError", "no report")), notifications());
    }

    @Test
    void given_exportChosen_when_submitting_then_theExportOpensInANewWindow() {
        buildWithRows("elicit_admin");
        HorizontalLayout cell = actionCell(1);
        actionBox(cell).setValue(SearchView.ACTION_EXPORT);
        UI.getCurrent().getInternals().dumpPendingJavaScriptInvocations();

        test(submit(cell)).click();

        assertTrue(UI.getCurrent().getInternals().dumpPendingJavaScriptInvocations().stream()
                .anyMatch(js -> js.getInvocation().getExpression().contains("window.open")
                        && js.getInvocation().getParameters().contains("/api/secured/respondent/export?id=102")));
        assertEquals(List.of(t("searchView.action.exportDownloading")), notifications());
        assertTrue(emailed.isEmpty());
        assertTrue(printed.isEmpty());
        assertNull(actionBox(cell).getValue());
    }

    @Test
    void given_noActionChosen_when_submitting_then_nothingHappens() {
        buildWithRows("elicit_user");
        HorizontalLayout cell = actionCell(0);
        actionBox(cell).setValue(SearchView.ACTION_SEND_EMAIL);
        Button submit = submit(cell);
        actionBox(cell).clear();
        // Put the button back as if it had been clicked before the layout caught up.
        cell.add(submit);

        test(submit).click();

        assertTrue(emailed.isEmpty());
        assertTrue(notifications().isEmpty());
        assertEquals(2, cell.getComponentCount(), "an empty choice leaves the row as it is");
    }

    @Test
    void given_otherRowActive_when_choosingOnAnother_then_theGridIsRefreshedOnlyForTheSwitch() {
        buildWithRows("elicit_user");
        HorizontalLayout first = actionCell(0);
        HorizontalLayout second = actionCell(1);
        int[] refreshes = {0};
        grid().getDataProvider().addDataProviderListener(e -> refreshes[0]++);

        actionBox(first).setValue(SearchView.ACTION_SEND_EMAIL);
        actionBox(first).setValue(SearchView.ACTION_PRINT_REPORTS);
        assertEquals(0, refreshes[0], "re-choosing on the active row needs no refresh");

        actionBox(second).setValue(SearchView.ACTION_SEND_EMAIL);
        assertEquals(1, refreshes[0], "the previously active row is redrawn without its choice (BR-007)");
    }

    @Test
    void given_anotherRowBecameActive_when_submittingTheFirst_then_theActiveRowIsKept() {
        buildWithRows("elicit_user");
        HorizontalLayout first = actionCell(0);
        HorizontalLayout second = actionCell(1);
        actionBox(first).setValue(SearchView.ACTION_SEND_EMAIL);
        Button firstSubmit = submit(first);
        actionBox(second).setValue(SearchView.ACTION_PRINT_REPORTS);

        test(firstSubmit).click();

        assertEquals(List.of("AAA111"), emailed);
        assertEquals("BBB222", field(view, "activeActionAccessCode"));
        assertEquals(SearchView.ACTION_PRINT_REPORTS, field(view, "activeActionValue"));
    }

    @Test
    void given_morePagesThanOne_when_turningThePage_then_theNextWindowIsFetchedAndSearchingStartsOver() {
        login.user = user("search.tester", oncology);
        for (int i = 1; i <= 12; i++) {
            data.rows.add(status(i, "C" + i, "Not Started"));
        }
        data.total = 12;
        build("elicit_user");
        assertEquals(10, test(grid()).size());
        int[] refreshes = {0};
        grid().getDataProvider().addDataProviderListener(e -> refreshes[0]++);
        Button next = find(Button.class, view).all().stream()
                .filter(b -> t("paginationControls.nextPage").equals(b.getAriaLabel().orElse(null))).findFirst().orElseThrow();

        test(next).click();

        assertTrue(refreshes[0] >= 1, "a page change refreshes the grid");
        assertEquals(2, test(grid()).size(), "only two rows remain on the second page");
        data.only = 0;
        test(grid()).getRow(0);
        data.only = null;
        assertEquals(10, data.windows.get(data.windows.size() - 1)[0], "the second page starts at row ten");

        test(byId(view, "search-button", Button.class)).click();
        assertEquals(10, test(grid()).size(), "a new search starts at the first page");
    }

    @Test
    void given_consoleUser_when_init_then_theLayoutIsCompactAndAligned() {
        buildWithRows("elicit_user");

        HorizontalLayout bar = assertInstanceOf(HorizontalLayout.class, departments().getParent().orElseThrow());
        assertTrue(bar.isPadding());
        assertEquals(FlexComponent.JustifyContentMode.CENTER, bar.getJustifyContentMode());
        assertEquals("100%", bar.getWidth());
        assertTrue(bar.isSpacing());
        assertEquals(FlexComponent.Alignment.BASELINE, bar.getAlignItems());
        for (String id : List.of("access-code-filter", "first-name-filter", "last-name-filter", "email-filter", "phone-filter")) {
            assertTrue(filter(id).getThemeNames().contains("small"), id);
        }
        assertTrue(departments().getThemeNames().contains("small"));
        assertTrue(byId(view, "search-button", Button.class).getThemeNames().contains("primary"));

        Grid<Status> grid = grid();
        assertEquals("100%", grid.getWidth());
        assertTrue(grid.getThemeNames().containsAll(List.of("row-stripes", "compact")));
        VerticalLayout gridAndPaging = assertInstanceOf(VerticalLayout.class, grid.getParent().orElseThrow());
        assertFalse(gridAndPaging.isPadding());
        assertFalse(gridAndPaging.isSpacing());
        assertTrue(gridAndPaging.getThemeList().contains("spacing-xs"));
        assertInstanceOf(PaginationControls.class, gridAndPaging.getComponentAt(1));
        VerticalLayout respondents = assertInstanceOf(VerticalLayout.class, gridAndPaging.getParent().orElseThrow());
        assertFalse(respondents.isPadding());
        assertFalse(respondents.isSpacing());
        assertEquals("100%", respondents.getWidth());

        Button edit = assertInstanceOf(Button.class, cellComponent(0, COL_EDIT));
        assertTrue(edit.getThemeNames().containsAll(List.of("icon", "tertiary")));
        HorizontalLayout cell = actionCell(0);
        assertTrue(cell.isSpacing());
        assertEquals(FlexComponent.Alignment.CENTER, cell.getAlignItems());
        ComboBox<String> box = actionBox(cell);
        assertEquals("120px", box.getWidth());
        assertTrue(box.getThemeNames().contains("small"));
        box.setValue(SearchView.ACTION_SEND_EMAIL);
        assertTrue(submit(cell).getThemeNames().containsAll(List.of("primary", "small")));
    }

    // ---- refresh, title, missing survey ----------------------------------------------------------

    @Test
    void given_thePaginationPageChanges_when_notified_then_theGridIsRefreshed() {
        buildWithRows("elicit_user");
        int[] refreshes = {0};
        grid().getDataProvider().addDataProviderListener(e -> refreshes[0]++);

        test(byId(view, "search-button", Button.class)).click();

        assertTrue(refreshes[0] >= 1, "searching refreshes the data provider");
    }

    @Test
    void given_theSchedulerTicks_when_itsTaskRuns_then_theGridIsRefreshed() throws Exception {
        buildWithRows("elicit_user");
        int[] refreshes = {0};
        grid().getDataProvider().addDataProviderListener(e -> refreshes[0]++);
        VaadinSession session = VaadinSession.getCurrent();
        long deadline = System.nanoTime() + 2_000_000_000L;
        while (session.getPendingAccessQueue().isEmpty() && System.nanoTime() < deadline) {
            Thread.sleep(5);
        }

        session.getService().runPendingAccessTasks(session);

        assertTrue(refreshes[0] >= 1, "the scheduled task refreshes the grid through ui.access");
    }

    @Test
    void given_theView_when_detached_then_theRefreshTaskIsCancelledAndTheSchedulerStopped() {
        buildWithRows("elicit_user");
        ScheduledFuture<?> task = field(view, "refreshTask");
        ScheduledExecutorService scheduler = field(view, "scheduler");

        UI.getCurrent().remove(view);

        assertTrue(task.isCancelled());
        assertTrue(scheduler.isShutdown());
    }

    @Test
    void given_noConsoleRecord_when_detached_then_theSchedulerIsStoppedAll_the_same() {
        build("elicit_user");
        ScheduledExecutorService scheduler = field(view, "scheduler");
        assertNull(field(view, "refreshTask"));

        UI.getCurrent().remove(view);

        assertTrue(scheduler.isShutdown());
    }

    @Test
    void given_theView_when_askedForItsTitle_then_theTranslatedTitleIsGiven() {
        buildWithRows("elicit_user");

        assertEquals(t("searchView.pageTitle"), view.getPageTitle());
    }

    @Test
    void given_noSurveyInstalled_when_entering_then_theEmptyStateLeadsTheViewForAnAdministrator() {
        presence.installed = false;
        buildWithRows("elicit_admin");

        view.beforeEnter(null);

        Component first = view.getComponentAt(0);
        assertEquals(MissingSurveyNotice.EMPTY_STATE_ID, first.getId().orElseThrow());
        assertEquals(1, find(RouterLink.class, first).all().size(), "an administrator can apply a definition (BR-078)");
        assertNotNull(grid(), "the view stays usable (BR-077)");
    }

    @Test
    void given_noSurveyInstalled_when_enteringAsAUser_then_theEmptyStateOffersNoLink() {
        presence.installed = false;
        buildWithRows("elicit_user");

        view.refreshMissingSurveyNotice();

        Component first = view.getComponentAt(0);
        assertEquals(MissingSurveyNotice.EMPTY_STATE_ID, first.getId().orElseThrow());
        assertTrue(find(RouterLink.class, first).all().isEmpty());
    }

    @Test
    void given_theEmptyStateShown_when_aSurveyIsInstalled_then_theNextEntryRemovesIt() {
        presence.installed = false;
        buildWithRows("elicit_user");
        view.refreshMissingSurveyNotice();
        int withNotice = view.getComponentCount();

        presence.installed = true;
        view.refreshMissingSurveyNotice();

        assertEquals(withNotice - 1, view.getComponentCount());
        assertTrue(view.getChildren().noneMatch(c -> c.getId().filter(MissingSurveyNotice.EMPTY_STATE_ID::equals).isPresent()));
    }

    @Test
    void given_noSurveyInstalled_when_enteringTwice_then_theEmptyStateIsShownOnce() {
        presence.installed = false;
        buildWithRows("elicit_user");

        view.refreshMissingSurveyNotice();
        view.refreshMissingSurveyNotice();

        assertEquals(1, view.getChildren()
                .filter(c -> c.getId().filter(MissingSurveyNotice.EMPTY_STATE_ID::equals).isPresent()).count());
    }

    @Test
    void given_aSurveyInstalled_when_entering_then_noEmptyStateIsShown() {
        buildWithRows("elicit_user");
        int before = view.getComponentCount();

        view.beforeEnter(null);

        assertEquals(before, view.getComponentCount());
        assertSame(H5.class, view.getComponentAt(0).getClass());
    }
}
