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
3. For each report definition, the system requests the report content from the report service for that respondent, at the definition's address resolved per BR-023, with the service token when that address's origin is trusted (UC-018 BR-160), telling the service which language the respondent answered in (BR-022).
4. The system assembles the responses into a single multi-page PDF.
5. The system caches the PDF and opens it in a new browser tab for viewing or download, named after the survey (BR-187).

## Alternative Flows

### A1: Report action unavailable

**Trigger:** The subject's survey is not finished (step 1).
**Flow:**

1. The system does not offer the report action for that subject.

### A2: External report request fails

**Trigger:** The report service is unreachable or rejects the request (step 3).
**Flow:**

1. The system renders an error block in place of the failed report section and continues assembling the rest of the PDF with the available content. The block names the report and says what kind of failure it was (BR-140). A report at a trusted origin is not requested at all when this console has no service token (UC-018 BR-161); its block says that the service token is not configured. A trusted service that answers 401 has a different token; its block says that the service refused the service token.

### A3: PDF assembly fails

**Trigger:** The PDF cannot be produced (step 4).
**Flow:**

1. The system notifies the user that report generation failed, with a reference the log carries with the cause; the cause itself is not shown (NFR-023).

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

A generated report is retrievable through a temporary, unguessable link that expires after ten minutes. An expired report is removed from memory within a minute of expiring, whether or not it was fetched, and at most 200 reports wait at once; when that limit is reached the oldest is dropped.

### BR-022: The request names the respondent's language

The report request carries, besides the respondent's id, the BCP-47 tag of the language the respondent answered in: the language Survey stamped on their answer rows, or none when they answered in the survey's base language. Survey's own report request has carried the same field since its content translations; a service that renders in one language ignores it, and Survey's built-in report renders in it.

### BR-023: A relative report address is resolved under Survey

A report definition's URL is used as written when it is absolute. When it is a path, it is resolved under this site's Survey address (`elicit.survey.url`), keeping any path prefix Survey runs under, because a report built into Survey is authored without knowing the site, while a site-specific service such as the Family History Survey's keeps its absolute URL. The resolved address decides whether the request carries the service token: a path resolves under `elicit.survey.url`, whose origin is always trusted, and an absolute URL receives the token only when its origin is listed in `elicit.service.trusted-origins` (UC-018 BR-160).

### BR-187: The PDF is named after the survey

The PDF is offered as `<name>-report.pdf`, where `<name>` is the survey's name in lower case with every run of characters other than the letters `a`–`z` and the digits made one hyphen, and no hyphen at either end (the Family History Survey's PDF is `family-history-survey-report.pdf`). A survey whose name leaves nothing gives `report.pdf`. Survey names the respondent's PDF the same way (Survey UC-005 BR-022).

### BR-136: A print holds only its own respondent's reports

Each request to print assembles its PDF from the answers the report services gave to that request alone. Nothing gathered for one print is shared with another, whoever started them and however close together.

### BR-140: A failed report shows a status, not the service's answer

The error block that stands in for a failed report names the report and gives the HTTP status the service answered with, the fixed license-check text for a 403, or — when the service could not be called or its answer could not be read — a fixed sentence pointing to the application log. The body of the service's answer and the technical cause are written to the log only and are never reproduced on the page or in the PDF. A redirect answered by a report service is not followed.

---

## Reference

Derived from `ReportingService`, `ReportService`, `PDFService`, `PDFDownloadResource`, `Survey`, and `ReportDefinition`.
