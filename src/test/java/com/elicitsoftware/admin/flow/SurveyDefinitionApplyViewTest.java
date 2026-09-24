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
import com.elicitsoftware.test.PostgresTestResource;
import com.vaadin.browserless.quarkus.QuarkusBrowserlessTest;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.upload.Upload;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Browserless UI test for {@link SurveyDefinitionApplyView}.
 *
 * <p>Traceability: UC-018 (Apply Survey Definition). Follows {@code RespondentImportViewTest}'s
 * pattern — the view comes from CDI so its constructor runs, and the package-private
 * {@code handleUpload} is called directly since route navigation is unavailable under
 * {@code @QuarkusTest}.</p>
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class SurveyDefinitionApplyViewTest extends QuarkusBrowserlessTest {

    @Inject
    SurveyDefinitionExportService exportService;

    @Inject
    EntityManager em;

    private SurveyDefinitionApplyView view;

    @BeforeEach
    @TestSecurity(user = "apply.admin", roles = {"elicit_admin"})
    void setUp() {
        view = CDI.current().select(SurveyDefinitionApplyView.class).get();
        UI.getCurrent().add(view);
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

    private Dialog openDialog() {
        return find(Dialog.class).single();
    }

    /** UC-018: the view is restricted to elicit_admin. */
    @Test
    void viewRequiresAdminRole() {
        RolesAllowed rolesAllowed = SurveyDefinitionApplyView.class.getAnnotation(RolesAllowed.class);
        assertNotNull(rolesAllowed, "SurveyDefinitionApplyView must declare @RolesAllowed");
        assertArrayEquals(new String[]{"elicit_admin"}, rolesAllowed.value());
    }

    /** UC-018: the view renders a header and an upload control accepting only .elicit files. */
    @Test
    @TestSecurity(user = "apply.admin", roles = {"elicit_admin"})
    void viewRendersUploadControl() {
        assertTrue(find(H3.class, view).all().stream()
                .anyMatch(h -> view.getTranslation("surveyDefinitionApplyView.title").equals(h.getText())));

        Upload upload = find(Upload.class, view).single();
        assertEquals("survey-apply-upload", upload.getId().orElse(null));
        assertEquals(List.of(".elicit"), upload.getAcceptedFileTypes());
        assertEquals(1, upload.getMaxFiles());
    }

    /**
     * UC-018 step 4: a file whose survey key this deployment has never seen reports as an
     * install, without the administrator having nominated a target.
     */
    @Test
    @TestTransaction
    @TestSecurity(user = "apply.admin", roles = {"elicit_admin"})
    void unknownKeyReportsAsNewSurveyInstalled() {
        Survey source = newSurveyWithStep("ApplyViewNew");
        String file = exportService.exportSurvey(source.id)
                .replace(source.surveyKey.toString(), UUID.randomUUID().toString())
                .replace("ApplyViewNew", "ApplyViewArrived");

        view.handleUpload(file.getBytes(StandardCharsets.UTF_8), "new.elicit");

        assertEquals(view.getTranslation("surveyDefinitionApplyView.newSurveyInstalled"), openDialog().getHeaderTitle());
    }

    /**
     * UC-018 step 5: a file whose survey key is already installed reports as an update, again
     * with no target nominated.
     */
    @Test
    @TestTransaction
    @TestSecurity(user = "apply.admin", roles = {"elicit_admin"})
    void knownKeyReportsAsSurveyUpdated() {
        Survey survey = newSurveyWithStep("ApplyViewExisting");
        String file = exportService.exportSurvey(survey.id).replace("Step A", "Step A revised");

        view.handleUpload(file.getBytes(StandardCharsets.UTF_8), "upd.elicit");

        assertEquals(view.getTranslation("surveyDefinitionApplyView.surveyUpdated"), openDialog().getHeaderTitle());
    }

    /**
     * UC-018 step 7 / A5: the dialog ends with the reporting schema rebuild's line, and a
     * rebuild that failed leaves the title "New Survey Installed" -- the apply stood. Nothing
     * listens on the stub port in this class, so the call fails at once with a refused
     * connection, which is exactly the failed-rebuild shape.
     */
    @Test
    @TestTransaction
    @TestSecurity(user = "apply.admin", roles = {"elicit_admin"})
    void failedReportingRebuildIsShownWithoutChangingTheOutcome() {
        Survey source = newSurveyWithStep("ApplyViewRebuild");
        String file = exportService.exportSurvey(source.id)
                .replace(source.surveyKey.toString(), UUID.randomUUID().toString())
                .replace("ApplyViewRebuild", "ApplyViewRebuildArrived");

        view.handleUpload(file.getBytes(StandardCharsets.UTF_8), "new.elicit");

        Dialog dialog = openDialog();
        assertEquals("New Survey Installed", dialog.getHeaderTitle());
        String text = find(com.vaadin.flow.component.html.Span.class, dialog).single().getText();
        assertTrue(text.contains("Installed as a new survey"), text);
        assertTrue(text.contains("\nReporting schema not rebuilt: "), "the rebuild line ends the summary: " + text);
    }

    /** UC-018 A3: a file that isn't a survey definition is reported, not applied. */
    @Test
    @TestTransaction
    @TestSecurity(user = "apply.admin", roles = {"elicit_admin"})
    void nonDefinitionFileReportsFailure() {
        view.handleUpload("not an elicit file".getBytes(StandardCharsets.UTF_8), "junk.elicit");

        assertEquals(view.getTranslation("surveyDefinitionApplyView.applyFailed"), openDialog().getHeaderTitle());
    }
}
