# Use Case: Import Survey Definition

## Overview

**Use Case ID:** UC-014
**Use Case Name:** Import Survey Definition
**Primary Actor:** Survey Administrator
**Goal:** Import a survey definition file into this system as a brand-new survey with fresh identifiers. To alter an existing survey in place instead, see UC-017.
**Status:** Implemented

## Preconditions

- The administrator presents a valid bearer token and holds the `elicit_admin` role (UC-001).
- The file is a valid survey definition produced by UC-013.

## Main Success Scenario

1. The administrator uploads a survey definition file.
2. The system validates the file's format header.
3. The system inserts the survey with a fresh identifier and a recomputed display order, then each dependent record, rewriting all references from source identifiers to the newly assigned ones. The survey's stable, cross-instance key is carried over from the file if present, or freshly assigned if the file predates key assignment.
4. The system reports success with per-table counts of the records imported.

## Alternative Flows

### A1: No file provided

**Trigger:** The request carries no file (step 1).
**Flow:**

1. The system rejects the request as a bad request.

### A2: Invalid or missing header

**Trigger:** The file does not begin with a recognized survey export header (step 2).
**Flow:**

1. The system reports the file format is invalid and imports nothing.

### A3: Unresolved reference or malformed record

**Trigger:** A required reference cannot be mapped, a record has the wrong shape, or more than one survey appears in the file (step 3).
**Flow:**

1. The system aborts the import, rolls back everything, and reports the failure.

### A4: Stable key already present in this instance

**Trigger:** A survey with the file's stable key already exists in this instance (step 3).
**Flow:**

1. The system rejects the import as a duplicate deployment and directs the administrator to UC-017 to update that survey instead.

## Postconditions

### Success Postconditions

- A new survey and all its definition records exist with newly allocated identifiers.

### Failure Postconditions

- No records are inserted; the entire import is rolled back.

## Business Rules

### BR-047: One survey per file

A survey definition file contains exactly one survey; a file describing more than one is rejected.

### BR-048: Reused shared lookups

Dimensions and ontology entries that already exist (matched by name, and by name and tag respectively) are reused rather than duplicated on import.

### BR-049: All-or-nothing import

The entire survey import runs as a single transaction; any failure rolls back the whole survey.

### BR-061: Preserve or assign a stable key on create

A created survey retains the stable key from the source file if present, establishing it as another deployment of the same authored survey; if the file predates key assignment, the system assigns a fresh key at import time.

### BR-062: No duplicate deployments

A create-import is rejected if a survey with the file's stable key already exists in the destination instance; altering that survey requires UC-017.

---

## Reference

Derived from `SurveyDefinitionImportResource` and `SurveyDefinitionImportService` (`ELICIT_SURVEY_EXPORT_V1` format). Writes the shared survey-definition tables.

### BR-076: Retired records are not installed

A versioned record whose validity window is closed in the file describes an element removed before this site ever had it; it is skipped and counted separately in the import summary rather than installed.
