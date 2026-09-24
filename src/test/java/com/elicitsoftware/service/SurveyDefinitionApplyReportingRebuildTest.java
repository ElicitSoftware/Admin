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
import com.elicitsoftware.test.SurveyEtlStub;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The reporting schema rebuild as seen from {@link SurveyDefinitionApplyService} (UC-018 step 7,
 * A5, BR-108; the same step in UC-014 and UC-017): after a successful install or update Survey
 * is asked once, its answer becomes the result's {@code reporting} line, and a failed or absent
 * answer leaves the apply successful. A rejected or failed apply asks nothing.
 * <p>
 * Survey is {@link SurveyEtlStub}. Same fixtures as {@link SurveyDefinitionApplyServiceTest}.
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class SurveyDefinitionApplyReportingRebuildTest {

    private static SurveyEtlStub survey;

    @Inject
    SurveyDefinitionApplyService applyService;

    @Inject
    SurveyDefinitionExportService exportService;

    @Inject
    EntityManager em;

    @BeforeAll
    static void startStub() throws IOException {
        survey = SurveyEtlStub.start();
    }

    @AfterAll
    static void stopStub() {
        survey.stop();
    }

    @BeforeEach
    void resetStub() {
        survey.reset();
    }

    private byte[] bytes(String content) {
        return content.getBytes(StandardCharsets.UTF_8);
    }

    private Survey newSurveyWithStep(String name) {
        return QuarkusTransaction.requiringNew().call(() -> {
            Survey s = new Survey();
            s.name = name;
            s.displayOrder = 1000 + (int) (Math.random() * 100000);
            s.title = "Title " + name;
            s.persist();
            long id = ((Number) em.createNativeQuery("SELECT nextval('survey.steps_seq')").getSingleResult()).longValue();
            em.createNativeQuery("INSERT INTO survey.steps (id, survey_id, display_order, name, dimension_name) "
                            + "VALUES (?1, ?2, 1, 'Step A', 'D')")
                    .setParameter(1, id).setParameter(2, s.id).executeUpdate();
            return s;
        });
    }

    private String fileForNewSurvey(String name) {
        Survey source = newSurveyWithStep(name);
        return exportService.exportSurvey(source.id)
                .replace(source.surveyKey.toString(), UUID.randomUUID().toString())
                .replace(name, name + "Arrived");
    }

    /** UC-014 step 5 via UC-018: an install asks Survey once and reports "rebuilt". */
    @Test
    @TestTransaction
    void successfulInstallRebuildsAndReportsIt() {
        SurveyDefinitionApplyService.ApplyResult result =
                applyService.apply(bytes(fileForNewSurvey("RebuildNew")), "new.elicit");

        assertTrue(result.success(), result.message());
        assertEquals(SurveyDefinitionApplyService.ApplyResult.Action.IMPORT, result.action());
        assertEquals("Reporting schema rebuilt.", result.reporting());
        assertEquals("Installed as a new survey", result.message(), "the routing message is untouched");
        assertEquals(List.of("POST /api/etl/build"), survey.requests());
    }

    /** UC-017 step 7 via UC-018: an update asks Survey once and reports "rebuilt". */
    @Test
    @TestTransaction
    void successfulUpdateRebuildsAndReportsIt() {
        Survey existing = newSurveyWithStep("RebuildExisting");
        String file = exportService.exportSurvey(existing.id).replace("Step A", "Step A revised");

        SurveyDefinitionApplyService.ApplyResult result = applyService.apply(bytes(file), "upd.elicit");

        assertTrue(result.success(), result.message());
        assertEquals(SurveyDefinitionApplyService.ApplyResult.Action.UPDATE, result.action());
        assertEquals("Reporting schema rebuilt.", result.reporting());
        assertEquals(List.of("POST /api/etl/build"), survey.requests());
    }

    /** UC-018 A5 / BR-108: Survey's failure is reported on the result and the apply stands. */
    @Test
    @TestTransaction
    void rebuildFailureDoesNotUndoTheApply() {
        survey.respond(500, "{\"status\":\"failed\",\"message\":\"ERROR: duplicate key value violates unique constraint \\\"dim_step_un\\\"\"}");
        String file = fileForNewSurvey("RebuildFails");

        SurveyDefinitionApplyService.ApplyResult result = applyService.apply(bytes(file), "new.elicit");

        assertTrue(result.success(), "the apply must succeed whatever the rebuild says: " + result.message());
        assertEquals("Reporting schema not rebuilt: ERROR: duplicate key value violates unique constraint \"dim_step_un\"",
                result.reporting());
        assertNotNull(Survey.find("surveyKey", result.surveyKey()).firstResult(), "the survey installed by the apply must remain");
    }

    /** UC-018 A5 / BR-108: no Survey at all is likewise a line on the result, not a failure. */
    @Test
    @TestTransaction
    void unreachableSurveyDoesNotUndoTheApply() {
        survey.stop();
        try {
            SurveyDefinitionApplyService.ApplyResult result =
                    applyService.apply(bytes(fileForNewSurvey("RebuildUnreachable")), "new.elicit");

            assertTrue(result.success(), result.message());
            assertTrue(result.reporting().startsWith("Reporting schema not rebuilt: "), result.reporting());
        } finally {
            try {
                survey = SurveyEtlStub.start();
            } catch (IOException e) {
                throw new IllegalStateException("could not restart the Survey stub", e);
            }
        }
    }

    /** UC-018 A4: a rejected file asks Survey nothing and carries no reporting line. */
    @Test
    @TestTransaction
    void rejectedFileAsksNothing() {
        SurveyDefinitionApplyService.ApplyResult result =
                applyService.apply(bytes("not an elicit file"), "junk.elicit");

        assertFalse(result.success());
        assertNull(result.reporting());
        assertTrue(survey.requests().isEmpty(), "no rebuild may be requested for a file that was not applied");
    }
}
