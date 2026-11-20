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
package com.elicitsoftware.service;

import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** UC-005 BR-023: how a report definition's URL is resolved before the service is called. */
class ReportUriResolutionTest {

    @Test
    void absoluteUrlIsUsedAsWritten() throws URISyntaxException {
        assertEquals("http://host.docker.internal:8082/proband/report",
                ReportingService.resolveReportUri("http://survey:8080", "http://host.docker.internal:8082/proband/report").toString());
    }

    @Test
    void relativePathIsResolvedUnderSurveysBaseAddress() throws URISyntaxException {
        assertEquals("http://survey:8080/api/reports/respondent",
                ReportingService.resolveReportUri("http://survey:8080", "/api/reports/respondent").toString());
        assertEquals("http://survey:8080/api/reports/respondent",
                ReportingService.resolveReportUri("http://survey:8080/", "api/reports/respondent").toString());
    }

    @Test
    void relativePathKeepsASitesPathPrefix() throws URISyntaxException {
        assertEquals("https://example.org/survey/api/reports/respondent",
                ReportingService.resolveReportUri("https://example.org/survey", "/api/reports/respondent").toString());
    }
}
