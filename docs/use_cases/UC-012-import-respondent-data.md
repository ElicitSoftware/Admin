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
3. The system resolves the destination survey by matching the file's stable survey key against the surveys in this instance, then inserts a new respondent with a fresh identifier attached to that survey, then its answers, dependents, subjects, messages, and post-survey-action records, re-linking references as it goes.
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

1. The system aborts the import and reports that the respondent's survey has not been deployed to this instance.

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

---

## Reference

Derived from `RespondentImportResource` and `RespondentImportService` (`ELICIT_EXPORT_V1` format). Writes the shared `respondents`, `answers`, `dependents`, `subjects`, `messages`, and respondent post-survey-action tables.
