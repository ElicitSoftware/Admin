# Use Case: Update Survey Definition

## Overview

**Use Case ID:** UC-017
**Use Case Name:** Update Survey Definition
**Primary Actor:** Survey Administrator
**Goal:** Apply a revised survey definition to an existing survey in place, so the same authored survey can be kept in sync across multiple independent deployments (e.g., separate institutions) without losing deployment-local data such as respondents.
**Status:** Implemented

## Preconditions

- The administrator presents a valid bearer token and holds the `elicit_admin` role (UC-001).
- An existing survey has been selected as the update target.
- The file is a valid survey definition produced by UC-013 and carries a stable survey key.

## Main Success Scenario

1. The administrator selects an existing survey in this instance to update and uploads a survey definition file.
2. The system validates the file's format header.
3. The system compares the file's stable survey key to the selected target survey's stable key.
4. On a match, the system compares the file's revision to the newest revision already applied to this survey in this instance, and proceeds only if the file is not older.
5. On a match, the system updates the target survey's own attributes in place. Every other table in the survey definition is part of this update too — matching each file record to the target's record by its stable element key, never by content or by the file's local identifiers:
   - For the eight Type 2 tables (select groups/items, steps, sections, steps-sections, questions, sections-questions, relationships): a matched record with identical content is left as-is; a matched record with different content has its current effective-dated version closed and a new version inserted effective now, under the same durable identifier; an element key with no match in the target is inserted as a brand-new record.
   - For the remaining tables that are part of the survey definition but not Type 2 versioned (reports, post-survey actions, dimensions, ontology, metadata): a matched record with different content is updated in place (no version history); an element key with no match is inserted as a brand-new record. Dimensions, being a shared lookup, are also matched by name.
   - A file record marked as retired (its validity window closed by the authoring tool when the element was removed) has the target's current version closed as of now, with nothing inserted; a record already retired here, or never installed here, is left alone. Respondents who started earlier keep the element; new respondents do not see it.
   - A target record whose element key no longer appears anywhere in the file is left untouched; this use case never deletes.
6. The system reports success with per-table counts of records created, versioned (or updated in place), retired, or left unchanged.
7. The system asks the Survey application to rebuild its reporting schema (Survey UC-008, `POST /api/etl/build`) so renamed steps and sections, new dimensions and newly tagged questions are reportable without a restart, and adds the outcome to its report: "Reporting schema rebuilt." or "Reporting schema not rebuilt: reason".

## Alternative Flows

### A1: No file provided or no target survey selected

**Trigger:** The request carries no file, or no target survey was selected (step 1).
**Flow:**

1. The system rejects the request as a bad request.

### A2: Invalid or missing header

**Trigger:** The file does not begin with a recognized survey export header (step 2).
**Flow:**

1. The system reports the file format is invalid and applies no changes.

### A3: Survey key mismatch

**Trigger:** The file's stable survey key does not equal the selected target survey's key (step 3).
**Flow:**

1. The system aborts the update, applies no changes, and reports that the file does not belong to the selected survey.

### A4: File has no stable survey key

**Trigger:** The file predates stable-key assignment and carries no key to compare (step 3).
**Flow:**

1. The system rejects the update; the administrator must either import the file as a new survey (UC-014), which assigns it a stable key, or re-export the target survey (UC-013) to see its current key before retrying.

### A5: Unresolved reference or malformed record

**Trigger:** A required reference cannot be mapped, or a record has the wrong shape (step 5).
**Flow:**

1. The system aborts the update, rolls back everything, and reports the failure.

### A6: File revision predates the installed revision

**Trigger:** The file's revision is older than the newest revision already applied to this survey here (step 4).
**Flow:**

1. The system aborts the update before any record is touched and reports that the file would regress the survey.
2. The administrator applies the newer file instead. There is no override: reverting a deployment to an earlier revision is an operational restore (the prior database from backup, plus the prior application image), not an update.

### A7: Unparseable revision

**Trigger:** The file carries a revision header whose value is not a valid timestamp (step 2).
**Flow:**

1. The system rejects the file and applies no changes. A corrupt revision is not treated as "no revision", because that would silently disable the regression check.

### A8: Reporting schema rebuild fails

**Trigger:** In step 7 the Survey application cannot be reached, does not answer within 60 seconds, has its reporting ETL disabled, or reports that the build failed (see UC-018 A5 for the known cause).
**Flow:**

1. The system reports the update as successful, with "Reporting schema not rebuilt:" and the reason as the last line of the result, and logs the reason at WARN (UC-018 BR-108).
2. The administrator fixes the cause and re-applies the same file -- an equal revision passes the regression check (BR-070) and every record reconciles as unchanged -- or restarts Survey.

## Postconditions

### Success Postconditions

- The target survey's own attributes reflect the file; changed Type 2 structural children have new effective-dated versions, changed Type 1 tables (reports, post-survey actions, dimensions, ontology, metadata) are updated in place, and unchanged records everywhere are untouched.
- The target survey's stable key and database identifier are unchanged, so existing respondents, subjects, and reports remain attached to it.
- The Survey application has been asked to rebuild its reporting schema, and the result says whether it did.

### Failure Postconditions

- No changes are applied; the entire update is rolled back.

## Business Rules

### BR-063: Update requires a key match

An update is applied only when the uploaded file's stable survey key equals the selected target survey's stable key; any mismatch aborts the update with no changes made.

### BR-064: Survey shell and Type 1 tables update in place; Type 2 structure is versioned

Updating a survey changes its own attributes (e.g., title, description, `published_by`, `published_comment`) in place, as do the survey-definition tables with no version history (reports, post-survey actions, dimensions, ontology, metadata). Changes to the eight Type 2 structural children are versioned instead: the prior effective-dated version is closed and a new one is inserted, never overwritten.

### BR-065: Stable key and database identifier never change

An update never alters the target survey's stable key or its database identifier, preserving every reference held against it (respondents, subjects, reports, post-survey actions).

### BR-066: All-or-nothing update

The entire update runs as a single transaction; any failure rolls back all changes.

### BR-067: Matching is by element key, never by content or local identifier

Every table in the survey definition carries a stable element key (the survey's own is its stable key); matching a file record to a target record always uses this key, never the file's local identifier (meaningful only within the system that produced the file) and never a comparison of content. Dimensions, a shared lookup, are the one exception: reuse by name takes priority over the file's key.

### BR-068: Update never deletes

No row is ever physically removed by an update. An element the file marks as retired has its current version closed (the same close that versioning performs, without the re-insert), so recorded answers and the as-of view of respondents already in progress are untouched. An element simply absent from the file is left as it is.

### BR-069: An older revision is always refused

A file whose revision predates the newest revision already applied to the target in this instance is rejected before any record is written, with no override. Applying it would not revert the survey — it would close the current versions and open *newer* ones carrying older content, which is both a silent content regression and, afterwards, indistinguishable from a deliberate edit.

Reverting a deployment to an earlier revision is deliberately outside this use case: it is an operational procedure (restore the prior database from backup, redeploy the prior image), for the same reason the Kimball Type 2 migration ships no down-migration. Expressing a revert as an update would leave the database in a state no backup corresponds to.

### BR-070: An absent or equal revision is not a regression

A file carrying no revision, or a target with no recorded revision, passes the check — neither is evidence of a regression, and blocking on them would make the first update after the revision header's introduction impossible. An equal revision also passes, since re-applying the identical file is how an administrator retries, and every record in it reconciles as unchanged.

### BR-071: The key match is evaluated before the revision

A file for a different survey is reported as a key mismatch (BR-063), not as a revision regression. Comparing revisions is only meaningful once the file is known to belong to the target.

### BR-109: Content is compared by value, and an apply with no differences versions nothing

Once a file record is matched to its target row (BR-067), whether the row is unchanged or must be versioned is decided by comparing each field's *value*, not its representation. Numeric display orders (`steps.display_order`, `sections.display_order`, `steps_sections.step_display_order` / `section_display_order`, `sections_questions.display_order` — decimal columns, so a new element can be slotted between two neighbours without renumbering) compare numerically: `1`, `1.0` and `1.00` are the same order, and `1.5` round-trips as `1.5`. Text compares after the same normalisation the update applies before writing (empty to null, defaulted `dimension_name`, rebased display keys). Consequently re-applying a file that differs from the target in nothing — the retry case of BR-070 — creates no new version in any table, and a file that changes one element versions that element alone.

### BR-107 / BR-108 (UC-018): The reporting schema is rebuilt after a successful update; a failed rebuild does not undo it

After the update has committed, the Survey application is asked once to rebuild its reporting schema; the outcome is reported and never fails the update. The rules are stated in UC-018, which shares this step, and the call is switched off with `elicit.survey.etl-build.enabled=false`.

---

## Reference

Implemented by `SurveyDefinitionUpdateResource` / `SurveyDefinitionUpdateService`, alongside `SurveyDefinitionImportService` / `SurveyDefinitionExportService` (shared `ELICIT_SURVEY_EXPORT_V1` format and field-parsing helpers, `SurveyDefinitionFileFields`) — operates on the same shared survey-definition tables as UC-013/UC-014, applying the Type 2 versioning already introduced for select groups/items, steps, sections, steps-sections, questions, sections-questions, and relationships (`docs/research/Kimball_type2.md`). The stable survey key and every table's element key are attributes on tables owned by the sibling Authoring module — adding them required cross-module schema coordination (C-008). Every import/update attempt, success or failure, is audited to `survey.survey_log` (`SurveyLogService`), including the revision of the file it applied — that column is what BR-069's regression check compares against, and what reports which revision this deployment is running. UC-018 wraps this use case and UC-014 behind a single entry point that picks between them by survey key. Step 7 is `ReportingSchemaRebuildClient`, called by the resource (or by UC-018's service) after this service's transaction has committed.
