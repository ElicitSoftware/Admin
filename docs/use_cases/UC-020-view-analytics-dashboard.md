# Use Case: View Analytics Dashboard

## Overview

**Use Case ID:** UC-020
**Use Case Name:** View Analytics Dashboard
**Primary Actor:** Analyst
**Supporting Actors:** Analytics Service, OIDC Provider
**Goal:** Read the Survey Operations dashboard inside the admin console and, when deeper exploration is needed, open the full analytics tool without a second sign-in, so that aggregate survey results are one click away from the console an analyst already uses.
**Status:** Draft

## Preconditions

- The analyst is authenticated (UC-001) and holds the `elicit_analytics` role, from the identity provider or from the local role table (UC-016).
- This deployment is configured with the location of its analytics service and the identity of the dashboard to embed.
- The analytics service is running and trusts this console as a host for its embedded dashboards.

## Main Success Scenario

1. The analyst opens the admin console and sees an Analytics item in the navigation.
2. The analyst selects Analytics.
3. The system obtains short-lived, read-only access to the Survey Operations dashboard on the analyst's behalf.
4. The system displays the Analytics screen with the Survey Operations dashboard embedded in it, together with an "Open in Superset" link.
5. The analyst reads the dashboard, applies its filters, and downloads a chart as an image where needed.
6. The analyst selects "Open in Superset".
7. The system opens the full analytics tool in a new browser tab; the identity provider recognises the analyst's existing session and signs them in without prompting.
8. The analyst explores the analytics tool with the permissions of an analyst, and the embedded dashboard remains available in the console.

## Alternative Flows

### A1: Analytics not configured for this deployment

**Trigger:** The deployment has no analytics service location or dashboard identity configured (step 1).
**Flow:**

1. The system omits the Analytics item from the navigation, even for users who hold the analytics role.
2. If the analyst reaches the Analytics screen by its address, the system explains that analytics is not configured for this deployment and offers nothing further.
3. Use case ends.

### A2: User does not hold the analytics role

**Trigger:** An authenticated user without `elicit_analytics` tries to reach the Analytics screen (step 1).
**Flow:**

1. The system omits the Analytics item from the navigation.
2. If the user reaches the screen by its address, the system shows the Access Restricted page (UC-001, A2).
3. Use case ends.

### A3: Embedded access expires while the dashboard is open

**Trigger:** The short-lived access granted in step 3 reaches the end of its lifetime while the Analytics screen is still open (step 5).
**Flow:**

1. The embedded dashboard asks the system for renewed access.
2. The system grants renewed access for the same dashboard, provided the analyst's console session is still valid and still carries the analytics role.
3. The dashboard continues without any action by the analyst.
4. Use case continues at step 5.

### A4: Analytics service unreachable

**Trigger:** The embedded dashboard cannot be loaded from the analytics service (step 4).
**Flow:**

1. The system shows, in place of the dashboard, a message that the analytics service could not be reached, and keeps the "Open in Superset" link available.
2. Use case ends.

### A5: Console session no longer valid at renewal

**Trigger:** Renewed access is requested (A3) but the analyst's console session has ended or the analytics role has been withdrawn (step 5).
**Flow:**

1. The system refuses renewed access.
2. The embedded dashboard stops updating and reports that access has ended.
3. The analyst signs in to the console again (UC-001) to resume.
4. Use case ends.

## Postconditions

### Success Postconditions

- The analyst has read the Survey Operations dashboard inside the console and, if chosen, is signed in to the full analytics tool in another tab.
- Every grant of embedded access was scoped to the configured dashboard, read-only, and short-lived.
- No stored data is changed by this use case.

### Failure Postconditions

- The analyst sees an explanation (not configured, access restricted, or service unreachable) instead of a dashboard.
- No embedded access was granted to anyone lacking the analytics role, and no stored data is changed.

## Business Rules

### BR-080: Analytics is an orthogonal grant

The `elicit_analytics` role is never implied by `elicit_admin` or any ladder role (UC-001, BR-006). An administrator reaches the Analytics screen only when granted the analytics role explicitly, because dashboards expose respondent-level aggregates that not every administrator should see.

### BR-081: Embedded access is scoped, read-only and short-lived

Access granted for the embedded dashboard names exactly the configured dashboard, permits reading only, and expires within five minutes of being granted (NFR-011). Renewal requires a valid console session that still carries the analytics role; nothing an analyst does in the embedded dashboard can widen its scope.

### BR-082: The analytics location and dashboard are deployment configuration

Which analytics service the console links to and which dashboard it embeds are set per deployment, never fixed in the console itself. A deployment without that configuration simply has no Analytics feature, and gains it by configuration alone.

---

## Reference

Traces to FR-020 (and NFR-011, C-012). Background and design: `docs/research/Superset.md`
(sections 3 to 5 and 7) and `docs/plan/superset-analytics-implementation.md` (sections 4.2 to 4.4).
The analytics role itself is granted through the identity provider or, in database
authorization mode, through UC-016. The sign-in without prompting in step 7 relies on the
same identity provider serving both the console and the analytics service.
