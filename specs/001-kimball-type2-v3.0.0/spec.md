# Feature Specification: Kimball Type 2 SCD Support — Admin v3.0.0

**Feature Branch**: `001-kimball-type2-v3.0.0`
**Created**: 2026-05-12
**Status**: Draft
**Input**: Research document: `Admin/research/Kimball_type2.md`

---

## Overview

Survey's Kimball Type 2 migration adds durable key columns, Type 2 time-range columns, and eight new durable sequences to the shared `survey` schema. Admin is responsible for exporting and importing survey definitions and respondent data. After the Kimball migration, Admin must:

1. Grant the `surveyadmin` database role access to the eight new durable sequences (Flyway migration).
2. Upgrade the survey definition export format to `V2` — include durable key columns and Type 2 metadata; export only current (non-draft) rows; use durable integer ids as cross-table references.
3. Upgrade the survey definition import to allocate durable ids from the new sequences, maintain dual surrogate+durable id maps, and support backward-compatible V1 import.
4. Add `question_version` to the respondent answers export and import format.

Admin is the only application that reads the full set of structural columns across all tables in batch. It does not execute live respondent-facing structural queries and does not implement the `firstAccessDt` time-range predicate.

---

## Clarifications

### Session 2026-05-12

- The new export format version string is `ELICIT_SURVEY_EXPORT_V2`; a header line `# schema_version: V2_KIMBALL_TYPE2` is added after `# generated:`.
- The Flyway migration that grants durable sequences is `V0.0.9__Add_Kimball_Durable_Seq_Grants.sql`.
- Export queries must add `AND is_draft = false` as an explicit safety guard in addition to the time-range predicate.
- The importer must maintain **two** id maps per structural table: `sourceId → newSurrogateId` (existing) and `sourceId → newDurableId` (new). Join-table FK columns are resolved via the durable map.
- When importing a V1 export (no durable key columns in the file): allocate a new durable id from the durable sequence; default `version = 0`, `effective_from = '1970-01-01 00:00:00+00'`, `effective_to = '9999-12-31 23:59:59+00'`, `is_draft = false`.
- `RespondentValidator` — no changes required.
- The `subjects` table — no changes required (FKs point to `surveys` and `respondents`, neither of which receives Type 2 columns).
- The Admin UI version history surfacing is an optional enhancement noted in the research doc and is explicitly out of scope for this release.
- The `answers` export format field order: `respondent_id|question_id|section_question_id|display_key|text_value|deleted|question_version`.

---

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Durable Sequence Grants (Priority: P1)

A DBA deploys Admin after the Survey Kimball migration. The Admin Flyway migration `V0.0.9` runs automatically and grants the `surveyadmin` role access to all eight new durable sequences, allowing `SurveyDefinitionImportService` to call `nextval()` on them.

**Why this priority**: Without the grants, any survey import attempt will fail with a PostgreSQL permission denied error on the first `nextval()` call. This is a hard blocker for all import functionality.

**Independent Test**: Deploy Admin against a Kimball-migrated Survey database. Attempt to import a minimal survey definition. The import completes without `ERROR: permission denied for sequence` errors.

**Acceptance Scenarios**:

1. **Given** the Admin application is deployed against a Kimball-migrated database, **When** `mvn flyway:migrate` runs, **Then** migration `V0.0.9` completes without error.
2. **Given** `V0.0.9` has run, **When** the `surveyadmin` role executes `SELECT nextval('survey.questions_durable_seq')`, **Then** the call succeeds.
3. **Given** `V0.0.9` has run, **When** the same test is repeated for all eight durable sequences, **Then** all eight succeed.

---

### User Story 2 — V2 Survey Definition Export (Priority: P1)

An administrator exports a survey definition from a Kimball-migrated database. The resulting file includes the durable key column for each structural table (used as `source_id`), all six Type 2 columns, and uses the renamed metadata column names. Draft rows are excluded. The file header identifies itself as `V2`.

**Why this priority**: Downstream systems (test environments, partner sites) need to import the Kimball schema. A V1 export from a Kimball-migrated database would be missing the durable key columns necessary to correctly re-create the survey in the target database.

**Independent Test**: Trigger a survey export against a Kimball-migrated database. Open the file and verify: header line reads `ELICIT_SURVEY_EXPORT_V2`; every `steps` row includes `step_id` as the first field; `metadata` rows reference `steps_sections_id` and `sections_question_id` column names; no draft rows are included (check `is_draft` field is `false` on all rows).

**Acceptance Scenarios**:

1. **Given** a survey in the Kimball-migrated database with one draft question and three published questions, **When** the export runs, **Then** the export file contains exactly three question rows (the draft is excluded).
2. **Given** the exported file, **When** the `steps` section is inspected, **Then** the first field of each row is the durable `step_id` (not the surrogate `id`), and `version`, `effective_from`, `effective_to`, `published_by`, `published_comment`, `is_draft` fields are present.
3. **Given** the exported file, **When** the `metadata` section is inspected, **Then** column headers reference `steps_sections_id` and `sections_question_id` (not the old `step_section_id`/`section_question_id`).
4. **Given** the exported file, **When** cross-table references are inspected (e.g. `steps_sections.step_id`), **Then** each value matches the durable `step_id` of the corresponding step row's `source_id`.

---

### User Story 3 — V2 Survey Definition Import (Priority: P1)

An administrator imports a V2 survey definition export into a fresh Kimball-migrated target database. The import allocates new surrogate ids from the existing sequences AND new durable ids from the durable sequences, maintains dual id maps, and correctly resolves durable FK cross-references. Imported rows have `version = 0`, `effective_from = '1970-01-01 00:00:00+00'`, `effective_to = '9999-12-31 23:59:59+00'`, `is_draft = false`.

**Why this priority**: Without the dual id map, join-table FK columns will reference wrong entities in the target database. This is the most complex data integrity concern in this spec.

**Independent Test**: Export a survey from source DB (V2 format). Import it into a fresh Kimball-migrated target DB. Query the target DB: verify all FK relationships in `steps_sections`, `sections_questions`, and `relationships` are intact (no orphaned FK references). Verify `questions.effective_to = '9999-12-31 23:59:59+00'` on all imported questions.

**Acceptance Scenarios**:

1. **Given** a V2 export file, **When** it is imported into a fresh target database, **Then** `SELECT COUNT(*) FROM survey.questions WHERE effective_to = '9999-12-31 23:59:59+00'` returns the same count as the export file's question row count.
2. **Given** the imported database, **When** `SELECT COUNT(*) FROM survey.steps_sections ss LEFT JOIN survey.steps s ON ss.step_id = s.step_id WHERE s.step_id IS NULL` is run, **Then** the result is 0 (no orphaned FK references).
3. **Given** the imported database, **When** `SELECT nextval('survey.questions_durable_seq')` is called, **Then** the returned value is higher than the highest `question_id` in the imported data (confirming the sequence was used and not reset to 1).

---

### User Story 4 — V1 Backward-Compatible Import (Priority: P2)

An administrator imports a V1 survey definition export (generated before the Kimball migration) into a Kimball-migrated target database. The import correctly allocates new durable ids from the durable sequences, sets Type 2 column defaults, and resolves join-table FKs via the surrogate map before converting to durable ids in the target.

**Why this priority**: Partner sites and test environments may have V1 exports from before the Kimball release. The Admin tool must not break their import workflows.

**Independent Test**: Take a pre-Kimball V1 export file. Import it into a fresh Kimball-migrated database. Verify all FK relationships are intact and all rows have correct Type 2 defaults.

**Acceptance Scenarios**:

1. **Given** a V1 export file (no `schema_version` header line), **When** the importer detects format version, **Then** it selects the V1 backward-compat import path.
2. **Given** the V1 import completes, **When** `SELECT version, effective_from, effective_to, is_draft FROM survey.questions LIMIT 5` is run on the target, **Then** all rows show `version = 0`, `effective_from = '1970-01-01 00:00:00+00'`, `effective_to = '9999-12-31 23:59:59+00'`, `is_draft = false`.

---

### User Story 5 — Respondent Export/Import with `question_version` (Priority: P2)

An administrator exports respondents (including answers) from a Kimball-migrated database. The export file includes `question_version` in each answer row. On import, `question_version` is inserted into `answers.question_version`. A V1 respondent export (no `question_version` field) imports with `question_version = 0`.

**Why this priority**: Without `question_version`, the Admin respondent export is schema-incompatible with the Kimball-migrated `answers` table.

**Acceptance Scenarios**:

1. **Given** a finalized respondent with answers in a Kimball-migrated database, **When** the respondent is exported, **Then** each answer row in the file contains a `question_version` field.
2. **Given** a V2 respondent export file, **When** it is imported into a fresh database, **Then** `SELECT question_version FROM survey.answers WHERE respondent_id = :id` returns the same values as in the export file.
3. **Given** a V1 respondent export file (no `question_version` column), **When** it is imported, **Then** all `answers.question_version` values are `0`.

---

### Edge Cases

- What happens if a V2 export file is imported into a pre-Kimball database (one that does not yet have the durable sequence columns)?
- What happens if the source and target databases have conflicting durable id ranges (e.g. target already has `question_id = 1` from a previous import)?
- What if the export is interrupted mid-file and produces a partial output?

---

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Admin MUST apply `V0.0.9__Add_Kimball_Durable_Seq_Grants.sql` granting `USAGE, SELECT` on all eight `survey.*_durable_seq` sequences to `${surveyadmin_user}`.
- **FR-002**: `SurveyDefinitionExportService` MUST bump the format version constant to `ELICIT_SURVEY_EXPORT_V2` and add a `# schema_version: V2_KIMBALL_TYPE2` header line.
- **FR-003**: Export queries for all eight structural tables MUST select the durable key column as `source_id` and include all six Type 2 columns.
- **FR-004**: Export queries MUST filter `effective_from <= NOW() AND effective_to > NOW() AND is_draft = false`.
- **FR-005**: Export query for `metadata` MUST use the renamed column names `steps_sections_id` and `sections_question_id`.
- **FR-006**: `SurveyDefinitionImportService` MUST maintain a `sourceId → newDurableId` map per structural table in addition to the existing surrogate map.
- **FR-007**: V2 import INSERT statements MUST call `nextval('survey.{table}_durable_seq')` and capture both surrogate and durable ids via `RETURNING id, {entity}_id`.
- **FR-008**: V2 import MUST set `version = 0`, `effective_from = '1970-01-01 00:00:00+00'`, `effective_to = '9999-12-31 23:59:59+00'`, `is_draft = false` on every inserted structural row.
- **FR-009**: V1 import MUST detect the absence of `# schema_version:` header and fall back to the V1 code path, allocating durable ids and applying Type 2 defaults.
- **FR-010**: `RespondentExportService` MUST add `question_version` as the final field in the answers row format.
- **FR-011**: `RespondentImportService` MUST parse `question_version` from V2 answer rows and include it in the INSERT; default to `0` for V1 rows.

### Key Entities

- **`SurveyDefinitionExportService`**: Serialises all structural tables to pipe-delimited text. Major update.
- **`SurveyDefinitionImportService`**: Re-creates structural tables from pipe-delimited text. Major update.
- **`RespondentExportService`**: Serialises respondent + answers. Minor update (`question_version`).
- **`RespondentImportService`**: Re-creates respondent + answers. Minor update (`question_version`).
- **`V0.0.9` migration**: New Flyway migration granting the eight durable sequences.

---

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: `V0.0.9` migration runs to completion without error on the Kimball-migrated shared database.
- **SC-002**: A V2 export of a known test survey produces a file with the correct header, correct `source_id` (durable key), and correct Type 2 columns in every structural section.
- **SC-003**: Importing that V2 export into a fresh Kimball-migrated database produces a structurally identical survey (same element counts, all FK references intact).
- **SC-004**: Importing a pre-Kimball V1 export into a Kimball-migrated database succeeds with zero FK constraint violations.
- **SC-005**: A respondent export/import round-trip preserves all `question_version` values exactly.

---

## Assumptions

- Survey's Kimball Flyway migrations (V011–V019) have been applied to the shared database before Admin is deployed.
- The `surveyadmin_user` database role exists and was created by an earlier Admin migration.
- V1 export files are identified solely by the absence of the `# schema_version:` header line; no other format versioning mechanism exists.
- Admin does not need to handle the `firstAccessDt` time-range predicate — that is Survey's responsibility.
- The Admin UI version-history feature is out of scope for this release.
