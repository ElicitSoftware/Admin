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
3. The system serializes the data into the portable respondent export format, recording the export timezone; the respondent's survey by its stable cross-instance key; each subject's department by its code; every question, section-question, and relationship reference by the element's cross-instance key and version; the respondent's progress state (active flag, first access, finalization) exactly as stored; and linking messages to their subjects. No site-local identifier appears in the file.
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

### A3: Subject's department has no code

**Trigger:** A subject of the respondent belongs to a department that has no department code (step 3).
**Flow:**

1. The system refuses the export and names the department, instructing the administrator to assign a department code first.
2. No file is produced.

## Postconditions

### Success Postconditions

- The administrator receives a self-contained export file for the respondent.

### Failure Postconditions

- No file is produced; the system reports the reason.

## Business Rules

### BR-038: Complete respondent graph

An export includes the respondent and all of its related answers, dependents, subjects, messages, and post-survey-action records.

### BR-039: Portable, self-describing format

The export uses a versioned, escaped, delimited format whose header records the format version, record counts, the survey key and name, the access code, and the timezone so it can be re-imported into another instance.

### BR-040: Orphaned messages skipped

A message whose subject is not part of the export set is omitted.

### BR-058: Survey identified by stable key

A respondent export identifies the respondent's survey by its stable, cross-instance key, not merely the source database's numeric survey identifier, so the file can be attributed to the correct survey after import into another instance.

### BR-099: Structural references by element key and version

Every reference from a respondent's data into the survey structure — an answer's question and section-question, a dependent's relationship — is exported as the element's stable cross-instance key together with the exact version the respondent answered against, never as the source database's surrogate identifier. Two instances that deployed the same survey definition lineage share these keys, so the file is meaningful on either. An answer whose question or section-question reference is empty in the source is exported with those fields empty.

### BR-100: Department carried by code

A subject's department is exported by its department code, the only department identity that is stable across instances. A department without a code cannot be carried portably, so the export is refused rather than emitting a local identifier.

### BR-101: Progress state exported faithfully

The respondent's active flag, first access time, and finalization time are exported exactly as stored: a timestamp that is null in the source is exported empty, never replaced by the export time. The same holds for every other nullable timestamp (answer saved time, message sent time, post-survey-action upload time). Only a NOT NULL creation timestamp that is unexpectedly missing may be substituted.

---

## Reference

Derived from `RespondentExportResource` and `RespondentExportService` (`ELICIT_EXPORT_V2` format). Reads the shared `respondents`, `answers`, `dependents`, `subjects`, `messages`, and respondent post-survey-action tables, joined to `surveys`, `questions`, `sections_questions`, `relationships`, `departments`, and `post_survey_actions` for the portable identities the file carries. A refused export (A3) is reported as a conflict by the REST endpoint.
