# Use Case: Export Survey Definition

## Overview

**Use Case ID:** UC-013
**Use Case Name:** Export Survey Definition
**Primary Actor:** Survey Administrator
**Goal:** Export a complete survey definition (its structure, not respondent data) for transfer between systems.
**Status:** Implemented

## Preconditions

- The administrator presents a valid bearer token and holds the `elicit_admin` role (UC-001).
- A survey with the requested identifier exists.

## Main Success Scenario

1. The administrator requests an export for a survey identifier.
2. The system loads the survey and all of its definition data in dependency order.
3. The system serializes the definition into the portable survey export format, preserving each record's original identifier so references can be re-mapped on import.
4. The system returns a downloadable definition file.

## Alternative Flows

### A1: Missing identifier

**Trigger:** The request omits the survey identifier (step 1).
**Flow:**

1. The system rejects the request as a bad request.

### A2: Survey not found

**Trigger:** No survey exists for the identifier (step 2).
**Flow:**

1. The system reports the survey was not found.

## Postconditions

### Success Postconditions

- The administrator receives a self-contained survey definition file.

### Failure Postconditions

- No file is produced; the system reports the reason.

## Business Rules

### BR-044: Definition only, no responses

A survey export contains only the survey's structural definition, never respondent response data.

### BR-045: Source identifiers preserved for re-mapping

Each exported record retains its original identifier so that references between records can be re-mapped when the definition is imported elsewhere.

### BR-046: Referenced lookups must pre-exist on import

Static lookup references (such as question type, operator, and action) are exported as-is and must already exist in the target system.

---

## Reference

Derived from `SurveyDefinitionExportResource` and `SurveyDefinitionExportService` (`ELICIT_SURVEY_EXPORT_V1` format). Reads the shared survey-definition tables (surveys, select groups/items, steps, sections, questions, relationships, reports, post-survey actions, dimensions, ontology, metadata).
