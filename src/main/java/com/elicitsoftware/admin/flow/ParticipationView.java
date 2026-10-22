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
import com.elicitsoftware.model.User;
import com.elicitsoftware.participation.Participation;
import com.elicitsoftware.participation.ParticipationExport;
import com.elicitsoftware.participation.ParticipationService;
import com.elicitsoftware.participation.ParticipationTables;
import com.elicitsoftware.participation.ParticipationTables.Section;
import com.elicitsoftware.participation.ParticipationTables.Table;
import com.elicitsoftware.participation.SmallCell;
import com.elicitsoftware.report.PDFService;
import com.elicitsoftware.report.ReportResponse;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.combobox.MultiSelectComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.H5;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.streams.DownloadHandler;
import com.vaadin.flow.server.streams.DownloadResponse;
import io.quarkus.logging.Log;
import io.quarkus.panache.common.Sort;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The Participation page (UC-031): for one survey and the user's departments, the participation
 * counts and rates, the same per department and per week, and one card per question the survey's
 * author marked for the administrator report, with a CSV and a PDF of the same tables.
 * <p>
 * Every figure is computed live by {@link ParticipationService} and rendered through
 * {@link ParticipationTables}, the one place counts are suppressed (BR-122), so the page and the
 * two downloads cannot disagree.
 */
@Route(value = "participation", layout = MainLayout.class)
@RolesAllowed({"elicit_user", "elicit_admin"})
public class ParticipationView extends VerticalLayout implements HasDynamicTitle {

    /** Element ids, for tests and page objects. */
    static final String SURVEY_ID = "participation-survey";
    static final String DEPARTMENTS_ID = "participation-departments";
    static final String FROM_ID = "participation-from";
    static final String TO_ID = "participation-to";
    static final String RESULTS_ID = "participation-results";
    static final String CSV_ID = "participation-csv";
    static final String PDF_ID = "participation-pdf";
    static final String EMPTY_NOTICE_ID = "participation-empty";

    /** The id of the "All departments" entry, as SearchView marks it. */
    private static final long ALL_DEPARTMENTS = -1;

    @Inject
    ParticipationService participationService;

    @Inject
    PDFService pdfService;

    @Inject
    HttpServletRequest httpRequest;

    @Inject
    UiSessionLogin uiSessionLogin;

    @Inject
    SecurityIdentity identity;

    /** BR-122: the smallest count shown as itself. */
    @ConfigProperty(name = "elicit.participation.small-cell", defaultValue = "5")
    int smallCell;

    User user;
    ComboBox<Survey> surveyPicker;
    MultiSelectComboBox<Department> departmentPicker;
    DatePicker fromPicker;
    DatePicker toPicker;
    final Div results = new Div();
    private Department allDepartments;

    /**
     * What is on screen, which the downloads export. It is captured when the page is drawn because
     * a download is served by a request of its own, outside the UI's.
     */
    record Shown(Participation participation, SmallCell cells, ParticipationExport.Heading heading,
                 String surveyKey, String departments, String user) {
    }

    Shown shown;

    /**
     * Default constructor for Vaadin route instantiation; the content is built in {@link #init()}
     * once the services are injected.
     */
    public ParticipationView() {
        setWidthFull();
        addClassName("participation-view");
    }

    @Override
    public String getPageTitle() {
        return getTranslation("participationView.title");
    }

    /**
     * Builds the page for the signed-in user.
     */
    @PostConstruct
    void init() {
        add(new H3(getTranslation("participationView.title")));
        add(new Paragraph(getTranslation("participationView.intro")));

        user = uiSessionLogin.getUser();
        if (user == null) {
            add(new Paragraph(getTranslation("searchView.noUser.loggedIn")));
            add(new Paragraph(getTranslation("searchView.noUser.help")));
            return;
        }
        List<Survey> surveys = Survey.findAll(Sort.by("displayOrder")).list();
        if (surveys.isEmpty()) {
            Paragraph empty = new Paragraph(getTranslation("participationView.empty"));
            empty.setId(EMPTY_NOTICE_ID);
            add(empty);
            return;
        }
        add(filterBar(surveys), downloads());
        results.setId(RESULTS_ID);
        results.setWidthFull();
        add(results);
        refresh();
    }

    private Component filterBar(List<Survey> surveys) {
        surveyPicker = new ComboBox<>(getTranslation("participationView.filter.survey"));
        surveyPicker.setId(SURVEY_ID);
        surveyPicker.setItems(surveys);
        surveyPicker.setItemLabelGenerator(s -> s.name == null ? String.valueOf(s.surveyKey) : s.name);
        surveyPicker.setValue(surveys.get(0));
        surveyPicker.setAllowCustomValue(false);
        surveyPicker.addValueChangeListener(e -> {
            if (e.getValue() != null) {
                refresh();
            }
        });

        allDepartments = new Department();
        allDepartments.id = ALL_DEPARTMENTS;
        allDepartments.name = getTranslation("participationView.filter.allDepartments");
        List<Department> departments = new ArrayList<>();
        departments.add(allDepartments);
        departments.addAll(userDepartments());
        departmentPicker = new MultiSelectComboBox<>(getTranslation("participationView.filter.departments"));
        departmentPicker.setId(DEPARTMENTS_ID);
        departmentPicker.setItems(departments);
        departmentPicker.setItemLabelGenerator(Department::getName);
        departmentPicker.setValue(Set.of(allDepartments));
        departmentPicker.addValueChangeListener(e -> {
            // Each correction sets a new value, whose own event lands in the last branch.
            Set<Department> chosen = e.getValue();
            if (chosen.isEmpty()) {
                departmentPicker.setValue(Set.of(allDepartments));
            } else if (chosen.size() > 1 && chosen.contains(allDepartments)) {
                boolean allAdded = !e.getOldValue().contains(allDepartments);
                departmentPicker.setValue(allAdded ? Set.of(allDepartments)
                        : chosen.stream().filter(d -> d.id != ALL_DEPARTMENTS).collect(Collectors.toSet()));
            } else {
                refresh();
            }
        });

        fromPicker = new DatePicker(getTranslation("participationView.filter.from"));
        fromPicker.setId(FROM_ID);
        fromPicker.setClearButtonVisible(true);
        toPicker = new DatePicker(getTranslation("participationView.filter.to"));
        toPicker.setId(TO_ID);
        toPicker.setClearButtonVisible(true);
        fromPicker.addValueChangeListener(e -> refresh());
        toPicker.addValueChangeListener(e -> refresh());

        HorizontalLayout bar = new HorizontalLayout(surveyPicker, departmentPicker, fromPicker, toPicker);
        bar.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.BASELINE);
        bar.setWrap(true);
        return bar;
    }

    private Component downloads() {
        Anchor csv = download(CSV_ID, "participationView.download.csv",
                DownloadHandler.fromInputStream(event -> csvDownload()));
        Anchor pdf = download(PDF_ID, "participationView.download.pdf",
                DownloadHandler.fromInputStream(event -> pdfDownload()));
        return new HorizontalLayout(csv, pdf);
    }

    private Anchor download(String id, String labelKey, DownloadHandler handler) {
        Button button = new Button(getTranslation(labelKey), VaadinIcon.DOWNLOAD.create());
        button.addThemeVariants(ButtonVariant.LUMO_SMALL);
        Anchor anchor = new Anchor(handler, "");
        anchor.setId(id);
        anchor.add(button);
        return anchor;
    }

    /** The user's departments, by name (BR-120). */
    List<Department> userDepartments() {
        return user.getDepartments().stream().sorted(Comparator.comparing(d -> d.name == null ? "" : d.name)).toList();
    }

    /** The departments the filter counts: the chosen ones, or all of the user's (BR-120). */
    List<Department> chosenDepartments() {
        Set<Department> chosen = departmentPicker.getValue();
        if (chosen.isEmpty() || chosen.contains(allDepartments)) {
            return userDepartments();
        }
        Set<Long> mine = userDepartments().stream().map(d -> d.id).collect(Collectors.toSet());
        return userDepartments().stream().filter(d -> mine.contains(d.id) && chosen.stream().anyMatch(c -> c.id == d.id)).toList();
    }

    boolean rangeValid() {
        LocalDate from = fromPicker.getValue();
        LocalDate to = toPicker.getValue();
        return from == null || to == null || !to.isBefore(from);
    }

    /** Recomputes and redraws the figures for the current filter (UC-031 steps 3 to 7). */
    void refresh() {
        if (surveyPicker == null || surveyPicker.getValue() == null) {
            return;
        }
        boolean valid = rangeValid();
        toPicker.setInvalid(!valid);
        toPicker.setErrorMessage(valid ? null : getTranslation("participationView.filter.rangeInvalid"));
        if (!valid) {
            return;
        }
        Participation participation = participationService.compute(filter(), departmentNames());
        shown = new Shown(participation, cells(), heading(), String.valueOf(surveyPicker.getValue().surveyKey),
                departmentNames().keySet().toString(), identity.getPrincipal().getName());
        Log.infof("Participation viewed by %s: survey %s, departments %s, registered %s to %s (UC-031 BR-127)",
                shown.user(), shown.surveyKey(), shown.departments(), fromPicker.getValue(), toPicker.getValue());
        render(new ParticipationTables(shown.cells()).sections(participation));
    }

    ParticipationService.Filter filter() {
        return new ParticipationService.Filter(surveyPicker.getValue().id,
                chosenDepartments().stream().map(d -> d.id).toList(), fromPicker.getValue(), toPicker.getValue());
    }

    private Map<Long, String> departmentNames() {
        Map<Long, String> names = new LinkedHashMap<>();
        for (Department d : chosenDepartments()) {
            names.put(d.id, d.name);
        }
        return names;
    }

    SmallCell cells() {
        return new SmallCell(smallCell, getLocale());
    }

    private void render(List<Section> sections) {
        results.removeAll();
        for (Section section : sections) {
            Div block = new Div();
            block.addClassName(section.card() ? "participation-card" : "participation-section");
            block.add(section.card() ? new H5(section.title()) : new H4(section.title()));
            if (section.note() != null) {
                block.add(new Paragraph(section.note()));
            }
            for (Table table : section.tables()) {
                if (table.title() != null) {
                    Paragraph caption = new Paragraph(table.title());
                    caption.addClassName("participation-caption");
                    block.add(caption);
                }
                block.add(grid(table));
            }
            results.add(block);
        }
    }

    private Grid<List<String>> grid(Table table) {
        Grid<List<String>> grid = new Grid<>();
        for (int i = 0; i < table.headers().size(); i++) {
            int column = i;
            grid.addColumn(row -> column < row.size() ? row.get(column) : "")
                    .setHeader(table.headers().get(i)).setAutoWidth(true).setFlexGrow(column == 0 ? 1 : 0);
        }
        grid.setItems(table.rows());
        grid.setAllRowsVisible(true);
        grid.addThemeVariants(GridVariant.LUMO_COMPACT, GridVariant.LUMO_ROW_STRIPES);
        grid.addClassName("participation-grid");
        return grid;
    }

    private ParticipationExport.Heading heading() {
        String departments = departmentPicker.getValue().contains(allDepartments)
                ? getTranslation("participationView.filter.allDepartments")
                : chosenDepartments().stream().map(d -> d.name).collect(Collectors.joining(", "));
        return new ParticipationExport.Heading(surveyPicker.getValue().name, departments, fromPicker.getValue(), toPicker.getValue());
    }

    /**
     * The CSV of the figures on screen (BR-126). Package-private so tests can call it without a
     * browser request, as {@link SurveyDefinitionExportView#download(Integer)} is.
     */
    DownloadResponse csvDownload() {
        if (shown == null) {
            return DownloadResponse.error(404, "Nothing to export"); // i18n:ignore (HTTP error body)
        }
        Shown s = shown;
        byte[] bytes = ParticipationExport.csv(s.participation(), s.cells(), s.heading());
        logExport("CSV", s);
        return new DownloadResponse(new ByteArrayInputStream(bytes),
                ParticipationExport.fileName(s.heading().surveyName(), LocalDate.now(), "csv"), "text/csv", bytes.length);
    }

    /** The PDF of the figures on screen (BR-126). */
    DownloadResponse pdfDownload() {
        if (shown == null) {
            return DownloadResponse.error(404, "Nothing to export"); // i18n:ignore (HTTP error body)
        }
        Shown s = shown;
        ArrayList<ReportResponse> parts = new ArrayList<>();
        parts.add(ParticipationExport.pdf(s.participation(), s.cells(), s.heading()));
        String header = ParticipationExport.drawableText(s.heading().surveyName() == null ? "" : s.heading().surveyName());
        byte[] bytes = pdfService.generatePDF(parts, baseUrl(), header);
        logExport("PDF", s);
        return new DownloadResponse(new ByteArrayInputStream(bytes),
                ParticipationExport.fileName(s.heading().surveyName(), LocalDate.now(), "pdf"), "application/pdf", bytes.length);
    }

    private String baseUrl() {
        int port = httpRequest.getServerPort();
        return httpRequest.getScheme() + "://" + httpRequest.getServerName()
                + (port != 80 && port != 443 ? ":" + port : "") + httpRequest.getContextPath();
    }

    private static void logExport(String format, Shown s) {
        Log.infof("Participation %s exported by %s: survey %s, departments %s, registered %s to %s (UC-031 BR-127)",
                format, s.user(), s.surveyKey(), s.departments(), s.heading().from(), s.heading().to());
    }
}
