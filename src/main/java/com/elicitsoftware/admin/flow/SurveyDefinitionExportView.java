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

import com.elicitsoftware.model.Survey;
import com.elicitsoftware.service.SurveyDefinitionExportService;
import com.elicitsoftware.service.SurveyLogService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.streams.DownloadHandler;
import com.vaadin.flow.server.streams.DownloadResponse;
import io.quarkus.logging.Log;
import io.quarkus.panache.common.Sort;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * Admin-only view for downloading an installed survey definition as a portable {@code .elicit}
 * file — UC-013 through the console rather than the integration endpoint.
 * <p>
 * The view lists every survey installed in this deployment with its stable key and the revision
 * last applied here, so an administrator can see which instrument they are about to export
 * before clicking Download. Each download is produced on demand by
 * {@link SurveyDefinitionExportService}, so it is always the definition as it stands at the moment
 * of the click, stamped with a fresh revision (BR-072).
 * <p>
 * The download runs through Vaadin's own stream handler rather than by opening the REST endpoint
 * in a new tab, so it needs nothing beyond the console session the administrator already has.
 */
@Route(value = "survey-export", layout = MainLayout.class)
@RolesAllowed("elicit_admin")
public class SurveyDefinitionExportView extends VerticalLayout {

    /** Element id of the grid listing the installed surveys, for tests and page objects. */
    static final String GRID_ID = "survey-export-grid";

    /** Element id of the notice shown instead of the grid when no survey is installed. */
    static final String EMPTY_NOTICE_ID = "survey-export-empty";

    /** CSS class carried by every row's download anchor, for tests and page objects. */
    static final String DOWNLOAD_CLASS = "survey-export-download";

    /** The extension of a portable survey definition file, shared with the Author tool. */
    static final String FILE_EXTENSION = ".elicit";

    private static final String CONTENT_TYPE = "application/octet-stream";
    private static final String REVISION_HEADER = "# survey_revision: "; // i18n:ignore (file format header)
    private static final DateTimeFormatter FILE_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm");
    private static final DateTimeFormatter DISPLAY_STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'");

    @Inject
    SurveyDefinitionExportService exportService;

    @Inject
    SurveyLogService surveyLogService;

    /**
     * Default constructor for Vaadin route instantiation; the content is built in
     * {@link #init()} once the services are injected.
     */
    public SurveyDefinitionExportView() {
        setSizeFull();
    }

    /**
     * Builds the page from the surveys installed in this deployment.
     */
    @PostConstruct
    void init() {
        render(Survey.findAll(Sort.by("displayOrder")).list());
    }

    /**
     * Builds the page: heading, explanation, and the grid of {@code surveys} -- or a notice in
     * the grid's place when there are none. Package-private so tests can exercise the empty case
     * without emptying the shared test database.
     *
     * @param surveys the installed surveys, in display order
     */
    void render(List<Survey> surveys) {
        add(new H3(getTranslation("surveyDefinitionExportView.title")));
        add(new Paragraph(getTranslation("surveyDefinitionExportView.intro")));
        add(new Paragraph(getTranslation("surveyDefinitionExportView.revisionNote")));

        if (surveys.isEmpty()) {
            Paragraph empty = new Paragraph(getTranslation("surveyDefinitionExportView.empty"));
            empty.setId(EMPTY_NOTICE_ID);
            add(empty);
            return;
        }

        Grid<Survey> grid = new Grid<>();
        grid.setId(GRID_ID);
        grid.setItems(surveys);
        grid.setAllRowsVisible(true);
        grid.addColumn(survey -> survey.name).setHeader(getTranslation("surveyDefinitionExportView.grid.name")).setAutoWidth(true).setFlexGrow(1);
        grid.addColumn(survey -> survey.title).setHeader(getTranslation("surveyDefinitionExportView.grid.title")).setAutoWidth(true).setFlexGrow(2);
        grid.addColumn(survey -> String.valueOf(survey.surveyKey)).setHeader(getTranslation("surveyDefinitionExportView.grid.surveyKey"))
                .setAutoWidth(true).setFlexGrow(0);
        grid.addColumn(this::installedRevisionOf).setHeader(getTranslation("surveyDefinitionExportView.grid.installedRevision"))
                .setAutoWidth(true).setFlexGrow(0);
        grid.addComponentColumn(this::downloadAnchor).setHeader(getTranslation("surveyDefinitionExportView.grid.download"))
                .setAutoWidth(true).setFlexGrow(0);
        add(grid);
    }

    /**
     * Describes the revision of the last definition file applied here for {@code survey}, or says
     * so when none is recorded — a survey created before revision stamping, or one that was
     * seeded directly rather than applied from a file. Shown in UTC so administrators at
     * different sites read the same instant the same way.
     */
    String installedRevisionOf(Survey survey) {
        OffsetDateTime revision = surveyLogService.findLatestAppliedRevision(survey.surveyKey);
        return revision == null ? getTranslation("surveyDefinitionExportView.notRecorded")
                : DISPLAY_STAMP.format(revision.withOffsetSameInstant(ZoneOffset.UTC));
    }

    private Anchor downloadAnchor(Survey survey) {
        Button button = new Button(getTranslation("surveyDefinitionExportView.btnDownload"), VaadinIcon.DOWNLOAD.create());
        button.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_PRIMARY);

        Anchor anchor = new Anchor(downloadHandlerFor(survey), "");
        anchor.addClassName(DOWNLOAD_CLASS);
        anchor.setAriaLabel(getTranslation("surveyDefinitionExportView.downloadAriaLabel", survey.name));
        anchor.add(button);
        return anchor;
    }

    private DownloadHandler downloadHandlerFor(Survey survey) {
        return DownloadHandler.fromInputStream(event -> download(survey.id));
    }

    /**
     * Produces the download for one survey. Package-private so tests can exercise the export
     * without simulating a browser request — the same precedent as
     * {@link SurveyDefinitionApplyView#handleUpload(byte[], String)}.
     *
     * @param surveyId the survey to export
     * @return the file to send, or a not-found error when the survey no longer exists
     */
    DownloadResponse download(Integer surveyId) {
        Survey survey = Survey.findById(surveyId);
        if (survey == null) {
            return DownloadResponse.error(404, "Survey not found: " + surveyId);
        }
        try {
            String export = exportService.exportSurvey(surveyId);
            byte[] bytes = export.getBytes(StandardCharsets.UTF_8);
            String fileName = fileNameFor(survey.name, revisionOf(export));
            Log.infof("Survey definition %d (%s) exported from the console as %s",
                    surveyId, survey.surveyKey, fileName);
            return new DownloadResponse(new ByteArrayInputStream(bytes), fileName, CONTENT_TYPE, bytes.length);
        } catch (IllegalArgumentException e) {
            return DownloadResponse.error(404, e.getMessage());
        }
    }

    /**
     * Names the file the way the Author tool names its own exports: the survey name with anything
     * unsafe for a file system replaced, then the revision to the minute, then the extension.
     *
     * @param surveyName the survey's name, possibly {@code null}
     * @param revision the file's revision, as stamped in its header
     * @return the file name to offer the browser
     */
    static String fileNameFor(String surveyName, OffsetDateTime revision) {
        return safeName(surveyName) + "_" + FILE_STAMP.format(revision) + FILE_EXTENSION;
    }

    /**
     * Reads the revision the export service stamped into the file, so the file name matches the
     * header exactly. Falls back to the current instant if the header is missing or unreadable,
     * which can only happen if the format changes underneath this view.
     */
    static OffsetDateTime revisionOf(String export) {
        for (String line : export.split("\n")) {
            if (line.startsWith(REVISION_HEADER)) {
                try {
                    return OffsetDateTime.parse(line.substring(REVISION_HEADER.length()).trim());
                } catch (DateTimeParseException e) {
                    break;
                }
            }
            if (!line.startsWith("#")) {
                break;
            }
        }
        return OffsetDateTime.now();
    }

    static String safeName(String name) {
        String cleaned = name == null ? "survey"
                : name.replaceAll("[^A-Za-z0-9._-]+", "_").replaceAll("^_+|_+$", "");
        return cleaned.isEmpty() ? "survey" : cleaned;
    }
}
