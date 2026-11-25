package com.elicitsoftware.service;

/*-
 * ***LICENSE_START***
 * Elicit Admin
 * %%
 * Copyright (C) 2025 - 2026 Elicit Software LLC
 * %%
 * PolyForm Noncommercial License 1.0.0
 * <https://polyformproject.org/licenses/noncommercial/1.0.0>
 * ***LICENSE_END***
 */

import com.elicitsoftware.model.ReportDefinition;
import com.elicitsoftware.report.ReportResponse;
import com.elicitsoftware.security.ServiceToken;
import com.elicitsoftware.test.PostgresTestResource;
import com.elicitsoftware.test.ScriptedEntityManager;
import com.sun.net.httpserver.HttpServer;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UC-005: Generate and Download a Subject Report — which report requests carry the service token
 * (BR-023; UC-018 BR-160, BR-161), over real HTTP against two local stand-ins for report
 * services: one at an origin the configuration trusts and one it does not.
 * <p>
 * Also NFR-024: the token is in no log line, no error block and no recorded span.
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class ReportingServiceServiceTokenTest {

    private static final String TOKEN = "report-test-service-token-0123456789abcdef";
    private static final String REPORT = "{\"title\":\"Summary\",\"innerHTML\":\"<p>ok</p>\"}";

    /** A stand-in report service that records what each request presented. */
    private static final class Stub {
        final HttpServer server;
        final List<String> authorizations = Collections.synchronizedList(new ArrayList<>());
        final List<String> paths = Collections.synchronizedList(new ArrayList<>());
        volatile int status = 200;
        volatile String location;

        Stub() throws IOException {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                paths.add(exchange.getRequestURI().getPath());
                authorizations.add(String.valueOf(exchange.getRequestHeaders().getFirst("Authorization")));
                exchange.getRequestBody().readAllBytes();
                byte[] bytes = (status == 200 ? REPORT : "").getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                if (location != null) {
                    exchange.getResponseHeaders().add("Location", location);
                }
                exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(bytes);
                }
            });
            server.start();
        }

        String origin() {
            return "http://127.0.0.1:" + server.getAddress().getPort();
        }

        void reset() {
            authorizations.clear();
            paths.clear();
            status = 200;
            location = null;
        }
    }

    private static Stub survey;
    private static Stub listed;
    private static Stub unlisted;

    @Inject
    InMemorySpanExporter spans;

    @BeforeAll
    static void startStubs() throws IOException {
        survey = new Stub();
        listed = new Stub();
        unlisted = new Stub();
    }

    @AfterAll
    static void stopStubs() {
        survey.server.stop(0);
        listed.server.stop(0);
        unlisted.server.stop(0);
    }

    @BeforeEach
    void resetStubs() {
        survey.reset();
        listed.reset();
        unlisted.reset();
    }

    /** The service as this site would configure it: Survey at one stand-in, one more origin trusted. */
    private static ReportingService service(String token) {
        ReportingService service = new ReportingService();
        service.em = new ScriptedEntityManager(call -> List.of()).entityManager();
        service.surveyUrl = survey.origin() + "/elicit";
        service.serviceToken = new ServiceToken(Optional.ofNullable(token), Optional.of(listed.origin()), service.surveyUrl);
        return service;
    }

    private static ReportResponse call(ReportingService service, String url) {
        ReportDefinition report = new ReportDefinition();
        report.name = "Summary";
        report.url = url;
        return service.callReport(report, 17);
    }

    /** Collects everything the report call and the token log, with parameters and stack traces. */
    private static List<String> captureLog(Runnable action) {
        List<String> lines = Collections.synchronizedList(new ArrayList<>());
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
        java.util.logging.Logger root = java.util.logging.Logger.getLogger("");
        root.addHandler(handler);
        try {
            action.run();
        } finally {
            root.removeHandler(handler);
        }
        return lines;
    }

    // --- BR-160: who receives the token ---------------------------------------------------------

    /** UC-005 BR-023: a path resolves under the Survey address, whose origin is always trusted. */
    @Test
    void given_aRelativeAddress_when_theReportIsRequested_then_surveyReceivesTheToken() {
        ReportResponse response = call(service(TOKEN), "/api/reports/respondent-summary");

        assertEquals("Summary", response.title);
        assertEquals(List.of("/elicit/api/reports/respondent-summary"), survey.paths);
        assertEquals(List.of("Bearer " + TOKEN), survey.authorizations);
    }

    @Test
    void given_anAbsoluteAddressAtAListedOrigin_when_theReportIsRequested_then_itReceivesTheToken() {
        ReportResponse response = call(service(TOKEN), listed.origin() + "/proband/report");

        assertEquals("Summary", response.title);
        assertEquals(List.of("Bearer " + TOKEN), listed.authorizations);
    }

    @Test
    void given_anAbsoluteAddressElsewhere_when_theReportIsRequested_then_itIsCalledWithoutTheToken() {
        ReportResponse response = call(service(TOKEN), unlisted.origin() + "/report");

        assertEquals("Summary", response.title, "the report is still requested, as before");
        assertEquals(List.of("null"), unlisted.authorizations);
    }

    /** The trusted origin as user information of another host: the request goes to that host, bare. */
    @Test
    void given_anAddressThatOnlyLooksTrusted_when_theReportIsRequested_then_noTokenLeaves() {
        String disguised = "http://" + listed.origin().substring("http://".length()) + "@127.0.0.1:"
                + unlisted.server.getAddress().getPort() + "/report";

        call(service(TOKEN), disguised);

        assertTrue(listed.authorizations.isEmpty(), "the trusted service was not the destination");
        assertEquals(1, unlisted.authorizations.size(), "the request went to the other host");
        assertTrue(unlisted.authorizations.stream().noneMatch(a -> a.contains(TOKEN) || a.startsWith("Bearer")),
                "and carried no service token");
    }

    /** UC-005 BR-140: a redirect from a trusted service is not followed, so the token is not carried on. */
    @Test
    void given_aTrustedServiceThatRedirects_when_theReportIsRequested_then_theRedirectIsNotFollowed() {
        listed.status = 302;
        listed.location = unlisted.origin() + "/collect";

        ReportResponse response = call(service(TOKEN), listed.origin() + "/proband/report");

        assertEquals(1, listed.authorizations.size());
        assertTrue(unlisted.authorizations.isEmpty(), "nothing followed the redirect");
        assertEquals("Error - Summary", response.title);
    }

    // --- BR-161: without a token ----------------------------------------------------------------

    @Test
    void given_noToken_when_aTrustedReportIsRequested_then_itIsNotCalledAndTheBlockSaysWhy() {
        ReportingService service = service(null);

        ReportResponse relative = call(service, "/api/reports/respondent-summary");
        ReportResponse absolute = call(service, listed.origin() + "/proband/report");

        assertTrue(survey.authorizations.isEmpty(), "no request leaves without the token");
        assertTrue(listed.authorizations.isEmpty(), "no request leaves without the token");
        String reason = "service token not configured (ELICIT_SERVICE_TOKEN is not set)";
        assertEquals("Report Generation Error - Service: Summary - Error: " + reason, relative.pdf.content[0].text);
        assertEquals("Report Generation Error - Service: Summary - Error: " + reason, absolute.pdf.content[0].text);
        assertTrue(relative.innerHTML.contains(reason));
    }

    @Test
    void given_noToken_when_aReportElsewhereIsRequested_then_itIsCalledAsBefore() {
        ReportResponse response = call(service(null), unlisted.origin() + "/report");

        assertEquals("Summary", response.title);
        assertEquals(List.of("null"), unlisted.authorizations);
    }

    // --- UC-005 A2: the trusted service has another token ---------------------------------------

    @Test
    void given_a401_when_theTokenWasSent_then_theBlockSaysTheTokenWasRefused() {
        listed.status = 401;
        unlisted.status = 401;

        ReportResponse trusted = call(service(TOKEN), listed.origin() + "/proband/report");
        ReportResponse other = call(service(TOKEN), unlisted.origin() + "/report");

        assertEquals("Report Generation Error - Service: Summary - Error: the service refused the service token;"
                + " ELICIT_SERVICE_TOKEN must have the same value on both services", trusted.pdf.content[0].text);
        assertTrue(other.pdf.content[0].text.contains("Service error (HTTP 401)"), other.pdf.content[0].text);
    }

    // --- NFR-024: the token is written nowhere --------------------------------------------------

    @Test
    void given_reportCallsOfEveryOutcome_when_made_then_theTokenIsInNoLogLineErrorBlockOrSpan() throws Exception {
        spans.reset();
        List<ReportResponse> responses = new ArrayList<>();
        List<String> log = captureLog(() -> {
            ReportingService service = service(TOKEN);
            responses.add(call(service, "/api/reports/respondent-summary"));
            responses.add(call(service, listed.origin() + "/proband/report"));
            listed.status = 401;
            responses.add(call(service, listed.origin() + "/proband/report"));
            listed.status = 500;
            responses.add(call(service, listed.origin() + "/proband/report"));
            listed.status = 302;
            listed.location = unlisted.origin() + "/collect";
            responses.add(call(service, listed.origin() + "/proband/report"));
            responses.add(call(service, "http://127.0.0.1:1/unreachable"));
            responses.add(call(service(null), listed.origin() + "/proband/report"));
        });

        assertEquals(5, listed.authorizations.size() + survey.authorizations.size(), "the token was really sent");
        assertFalse(log.isEmpty(), "the failures were logged");
        assertTrue(log.stream().noneMatch(line -> line.contains(TOKEN)), "the token is in no log line");
        for (ReportResponse response : responses) {
            assertFalse(String.valueOf(response.title).contains(TOKEN));
            assertFalse(String.valueOf(response.innerHTML).contains(TOKEN));
            if (response.pdf != null && response.pdf.content != null) {
                assertFalse(String.valueOf(response.pdf.content[0].text).contains(TOKEN));
            }
        }

        // The client spans of the calls that carried the token, once the exporter has them.
        String trustedPort = ":" + listed.server.getAddress().getPort();
        List<SpanData> recorded = List.of();
        long deadline = System.currentTimeMillis() + 20_000;
        while (System.currentTimeMillis() < deadline) {
            recorded = spans.getFinishedSpanItems();
            if (recorded.stream().filter(span -> span.toString().contains(trustedPort)).count() >= 4) {
                break;
            }
            Thread.sleep(200);
        }
        assertTrue(recorded.stream().anyMatch(span -> span.toString().contains(trustedPort)),
                "the calls that carried the token were traced");
        for (SpanData span : recorded) {
            assertFalse(span.toString().contains(TOKEN), "the token is in no span: " + span.getName());
            assertFalse(span.getAttributes().toString().contains("Bearer"), "nor is the header: " + span.getName());
        }
    }
}
