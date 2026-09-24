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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ReportingSchemaRebuildClient} against a scripted Survey ({@link SurveyEtlStub}).
 *
 * <p>Traceability: UC-018 step 7 and A5, BR-107, BR-108 (and the same step in UC-014 and
 * UC-017): the call is a POST to {@code /api/etl/build}, a 200 is "rebuilt", anything else or
 * no answer at all is "not rebuilt" with Survey's own message where it gave one, and none of
 * it throws. The disabled branch cannot be reached in the shared test JVM (the flag is read at
 * boot), so it is covered on an unbooted instance in {@link #disabledMakesNoCall()}.</p>
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class ReportingSchemaRebuildClientTest {

    private static SurveyEtlStub survey;

    @Inject
    ReportingSchemaRebuildClient client;

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

    /** UC-018 step 7: the call is a POST to Survey's endpoint, and a 200 is reported as rebuilt. */
    @Test
    void surveyAnswers200_rebuilt() {
        survey.respond(200, "{\"status\":\"ok\",\"message\":\"Step dimensions upserted: 3\"}");

        ReportingSchemaRebuildClient.Outcome outcome = client.rebuild();

        assertEquals(ReportingSchemaRebuildClient.Status.REBUILT, outcome.status());
        assertEquals("Step dimensions upserted: 3", outcome.message());
        assertEquals("Reporting schema rebuilt.", outcome.summaryLine());
        assertEquals(List.of("POST /api/etl/build"), survey.requests());
        assertEquals("http://localhost:" + SurveyEtlStub.PORT + "/api/etl/build", client.endpoint());
    }

    /** UC-018 A5 / BR-108: Survey's 500 carries the cause (the dim_step_un case) into the line. */
    @Test
    void surveyAnswers500_failedWithSurveysMessage() {
        survey.respond(500, "{\"status\":\"failed\",\"message\":\"ERROR: duplicate key value violates unique constraint \\\"dim_step_un\\\"\"}");

        ReportingSchemaRebuildClient.Outcome outcome = client.rebuild();

        assertEquals(ReportingSchemaRebuildClient.Status.FAILED, outcome.status());
        assertEquals("Reporting schema not rebuilt: ERROR: duplicate key value violates unique constraint \"dim_step_un\"",
                outcome.summaryLine());
    }

    /** UC-018 A5: a Survey with its ETL turned off answers 409; that is reported, not hidden. */
    @Test
    void surveyAnswers409_failedWithDisabledReason() {
        survey.respond(409, "{\"status\":\"disabled\",\"message\":\"Reporting ETL is disabled (elicit.etl.enabled=false)\"}");

        ReportingSchemaRebuildClient.Outcome outcome = client.rebuild();

        assertEquals(ReportingSchemaRebuildClient.Status.FAILED, outcome.status());
        assertEquals("Reporting schema not rebuilt: Reporting ETL is disabled (elicit.etl.enabled=false)",
                outcome.summaryLine());
    }

    /** UC-018 A5: a body that is not Survey's JSON (a proxy error page) still yields a readable line. */
    @Test
    void nonJsonAnswer_failedWithStatusAndBodyExcerpt() {
        survey.respond(502, "<html>Bad Gateway</html>");

        ReportingSchemaRebuildClient.Outcome outcome = client.rebuild();

        assertEquals(ReportingSchemaRebuildClient.Status.FAILED, outcome.status());
        assertTrue(outcome.message().startsWith("HTTP 502: <html>Bad Gateway</html>"), outcome.message());
    }

    /** UC-018 A5 / BR-108: an unreachable Survey is a failed rebuild, not an exception. */
    @Test
    void surveyUnreachable_failedWithoutThrowing() {
        survey.stop();
        try {
            ReportingSchemaRebuildClient.Outcome outcome = client.rebuild();

            assertEquals(ReportingSchemaRebuildClient.Status.FAILED, outcome.status());
            assertTrue(outcome.summaryLine().startsWith("Reporting schema not rebuilt: "), outcome.summaryLine());
            assertTrue(outcome.message().contains("/api/etl/build"),
                    "the line names the address that did not answer: " + outcome.message());
        } finally {
            try {
                survey = SurveyEtlStub.start();
            } catch (IOException e) {
                throw new IllegalStateException("could not restart the Survey stub", e);
            }
        }
    }

    /** UC-018 BR-107: elicit.survey.etl-build.enabled=false makes no call at all. */
    @Test
    void disabledMakesNoCall() {
        ReportingSchemaRebuildClient off = new ReportingSchemaRebuildClient();
        off.enabled = false;
        off.surveyUrl = "http://localhost:" + SurveyEtlStub.PORT;

        ReportingSchemaRebuildClient.Outcome outcome = off.rebuild();

        assertEquals(ReportingSchemaRebuildClient.Status.SKIPPED, outcome.status());
        assertNull(outcome.summaryLine(), "a skipped rebuild adds nothing to the result");
        assertTrue(survey.requests().isEmpty(), "no request may reach Survey when the call is disabled");
    }

    /** The body parser: Survey's shape, an empty body, and garbage. */
    @Test
    void messageOf_handlesEveryShape() {
        assertEquals("built", ReportingSchemaRebuildClient.messageOf("{\"status\":\"ok\",\"message\":\"built\"}", "HTTP 200"));
        assertEquals("HTTP 200", ReportingSchemaRebuildClient.messageOf("{\"status\":\"ok\"}", "HTTP 200"));
        assertEquals("HTTP 200", ReportingSchemaRebuildClient.messageOf("", "HTTP 200"));
        assertEquals("HTTP 502: nope", ReportingSchemaRebuildClient.messageOf("nope", "HTTP 502"));
    }
}
