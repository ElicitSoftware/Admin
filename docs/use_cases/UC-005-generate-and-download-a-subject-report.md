# Use Case: Generate and Download a Subject Report

## Overview

**Use Case ID:** UC-005
**Use Case Name:** Generate and Download a Subject Report
**Primary Actor:** Survey User
**Supporting Actor:** Report Service
**Goal:** Produce a subject's completed-survey report as a PDF and open it for viewing or download.
**Status:** Implemented

## Preconditions

- The user is authenticated with the `elicit_user` or `elicit_admin` role (UC-001).
- The subject's survey is finished.
- The survey has one or more report definitions.

## Main Success Scenario

1. The user selects a finished subject and chooses to generate reports.
2. The system loads the survey and its report definitions.
3. For each report definition, the system requests the report content from the external report service for that respondent.
4. The system assembles the responses into a single multi-page PDF.
5. The system caches the PDF and opens it in a new browser tab for viewing or download.

## Alternative Flows

### A1: Report action unavailable

**Trigger:** The subject's survey is not finished (step 1).
**Flow:**

1. The system does not offer the report action for that subject.

### A2: External report request fails

**Trigger:** The report service is unreachable or rejects the request (step 3).
**Flow:**

1. The system renders an error block in place of the failed report section and continues assembling the rest of the PDF with the available content.

### A3: PDF assembly fails

**Trigger:** The PDF cannot be produced (step 4).
**Flow:**

1. The system notifies the user that report generation failed.

### A4: Download link expired

**Trigger:** The user opens the download link after it has expired (step 5).
**Flow:**

1. The system reports that the requested report is no longer available.

## Postconditions

### Success Postconditions

- A PDF report is available to the user via a temporary download link.

### Failure Postconditions

- No complete PDF is delivered; the user sees an error, or the PDF contains error blocks for the sections that failed.

## Business Rules

### BR-019: Reports only for finished surveys

A report may only be generated for a subject whose survey is finished.

### BR-020: One PDF aggregates all report definitions

A single generated PDF combines the output of all of the survey's report definitions.

### BR-021: Download link expiry

A generated report is retrievable through a temporary, unguessable link that expires after a limited time.

---

## Reference

Derived from `ReportingService`, `ReportService`, `PDFService`, `PDFDownloadResource`, `Survey`, and `ReportDefinition`.
