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
3. The system serializes the definition into the portable survey export format, preserving each record's original identifier so references can be re-mapped on import, and includes the survey's stable, cross-instance key.
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

### BR-072: Every export is stamped with a revision

An export records the moment it was produced as the file's revision. This is the only identifier in the format comparable across deployments — the version numbers on the Type 2 structural tables are derived locally by each receiving instance, so a deployment that adopted a survey late carries lower version numbers than its peers for identical content. "Which revision of this instrument is this deployment running?" is answered by the revision of the last file applied there, recorded in the survey log.

Each export produces a fresh revision, including a re-export of a survey that was itself imported. "Same revision" therefore means "same file", so a multi-deployment rollout should distribute one exported file to every site rather than re-exporting per site.

### BR-046: Referenced lookups must pre-exist on import

Static lookup references (such as question type, operator, and action) are exported as-is and must already exist in the target system.

### BR-060: Export carries the stable survey key

Every export includes the survey's stable, cross-instance-portable key (assigned once and never reassigned) alongside preserved source identifiers, so the same authored survey can be recognized across separate deployments (e.g., different institutions) and matched for an update (UC-017) or for respondent data consolidation.

---

## Reference

Derived from `SurveyDefinitionExportResource` and `SurveyDefinitionExportService` (`ELICIT_SURVEY_EXPORT_V1` format). Reads the shared survey-definition tables (surveys, select groups/items, steps, sections, questions, relationships, reports, post-survey actions, dimensions, ontology, metadata). See UC-017 for the update flow this key enables.

### BR-073: Retired elements travel with the file

For each versioned element the export writes its current version; an element whose every version is closed is written once, as its latest version with its closing instant, so that a removal made in the authoring tool is applied at every site that later updates from the file (UC-017).
