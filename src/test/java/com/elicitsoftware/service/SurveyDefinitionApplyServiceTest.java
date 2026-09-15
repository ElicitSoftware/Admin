package com.elicitsoftware.service;

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
import com.elicitsoftware.test.PostgresTestResource;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Booted tests for {@link SurveyDefinitionApplyService}.
 *
 * <p>Traceability: UC-014 (Import Survey Definition) and UC-017 (Update Survey Definition) —
 * this service chooses between them by {@code survey_key} rather than requiring the operator to.
 * The routing decision is what's under test here; the behaviour of each destination service is
 * covered by {@code SurveyDefinitionImportServiceTest} and
 * {@code SurveyDefinitionUpdateServiceTest}.</p>
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class SurveyDefinitionApplyServiceTest {

    @Inject
    SurveyDefinitionApplyService applyService;

    @Inject
    SurveyDefinitionExportService exportService;

    @Inject
    EntityManager em;

    private byte[] bytes(String content) {
        return content.getBytes(StandardCharsets.UTF_8);
    }

    private Survey newSurvey(String name) {
        return QuarkusTransaction.requiringNew().call(() -> {
            Survey survey = new Survey();
            survey.name = name;
            survey.displayOrder = 1000 + (int) (Math.random() * 100000);
            survey.title = "Title " + name;
            survey.persist();
            return survey;
        });
    }

    private void insertStep(int surveyId, String name) {
        QuarkusTransaction.requiringNew().run(() -> {
            long id = ((Number) em.createNativeQuery("SELECT nextval('survey.steps_seq')")
                    .getSingleResult()).longValue();
            em.createNativeQuery("INSERT INTO survey.steps (id, survey_id, display_order, name, dimension_name) "
                            + "VALUES (?1, ?2, 1, ?3, 'D')")
                    .setParameter(1, id).setParameter(2, surveyId).setParameter(3, name)
                    .executeUpdate();
        });
    }

    /**
     * A file whose survey_key is not installed here is a create — the operator doesn't have to
     * know that, which is the whole point when the same file goes to several deployments.
     */
    @Test
    @TestTransaction
    void unknownSurveyKeyRoutesToImport() {
        Survey source = newSurvey("ApplyNew");
        insertStep(source.id, "Step A");

        // Re-key and rename so the file describes a survey this instance has never seen.
        UUID freshKey = UUID.randomUUID();
        String file = exportService.exportSurvey(source.id)
                .replace(source.surveyKey.toString(), freshKey.toString())
                .replace("ApplyNew", "ApplyNewArrived");

        SurveyDefinitionApplyService.ApplyResult result = applyService.apply(bytes(file), "new.elicit");

        assertTrue(result.success(), () -> "apply failed: " + result.message());
        assertEquals(SurveyDefinitionApplyService.ApplyResult.Action.IMPORT, result.action());
        assertEquals(freshKey, result.surveyKey());
        assertNotNull(Survey.find("surveyKey", freshKey).firstResult(),
                "the survey should now exist under the file's own key");
    }

    /**
     * A file whose survey_key is already installed here is an update against that survey — the
     * operator doesn't supply the target id, the key resolves it.
     */
    @Test
    @TestTransaction
    void knownSurveyKeyRoutesToUpdate() {
        Survey survey = newSurvey("ApplyExisting");
        insertStep(survey.id, "Step A");

        String file = exportService.exportSurvey(survey.id).replace("Step A", "Step A revised");

        SurveyDefinitionApplyService.ApplyResult result = applyService.apply(bytes(file), "upd.elicit");

        assertTrue(result.success(), () -> "apply failed: " + result.message());
        assertEquals(SurveyDefinitionApplyService.ApplyResult.Action.UPDATE, result.action());
        assertEquals(survey.surveyKey, result.surveyKey());
        assertEquals(1L, ((Number) em.createNativeQuery(
                        "SELECT count(*) FROM survey.steps WHERE survey_id = ?1 AND name = 'Step A revised' "
                                + "AND effective_to = '9999-12-31 23:59:59+00'")
                .setParameter(1, survey.id).getSingleResult()).longValue(),
                "the update should have versioned the step in place");
        assertEquals(1L, ((Number) em.createNativeQuery(
                        "SELECT count(*) FROM survey.surveys WHERE survey_key = ?1")
                .setParameter(1, survey.surveyKey).getSingleResult()).longValue(),
                "routing to update must not have created a second survey");
    }

    /**
     * A pre-key file can't be matched against what's installed, so routing refuses rather than
     * guessing — installing it as new is a decision only the operator can make.
     */
    @Test
    @TestTransaction
    void missingSurveyKeyIsRejected() {
        Survey survey = newSurvey("ApplyNoKey");
        String file = exportService.exportSurvey(survey.id)
                .replaceAll("(?m)^surveys: ([^|]*)\\|[^|]*\\|", "surveys: $1||");

        SurveyDefinitionApplyService.ApplyResult result = applyService.apply(bytes(file), "nokey.elicit");

        assertFalse(result.success());
        assertEquals(SurveyDefinitionApplyService.ApplyResult.Action.REJECTED, result.action());
        assertNull(result.surveyKey());
        assertTrue(result.message().contains("carries no survey_key"), result.message());
    }

    /** A file with no surveys record isn't a definition export at all. */
    @Test
    @TestTransaction
    void fileWithoutSurveysRecordIsRejected() {
        SurveyDefinitionApplyService.ApplyResult result = applyService.apply(
                bytes("# ELICIT_SURVEY_EXPORT_V1\n# survey_id: 1\n\nsteps: 1|" + UUID.randomUUID() + "|1|S|D||\n"),
                "bogus.elicit");

        assertFalse(result.success());
        assertEquals(SurveyDefinitionApplyService.ApplyResult.Action.REJECTED, result.action());
        assertTrue(result.message().contains("no surveys record"), result.message());
    }

    /** An empty upload is refused before anything else is attempted. */
    @Test
    @TestTransaction
    void emptyFileIsRejected() {
        SurveyDefinitionApplyService.ApplyResult result = applyService.apply(new byte[0], "empty.elicit");

        assertFalse(result.success());
        assertEquals(SurveyDefinitionApplyService.ApplyResult.Action.REJECTED, result.action());
    }
}
