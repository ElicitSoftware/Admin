# Use Case: View System Overview

## Overview

**Use Case ID:** UC-020
**Use Case Name:** View System Overview
**Primary Actor:** Survey Administrator
**Goal:** See on one screen whether this deployment is wired correctly: what is running, whether it is healthy, whether every required setting is present, and what setup work remains.
**Status:** Tested

## Preconditions

- The administrator is authenticated and holds the `elicit_admin` role (UC-001).

## Main Success Scenario

1. The administrator opens Overview from the System section of the navigation.
2. The system shows what is running: application name, version, build time, active profile and the time since the service started.
3. The system shows health: the outcome of the database readiness check and a one-line summary of each diagnostic area (database, branding, email, connections), each linking to its own screen (UC-022 to UC-025).
4. The system lists every setting that has no default and must be supplied by the deployment (database passwords for both connections, the OIDC client secret, the sender address for email) and reports each as present or absent, never its value.
5. The system lists the setup work still outstanding: seeded default accounts still present (UC-021), no survey installed (UC-019), no department with a sender address, no message template containing the `<ACCESS_CODE>` placeholder, and any legacy setting that is set but no longer read.
6. Each outstanding item offers the screen where it is resolved.
7. The administrator works through the list and returns to confirm it is empty.

## Alternative Flows

### A1: A required setting is absent

**Trigger:** A setting listed in step 4 has no value (step 4).
**Flow:**

1. The system marks the setting absent and explains which environment variable or property supplies it.
2. Use case continues at step 5; the screen still renders.

### A2: A diagnostic area cannot be summarised

**Trigger:** A summary check in step 3 fails or times out (step 3).
**Flow:**

1. The system shows the area as failed with the failure reason and keeps the link to its screen.
2. Use case continues at step 4.

### A3: The build information was not recorded

**Trigger:** The version or build time is not available in this build (step 2).
**Flow:**

1. The system shows "unknown" for that value instead of failing.
2. Use case continues at step 3.

## Postconditions

### Success Postconditions

- The administrator has seen the running version, health and outstanding setup work; no data is changed.

### Failure Postconditions

- None; the screen is read-only.

## Business Rules

### BR-082: Secrets are reported as present or absent

No password, client secret or token value is rendered on any System screen (NFR-011). A required secret is shown only as present or absent, together with the name of the setting that supplies it.

### BR-083: The overview reports what is true now

Setup warnings and summaries are re-established each time the screen is opened, not captured at service start, so that work done since (renaming an account, applying a survey, adding a sender address) is reflected without a restart. The rule follows BR-076.

### BR-084: Configuration is shown, not edited

Datasource, OIDC, mailer and telemetry settings are startup configuration (C-012). The screen names the setting and reports its state; it never offers to change it.

---

## Reference

Traces to FR-020. The version and build time follow the Survey module's pattern of Maven-filtered
`quarkus.application.version` and `build.timestamp` properties.

Implemented by `SystemOverviewView` with `BuildInfo`, `RequiredConfigCheck`, `SetupWarnings` and the
per-area summaries from `DatabaseDiagnostics`, `BrandDiagnostics`, `MailerDiagnostics` and `ConnectionChecks`
(all under `com.elicitsoftware.diagnostics`). The version and build time come from the Maven-filtered
`quarkus.application.version` and `build.timestamp` properties. Verified by `SystemViewsRenderTest`.
