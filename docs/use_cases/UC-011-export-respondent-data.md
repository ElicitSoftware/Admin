# Use Case: Export Respondent Data

## Overview

**Use Case ID:** UC-011
**Use Case Name:** Export Respondent Data
**Primary Actor:** Survey Administrator
**Goal:** Export a single respondent's complete survey response data as a portable file for backup or transfer to another Elicit instance.
**Status:** Implemented

## Preconditions

- The administrator presents a valid bearer token and holds the `elicit_admin` role (UC-001).
- A respondent with the requested identifier exists.

## Main Success Scenario

1. The administrator requests an export for a respondent identifier.
2. The system loads the respondent and all related data: answers, dependents, subjects, messages, and post-survey-action records.
3. The system serializes the data into the portable respondent export format, recording the export timezone and linking messages to their subjects.
4. The system returns a downloadable export file.

## Alternative Flows

### A1: Missing identifier

**Trigger:** The request omits the respondent identifier (step 1).
**Flow:**

1. The system rejects the request as a bad request.

### A2: Respondent not found

**Trigger:** No respondent exists for the identifier (step 2).
**Flow:**

1. The system reports the respondent was not found.

## Postconditions

### Success Postconditions

- The administrator receives a self-contained export file for the respondent.

### Failure Postconditions

- No file is produced; the system reports the reason.

## Business Rules

### BR-038: Complete respondent graph

An export includes the respondent and all of its related answers, dependents, subjects, messages, and post-survey-action records.

### BR-039: Portable, self-describing format

The export uses a versioned, escaped, delimited format whose header records counts, survey, token, and timezone so it can be re-imported into another instance.

### BR-040: Orphaned messages skipped

A message whose subject is not part of the export set is omitted.

---

## Reference

Derived from `RespondentExportResource` and `RespondentExportService` (`ELICIT_EXPORT_V1` format). Reads the shared `respondents`, `answers`, `dependents`, `subjects`, `messages`, and respondent post-survey-action tables.
