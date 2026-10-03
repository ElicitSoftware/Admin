# Use Case: Rename a Survey's Reporting Schema

## Overview

**Use Case ID:** UC-030
**Use Case Name:** Rename a Survey's Reporting Schema
**Primary Actor:** Survey Administrator
**Secondary Actor:** Survey Application
**Goal:** See the name of each installed survey's reporting schema and give it the name the site wants, keeping everything in it, so that analysts and BI tools can use a short or conventional name.
**Status:** Implemented

## Preconditions

- The administrator is signed in to the console and holds the `elicit_admin` role (UC-001).
- At least one survey is installed (UC-014 or UC-018).
- The Survey application is reachable at `elicit.survey.url` (UC-025 lists it).

## Main Success Scenario

1. The administrator opens Export Survey Definition in the console's Admin section, which lists every installed survey (UC-013). Beside each survey the list shows the name of its reporting schema, read from `survey.surveys.report_schema`, and a Rename action.
2. The administrator chooses Rename for a survey.
3. The system opens a dialog naming the survey and its current schema, with a field for the new name and a warning: queries and BI connections outside Elicit that name the old schema stop working, while nothing inside Elicit is affected because every module reads the name from the survey.
4. The administrator types the new name.
5. The system checks the name as it is typed (BR-116), showing in the field why a name is not acceptable.
6. The administrator confirms an acceptable name.
7. The system asks the Survey application to rename the schema (Survey UC-010, `POST /api/etl/schema/<survey_key>/rename?name=<new_name>`), bounded by the same timeout as the rebuild after an apply.
8. Survey answers that the schema was renamed; the system reports it, closes the dialog and shows the new name in the list.

## Alternative Flows

### A1: The survey has no reporting schema yet

**Trigger:** `report_schema` is null: the survey was installed but its first build has not run or did not succeed (step 1).
**Flow:**

1. The list says "not built yet" in place of the name, and the Rename action is not offered.
2. Use case ends; the schema is named by the next successful build (UC-018 BR-107).

### A2: The name is not acceptable

**Trigger:** The typed name fails BR-116 (step 5).
**Flow:**

1. The field shows why, and confirming sends nothing.
2. Use case continues at step 4.

### A3: Survey refuses the name

**Trigger:** Survey answers 400 (invalid), 409 (taken, or not built) or 404 (no survey has the key) (step 7).
**Flow:**

1. The system shows Survey's reason in the dialog and leaves it open with the typed name; nothing was changed.
2. Use case continues at step 4, or the administrator closes the dialog.

### A4: Survey cannot be reached or fails

**Trigger:** Survey cannot be reached, does not answer in time, or answers 500 (step 7).
**Flow:**

1. The system shows the reason in the dialog and logs it at WARN. Nothing was changed: Survey renames the schema and records the new name in one transaction, so a failure leaves the old name everywhere.
2. Use case continues at step 4, or the administrator closes the dialog.

### A5: The administrator cancels

**Trigger:** The administrator closes the dialog instead of confirming (step 6).
**Flow:**

1. Nothing changes.
2. Use case ends.

## Postconditions

### Success Postconditions

- The survey's reporting schema carries the new name, with every table, view and fact row it had; `survey.surveys.report_schema` is the new name, so Survey's next build and the FHHS reports use it at once.

### Failure Postconditions

- The schema and the recorded name are unchanged.

## Business Rules

### BR-116: A schema name is a plain lower-case identifier

The name must match `^[a-z_][a-z0-9_]{0,62}$` and may not be `survey`, `surveyreport`, `public` or `information_schema`, nor start with `pg_`: the same rule Survey applies (Survey UC-008 BR-006). The console checks it before asking, so the ordinary mistakes (upper case, a space, a hyphen) are caught in the field; Survey remains the authority and is the one that knows whether a name is already taken.

### BR-117: The rename is Survey's to do

The console never touches a reporting schema itself: it connects as `surveyadmin_user`, which owns none of them, and the schema's name lives on the survey row that Survey's build maintains. The console shows the name and relays the request; Survey validates, renames and records in one transaction.

### BR-118: The warning is about the outside

Inside Elicit a rename takes effect everywhere at once, because nothing hard-codes a survey schema's name. What breaks is a query, a saved report or a BI connection outside Elicit that names the old schema. The dialog says so before the administrator confirms, and the name is shown in the list afterwards so it can be passed on.

---

## Reference

Traces to FR-037. Implemented by `SurveyDefinitionExportView` (the schema column and the Rename action), `RenameReportingSchemaDialog` (the dialog, the client-side check of BR-116 and the outcome messages) and `ReportingSchemaRenameClient` (the call to Survey, with the same base address and timeout as `ReportingSchemaRebuildClient`). The schema name is read through `Survey.reportSchema`, mapped read-only because Survey's ETL is its only writer. Verified by `ReportingSchemaRenameClientTest`, `RenameReportingSchemaDialogTest` and `SurveyDefinitionExportViewTest`.
