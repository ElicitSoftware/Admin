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

/**
 * ReportRequest represents the payload used for requesting a report from the ReportService.
 * <p>
 * This class serves as a data transfer object (DTO) that encapsulates the parameters
 * necessary to identify and request a specific report from the report generation service.
 * It is designed to be serialized as JSON for REST API communication with external
 * report services.
 * <p>
 * The class currently contains a single identifier field, but can be extended in the
 * future to include additional report parameters such as:
 * - Report type or template selection
 * - Date ranges or filtering criteria
 * - Output format preferences
 * - User-specific parameters
 * <p>
 * Key features:
 * - Simple, lightweight structure for REST API consumption
 * - Immutable after construction (except through setters)
 * - JSON serialization compatible
 * - Extensible design for future report parameters
 * <p>
 * Usage example:
 * <pre>
 * {@code
 * ReportRequest request = new ReportRequest(12345);
 * ReportResponse response = reportService.callReport(request);
 * }
 * </pre>
 * 
 * @see ReportService#callReport(ReportRequest)
 * @see ReportResponse
 * @since 1.0.0
 */
public class ReportRequest {
    /**
     * Unique identifier for the report request.
     * <p>
     * This field serves as the primary key to identify which specific report
     * should be generated or retrieved from the report service. The ID typically
     * corresponds to a database record, survey instance, or other entity that
     * the report is based on.
     */
    private int id;

    /**
     * Constructs a new ReportRequest with the specified ID.
     * <p>
     * Creates a report request object that can be used to request a specific
     * report from the report service. The ID should correspond to a valid
     * entity that can be used to generate the requested report.
     *
     * @param id The unique identifier for the report request; should be positive
     */
    public ReportRequest(int id) {
        super();
        this.id = id;
    }

    /**
     * Returns the unique identifier for this report request.
     * <p>
     * The ID represents the primary key or identifier that the report service
     * will use to determine which report to generate or retrieve.
     *
     * @return The report request ID as an integer
     */
    public int getId() {
        return id;
    }

    /**
     * Sets the unique identifier for this report request.
     * <p>
     * Updates the ID that will be used by the report service to identify
     * which report to generate or retrieve. The ID should correspond to
     * a valid entity in the system.
     *
     * @param id The new report request ID; should be positive
     */
    public void setId(int id) {
        this.id = id;
    }
}
