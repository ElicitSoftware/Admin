package com.elicitsoftware.service;

/*-
 * ***LICENSE_START***
 * Elicit Admin
 * %%
 * Copyright (C) 2025 - 2026 The Regents of the University of Michigan - Rogel Cancer Center
 * %%
 * PolyForm Noncommercial License 1.0.0
 * <https://polyformproject.org/licenses/noncommercial/1.0.0>
 * ***LICENSE_END***
 */

import com.elicitsoftware.test.PostgresTestResource;
import com.elicitsoftware.test.SurveyEtlStub;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ReportingSchemaRenameClient} against a scripted Survey ({@link SurveyEtlStub}).
 *
 * <p>Traceability: UC-030 step 7 and A3/A4, BR-116, BR-117: the call is a POST to
 * {@code /api/etl/schema/<key>/rename?name=<new>}, each of Survey's answers maps to its own
 * outcome, and none of it throws.</p>
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class ReportingSchemaRenameClientTest {

    private static SurveyEtlStub survey;

    static final UUID KEY = UUID.fromString("5e91c606-59a1-450a-a8d7-2f1530ff472b");

    @Inject
    ReportingSchemaRenameClient client;

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

    /** UC-030 step 7-8: the request names the survey and the new name; 200 is "renamed" with the name Survey recorded. */
    @Test
    void surveyAnswers200_renamed() {
        survey.respond(200, "{\"status\":\"ok\",\"message\":\"Reporting schema of FHHS renamed from report_a to report_fhh.\",\"schema\":\"report_fhh\"}");

        ReportingSchemaRenameClient.Outcome outcome = client.rename(KEY, "report_fhh");

        assertEquals(ReportingSchemaRenameClient.Status.RENAMED, outcome.status());
        assertEquals("report_fhh", outcome.schema());
        assertEquals(List.of("POST /api/etl/schema/" + KEY + "/rename?name=report_fhh"), survey.requests());
    }

    /** UC-030 A3: each refusal keeps its own meaning so the dialog can word it. */
    @Test
    void surveyRefusals_mapToTheirOutcomes() {
        survey.respond(400, "{\"status\":\"invalid\",\"message\":\"A schema name must be...\",\"schema\":\"report_a\"}");
        assertEquals(ReportingSchemaRenameClient.Status.INVALID, client.rename(KEY, "report_x").status());

        survey.respond(409, "{\"status\":\"taken\",\"message\":\"The name report_x is already in use.\",\"schema\":\"report_a\"}");
        ReportingSchemaRenameClient.Outcome taken = client.rename(KEY, "report_x");
        assertEquals(ReportingSchemaRenameClient.Status.TAKEN, taken.status());
        assertEquals("report_a", taken.schema(), "the old name is still the schema's");

        survey.respond(409, "{\"status\":\"unbuilt\",\"message\":\"Survey X has no reporting schema yet; build it first.\",\"schema\":null}");
        ReportingSchemaRenameClient.Outcome unbuilt = client.rename(KEY, "report_x");
        assertEquals(ReportingSchemaRenameClient.Status.UNBUILT, unbuilt.status());
        assertNull(unbuilt.schema());

        survey.respond(404, "{\"status\":\"unknown\",\"message\":\"No survey has the key ...\"}");
        assertEquals(ReportingSchemaRenameClient.Status.UNKNOWN, client.rename(KEY, "report_x").status());
    }

    /** UC-030 A4: a 500 and an unreachable Survey are failures with a reason, never exceptions. */
    @Test
    void surveyFailures_failedWithoutThrowing() {
        survey.respond(500, "{\"status\":\"failed\",\"message\":\"ERROR: something\"}");
        ReportingSchemaRenameClient.Outcome failed = client.rename(KEY, "report_x");
        assertEquals(ReportingSchemaRenameClient.Status.FAILED, failed.status());
        assertEquals("ERROR: something", failed.message());

        survey.stop();
        try {
            ReportingSchemaRenameClient.Outcome unreachable = client.rename(KEY, "report_x");
            assertEquals(ReportingSchemaRenameClient.Status.FAILED, unreachable.status());
            assertTrue(unreachable.message().contains("/api/etl/schema/"), unreachable.message());
        } finally {
            try {
                survey = SurveyEtlStub.start();
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    /** BR-116: the console's own check matches Survey's rule. */
    @Test
    void objection_matchesSurveysRule() {
        assertNull(ReportingSchemaRenameClient.objection("report_fhh"));
        assertNull(ReportingSchemaRenameClient.objection("_x1"));
        assertNotNull(ReportingSchemaRenameClient.objection(null));
        assertNotNull(ReportingSchemaRenameClient.objection(""));
        assertNotNull(ReportingSchemaRenameClient.objection("Report_FHH"));
        assertNotNull(ReportingSchemaRenameClient.objection("report fhh"));
        assertNotNull(ReportingSchemaRenameClient.objection("1report"));
        assertNotNull(ReportingSchemaRenameClient.objection("r".repeat(64)));
        assertNotNull(ReportingSchemaRenameClient.objection("surveyreport"));
        assertNotNull(ReportingSchemaRenameClient.objection("pg_catalog"));
    }

    /** The name travels as a query parameter, encoded. */
    @Test
    void endpoint_encodesTheName() {
        assertEquals("http://localhost:" + SurveyEtlStub.PORT + "/api/etl/schema/" + KEY + "/rename?name=report_fhh",
                client.endpoint(KEY, "report_fhh"));
        assertTrue(client.endpoint(KEY, "a b").endsWith("name=a+b"));
    }
}
