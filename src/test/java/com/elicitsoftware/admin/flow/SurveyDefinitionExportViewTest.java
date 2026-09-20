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
import com.elicitsoftware.service.SurveyLogService;
import com.elicitsoftware.test.PostgresTestResource;
import com.vaadin.browserless.quarkus.QuarkusBrowserlessTest;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.server.streams.DownloadResponse;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UC-013 through the console: the Export Survey Definition view lists the installed surveys for
 * an administrator and hands back a portable definition file on demand.
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class SurveyDefinitionExportViewTest extends QuarkusBrowserlessTest {

    @Inject
    EntityManager em;

    @Inject
    SurveyLogService surveyLogService;

    private SurveyDefinitionExportView attachView() {
        SurveyDefinitionExportView view = CDI.current().select(SurveyDefinitionExportView.class).get();
        UI.getCurrent().add(view);
        return view;
    }

    private Survey newSurveyWithStep(String name) {
        return QuarkusTransaction.requiringNew().call(() -> {
            Survey survey = new Survey();
            survey.name = name;
            survey.displayOrder = 1000 + (int) (Math.random() * 100000);
            survey.title = "Title " + name;
            survey.persist();
            long id = ((Number) em.createNativeQuery("SELECT nextval('survey.steps_seq')")
                    .getSingleResult()).longValue();
            em.createNativeQuery("INSERT INTO survey.steps (id, survey_id, display_order, name, dimension_name) "
                            + "VALUES (?1, ?2, 1, 'Step A', 'D')")
                    .setParameter(1, id).setParameter(2, survey.id)
                    .executeUpdate();
            return survey;
        });
    }

    private Grid<?> grid(SurveyDefinitionExportView view) {
        return find(Grid.class, view).single();
    }

    /** UC-013: the view is restricted to elicit_admin, the same gate as the REST endpoint. */
    @Test
    void viewRequiresAdminRole() {
        RolesAllowed rolesAllowed = SurveyDefinitionExportView.class.getAnnotation(RolesAllowed.class);
        assertNotNull(rolesAllowed, "SurveyDefinitionExportView must declare @RolesAllowed");
        assertArrayEquals(new String[]{"elicit_admin"}, rolesAllowed.value());
    }

    /** UC-013 step 1: every installed survey is listed, each with its own download control. */
    @Test
    @TestTransaction
    @TestSecurity(user = "export.admin", roles = {"elicit_admin"})
    void viewListsInstalledSurveysWithDownloads() {
        Survey survey = newSurveyWithStep("ExportViewListed");

        SurveyDefinitionExportView view = attachView();

        assertTrue(find(H3.class, view).all().stream()
                .anyMatch(h -> UI.getCurrent().getTranslation("surveyDefinitionExportView.title").equals(h.getText())));
        Grid<?> grid = grid(view);
        assertEquals(SurveyDefinitionExportView.GRID_ID, grid.getId().orElse(null));

        int rows = test(grid).size();
        assertTrue(rows >= 1, "the persisted survey should appear in the grid");
        int row = rowOf(grid, survey);
        assertEquals("ExportViewListed", test(grid).getCellText(row, 0));
        assertEquals(survey.surveyKey.toString(), test(grid).getCellText(row, 2));

        Component downloadCell = test(grid).getCellComponent(row, 4);
        assertTrue(downloadCell instanceof Anchor, "the download column should hold an anchor");
        Anchor anchor = (Anchor) downloadCell;
        assertTrue(anchor.hasClassName(SurveyDefinitionExportView.DOWNLOAD_CLASS));
        assertNotNull(anchor.getHref(), "the anchor should point at a download handler");
    }

    /** UC-013 step 4: the download is a self-contained definition file named after the survey. */
    @Test
    @TestTransaction
    @TestSecurity(user = "export.admin", roles = {"elicit_admin"})
    void downloadProducesDefinitionFile() throws IOException {
        Survey survey = newSurveyWithStep("Export View File");
        SurveyDefinitionExportView view = attachView();

        DownloadResponse response = view.download(survey.id);

        assertFalse(response.hasError(), "an installed survey should export cleanly");
        assertEquals("application/octet-stream", response.getContentType());
        String content = new String(response.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(content.startsWith("# ELICIT_SURVEY_EXPORT_V1"), "the file must be in the portable format");
        assertTrue(content.contains("# survey_key: " + survey.surveyKey), "BR-060: the stable key travels with the file");
        assertTrue(content.contains("Step A"), "the definition's structure must be in the file");
        assertEquals(content.getBytes(StandardCharsets.UTF_8).length, response.getContentLength());

        String fileName = response.getFileName();
        assertTrue(fileName.startsWith("Export_View_File_"), "unsafe characters are replaced, as in Author: " + fileName);
        assertTrue(fileName.endsWith(".elicit"), fileName);
        OffsetDateTime revision = SurveyDefinitionExportView.revisionOf(content);
        assertEquals(SurveyDefinitionExportView.fileNameFor(survey.name, revision), fileName,
                "BR-072: the file name carries the revision stamped in the header");
    }

    /** UC-013 A2: a survey removed between listing and click yields a not-found error, not a crash. */
    @Test
    @TestTransaction
    @TestSecurity(user = "export.admin", roles = {"elicit_admin"})
    void downloadOfMissingSurveyReportsNotFound() {
        SurveyDefinitionExportView view = attachView();

        DownloadResponse response = view.download(999999);

        assertTrue(response.hasError());
        assertEquals(404, response.getError());
    }

    /** UC-013: the Installed Revision column reflects the survey log, or says nothing is recorded. */
    @Test
    @TestTransaction
    @TestSecurity(user = "export.admin", roles = {"elicit_admin"})
    void installedRevisionColumnReflectsSurveyLog() {
        Survey unlogged = newSurveyWithStep("ExportViewUnlogged");
        Survey logged = newSurveyWithStep("ExportViewLogged");
        OffsetDateTime revision = OffsetDateTime.of(2026, 9, 15, 14, 32, 7, 0, ZoneOffset.UTC);
        surveyLogService.logSuccess(logged.id.longValue(), logged.surveyKey, "IMPORT", "logged.elicit",
                "test", revision);

        SurveyDefinitionExportView view = attachView();

        assertEquals(UI.getCurrent().getTranslation("surveyDefinitionExportView.notRecorded"), view.installedRevisionOf(unlogged));
        assertEquals("2026-09-15 14:32 UTC", view.installedRevisionOf(logged));
    }

    /** With nothing installed the view says so instead of showing an empty grid. */
    @Test
    @TestTransaction
    @TestSecurity(user = "export.admin", roles = {"elicit_admin"})
    void emptyDeploymentShowsNoticeInsteadOfGrid() {
        SurveyDefinitionExportView view = attachView();
        view.removeAll();

        view.render(List.of());

        assertTrue(find(Grid.class, view).all().isEmpty(), "no grid when nothing is installed");
        Paragraph notice = find(Paragraph.class, view).all().stream()
                .filter(p -> SurveyDefinitionExportView.EMPTY_NOTICE_ID.equals(p.getId().orElse(null)))
                .findFirst().orElse(null);
        assertNotNull(notice, "the empty notice should be shown");
    }

    /** File names follow the Author tool's convention so the two tools' exports sort together. */
    @Test
    void fileNameMatchesAuthorConvention() {
        OffsetDateTime revision = OffsetDateTime.of(2026, 9, 15, 14, 32, 7, 0, ZoneOffset.UTC);
        assertEquals("Family_History_20260915-1432.elicit",
                SurveyDefinitionExportView.fileNameFor("Family History", revision));
        assertEquals("survey_20260915-1432.elicit", SurveyDefinitionExportView.fileNameFor(null, revision));
        assertEquals("survey_20260915-1432.elicit", SurveyDefinitionExportView.fileNameFor("///", revision));
        assertEquals("a.b-c_20260915-1432.elicit", SurveyDefinitionExportView.fileNameFor("_a.b-c_", revision));
    }

    /** The revision comes from the file's own header, so the name and the content agree. */
    @Test
    void revisionIsReadFromHeader() {
        String export = "# ELICIT_SURVEY_EXPORT_V1\n# survey_id: 1\n"
                + "# survey_revision: 2026-09-15T14:32:07.412-04:00\n\n[surveys]\n";
        assertEquals(OffsetDateTime.parse("2026-09-15T14:32:07.412-04:00"),
                SurveyDefinitionExportView.revisionOf(export));

        OffsetDateTime fallback = SurveyDefinitionExportView.revisionOf("# ELICIT_SURVEY_EXPORT_V1\n[surveys]\n");
        assertTrue(!fallback.isAfter(OffsetDateTime.now()), "a missing header falls back to now");
    }

    private int rowOf(Grid<?> grid, Survey survey) {
        List<String> keys = new ArrayList<>();
        for (int i = 0; i < test(grid).size(); i++) {
            String key = test(grid).getCellText(i, 2);
            if (survey.surveyKey.toString().equals(key)) {
                return i;
            }
            keys.add(key);
        }
        throw new AssertionError("survey " + survey.surveyKey + " not in grid; rows: " + keys);
    }
}
