package com.elicitsoftware.report;

/*-
 * ***LICENSE_START***
 * Elicit Survey
 * %%
 * Copyright (C) 2025 The Regents of the University of Michigan - Rogel Cancer Center
 * %%
 * PolyForm Noncommercial License 1.0.0
 * <https://polyformproject.org/licenses/noncommercial/1.0.0>
 * ***LICENSE_END***
 */

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

/**
 * ReportService is a RESTful client interface for interacting with external report generation services.
 * <p>
 * This interface defines a REST client that communicates with external report services to
 * generate and retrieve report data. It uses MicroProfile REST Client annotations to
 * automatically generate HTTP client implementations for service-to-service communication.
 * <p>
 * The service follows a simple request-response pattern where:
 * - A {@link ReportRequest} containing report parameters is sent via POST
 * - The external service processes the request and generates report content
 * - A {@link ReportResponse} with multiple format options is returned
 * <p>
 * Key features:
 * - **Automatic client generation** via MicroProfile REST Client
 * - **JSON payload handling** for request and response data
 * - **Service discovery** integration for microservice architectures
 * - **Type-safe communication** with strongly-typed request/response objects
 * - **Fault tolerance** support through MicroProfile integration
 * <p>
 * Configuration:
 * The REST client can be configured through MicroProfile Config properties:
 * <pre>
 * # Base URL for the report service
 * com.elicitsoftware.report.ReportService/mp-rest/url=http://report-service:8080
 *
 * # Connection timeout
 * com.elicitsoftware.report.ReportService/mp-rest/connectTimeout=5000
 *
 * # Read timeout
 * com.elicitsoftware.report.ReportService/mp-rest/readTimeout=30000
 * </pre>
 * <p>
 * Usage example:
 * <pre>
 * {@code
 * @Inject
 * @RestClient
 * ReportService reportService;
 *
 * public ReportResponse generateReport(int reportId) {
 *     ReportRequest request = new ReportRequest(reportId);
 *     try {
 *         return reportService.callReport(request);
 *     } catch (Exception e) {
 *         log.error("Failed to generate report for ID: " + reportId, e);
 *         throw new ReportGenerationException("Report generation failed", e);
 *     }
 * }
 * }
 * </pre>
 *
 * @see ReportRequest
 * @see ReportResponse
 * @see RegisterRestClient
 * @since 1.0.0
 */
@Path("/")
@RegisterRestClient
public interface ReportService {
    /**
     * Calls the external report service to generate a report based on the provided request.
     * <p>
     * This method sends a POST request to the external report generation service with
     * the specified report parameters. The service processes the request and returns
     * a comprehensive report response containing multiple format representations.
     * <p>
     * The method handles:
     * - **JSON serialization** of the request object
     * - **HTTP POST communication** with the external service
     * - **JSON deserialization** of the response object
     * - **Error handling** for network and service failures
     * <p>
     * Request processing flow:
     * 1. Serialize ReportRequest to JSON
     * 2. Send POST request to configured service endpoint
     * 3. Wait for service to process and generate report
     * 4. Receive and deserialize ReportResponse from JSON
     * 5. Return complete response with all format options
     * <p>
     * The external service is expected to:
     * - Accept JSON payloads with ReportRequest structure
     * - Process the report ID and generate appropriate content
     * - Return JSON responses with ReportResponse structure
     * - Include both HTML and PDF representations when applicable
     * <p>
     * Error scenarios:
     * - **Network failures**: Connection timeouts, DNS resolution issues
     * - **Service unavailable**: External service down or overloaded
     * - **Invalid request**: Malformed JSON or missing required fields
     * - **Processing errors**: Report generation failures on external service
     * - **Response parsing**: Invalid JSON response structure
     * <p>
     * Timeout configuration:
     * The method respects MicroProfile REST Client timeout settings:
     * - Connect timeout: Time to establish connection
     * - Read timeout: Time to wait for response after request sent
     *
     * @param request The report request containing parameters for report generation;
     *                must not be null and should contain a valid report ID
     * @return ReportResponse containing the generated report in multiple formats
     *         (HTML for web display, PDF for download); never null but fields may be null
     * @throws jakarta.ws.rs.ProcessingException if network communication fails
     * @throws jakarta.ws.rs.WebApplicationException if the service returns an error status (including license validation errors)
     * @throws java.lang.IllegalArgumentException if the request is null or invalid
     * @see ReportRequest
     * @see ReportResponse
     * @see org.eclipse.microprofile.rest.client.inject.RegisterRestClient
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    ReportResponse callReport(ReportRequest request);
}
