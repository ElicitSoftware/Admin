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

import com.elicitsoftware.model.Department;
import com.elicitsoftware.model.ReportDefinition;
import com.elicitsoftware.model.Status;
import com.elicitsoftware.model.Survey;
import com.elicitsoftware.test.PostgresTestResource;
import com.sun.net.httpserver.HttpServer;
import com.vaadin.browserless.quarkus.QuarkusBrowserlessTest;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Booted test for {@link ReportingService#printReports(Status)} against embedded HTTP stub
 * servers standing in for the external report service(s) referenced by {@link ReportDefinition#url}.
 *
 * <p>Traceability: UC-005 (Generate and Download Survey Report). Closes the coverage gap flagged
 * in the 2026-09-03 best-practices audit: {@code ReportingService} had zero test coverage. Per
 * this project's convention (see {@code EmailServiceTest}), a real network endpoint is used
 * instead of Mockito/WireMock — here a plain {@link HttpServer} standing in for the dynamically
 * built {@code ReportService} REST client.</p>
 *
 * <p>Extends {@code QuarkusBrowserlessTest} because {@code printReports} unconditionally opens the
 * generated PDF via {@code UI.getCurrent()} on success and shows a {@code Notification} on failure
 * — both require a live UI, which only the browserless mock provides outside a real request.</p>
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class ReportingServiceTest extends QuarkusBrowserlessTest {

    @Inject
    ReportingService reportingService;

    private HttpServer okServer;
    private HttpServer failingServer;
    private final AtomicInteger okServerRequests = new AtomicInteger(0);
    private final AtomicInteger failingServerRequests = new AtomicInteger(0);

    /** Starts embedded HTTP stub servers on ephemeral local ports before each test. */
    @BeforeEach
    void startStubServers() throws IOException {
        okServerRequests.set(0);
        failingServerRequests.set(0);

        okServer = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        okServer.createContext("/", exchange -> {
            okServerRequests.incrementAndGet();
            byte[] body = "{\"title\":\"UC-005 Stub Report\",\"innerHTML\":\"<p>ok</p>\"}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        okServer.start();

        failingServer = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        failingServer.createContext("/", exchange -> {
            failingServerRequests.incrementAndGet();
            exchange.sendResponseHeaders(500, -1);
            exchange.close();
        });
        failingServer.start();
    }

    /** Stops the embedded stub servers after each test. */
    @AfterEach
    void stopStubServers() {
        if (okServer != null) {
            okServer.stop(0);
        }
        if (failingServer != null) {
            failingServer.stop(0);
        }
    }

    private long persistDepartment(String token) {
        Department department = new Department();
        department.name = "ReportingService Dept " + token;
        department.code = "RS-" + token;
        department.defaultMessageId = "1";
        department.fromEmail = "rs@example.org";
        department.persist();
        return department.id;
    }

    private ReportDefinition persistReportDefinition(Survey survey, String name, String baseUrl) {
        ReportDefinition rpt = new ReportDefinition();
        rpt.survey = survey;
        rpt.name = name;
        rpt.description = "UC-005 test report definition";
        rpt.url = baseUrl;
        rpt.displayOrder = 1;
        rpt.persist();
        // survey.reports is @OneToMany(fetch = EAGER) and was already initialized when the caller
        // fetched `survey`, so the newly persisted row must be added in-memory too — otherwise
        // printReports's own Survey.findById(...) returns the same managed, stale-collection
        // instance from the shared persistence context and never sees this report definition.
        survey.reports.add(rpt);
        return rpt;
    }

    /**
     * UC-005: a single report definition pointing at a healthy stub server is called and its
     * response is folded into the generated PDF without an exception propagating out of
     * {@code printReports}.
     */
    @Test
    @TestTransaction
    void printReportsCallsSingleHealthyReportEndpoint() {
        Survey survey = Survey.findById(1L);
        long deptId = persistDepartment("RS-TOK-1");

        String baseUrl = "http://localhost:" + okServer.getAddress().getPort();
        persistReportDefinition(survey, "UC-005 Report A", baseUrl);

        Status status = new Status();
        status.setSurveyId(1L);
        status.setDepartmentId(deptId);
        status.setRespondentId(1L);
        status.setEmail("rs1@example.org");
        status.setToken("rs-token-1");

        assertDoesNotThrow(() -> reportingService.printReports(status));

        assertEquals(1, okServerRequests.get(), "the healthy stub server must have received exactly one request");
    }

    /**
     * UC-005: when a survey has two report definitions and one endpoint fails (HTTP 500), the
     * failure is captured per-report and does not stop the other report definition from being
     * called — partial-failure tolerance.
     */
    @Test
    @TestTransaction
    void printReportsToleratesPartialFailureAcrossMultipleReportEndpoints() {
        Survey survey = Survey.findById(1L);
        long deptId = persistDepartment("RS-TOK-2");

        String okUrl = "http://localhost:" + okServer.getAddress().getPort();
        String failingUrl = "http://localhost:" + failingServer.getAddress().getPort();
        persistReportDefinition(survey, "UC-005 Report OK", okUrl);
        persistReportDefinition(survey, "UC-005 Report Failing", failingUrl);

        Status status = new Status();
        status.setSurveyId(1L);
        status.setDepartmentId(deptId);
        status.setRespondentId(2L);
        status.setEmail("rs2@example.org");
        status.setToken("rs-token-2");

        assertDoesNotThrow(() -> reportingService.printReports(status));

        assertEquals(1, okServerRequests.get(), "the healthy endpoint must have been called");
        assertTrue(failingServerRequests.get() >= 1, "the failing endpoint must also have been attempted");
    }
}
