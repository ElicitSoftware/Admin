# Use Case: Import Respondent Data

## Overview

**Use Case ID:** UC-012
**Use Case Name:** Import Respondent Data
**Primary Actor:** Survey Administrator
**Goal:** Import a respondent export file into this system, allocating fresh identifiers, to restore or transfer a respondent's data.
**Status:** Implemented

## Preconditions

- The administrator presents a valid bearer token and holds the `elicit_admin` role (UC-001).
- The file is a valid respondent export produced by UC-011.

## Main Success Scenario

1. The administrator uploads a respondent export file.
2. The system validates the file's format header.
3. The system resolves the destination survey by matching the file's stable survey key against the surveys in this instance, confirms the file's access code is not already in use on that survey, then inserts a new respondent with a fresh identifier attached to that survey, carrying its progress state unchanged (a Finished respondent stays Finished; an inactive one stays inactive; a never-started one keeps no first-access time). It then inserts the answers, dependents, subjects, messages, and post-survey-action records, resolving each subject's department by its code, each question, section-question, and relationship reference by element key and exact version, and each post-survey action by name on the destination survey, re-linking internal references as it goes.
4. The system reports success with per-table counts of the records imported.

## Alternative Flows

### A1: No file provided

**Trigger:** The request carries no file (step 1).
**Flow:**

1. The system rejects the request as a bad request.

### A2: Invalid or missing header

**Trigger:** The file does not begin with a recognized export header (step 2).
**Flow:**

1. The system reports the file format is invalid and imports nothing.

### A3: Row error during import

**Trigger:** A record is malformed or a reference cannot be resolved (step 3).
**Flow:**

1. The system aborts the import, rolls back all inserted records, and reports the failing line.

### A4: No matching survey in this instance

**Trigger:** No survey in this instance carries the file's stable survey key (step 3).
**Flow:**

1. The system aborts the import and reports that the respondent's survey has not been deployed to this instance, pointing the administrator at applying the survey definition (UC-018).

### A5: Department code not found

**Trigger:** A subject's department code matches no department in this instance (step 3).
**Flow:**

1. The system aborts the import, rolls back everything inserted so far, and reports the missing code, instructing the administrator to create the department before importing.
2. No department is created on the administrator's behalf.

### A6: Element version not present

**Trigger:** A question, section-question, or relationship referenced by the file does not exist in this instance at the exact key and version the file names (step 3).
**Flow:**

1. The system aborts the import, rolls back everything inserted so far, and reports the missing element key and version, pointing the administrator at updating the survey definition (UC-017) so the source survey's versions exist here.
2. The system never substitutes another version of the element.

### A7: Access code already in use

**Trigger:** A respondent with the file's access code already exists on the destination survey (step 3).
**Flow:**

1. The system aborts the import before inserting anything and reports the collision.
2. The system does not generate a replacement access code.

### A8: Legacy format version

**Trigger:** The file's format header names an earlier export format version (step 2).
**Flow:**

1. The system reports that the file carries identifiers specific to the instance that produced it and cannot be imported, instructs the administrator to re-export the respondent from the source instance, and imports nothing.

## Postconditions

### Success Postconditions

- A new respondent and all its related records exist with newly allocated identifiers.

### Failure Postconditions

- No records are inserted; the entire import is rolled back.

## Business Rules

### BR-041: Header validation required

An import proceeds only if the file starts with a valid, recognized export format header.

### BR-042: All-or-nothing import

The entire import runs as a single transaction; any row failure rolls back the whole import.

### BR-043: Fresh identifiers with re-linked references

Imported records receive new identifiers, and internal references (such as message-to-subject and dependent links) are resolved to the newly assigned identifiers.

### BR-059: Destination survey resolved by stable key

The survey a respondent's data is attached to is resolved by matching the export's stable survey key against surveys in this instance; a numeric survey identifier from the source database is never trusted directly.

### BR-102: Department resolved by code, never created

A subject's department is resolved by matching the file's department code against the departments of this instance. An import never creates a department; a code with no match aborts the import.

### BR-103: Element references resolved by key and exact version

Every question, section-question, and relationship reference is resolved by the element's stable cross-instance key and the exact version named in the file. There is no fallback to the current or any other version: if that version is not deployed here, the import aborts. A reference that is empty in the file stays empty.

### BR-104: Respondent state preserved

The imported respondent carries the source's active flag, first access time, and finalization time unchanged, so its status in this instance (Not Started, In Progress, Finished) is the same as it was at the source. Empty timestamps import as null.

### BR-105: Access-code collisions rejected

An access code is unique per survey. If the file's access code already exists on the destination survey, the import is rejected outright; it neither reuses the existing respondent nor generates a new code.

### BR-106: Only the current format version accepted

Only files in the current export format version are imported. A file in an earlier version, which carries instance-specific identifiers, is refused with the instruction to re-export it from the source instance.

---

## Reference

Derived from `RespondentImportResource` and `RespondentImportService` (`ELICIT_EXPORT_V2` format). Writes the shared `respondents`, `answers`, `dependents`, `subjects`, `messages`, and respondent post-survey-action tables; reads `surveys`, `departments`, `questions`, `sections_questions`, `relationships`, and `post_survey_actions` to resolve the file's portable identities. Unresolvable references (A4–A7) surface as `RespondentImportService.ImportValidationException`, which the REST endpoint reports as a bad request carrying the message.
