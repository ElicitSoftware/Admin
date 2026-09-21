# Use Case: Check Connections

## Overview

**Use Case ID:** UC-025
**Use Case Name:** Check Connections
**Primary Actor:** Survey Administrator
**Goal:** Check each outbound dependency of the deployment with a bounded probe, so that an unreachable service is named here rather than discovered inside a generated report or a stalled survey.
**Status:** Tested

## Preconditions

- The administrator is authenticated and holds the `elicit_admin` role (UC-001).

## Main Success Scenario

1. The administrator opens Connections from the System section.
2. The system lists every outbound target it knows: the identity provider's discovery document, each report service address stored with a report definition, each post-survey action address stored with a survey, the mail relay, and the telemetry collector.
3. The administrator chooses Check all, or Check on one row.
4. For each web target the system issues a harmless read-only request and records whether an answer came back, its status, and how long it took; for each socket target it records whether a connection opened.
5. The system marks each target reachable or unreachable, with the detail beside it.
6. The administrator reads the result to find the dependency that is down or misaddressed.

## Alternative Flows

### A1: A target does not answer in time

**Trigger:** No answer within the bounded time (step 4).
**Flow:**

1. The system marks the target unreachable with "timed out" and the limit, and moves on to the next target.
2. Use case continues at step 5.

### A2: A report service answers forbidden

**Trigger:** A report or post-survey target answers with HTTP 403 (step 4).
**Flow:**

1. The system marks the target reachable and adds that its licence validation may have failed, the same reading the report generator gives that status.
2. Use case continues at step 5.

### A3: The identity provider answers but not with a discovery document

**Trigger:** The discovery address answers without an issuer (step 4).
**Flow:**

1. The system marks the provider reachable but misaddressed, showing the address it used.
2. Use case continues at step 5.

## Postconditions

### Success Postconditions

- Each target carries a fresh result; nothing is stored and no target's state is changed.

### Failure Postconditions

- None; unreachable targets are reported, not retried.

## Business Rules

### BR-096: Probes are harmless

A web target is probed with a read-only request and redirects are not followed; a post-survey action is never invoked, and no report is generated. A socket target is only connected to and closed.

### BR-097: Probes are bounded

Every probe gives up after five seconds (NFR-012). Checking all targets never takes longer than the sum of those limits, and one dead target never hides the others.

### BR-098: Targets come from where the runtime reads them

Report and post-survey addresses are read from the same stored rows the runtime uses (`survey.reports`, `survey.post_survey_actions`), and the provider, relay and collector addresses from the same configuration, so the screen checks what will actually be called.

---

## Reference

Traces to FR-025. The report generator (`ReportingService`) today folds an unreachable report
service into the rendered PDF as red text; this screen is the direct test of the same addresses.

Implemented by `ConnectionChecks` (targets from `survey.reports`, `survey.post_survey_actions` and
configuration; GET with redirects disabled or a TCP connect, each bounded to five seconds) and
`SystemConnectionsView`. Verified by `ConnectionChecksTest` and `SystemViewsRenderTest`.
