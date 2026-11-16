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

import com.elicitsoftware.security.ServiceToken;
import org.junit.jupiter.api.Test;

import java.net.http.HttpRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UC-018: Apply Survey Definition and UC-030: Rename a Survey's Reporting Schema — the service
 * token on the two requests the console sends to Survey (BR-159), what happens without one
 * (BR-161, A7) and when Survey refuses it (A8), and that the token is in nothing the calls log
 * or report (NFR-024). The HTTP client is stubbed (no network).
 */
class ServiceTokenCallsTest {

    private static final UUID KEY = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String TOKEN = "unit-test-service-token-0123456789abcdef";
    private static final String NOT_CONFIGURED = "service token not configured (ELICIT_SERVICE_TOKEN is not set)";
    private static final String REFUSED =
            "the service refused the service token; ELICIT_SERVICE_TOKEN must have the same value on both services";

    private static ServiceToken token(String value) {
        return new ServiceToken(Optional.ofNullable(value), Optional.empty(), "http://survey:8080");
    }

    private static ReportingSchemaRebuildClient rebuilder(String token) {
        ReportingSchemaRebuildClient client = new ReportingSchemaRebuildClient();
        client.surveyUrl = "http://survey:8080";
        client.enabled = true;
        client.serviceToken = token(token);
        return client;
    }

    private static ReportingSchemaRenameClient renamer(String token) {
        ReportingSchemaRenameClient client = new ReportingSchemaRenameClient();
        client.surveyUrl = "http://survey:8080";
        client.serviceToken = token(token);
        return client;
    }

    /** Collects everything the two clients and the token log, with parameters and stack traces. */
    private static List<String> captureLog(Runnable action) {
        List<String> lines = new ArrayList<>();
        java.util.logging.Handler handler = new java.util.logging.Handler() {
            @Override
            public void publish(java.util.logging.LogRecord record) {
                StringBuilder line = new StringBuilder(String.valueOf(record.getMessage()))
                        .append(' ').append(java.util.Arrays.toString(record.getParameters()));
                if (record.getThrown() != null) {
                    java.io.StringWriter trace = new java.io.StringWriter();
                    record.getThrown().printStackTrace(new java.io.PrintWriter(trace));
                    line.append(' ').append(trace);
                }
                lines.add(line.toString());
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        List<java.util.logging.Logger> loggers = List.of(
                java.util.logging.Logger.getLogger(ReportingSchemaRebuildClient.class.getName()),
                java.util.logging.Logger.getLogger(ReportingSchemaRenameClient.class.getName()),
                java.util.logging.Logger.getLogger(ServiceToken.class.getName()));
        loggers.forEach(logger -> logger.addHandler(handler));
        try {
            action.run();
        } finally {
            loggers.forEach(logger -> logger.removeHandler(handler));
        }
        return lines;
    }

    // --- BR-159: the token goes with the request ----------------------------------------------

    @Test
    void given_aToken_when_theRebuildIsAsked_then_theRequestCarriesItAsABearerHeaderOnly() {
        ReportingSchemaRebuildClient client = rebuilder(TOKEN);
        StubHttpClient http = new StubHttpClient(StubHttpClient.answering(200, "{\"message\":\"built\"}")).installInto(client);

        assertEquals(ReportingSchemaRebuildClient.Status.REBUILT, client.rebuild(KEY).status());

        HttpRequest request = http.requests.get(0);
        assertEquals(List.of("Bearer " + TOKEN), request.headers().allValues("Authorization"));
        assertFalse(request.uri().toString().contains(TOKEN), "never in the address");
        assertEquals(java.net.http.HttpClient.Redirect.NEVER, http.followRedirects());
    }

    @Test
    void given_aToken_when_theRenameIsAsked_then_theRequestCarriesItAsABearerHeaderOnly() {
        ReportingSchemaRenameClient client = renamer(TOKEN);
        StubHttpClient http = new StubHttpClient(StubHttpClient.answering(200,
                "{\"status\":\"renamed\",\"message\":\"ok\",\"schema\":\"r2\"}")).installInto(client);

        assertEquals(ReportingSchemaRenameClient.Status.RENAMED, client.rename(KEY, "r2").status());

        HttpRequest request = http.requests.get(0);
        assertEquals(List.of("Bearer " + TOKEN), request.headers().allValues("Authorization"));
        assertFalse(request.uri().toString().contains(TOKEN), "never in the address");
    }

    /** The clients Survey is really called with never follow a redirect (BR-159). */
    @Test
    void given_theRealClients_when_inspected_then_redirectsAreNeverFollowed() throws Exception {
        for (Object client : new Object[]{new ReportingSchemaRebuildClient(), new ReportingSchemaRenameClient()}) {
            java.lang.reflect.Field field = client.getClass().getDeclaredField("client");
            field.setAccessible(true);
            assertEquals(java.net.http.HttpClient.Redirect.NEVER,
                    ((java.net.http.HttpClient) field.get(client)).followRedirects());
        }
    }

    // --- BR-161, A7: no token, no call ----------------------------------------------------------

    @Test
    void given_noToken_when_theRebuildIsAsked_then_nothingIsSentAndTheResultSaysWhy() {
        for (String unset : new String[]{null, "", "  "}) {
            ReportingSchemaRebuildClient client = rebuilder(unset);
            StubHttpClient http = new StubHttpClient(StubHttpClient.answering(200, "{}")).installInto(client);

            List<ReportingSchemaRebuildClient.Outcome> outcome = new ArrayList<>();
            List<String> log = captureLog(() -> outcome.add(client.rebuild(KEY)));

            assertTrue(http.requests.isEmpty(), "no request leaves without the token");
            assertEquals(ReportingSchemaRebuildClient.Status.FAILED, outcome.get(0).status());
            assertEquals(NOT_CONFIGURED, outcome.get(0).message());
            assertEquals("Reporting schema not rebuilt: " + NOT_CONFIGURED, outcome.get(0).summaryLine());
            assertEquals(1, log.size(), log.toString());
            assertTrue(log.get(0).startsWith("Reporting schema not rebuilt: service token not configured"), log.get(0));
        }
    }

    @Test
    void given_noToken_when_theRenameIsAsked_then_nothingIsSentAndTheResultSaysWhy() {
        ReportingSchemaRenameClient client = renamer(null);
        StubHttpClient http = new StubHttpClient(StubHttpClient.answering(200, "{}")).installInto(client);

        ReportingSchemaRenameClient.Outcome outcome = client.rename(KEY, "r2");

        assertTrue(http.requests.isEmpty(), "no request leaves without the token");
        assertEquals(new ReportingSchemaRenameClient.Outcome(ReportingSchemaRenameClient.Status.FAILED, NOT_CONFIGURED, null),
                outcome);
    }

    @Test
    void given_noTokenAndTheRebuildSwitchedOff_when_asked_then_itIsStillOnlySkipped() {
        ReportingSchemaRebuildClient client = rebuilder(null);
        client.enabled = false;
        assertEquals(ReportingSchemaRebuildClient.Status.SKIPPED, client.rebuild(KEY).status());
    }

    // --- A8: Survey has another token -----------------------------------------------------------

    @Test
    void given_surveyAnswers401_when_asked_then_theResultSaysTheTokenWasRefused() {
        ReportingSchemaRebuildClient rebuild = rebuilder(TOKEN);
        new StubHttpClient(StubHttpClient.answering(401, "")).installInto(rebuild);
        ReportingSchemaRebuildClient.Outcome rebuilt = rebuild.rebuild(KEY);
        assertEquals(ReportingSchemaRebuildClient.Status.FAILED, rebuilt.status());
        assertEquals(REFUSED, rebuilt.message());

        ReportingSchemaRenameClient rename = renamer(TOKEN);
        new StubHttpClient(StubHttpClient.answering(401, "")).installInto(rename);
        assertEquals(new ReportingSchemaRenameClient.Outcome(ReportingSchemaRenameClient.Status.FAILED, REFUSED, null),
                rename.rename(KEY, "r2"));
    }

    // --- NFR-024: the token is in no log line and no result --------------------------------------

    @Test
    void given_everyOutcome_when_theCallsAreMade_then_theTokenIsInNoLogLineAndNoResult() {
        List<StubHttpClient.Script> scripts = List.of(
                StubHttpClient.answering(200, "{\"message\":\"built\"}"),
                StubHttpClient.answering(401, ""),
                StubHttpClient.answering(409, "{\"status\":\"unbuilt\"}"),
                StubHttpClient.answering(500, "<html>error</html>"),
                StubHttpClient.answering(503, "{\"error\":\"service token not configured\"}"),
                request -> {
                    throw new java.net.http.HttpTimeoutException("timed out");
                },
                request -> {
                    throw new java.io.IOException("failed: " + request.uri());
                });
        List<String> results = new ArrayList<>();
        List<String> log = captureLog(() -> {
            for (StubHttpClient.Script script : scripts) {
                ReportingSchemaRebuildClient rebuild = rebuilder(TOKEN);
                new StubHttpClient(script).installInto(rebuild);
                ReportingSchemaRebuildClient.Outcome rebuilt = rebuild.rebuild(KEY);
                results.add(rebuilt + " " + rebuilt.summaryLine());

                ReportingSchemaRenameClient rename = renamer(TOKEN);
                new StubHttpClient(script).installInto(rename);
                results.add(String.valueOf(rename.rename(KEY, "r2")));
            }
        });

        assertEquals(scripts.size() * 2, results.size());
        assertFalse(log.isEmpty(), "the calls were logged");
        assertTrue(log.stream().noneMatch(line -> line.contains(TOKEN)), "the token is in no log line");
        assertTrue(log.stream().noneMatch(line -> line.contains("Bearer")), "nor is the header");
        assertTrue(results.stream().noneMatch(line -> line.contains(TOKEN)), "the token is in no result");
    }
}
