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
4. On a match, the system updates the target survey's own attributes in place. Every other table in the survey definition is part of this update too — matching each file record to the target's record by its stable element key, never by content or by the file's local identifiers:
   - For the eight Type 2 tables (select groups/items, steps, sections, steps-sections, questions, sections-questions, relationships): a matched record with identical content is left as-is; a matched record with different content has its current effective-dated version closed and a new version inserted effective now, under the same durable identifier; an element key with no match in the target is inserted as a brand-new record.
   - For the remaining tables that are part of the survey definition but not Type 2 versioned (reports, post-survey actions, dimensions, ontology, metadata): a matched record with different content is updated in place (no version history); an element key with no match is inserted as a brand-new record. Dimensions, being a shared lookup, are also matched by name.
   - A target record whose element key no longer appears anywhere in the file is left untouched; this use case never deletes.
5. The system reports success with per-table counts of records created, versioned (or updated in place), or left unchanged.

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

**Trigger:** A required reference cannot be mapped, or a record has the wrong shape (step 4).
**Flow:**

1. The system aborts the update, rolls back everything, and reports the failure.

## Postconditions

### Success Postconditions

- The target survey's own attributes reflect the file; changed Type 2 structural children have new effective-dated versions, changed Type 1 tables (reports, post-survey actions, dimensions, ontology, metadata) are updated in place, and unchanged records everywhere are untouched.
- The target survey's stable key and database identifier are unchanged, so existing respondents, subjects, and reports remain attached to it.

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

A target record whose element key is absent from the file is left completely untouched. This use case has no mechanism for removing a record that was deleted from the authored copy; that is out of scope.

---

## Reference

Implemented by `SurveyDefinitionUpdateResource` / `SurveyDefinitionUpdateService`, alongside `SurveyDefinitionImportService` / `SurveyDefinitionExportService` (shared `ELICIT_SURVEY_EXPORT_V1` format and field-parsing helpers, `SurveyDefinitionFileFields`) — operates on the same shared survey-definition tables as UC-013/UC-014, applying the Type 2 versioning already introduced for select groups/items, steps, sections, steps-sections, questions, sections-questions, and relationships (`docs/research/Kimball_type2.md`). The stable survey key and every table's element key are attributes on tables owned by the sibling Authoring module — adding them required cross-module schema coordination (C-008). Every import/update attempt, success or failure, is audited to `survey.survey_log` (`SurveyLogService`).
