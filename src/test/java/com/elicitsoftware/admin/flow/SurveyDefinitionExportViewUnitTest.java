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
import com.elicitsoftware.service.ReportingSchemaRenameClient;
import com.elicitsoftware.service.ReportingSchemaRenameClient.Outcome;
import com.elicitsoftware.service.ReportingSchemaRenameClient.Status;
import com.elicitsoftware.service.SurveyLogService;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.icon.Icon;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UC-013: Export Survey Definition and UC-030: Rename a Survey's Reporting Schema — the survey
 * list of {@link SurveyDefinitionExportView} in a plain browserless UI, with stand-ins for the
 * survey log and the rename client. Covers the grid with each survey's installed revision and
 * reporting schema, the download offered as an administrator's action (BR-080), the file named for
 * its survey and revision (BR-081), and the rename button shown only for a built schema whose new
 * name is shown in place after a rename.
 *
 * <p>Not reached here: {@code init}, which lists the surveys with {@code Survey.findAll}, and the
 * download itself, which starts with {@code Survey.findById}; both are Panache static calls.</p>
 */
class SurveyDefinitionExportViewUnitTest extends PlainSmallViewTestSupport {

    private static final UUID FAMILY_KEY = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID CANCER_KEY = UUID.fromString("00000000-0000-0000-0000-000000000002");

    private final Map<UUID, OffsetDateTime> revisions = new HashMap<>();
    private SurveyDefinitionExportView view;
    private Survey family;
    private Survey cancer;

    @BeforeEach
    void build() {
        family = survey(1, "Family History", "Family history title", FAMILY_KEY, "report_family");
        cancer = survey(2, "Cancer", "Cancer title", CANCER_KEY, null);
        revisions.put(FAMILY_KEY, OffsetDateTime.of(2026, 10, 1, 9, 30, 0, 0, ZoneOffset.ofHours(-4)));
        view = new SurveyDefinitionExportView();
        view.surveyLogService = new SurveyLogService() {
            @Override
            public OffsetDateTime findLatestAppliedRevision(UUID surveyKey) {
                return revisions.get(surveyKey);
            }
        };
        view.renameClient = new ReportingSchemaRenameClient() {
            @Override
            public Outcome rename(UUID surveyKey, String newName) {
                return new Outcome(Status.RENAMED, "ok", newName);
            }
        };
        UI.getCurrent().add(view);
    }

    private static Survey survey(int id, String name, String title, UUID key, String schema) {
        Survey s = new Survey();
        s.id = id;
        s.name = name;
        s.title = title;
        s.surveyKey = key;
        s.reportSchema = schema;
        return s;
    }

    @SuppressWarnings("unchecked")
    private Grid<Survey> grid() {
        return byId(view, SurveyDefinitionExportView.GRID_ID, Grid.class);
    }

    @Test
    void given_noSurveys_when_rendered_then_emptyNoticeIsShownAndNoGrid() {
        view.render(List.of());

        List<Component> parts = view.getChildren().toList();
        assertEquals(4, parts.size());
        assertEquals(t("surveyDefinitionExportView.title"), ((H3) parts.get(0)).getText());
        assertEquals(t("surveyDefinitionExportView.intro"), ((Paragraph) parts.get(1)).getText());
        assertEquals(t("surveyDefinitionExportView.revisionNote"), ((Paragraph) parts.get(2)).getText());
        Paragraph empty = (Paragraph) parts.get(3);
        assertEquals(SurveyDefinitionExportView.EMPTY_NOTICE_ID, empty.getId().orElseThrow());
        assertEquals(t("surveyDefinitionExportView.empty"), empty.getText());
        assertNull(grid());
        assertEquals("100%", view.getWidth());
        assertEquals("100%", view.getHeight());
    }

    @Test
    void given_surveys_when_rendered_then_eachRowShowsItsFactsRevisionAndSchema() {
        view.render(List.of(family, cancer));

        Grid<Survey> grid = grid();
        assertEquals(List.of(t("surveyDefinitionExportView.grid.name"), t("surveyDefinitionExportView.grid.title"),
                t("surveyDefinitionExportView.grid.surveyKey"), t("surveyDefinitionExportView.grid.installedRevision"),
                t("surveyDefinitionExportView.grid.download"), t("surveyDefinitionExportView.grid.reportSchema"),
                t("surveyDefinitionExportView.grid.rename")), headers(grid));
        assertTrue(grid.isAllRowsVisible());
        assertEquals(List.of("Family History", "Cancer"), column(grid, 0));
        assertEquals(List.of("Family history title", "Cancer title"), column(grid, 1));
        assertEquals(List.of(FAMILY_KEY.toString(), CANCER_KEY.toString()), column(grid, 2));
        assertEquals(List.of("2026-10-01 13:30 UTC", t("surveyDefinitionExportView.notRecorded")), column(grid, 3),
                "the revision is shown in UTC");
        assertEquals(List.of("report_family", t("surveyDefinitionExportView.notBuilt")), column(grid, 5));

        Grid.Column<Survey> key = grid.getColumns().get(2);
        assertEquals(SurveyDefinitionExportView.KEY_COLUMN_WIDTH, key.getWidth());
        assertEquals(0, key.getFlexGrow());
        assertEquals(SurveyDefinitionExportView.KEY_CELL_PART, key.getPartNameGenerator().apply(family));
        assertEquals(1, grid.getColumns().get(0).getFlexGrow());
        assertEquals(2, grid.getColumns().get(1).getFlexGrow());
    }

    @Test
    void given_surveys_when_rendered_then_downloadIsAnAnchorWrappingASmallPrimaryButton() {
        view.render(List.of(family));

        Anchor anchor = (Anchor) test(grid()).getCellComponent(0, 4);
        assertTrue(anchor.hasClassName(SurveyDefinitionExportView.DOWNLOAD_CLASS));
        assertEquals(t("surveyDefinitionExportView.downloadAriaLabel", "Family History"), anchor.getAriaLabel().orElseThrow());
        Button button = (Button) anchor.getChildren().findFirst().orElseThrow();
        assertEquals(t("surveyDefinitionExportView.btnDownload"), button.getText());
        assertInstanceOf(Icon.class, button.getIcon());
        assertTrue(button.getThemeNames().contains(ButtonVariant.LUMO_SMALL.getVariantName()));
        assertTrue(button.getThemeNames().contains(ButtonVariant.LUMO_PRIMARY.getVariantName()));
    }

    @Test
    void given_builtAndUnbuiltSchemas_when_rendered_then_renameIsOfferedOnlyForTheBuiltOne() {
        view.render(List.of(family, cancer));

        Button built = (Button) test(grid()).getCellComponent(0, 6);
        assertTrue(built.isVisible());
        assertEquals(t("surveyDefinitionExportView.btnRename"), built.getText());
        assertTrue(built.hasClassName(SurveyDefinitionExportView.RENAME_CLASS));
        assertTrue(built.getThemeNames().contains(ButtonVariant.LUMO_SMALL.getVariantName()));
        assertEquals(t("surveyDefinitionExportView.renameAriaLabel", "Family History"), built.getAriaLabel().orElseThrow());
        assertInstanceOf(Icon.class, built.getIcon());
        assertFalse(((Button) test(grid()).getCellComponent(1, 6)).isVisible());
    }

    @Test
    void given_builtSchema_when_renamedThroughTheDialog_then_theNewNameIsShownInPlace() {
        view.render(List.of(family));
        List<Object> refreshes = refreshesOf(grid());

        test((Button) test(grid()).getCellComponent(0, 6)).click();
        RenameReportingSchemaDialog dialog = $(RenameReportingSchemaDialog.class).single();
        assertTrue(dialog.isOpened());
        test(byId(dialog, RenameReportingSchemaDialog.NAME_FIELD_ID,
                com.vaadin.flow.component.textfield.TextField.class)).setValue("report_kin");
        dialog.rename();

        assertEquals("report_kin", family.reportSchema);
        assertEquals("report_kin", test(grid()).getCellText(0, 5));
        assertEquals(List.of(family), refreshes, "the renamed survey's row is redrawn");
        assertFalse(dialog.isOpened());
    }

    @Test
    void given_nameAndRevision_when_fileNamed_then_itIsSafeNameStampAndExtension() {
        OffsetDateTime revision = OffsetDateTime.of(2026, 3, 4, 5, 6, 7, 0, ZoneOffset.UTC);

        assertEquals("Family_History_20260304-0506.elicit",
                SurveyDefinitionExportView.fileNameFor("Family History", revision));
    }

    @Test
    void given_awkwardNames_when_madeSafe_then_onlyPortableCharactersRemain() {
        assertEquals("a_b.c-d", SurveyDefinitionExportView.safeName("a / b.c-d"));
        assertEquals("abc", SurveyDefinitionExportView.safeName("__abc__"));
        assertEquals("x_y", SurveyDefinitionExportView.safeName("  x  y  "));
        assertEquals("survey", SurveyDefinitionExportView.safeName("///"));
        assertEquals("survey", SurveyDefinitionExportView.safeName(null));
        assertEquals("survey", SurveyDefinitionExportView.safeName(""));
    }

    @Test
    void given_revisionHeaderAmongComments_when_read_then_thatRevisionIsReturned() {
        String export = "# Elicit survey definition\n# survey_revision: 2026-10-01T09:30:00-04:00 \nsurveys:\n";

        assertEquals(OffsetDateTime.parse("2026-10-01T09:30:00-04:00"), SurveyDefinitionExportView.revisionOf(export));
    }

    private static void assertNow(OffsetDateTime actual) {
        Duration skew = Duration.between(actual, OffsetDateTime.now()).abs();
        assertTrue(skew.compareTo(Duration.ofMinutes(1)) < 0, "expected the current time, was " + actual);
    }

    @Test
    void given_headerAfterTheComments_when_read_then_nowIsUsed() {
        assertNow(SurveyDefinitionExportView.revisionOf(
                "# Elicit\nsurveys:\n# survey_revision: 2001-01-01T00:00:00Z\n"));
    }

    @Test
    void given_unparsableRevision_when_read_then_nowIsUsed() {
        assertNow(SurveyDefinitionExportView.revisionOf("# survey_revision: yesterday\n"));
    }

    @Test
    void given_noHeader_when_read_then_nowIsUsed() {
        assertNow(SurveyDefinitionExportView.revisionOf("# only a comment\n# another\n"));
    }

    @Test
    void given_surveyWithoutSchema_when_schemaAsked_then_notBuiltIsWorded() {
        assertEquals(t("surveyDefinitionExportView.notBuilt"), view.reportSchemaOf(cancer));
        assertEquals("report_family", view.reportSchemaOf(family));
        assertEquals(t("surveyDefinitionExportView.notRecorded"), view.installedRevisionOf(cancer));
    }
}
