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
import com.elicitsoftware.model.Survey;
import com.elicitsoftware.participation.Participation;
import com.elicitsoftware.participation.Participation.Card;
import com.elicitsoftware.participation.Participation.Counts;
import com.elicitsoftware.participation.Participation.DepartmentCounts;
import com.elicitsoftware.participation.Participation.Kind;
import com.elicitsoftware.participation.Participation.OptionCount;
import com.elicitsoftware.participation.Participation.QuestionStats;
import com.elicitsoftware.participation.Participation.Week;
import com.elicitsoftware.participation.ParticipationExport;
import com.elicitsoftware.participation.ParticipationService;
import com.elicitsoftware.participation.ParticipationTables;
import com.elicitsoftware.participation.ParticipationTables.Section;
import com.elicitsoftware.participation.ParticipationTables.Table;
import com.elicitsoftware.participation.SmallCell;
import com.elicitsoftware.report.PDFService;
import com.elicitsoftware.report.ReportResponse;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Svg;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.combobox.MultiSelectComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.H5;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.server.streams.DownloadResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UC-031: Monitor a Survey's Participation (BR-120 the user's departments, BR-122 small-cell
 * suppression, BR-126 the exports are the page, BR-127 live and logged, BR-129 charts beside
 * their tables) — {@link ParticipationView} built with {@code new} in a plain browserless UI.
 * <p>
 * {@code init()} lists the installed surveys with a Panache query once a user is signed in, so
 * past the no-user notice the page is assembled here from the same private builders
 * ({@code filterBar}, {@code downloads}) that {@code init()} calls, followed by the package-private
 * {@code refresh()}; the figures come from a stand-in {@link ParticipationService}.
 */
class ParticipationViewUnitTest extends PlainAdminViewTestSupport {

    private Login login;
    private final List<ParticipationService.Filter> filters = new ArrayList<>();
    private final List<Map<Long, String>> names = new ArrayList<>();
    private final List<String> pdfCalls = new ArrayList<>();
    private Participation answer;
    private int port;
    private ParticipationView view;

    private final Department oncology = department(3, "Oncology");
    private final Department clinic = department(7, "Clinic");
    private final Department unnamed = department(9, null);
    private Survey census;
    private Survey family;

    @BeforeEach
    void stubs() {
        login = new Login();
        answer = sample();
        port = 8443;
        census = survey(11, "Census, 2026");
        family = survey(12, null);
    }

    private static Survey survey(int id, String name) {
        Survey survey = new Survey();
        survey.id = id;
        survey.name = name;
        survey.surveyKey = UUID.nameUUIDFromBytes(("survey" + id).getBytes());
        return survey;
    }

    private static Participation sample() {
        QuestionStats smoker = new QuestionStats("Do you smoke?", Kind.CHOICE, 12, 12, 11,
                List.of(new OptionCount("Yes", 3, true), new OptionCount("No", 8, true)), null, null);
        Participation.Progress progress = new Participation.Progress(List.of(
                new Participation.StepProgress(1, "Household", 12, 6, new Participation.Durations(12, 300L, 420L, 900L))),
                3, 0, new Participation.Durations(12, 1800L, 5400L, 90000L), 0);
        return new Participation(new Counts(40, 10, 18, 12, 8),
                List.of(new DepartmentCounts("Oncology", new Counts(37, 9, 16, 12, 6)),
                        new DepartmentCounts("Clinic", new Counts(3, 1, 2, 0, 2))),
                List.of(new Week(LocalDate.of(2026, 1, 5), 40, 30, 12)),
                List.of(new Card("Smoker", List.of(smoker))), progress, 14);
    }

    private ParticipationView newView() {
        view = new ParticipationView();
        view.uiSessionLogin = login(login);
        view.identity = identity("participation.viewer", "elicit_user");
        view.smallCell = 5;
        view.participationService = new ParticipationService() {
            @Override
            public Participation compute(Filter filter, Map<Long, String> departmentNames) {
                filters.add(filter);
                names.add(new LinkedHashMap<>(departmentNames));
                return answer;
            }
        };
        view.pdfService = new PDFService() {
            @Override
            public byte[] generatePDF(ArrayList<ReportResponse> reportResponses, String baseUrl, String headerTitle) {
                pdfCalls.add(reportResponses.size() + "|" + baseUrl + "|" + headerTitle);
                return new byte[] {1, 2, 3, 4};
            }
        };
        view.httpRequest = request();
        return view;
    }

    private HttpServletRequest request() {
        return (HttpServletRequest) Proxy.newProxyInstance(HttpServletRequest.class.getClassLoader(),
                new Class<?>[] {HttpServletRequest.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getServerPort" -> port;
                    case "getScheme" -> "https";
                    case "getServerName" -> "elicit.example.org";
                    case "getContextPath" -> "/admin";
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    /** What {@code init()} builds once it has the surveys, from the same builders. */
    private ParticipationView page(List<Survey> surveys, Department... departments) {
        newView();
        view.user = user("participation.viewer", departments);
        Component bar = invoke(view, "filterBar", new Class<?>[] {List.class}, surveys);
        Component downloads = invoke(view, "downloads", new Class<?>[0]);
        view.add(bar, downloads, view.results);
        UI.getCurrent().add(view);
        view.refresh();
        return view;
    }

    private ParticipationService.Filter lastFilter() {
        return filters.get(filters.size() - 1);
    }

    private Department all() {
        return view.departmentPicker.getListDataView().getItems().findFirst().orElseThrow();
    }

    // ---- page shell ------------------------------------------------------------------------------

    @Test
    void given_noConsoleRecord_when_init_then_theTitleIntroAndExplanationAreShown() {
        newView();

        view.init();

        List<Component> children = view.getChildren().toList();
        assertEquals(4, children.size());
        assertEquals(t("participationView.title"), assertInstanceOf(H3.class, children.get(0)).getText());
        assertEquals(t("participationView.intro"), assertInstanceOf(Paragraph.class, children.get(1)).getText());
        assertEquals(t("searchView.noUser.loggedIn"), assertInstanceOf(Paragraph.class, children.get(2)).getText());
        assertEquals(t("searchView.noUser.help"), assertInstanceOf(Paragraph.class, children.get(3)).getText());
        assertTrue(filters.isEmpty());
    }

    @Test
    void given_theView_when_constructed_then_itIsFullWidthAndTitled() {
        newView();

        assertEquals("100%", view.getWidth());
        assertTrue(view.hasClassName("participation-view"));
        assertEquals(t("participationView.title"), view.getPageTitle());
    }

    @Test
    void given_noFilterBarYet_when_refreshing_then_nothingIsComputed() {
        newView();

        view.refresh();

        assertTrue(filters.isEmpty());
        assertNull(view.shown);
    }

    // ---- filter bar (UC-031 steps 2 and 3) -------------------------------------------------------

    @Test
    void given_surveysAndDepartments_when_theFilterBarIsBuilt_then_itDefaultsToTheFirstSurveyAndAllDepartments() {
        page(List.of(census, family), oncology, clinic, unnamed);

        ComboBox<Survey> surveys = byId(view, ParticipationView.SURVEY_ID, ComboBox.class);
        assertEquals(t("participationView.filter.survey"), surveys.getLabel());
        assertEquals(census, surveys.getValue());
        assertFalse(surveys.isAllowCustomValue());
        assertEquals("Census, 2026", surveys.getItemLabelGenerator().apply(census));
        assertEquals(family.surveyKey.toString(), surveys.getItemLabelGenerator().apply(family),
                "a survey without a name is listed by its key");

        MultiSelectComboBox<Department> departments = byId(view, ParticipationView.DEPARTMENTS_ID, MultiSelectComboBox.class);
        assertEquals(t("participationView.filter.departments"), departments.getLabel());
        List<Department> items = departments.getListDataView().getItems().toList();
        assertEquals(List.of(-1L, 9L, 7L, 3L), items.stream().map(d -> d.id).toList(),
                "All departments, then the user's by name (an unnamed one first)");
        assertEquals(t("participationView.filter.allDepartments"), departments.getItemLabelGenerator().apply(items.get(0)));
        assertEquals(Set.of(items.get(0)), departments.getValue());

        DatePicker from = byId(view, ParticipationView.FROM_ID, DatePicker.class);
        DatePicker to = byId(view, ParticipationView.TO_ID, DatePicker.class);
        assertEquals(t("participationView.filter.from"), from.getLabel());
        assertEquals(t("participationView.filter.to"), to.getLabel());
        assertTrue(from.isClearButtonVisible());
        assertTrue(to.isClearButtonVisible());

        HorizontalLayout bar = assertInstanceOf(HorizontalLayout.class, surveys.getParent().orElseThrow());
        assertTrue(bar.isWrap());
        assertEquals(FlexComponent.Alignment.BASELINE, bar.getDefaultVerticalComponentAlignment());
    }

    @Test
    void given_thePage_when_built_then_theCsvAndPdfDownloadsAreOffered() {
        page(List.of(census), oncology);

        Anchor csv = byId(view, ParticipationView.CSV_ID, Anchor.class);
        Anchor pdf = byId(view, ParticipationView.PDF_ID, Anchor.class);
        assertEquals(t("participationView.download.csv"), find(Button.class, csv).single().getText());
        assertEquals(t("participationView.download.pdf"), find(Button.class, pdf).single().getText());
        assertTrue(find(Button.class, csv).single().getThemeNames().contains("small"));
        assertNotNull(csv.getHref());
    }

    @Test
    void given_allDepartments_when_refreshing_then_everyOwnDepartmentIsCounted() {
        page(List.of(census), oncology, clinic);

        assertEquals(1, filters.size(), "the page computes once when drawn");
        ParticipationService.Filter filter = lastFilter();
        assertEquals(11, filter.surveyId());
        assertEquals(List.of(7L, 3L), List.copyOf(filter.departmentIds()));
        assertNull(filter.from());
        assertNull(filter.to());
        assertEquals(Map.of(7L, "Clinic", 3L, "Oncology"), names.get(0));
        assertEquals(List.of(7L, 3L), List.copyOf(names.get(0).keySet()), "in the order the table lists them");

        ParticipationView.Shown shown = view.shown;
        assertEquals(census.surveyKey.toString(), shown.surveyKey());
        assertEquals("[7, 3]", shown.departments());
        assertEquals("participation.viewer", shown.user());
        assertEquals(new SmallCell(5, Locale.ENGLISH), shown.cells());
        assertEquals(new ParticipationExport.Heading("Census, 2026", t("participationView.filter.allDepartments"), null, null),
                shown.heading());
    }

    @Test
    void given_oneDepartmentChosen_when_refreshing_then_onlyItIsCountedAndNamed() {
        page(List.of(census), oncology, clinic);

        view.departmentPicker.setValue(Set.of(oncology));

        assertEquals(List.of(3L), List.copyOf(lastFilter().departmentIds()));
        assertEquals("Oncology", view.shown.heading().departments());
        assertEquals("[3]", view.shown.departments());
    }

    @Test
    void given_twoDepartmentsChosen_when_refreshing_then_theHeadingJoinsTheirNames() {
        page(List.of(census), oncology, clinic);

        view.departmentPicker.setValue(Set.of(oncology, clinic));

        assertEquals("Clinic, Oncology", view.shown.heading().departments());
        assertEquals(List.of(7L, 3L), view.chosenDepartments().stream().map(d -> d.id).toList());
    }

    @Test
    void given_aDepartmentOutsideTheUsersOwn_when_choosing_then_itIsNotCounted() {
        page(List.of(census), oncology, clinic);

        view.departmentPicker.setValue(Set.of(oncology, department(99, "Elsewhere")));

        assertEquals(List.of(3L), view.chosenDepartments().stream().map(d -> d.id).toList(), "BR-120");
    }

    @Test
    void given_allDepartmentsChosen_when_addingOne_then_allIsDropped() {
        page(List.of(census), oncology, clinic);

        view.departmentPicker.setValue(Set.of(all(), clinic));

        assertEquals(Set.of(clinic), view.departmentPicker.getValue());
        assertEquals(List.of(7L), List.copyOf(lastFilter().departmentIds()));
    }

    @Test
    void given_aDepartmentChosen_when_addingAll_then_onlyAllRemains() {
        page(List.of(census), oncology, clinic);
        view.departmentPicker.setValue(Set.of(clinic));

        view.departmentPicker.setValue(Set.of(clinic, all()));

        assertEquals(Set.of(all()), view.departmentPicker.getValue());
        assertEquals(List.of(7L, 3L), List.copyOf(lastFilter().departmentIds()));
    }

    @Test
    void given_aDepartmentChosen_when_clearingTheChoice_then_allIsRestored() {
        page(List.of(census), oncology, clinic);
        view.departmentPicker.setValue(Set.of(clinic));
        int before = filters.size();

        view.departmentPicker.clear();

        assertEquals(Set.of(all()), view.departmentPicker.getValue());
        assertEquals(before + 1, filters.size(), "the restored value refreshes once");
        assertEquals(List.of(7L, 3L), List.copyOf(lastFilter().departmentIds()));
    }

    @Test
    void given_anEmptyChoiceReadDirectly_when_choosingDepartments_then_allOfTheUsersAreCounted() {
        page(List.of(census), oncology, clinic);
        view.departmentPicker = new MultiSelectComboBox<>();

        assertEquals(List.of(7L, 3L), view.chosenDepartments().stream().map(d -> d.id).toList());
    }

    @Test
    void given_anotherSurvey_when_picked_then_itIsComputed() {
        page(List.of(census, family), oncology);

        view.surveyPicker.setValue(family);

        assertEquals(12, lastFilter().surveyId());
        assertEquals(2, filters.size());
    }

    @Test
    void given_theSurveyCleared_when_picked_then_nothingIsComputed() {
        page(List.of(census, family), oncology);

        view.surveyPicker.clear();

        assertEquals(1, filters.size());
    }

    @Test
    void given_aDateRange_when_picked_then_itIsHandedToTheService() {
        page(List.of(census), oncology);

        view.fromPicker.setValue(LocalDate.of(2026, 1, 1));
        view.toPicker.setValue(LocalDate.of(2026, 1, 1));

        assertEquals(3, filters.size(), "each date refreshes");
        assertEquals(LocalDate.of(2026, 1, 1), lastFilter().from());
        assertEquals(LocalDate.of(2026, 1, 1), lastFilter().to());
        assertFalse(view.toPicker.isInvalid(), "a one-day range is valid");
        assertTrue(view.toPicker.getErrorMessage() == null || view.toPicker.getErrorMessage().isEmpty());
        assertEquals(LocalDate.of(2026, 1, 1), view.shown.heading().from());
        assertEquals(LocalDate.of(2026, 1, 1), view.shown.heading().to());
    }

    @Test
    void given_anEndBeforeTheStart_when_picked_then_theRangeIsRefusedAndNothingIsComputed() {
        page(List.of(census), oncology);
        view.fromPicker.setValue(LocalDate.of(2026, 3, 1));
        int before = filters.size();

        view.toPicker.setValue(LocalDate.of(2026, 2, 28));

        assertFalse(view.rangeValid());
        assertTrue(view.toPicker.isInvalid());
        assertEquals(t("participationView.filter.rangeInvalid"), view.toPicker.getErrorMessage());
        assertEquals(before, filters.size());

        view.toPicker.setValue(LocalDate.of(2026, 3, 2));
        assertTrue(view.rangeValid());
        assertFalse(view.toPicker.isInvalid());
    }

    @Test
    void given_onlyOneEnd_when_checkingTheRange_then_itIsValid() {
        page(List.of(census), oncology);

        view.toPicker.setValue(LocalDate.of(2020, 1, 1));
        assertTrue(view.rangeValid());
        view.toPicker.clear();
        view.fromPicker.setValue(LocalDate.of(2030, 1, 1));
        assertTrue(view.rangeValid());
    }

    @Test
    void given_theSmallCellSetting_when_buildingCells_then_itAndTheUiLocaleAreUsed() {
        newView();
        view.smallCell = 11;

        assertEquals(new SmallCell(11, Locale.ENGLISH), view.cells());
    }

    // ---- figures (UC-031 steps 4 to 7) -----------------------------------------------------------

    @Test
    void given_figures_when_drawn_then_everySectionTableAndChartIsRendered() {
        page(List.of(census), oncology, clinic);

        List<Section> expected = new ParticipationTables(new SmallCell(5, Locale.ENGLISH)).sections(answer);
        List<Div> blocks = view.results.getChildren().map(Div.class::cast).toList();
        assertEquals(expected.size(), blocks.size());
        for (int i = 0; i < expected.size(); i++) {
            Section section = expected.get(i);
            Div block = blocks.get(i);
            List<Component> parts = block.getChildren().toList();
            int at = 0;
            if (section.card()) {
                assertTrue(block.hasClassName("participation-card"));
                assertEquals(section.title(), assertInstanceOf(H5.class, parts.get(at++)).getText());
            } else {
                assertTrue(block.hasClassName("participation-section"));
                assertEquals(section.title(), assertInstanceOf(H4.class, parts.get(at++)).getText());
            }
            if (section.note() != null) {
                assertEquals(section.note(), assertInstanceOf(Paragraph.class, parts.get(at++)).getText());
            }
            for (Table table : section.tables()) {
                if (table.title() != null) {
                    Paragraph caption = assertInstanceOf(Paragraph.class, parts.get(at++));
                    assertEquals(table.title(), caption.getText());
                    assertTrue(caption.hasClassName("participation-caption"));
                }
                if (table.chart() != null) {
                    Svg chart = assertInstanceOf(Svg.class, parts.get(at++));
                    assertTrue(chart.hasClassName("participation-chart-box"));
                }
                Grid<?> grid = assertInstanceOf(Grid.class, parts.get(at++));
                assertGrid(table, grid);
            }
            assertEquals(parts.size(), at, "nothing else in section " + section.title());
        }
        assertTrue(expected.stream().anyMatch(Section::card), "the sample has a question card");
        assertTrue(expected.stream().anyMatch(s -> s.note() != null), "the sample has a note");
        assertTrue(expected.stream().flatMap(s -> s.tables().stream()).anyMatch(t -> t.chart() != null),
                "the sample has a chart");
    }

    @SuppressWarnings("unchecked")
    private void assertGrid(Table table, Grid<?> raw) {
        Grid<List<String>> grid = (Grid<List<String>>) raw;
        assertTrue(grid.isAllRowsVisible());
        assertTrue(grid.hasClassName("participation-grid"));
        assertTrue(grid.getThemeNames().containsAll(List.of("compact", "row-stripes")));
        assertEquals(table.headers().size(), grid.getColumns().size());
        for (int c = 0; c < table.headers().size(); c++) {
            assertEquals(table.headers().get(c), grid.getHeaderRows().get(0).getCell(grid.getColumns().get(c)).getText());
            assertEquals(c == 0 ? 1 : 0, grid.getColumns().get(c).getFlexGrow());
            assertTrue(grid.getColumns().get(c).isAutoWidth());
        }
        assertEquals(table.rows().size(), test(grid).size());
        for (int r = 0; r < table.rows().size(); r++) {
            for (int c = 0; c < table.headers().size(); c++) {
                List<String> row = table.rows().get(r);
                assertEquals(c < row.size() ? row.get(c) : "", test(grid).getCellText(r, c));
            }
        }
    }

    @Test
    void given_aNewFilter_when_drawn_then_thePreviousFiguresAreReplaced() {
        page(List.of(census), oncology, clinic);
        long blocks = view.results.getChildren().count();

        view.surveyPicker.setValue(survey(13, "Other"));

        assertEquals(blocks, view.results.getChildren().count());
    }

    // ---- downloads (BR-126, BR-127) --------------------------------------------------------------

    @Test
    void given_nothingShown_when_downloading_then_bothDownloadsAnswerNotFound() {
        newView();

        DownloadResponse csv = view.csvDownload();
        DownloadResponse pdf = view.pdfDownload();

        assertTrue(csv.hasError());
        assertEquals(404, csv.getError());
        assertEquals(404, pdf.getError());
        assertTrue(pdfCalls.isEmpty());
    }

    @Test
    void given_figuresShown_when_downloadingTheCsv_then_itHoldsExactlyThePagesTables() throws IOException {
        page(List.of(census), oncology, clinic);
        ParticipationView.Shown shown = view.shown;
        LocalDate before = LocalDate.now();

        DownloadResponse response = view.csvDownload();

        byte[] expected = ParticipationExport.csv(shown.participation(), shown.cells(), shown.heading());
        byte[] actual = response.getInputStream().readAllBytes();
        assertArrayEquals(expected, actual);
        assertEquals(expected.length, response.getContentLength());
        assertEquals("text/csv", response.getContentType());
        assertTrue(List.of(ParticipationExport.fileName("Census, 2026", before, "csv"),
                        ParticipationExport.fileName("Census, 2026", LocalDate.now(), "csv"))
                .contains(response.getFileName()), response.getFileName());
        assertFalse(response.hasError());
    }

    @Test
    void given_figuresShown_when_downloadingThePdf_then_theServiceRendersOnePartWithTheSurveyAsHeader() throws IOException {
        page(List.of(census), oncology);
        LocalDate before = LocalDate.now();

        DownloadResponse response = view.pdfDownload();

        assertEquals(List.of("1|https://elicit.example.org:8443/admin|" + ParticipationExport.drawableText("Census, 2026")),
                pdfCalls);
        assertArrayEquals(new byte[] {1, 2, 3, 4}, response.getInputStream().readAllBytes());
        assertEquals(4, response.getContentLength());
        assertEquals("application/pdf", response.getContentType());
        assertTrue(List.of(ParticipationExport.fileName("Census, 2026", before, "pdf"),
                        ParticipationExport.fileName("Census, 2026", LocalDate.now(), "pdf"))
                .contains(response.getFileName()), response.getFileName());
    }

    @Test
    void given_theStandardHttpsPort_when_downloadingThePdf_then_theBaseUrlOmitsIt() {
        port = 443;
        page(List.of(census), oncology);

        view.pdfDownload();

        assertEquals("1|https://elicit.example.org/admin|" + ParticipationExport.drawableText("Census, 2026"), pdfCalls.get(0));
    }

    @Test
    void given_theStandardHttpPort_when_downloadingThePdf_then_theBaseUrlOmitsIt() {
        port = 80;
        page(List.of(census), oncology);

        view.pdfDownload();

        assertTrue(pdfCalls.get(0).startsWith("1|https://elicit.example.org/admin|"), pdfCalls.get(0));
    }

    // ---- audit log (BR-127) ----------------------------------------------------------------------

    /** Collects what the view logs, as text with its parameters, while a test runs. */
    private List<String> captureLog(Runnable action) {
        List<String> lines = new ArrayList<>();
        java.util.logging.Logger logger = java.util.logging.Logger.getLogger(ParticipationView.class.getName());
        java.util.logging.Handler handler = new java.util.logging.Handler() {
            @Override
            public void publish(java.util.logging.LogRecord record) {
                lines.add(record.getMessage() + " " + java.util.Arrays.toString(record.getParameters()));
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        logger.addHandler(handler);
        try {
            action.run();
        } finally {
            logger.removeHandler(handler);
        }
        return lines;
    }

    @Test
    void given_aFilter_when_viewed_then_whoLookedAtWhatIsLogged() {
        List<String> lines = captureLog(() -> page(List.of(census), oncology));

        assertEquals(1, lines.size(), lines.toString());
        assertTrue(lines.get(0).contains("Participation viewed by"), lines.get(0));
        assertTrue(lines.get(0).contains("participation.viewer"), lines.get(0));
        assertTrue(lines.get(0).contains(census.surveyKey.toString()), lines.get(0));
    }

    @Test
    void given_figuresShown_when_exported_then_eachExportIsLoggedWithItsFormat() {
        page(List.of(census), oncology);

        List<String> csv = captureLog(view::csvDownload);
        List<String> pdf = captureLog(view::pdfDownload);

        assertEquals(1, csv.size(), csv.toString());
        assertTrue(csv.get(0).contains("CSV") && csv.get(0).contains("exported by") && csv.get(0).contains("participation.viewer"),
                csv.get(0));
        assertEquals(1, pdf.size(), pdf.toString());
        assertTrue(pdf.get(0).contains("PDF") && pdf.get(0).contains("participation.viewer"), pdf.get(0));
    }
}
