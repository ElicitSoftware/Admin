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

import com.elicitsoftware.service.ReportingSchemaRebuildClient.Outcome;
import com.elicitsoftware.service.ReportingSchemaRenameClient.Status;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UC-018: Apply Survey Definition (the reporting-schema rebuild Admin asks Survey for) and UC-030:
 * Rename a Survey's Reporting Schema — how the two Survey clients build their calls and read
 * Survey's answers, with the HTTP client stubbed (no network).
 * <p>
 * Covers BR-107 (the rebuild can be switched off and then makes no call).
 */
class ReportingSchemaClientsUnitTest {

    private static final UUID KEY = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String BUILD = "http://survey:8080/api/etl/build?survey=" + KEY;

    private static ReportingSchemaRebuildClient rebuilder(String surveyUrl) {
        ReportingSchemaRebuildClient client = new ReportingSchemaRebuildClient();
        client.surveyUrl = surveyUrl;
        client.enabled = true;
        return client;
    }

    private static Outcome rebuild(StubHttpClient.Script script) {
        ReportingSchemaRebuildClient client = rebuilder("http://survey:8080");
        new StubHttpClient(script).installInto(client);
        return client.rebuild(KEY);
    }

    private static ReportingSchemaRenameClient renamer(StubHttpClient.Script script) {
        ReportingSchemaRenameClient client = new ReportingSchemaRenameClient();
        client.surveyUrl = "http://survey:8080/";
        new StubHttpClient(script).installInto(client);
        return client;
    }

    // --- rebuild -------------------------------------------------------------------------

    @Test
    void given_theRebuildIsSwitchedOff_when_asked_then_itIsSkippedWithoutACall() {
        ReportingSchemaRebuildClient client = rebuilder("http://survey:8080");
        client.enabled = false;
        StubHttpClient http = new StubHttpClient(StubHttpClient.answering(200, "")).installInto(client);

        Outcome outcome = client.rebuild(KEY);

        assertEquals(ReportingSchemaRebuildClient.Status.SKIPPED, outcome.status());
        assertEquals("", outcome.message());
        assertNull(outcome.summaryLine());
        assertTrue(http.requests.isEmpty());
    }

    @Test
    void given_surveyRebuilds_when_asked_then_theOutcomeIsRebuiltWithSurveysMessage() {
        ReportingSchemaRebuildClient client = rebuilder("http://survey:8080");
        StubHttpClient http = new StubHttpClient(StubHttpClient.answering(200, "{\"message\":\"  built 15 steps  \"}"))
                .installInto(client);

        Outcome outcome = client.rebuild(KEY);

        assertEquals(ReportingSchemaRebuildClient.Status.REBUILT, outcome.status());
        assertEquals("built 15 steps", outcome.message());
        assertEquals("Reporting schema rebuilt.", outcome.summaryLine());
        HttpRequest request = http.requests.get(0);
        assertEquals(URI.create(BUILD), request.uri());
        assertEquals("POST", request.method());
        assertEquals(Optional.of("application/json"), request.headers().firstValue("Accept"));
        assertEquals(Optional.of(Duration.ofSeconds(60)), request.timeout());
        assertEquals(Optional.of(0L), request.bodyPublisher().map(HttpRequest.BodyPublisher::contentLength));
    }

    @Test
    void given_surveyRefuses_when_asked_then_theOutcomeIsFailedWithSurveysMessage() {
        Outcome outcome = rebuild(StubHttpClient.answering(500, "{\"message\":\"schema locked\"}"));
        assertEquals(ReportingSchemaRebuildClient.Status.FAILED, outcome.status());
        assertEquals("schema locked", outcome.message());
        assertEquals("Reporting schema not rebuilt: schema locked", outcome.summaryLine());
    }

    @Test
    void given_surveysEtlIsDisabled_when_asked_then_theReasonIsInTheAdministratorsLanguage() {
        Outcome outcome = rebuild(StubHttpClient.answering(409, "{\"message\":\"ETL off\"}"));
        assertEquals(ReportingSchemaRebuildClient.Status.FAILED, outcome.status());
        assertEquals("Reporting ETL is disabled (elicit.etl.enabled=false)", outcome.message());
    }

    @Test
    void given_anEmptyAnswer_when_asked_then_theHttpStatusIsTheMessage() {
        assertEquals("HTTP 502", rebuild(StubHttpClient.answering(502, "")).message());
        assertEquals("HTTP 200", rebuild(StubHttpClient.answering(200, null)).message());
    }

    @Test
    void given_noAnswerInTime_when_asked_then_theTimeoutIsNamed() {
        Outcome outcome = rebuild(r -> {
            throw new HttpTimeoutException("timed out");
        });
        assertEquals(ReportingSchemaRebuildClient.Status.FAILED, outcome.status());
        assertEquals("no answer from " + BUILD + " within 60 s", outcome.message());
    }

    @Test
    void given_theWaitIsInterrupted_when_asked_then_theThreadStaysInterrupted() {
        try {
            Outcome outcome = rebuild(r -> {
                throw new InterruptedException();
            });
            assertEquals(ReportingSchemaRebuildClient.Status.FAILED, outcome.status());
            assertEquals("interrupted while waiting for " + BUILD, outcome.message());
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void given_surveyCannotBeReached_when_asked_then_theRootCauseAndEndpointAreNamed() {
        Outcome outcome = rebuild(r -> {
            throw new IOException("wrapped", new ConnectException("Connection refused"));
        });
        assertEquals(ReportingSchemaRebuildClient.Status.FAILED, outcome.status());
        assertEquals("Connection refused (" + BUILD + ")", outcome.message());
    }

    @Test
    void given_surveyUrls_when_theEndpointIsBuilt_then_trailingSlashesAndSpacesGo() {
        assertEquals("http://survey:8080/api/etl/build", rebuilder(" http://survey:8080// ").endpoint());
        assertEquals("http://survey:8080/api/etl/build", rebuilder("http://survey:8080").endpoint(null));
        assertEquals(BUILD, rebuilder("http://survey:8080/").endpoint(KEY));
        assertEquals("", ReportingSchemaRebuildClient.baseUrl(null));
        assertEquals("", ReportingSchemaRebuildClient.baseUrl("///"));
    }

    @Test
    void given_answerBodies_when_read_then_theMessageOrAFallbackIsTaken() {
        assertEquals("fb", ReportingSchemaRebuildClient.messageOf(null, "fb"));
        assertEquals("fb", ReportingSchemaRebuildClient.messageOf("   ", "fb"));
        assertEquals("fb", ReportingSchemaRebuildClient.messageOf("{\"status\":\"ok\"}", "fb"));
        assertEquals("fb", ReportingSchemaRebuildClient.messageOf("{\"message\":\"  \"}", "fb"));
        assertEquals("fb: <html>oops</html>", ReportingSchemaRebuildClient.messageOf("  <html>oops</html> ", "fb"));
        assertEquals("fb: [1]", ReportingSchemaRebuildClient.messageOf("[1]", "fb"));
        String exactly200 = "x".repeat(200);
        assertEquals("fb: " + exactly200, ReportingSchemaRebuildClient.messageOf(exactly200, "fb"));
        assertEquals("fb: " + exactly200 + "...", ReportingSchemaRebuildClient.messageOf(exactly200 + "yz", "fb"));
    }

    @Test
    void given_exceptionChains_when_theRootMessageIsTaken_then_theInnermostSpeaks() {
        assertEquals("inner", ReportingSchemaRebuildClient.rootMessage(
                new RuntimeException("outer", new IllegalStateException("middle", new IOException("inner")))));
        assertEquals("IOException", ReportingSchemaRebuildClient.rootMessage(new RuntimeException("outer", new IOException())));
        assertEquals("IOException", ReportingSchemaRebuildClient.rootMessage(new IOException(" ")));
        assertEquals("alone", ReportingSchemaRebuildClient.rootMessage(new IOException("alone")));
    }

    // --- rename --------------------------------------------------------------------------

    @Test
    void given_candidateNames_when_checked_then_onlyValidUnreservedNamesPass() {
        assertEquals("A schema name is required.", ReportingSchemaRenameClient.objection(null));
        assertEquals("A schema name is required.", ReportingSchemaRenameClient.objection(" "));
        String pattern = "Use 1 to 63 lower-case letters, digits and underscores, not starting with a digit.";
        assertEquals(pattern, ReportingSchemaRenameClient.objection("Report"));
        assertEquals(pattern, ReportingSchemaRenameClient.objection("1report"));
        assertEquals(pattern, ReportingSchemaRenameClient.objection("a".repeat(64)));
        assertNull(ReportingSchemaRenameClient.objection("a".repeat(63)));
        assertNull(ReportingSchemaRenameClient.objection("_r2"));
        for (String reserved : new String[]{"survey", "surveyreport", "public", "information_schema", "pg_catalog"}) {
            assertEquals("The name " + reserved + " is reserved.", ReportingSchemaRenameClient.objection(reserved));
        }
        assertNull(ReportingSchemaRenameClient.objection("report_family"));
        assertNull(ReportingSchemaRenameClient.objection("surveys"));
    }

    @Test
    void given_aNewName_when_theEndpointIsBuilt_then_theNameIsEncoded() {
        ReportingSchemaRenameClient client = new ReportingSchemaRenameClient();
        client.surveyUrl = "http://survey:8080//";
        assertEquals("http://survey:8080/api/etl/schema/" + KEY + "/rename?name=a+b%26c", client.endpoint(KEY, "a b&c"));
    }

    @Test
    void given_surveyAnswers_when_read_then_eachStatusMapsToAnOutcome() {
        ReportingSchemaRenameClient.Outcome renamed = ReportingSchemaRenameClient.outcomeOf(200,
                "{\"status\":\"renamed\",\"message\":\"done\",\"schema\":\"report_x\"}", "e");
        assertEquals(new ReportingSchemaRenameClient.Outcome(Status.RENAMED, "done", "report_x"), renamed);

        assertEquals(Status.INVALID, ReportingSchemaRenameClient.outcomeOf(400, "{\"message\":\"bad\"}", "e").status());
        assertEquals(Status.UNKNOWN, ReportingSchemaRenameClient.outcomeOf(404, "{}", "e").status());
        assertEquals(Status.UNBUILT, ReportingSchemaRenameClient.outcomeOf(409, "{\"status\":\"unbuilt\"}", "e").status());
        assertEquals(Status.TAKEN, ReportingSchemaRenameClient.outcomeOf(409, "{\"status\":\"taken\"}", "e").status());
        assertEquals(Status.TAKEN, ReportingSchemaRenameClient.outcomeOf(409, "", "e").status());
        assertEquals(Status.FAILED, ReportingSchemaRenameClient.outcomeOf(500, "{}", "e").status());
        assertEquals(Status.FAILED, ReportingSchemaRenameClient.outcomeOf(201, "{}", "e").status());
    }

    @Test
    void given_answersWithoutAMessage_when_read_then_theHttpStatusIsTheMessage() {
        assertEquals(new ReportingSchemaRenameClient.Outcome(Status.FAILED, "HTTP 502", null),
                ReportingSchemaRenameClient.outcomeOf(502, "<html>proxy</html>", "e"));
        assertEquals(new ReportingSchemaRenameClient.Outcome(Status.INVALID, "HTTP 400", null),
                ReportingSchemaRenameClient.outcomeOf(400, null, "e"));
        assertEquals(new ReportingSchemaRenameClient.Outcome(Status.UNKNOWN, "HTTP 404", null),
                ReportingSchemaRenameClient.outcomeOf(404, "{\"message\":\" \",\"schema\":null}", "e"));
        assertEquals("HTTP 409", ReportingSchemaRenameClient.outcomeOf(409, "  ", "e").message());
    }

    @Test
    void given_surveyRenames_when_asked_then_thePostCarriesTheName() {
        StubHttpClient.Script ok = StubHttpClient.answering(200, "{\"status\":\"renamed\",\"message\":\"ok\",\"schema\":\"r2\"}");
        ReportingSchemaRenameClient client = renamer(ok);
        StubHttpClient http = new StubHttpClient(ok).installInto(client);

        ReportingSchemaRenameClient.Outcome outcome = client.rename(KEY, "r2");

        assertEquals(new ReportingSchemaRenameClient.Outcome(Status.RENAMED, "ok", "r2"), outcome);
        HttpRequest request = http.requests.get(0);
        assertEquals(URI.create("http://survey:8080/api/etl/schema/" + KEY + "/rename?name=r2"), request.uri());
        assertEquals("POST", request.method());
        assertEquals(Optional.of("application/json"), request.headers().firstValue("Accept"));
        assertEquals(Optional.of(Duration.ofSeconds(60)), request.timeout());
    }

    @Test
    void given_theRenameCallFails_when_asked_then_theFailureIsDescribed() {
        String endpoint = "http://survey:8080/api/etl/schema/" + KEY + "/rename?name=r2";
        ReportingSchemaRenameClient.Outcome timeout = renamer(r -> {
            throw new HttpTimeoutException("t");
        }).rename(KEY, "r2");
        assertEquals(new ReportingSchemaRenameClient.Outcome(Status.FAILED, "no answer from " + endpoint + " within 60 s", null), timeout);

        ReportingSchemaRenameClient.Outcome refused = renamer(r -> {
            throw new ConnectException("Connection refused");
        }).rename(KEY, "r2");
        assertEquals(new ReportingSchemaRenameClient.Outcome(Status.FAILED, "Connection refused (" + endpoint + ")", null), refused);

        try {
            ReportingSchemaRenameClient.Outcome interrupted = renamer(r -> {
                throw new InterruptedException();
            }).rename(KEY, "r2");
            assertEquals(new ReportingSchemaRenameClient.Outcome(Status.FAILED, "interrupted while waiting for " + endpoint, null),
                    interrupted);
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }
}
