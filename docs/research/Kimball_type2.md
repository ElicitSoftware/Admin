# Kimball Type 2 Impact on Admin

> **Status (2026-09-12):** Implemented. Survey shipped its Kimball Type 2 migration
> (`V001__Create_Survey_Schema.sql` / `migration-v3/V010__Kimball_Type2_SCD.sql`), and
> Admin's side described below is now done: `SurveyDefinitionExportService`/
> `SurveyDefinitionImportService` are on `ELICIT_SURVEY_EXPORT_V2` (durable keys, Type 2
> columns, no V1 backward compatibility), `RespondentExportService`/
> `RespondentImportService` carry `question_version` on `ELICIT_EXPORT_V2`, and
> `V0.0.12__Add_Kimball_Durable_Seq_Grants.sql` grants `surveyadmin_user` access to the
> new durable sequences. Section 6 (Admin UI version-history surfacing) remains out of
> scope per the 2026-09-11 scope note below.

## Overview

Admin owns two distinct responsibilities that both touch the survey structural tables
directly:

1. **Survey Definition Export/Import** — Admin serialises every structural table
   (`surveys`, `select_groups`, `select_items`, `steps`, `sections`, `steps_sections`,
   `questions`, `sections_questions`, `relationships`, `reports`, `post_survey_actions`,
   `dimensions`, `ontology`, `metadata`) to a pipe-delimited text file and re-creates
   them in a target database. After Survey implements Kimball Type 2, every one of those
   tables gains new columns that must be part of the export and correctly re-created on
   import.

2. **Respondent Administration** — Admin imports and exports respondents together with
   their answers. The `answers` table gains a `question_version` column. The subjects
   table (`survey.subjects`) holds FKs into `survey.surveys` and `survey.respondents`,
   neither of which receives Type 2 versioning columns, so subjects is unaffected.

Admin never executes live structural queries on behalf of respondents (that is Survey's
job), so Admin does not need to handle the `firstAccessDt` time-range predicate. Its
concern is purely data lifecycle management.

---

## 1. New Columns Introduced by Kimball Type 2

Every structural table receives these additional columns:

| Column | Type | Relevant to Admin? |
|---|---|---|
| `{entity}_id` (durable key) | `INTEGER` | **Yes** — export must include it; import must populate it |
| `version` | `INTEGER` | **Yes** — export; import sets to `0` for new entities |
| `effective_from` | `TIMESTAMPTZ` | **Yes** — export; import sets to `'1970-01-01 00:00:00+00'` |
| `effective_to` | `TIMESTAMPTZ` | **Yes** — export; import sets to `'9999-12-31 23:59:59+00'` |
| `published_by` | `TEXT` | **Yes** — export; import may leave `NULL` |
| `published_comment` | `TEXT` | **Yes** — export; import may leave `NULL` |
| `is_draft` | `BOOLEAN` | **Yes** — export; import sets to `false` |

`metadata` gets its three surrogate FK columns renamed and/or replaced with durable
integer columns (`question_id` → durable integer, `section_question_id` →
`sections_question_id`, `step_section_id` → `steps_sections_id`). The export format
must track these renamed columns.

`surveys` receives only `published_by` and `published_comment` (SCD Type 1 — no time
range columns).

`answers` receives `question_version INTEGER NOT NULL DEFAULT 0`.

---

## 2. Survey Definition Export — `SurveyDefinitionExportService`

### What must change

The export currently serialises each table's content rows using surrogate `id` values as
cross-table references (`source_id`). After Kimball Type 2, the canonical cross-table
reference for structural tables is the **durable integer key** (`question_id`,
`section_id`, etc.), not the surrogate `id`. The file format version must be bumped to
`ELICIT_SURVEY_EXPORT_V2`.

### File format header addition

Add a new metadata line after the `# generated:` line:

```
# schema_version: V2_KIMBALL_TYPE2
```

### Table-by-table export changes

#### `surveys`

Add `published_by` and `published_comment` to the exported fields (nullable — export as
empty field if `NULL`):

```
surveys: source_id|name|display_order|title|description|initial_display_key|post_survey_url|published_by|published_comment
```

#### `select_groups`, `select_items`, `steps`, `sections`

Add the six new Kimball columns to every row. Use the **durable key** as `source_id`
(replace old surrogate `id` source_id with `{entity}_id`):

```
-- Example: steps
steps: source_id(=step_id)|display_order|name|dimension_name|description|version|effective_from|effective_to|published_by|published_comment|is_draft
```

Query change in `getSteps()`:

```java
// OLD
"SELECT s.id, s.display_order, s.name, s.dimension_name, s.description " +
"FROM survey.steps s WHERE s.survey_id = ?1 ORDER BY s.display_order"

// NEW — select current version rows only; use durable step_id as source_id
"SELECT s.step_id, s.display_order, s.name, s.dimension_name, s.description, " +
"       s.version, s.effective_from, s.effective_to, s.published_by, s.published_comment, s.is_draft " +
"FROM survey.steps s " +
"WHERE s.survey_id = ?1 " +
"  AND s.effective_from <= NOW() AND s.effective_to > NOW() " +
"ORDER BY s.display_order"
```

Apply the same pattern to `getSelectGroups()`, `getSelectItems()`, `getSections()`,
`getQuestions()`.

#### `steps_sections`

The join table references durable `step_id` and `section_id`. Export `steps_sections_id`
as `source_id`. The cross-references (`step_id`, `section_id`) are now durable integers
and must be exported as-is (they map to the `source_id` of the corresponding step/section
row):

```
steps_sections: source_id(=steps_sections_id)|step_id(durable)|step_display_order|section_id(durable)|section_display_order|display_key|version|effective_from|effective_to|published_by|published_comment|is_draft
```

#### `sections_questions`

Export `sections_question_id` as `source_id`. The `question_id` and `section_id` fields
are now durable integers:

```
sections_questions: source_id(=sections_question_id)|question_id(durable)|section_id(durable)|display_order|version|effective_from|effective_to|published_by|published_comment|is_draft
```

#### `relationships`

Export `relationship_id` as `source_id`. All five FK columns
(`upstream_step_id`, `upstream_sq_id`, `downstream_step_id`, `downstream_ss_id`,
`downstream_sq_id`) are now durable integers:

```
relationships: source_id(=relationship_id)|upstream_step_id(durable)|upstream_sq_id(durable)|downstream_step_id(durable)|downstream_ss_id(durable)|downstream_sq_id(durable)|operator_id|action_id|description|token|reference_value|default_upstream_value|override_upstream_value|version|effective_from|effective_to|published_by|published_comment|is_draft
```

#### `metadata`

The three element FK columns are renamed/replaced with durable integers:

```
-- OLD column names:  step_section_id | question_id (surrogate) | section_question_id
-- NEW column names:  steps_sections_id | question_id (durable)  | sections_question_id
```

Export query must reference the new column names:

```java
// NEW
"SELECT m.id, m.steps_sections_id, m.question_id, m.sections_question_id, m.ontology_id, m.value " +
"FROM survey.metadata m " +
"JOIN survey.questions q ON q.question_id = m.question_id " +
"WHERE q.survey_id = ?1"
```

---

## 3. Survey Definition Import — `SurveyDefinitionImportService`

### What must change

The importer must:

1. Detect the format version (`V1` vs `V2`). For `V2`, use durable-key-aware insert
   queries and grant the new durable key sequences.
2. Allocate new surrogate `id` values from the existing sequences AND allocate new
   durable key values from the new `{entity}_durable_seq` sequences.
3. Set `version = 0`, `effective_from = '1970-01-01 00:00:00+00'`,
   `effective_to = '9999-12-31 23:59:59+00'`, `is_draft = false` on every inserted row.
4. Keep `published_by` and `published_comment` as `NULL` on import (the survey is being
   installed, not authored).
5. Re-map durable key cross-references: the importer already maintains a
   `source_id → new_id` map for surrogate keys; a parallel `source_durable_id →
   new_durable_id` map is now required for each table.

### New sequence grants required

Admin's migrations now run through `V0.0.11` (see
`src/main/resources/db/migration/`), so this needs a new
`V0.0.12__Add_Kimball_Durable_Seq_Grants.sql` (the `V0.0.9` name originally proposed here
has since been used by `V0.0.9__Add_User_Roles.sql`):

```sql
-- Durable key sequences created by the Survey Kimball Type 2 migration
GRANT ALL ON SEQUENCE survey.questions_durable_seq      TO ${surveyadmin_user};
GRANT ALL ON SEQUENCE survey.select_groups_durable_seq  TO ${surveyadmin_user};
GRANT ALL ON SEQUENCE survey.select_items_durable_seq   TO ${surveyadmin_user};
GRANT ALL ON SEQUENCE survey.sections_durable_seq       TO ${surveyadmin_user};
GRANT ALL ON SEQUENCE survey.steps_durable_seq          TO ${surveyadmin_user};
GRANT ALL ON SEQUENCE survey.sections_questions_durable_seq TO ${surveyadmin_user};
GRANT ALL ON SEQUENCE survey.steps_sections_durable_seq TO ${surveyadmin_user};
GRANT ALL ON SEQUENCE survey.relationships_durable_seq  TO ${surveyadmin_user};
```

### Import INSERT changes

Every structural table INSERT must populate the new columns. Example for `steps`:

```java
// V2 import INSERT for steps
private static final String INSERT_STEP_V2 =
    "INSERT INTO survey.steps " +
    "  (id, survey_id, display_order, name, dimension_name, description, " +
    "   step_id, version, effective_from, effective_to, published_by, published_comment, is_draft) " +
    "VALUES " +
    "  (nextval('survey.steps_seq'), :survey_id, :display_order, :name, :dimension_name, :description, " +
    "   nextval('survey.steps_durable_seq'), 0, " +
    "   '1970-01-01 00:00:00+00', '9999-12-31 23:59:59+00', NULL, NULL, false) " +
    "RETURNING id, step_id";
```

After inserting, capture both `id` (surrogate) and `step_id` (durable) from the
`RETURNING` clause and store both in separate maps:

```java
// In the importer method:
surrogateMap.put(sourceId, newSurrogateId);
durableMap.put(sourceId, newDurableId);
```

Then, when inserting `steps_sections`, resolve the durable step/section ids from the
durable map rather than the surrogate map.

### No backward compatibility with V1 exports — decision reversed (2026-09-11)

**Decision**: `SurveyDefinitionImportService` will **not** support importing pre-Kimball
(`V1`) export files at all. There is no surrogate-to-durable translation path. On
detecting a format-version header that is not `V2_KIMBALL_TYPE2`, the importer must
reject the file immediately with a clear error message (e.g. "This export was generated
before the Kimball Type 2 migration and cannot be imported into this database version —
re-export the source survey with a current Admin build") and perform no inserts.

This removes the dual surrogate/durable mapping logic that a backward-compatibility path
would otherwise require, at the cost of no longer being able to import archived V1
export files without first re-exporting them from a pre-Kimball Admin build.

---

## 4. Respondent Import/Export — `RespondentImportService` / `RespondentExportService`

### `answers.question_version`

A new column `question_version INTEGER NOT NULL DEFAULT 0` is added to `survey.answers`.

**Export** — add `question_version` to the pipe-delimited answer rows:

```
answers: respondent_id|question_id|section_question_id|display_key|text_value|deleted|question_version
```

**Import** — parse `question_version` from the data line and include it in the INSERT:

```java
// NEW — include question_version in INSERT
"INSERT INTO survey.answers " +
"  (respondent_id, survey_id, question_id, section_question_id, display_key, text_value, deleted, question_version) " +
"VALUES " +
"  (:respondent_id, :survey_id, :question_id, :section_question_id, :display_key, :text_value, :deleted, :question_version)"
```

Consistent with the decision above, `RespondentImportService` does not support V1
(pre-Kimball) exports either — an import file missing `question_version` is a V1 export
and must be rejected with a clear error rather than defaulted to `0`.

---

## 5. Respondent Validator — no changes required

`RespondentValidator` checks whether a respondent token is valid and whether the
respondent is active. It queries `survey.respondents` (which does not receive Type 2
changes) and `survey.surveys` (Type 1 only). No changes needed.

---

## 6. Admin UI — Surfacing Version History (optional enhancement)

> **Scope note (2026-09-11)**: This section, and any other Author/Admin Tool UI work
> (draft editing screens, publish-workflow controls, version-history views), is
> intentionally **out of scope** for this implementation pass. It will be designed and
> built in a separate, later effort. The service-layer/export-import work in Sections
> 1–4 does not depend on it.

Although not strictly required for the Kimball Type 2 implementation itself, the Admin
Tool is the natural place to surface version history to administrators:

- **Survey Definition View**: Show `version`, `effective_from`, `effective_to`, and
  `published_by` / `published_comment` for each structural element. This requires
  read-only queries against the structural tables filtering on the specific entity's
  durable id and ordering by `version`.

- **Respondent Search**: The existing `SearchView` looks up respondents. No change is
  required because `respondents` is unchanged.

- **Export — draft exclusion**: The current export query should add
  `AND is_draft = false` as a safety guard so that unpublished draft rows are never
  exported to a target system. Current version rows already satisfy the time-range
  predicate, but an explicit `is_draft = false` clause makes the intent unambiguous.

---

## 7. Implementation Steps (sequenced)

Perform these in order after Survey's Kimball Type 2 Flyway migrations have been applied
to the shared database:

| Step | Action | File(s) to change |
|---|---|---|
| 1 | Add Flyway migration `V0.0.12__Add_Kimball_Durable_Seq_Grants.sql` granting `${surveyadmin_user}` access to all eight new durable sequences | `src/main/resources/db/migration/` |
| 2 | Bump export format version to `ELICIT_SURVEY_EXPORT_V2`; update the `FORMAT_VERSION` constant | `src/main/java/com/elicitsoftware/service/SurveyDefinitionExportService.java` |
| 3 | Update all `get*()` export queries to select durable key columns, Type 2 columns, and renamed metadata columns; filter on `effective_from <= NOW() AND effective_to > NOW()` | `src/main/java/com/elicitsoftware/service/SurveyDefinitionExportService.java` |
| 4 | Add durable-key maps to the importer; update all `INSERT` statements to populate durable key and Type 2 columns; consume `RETURNING id, {entity}_id` | `src/main/java/com/elicitsoftware/service/SurveyDefinitionImportService.java` |
| 5 | Add explicit rejection of pre-Kimball (`V1`) export files: detect the format-version header and fail fast with a clear error message — no surrogate/durable translation path is built | `src/main/java/com/elicitsoftware/service/SurveyDefinitionImportService.java` |
| 6 | Add `question_version` to the respondent export format; update the export query | `src/main/java/com/elicitsoftware/service/RespondentExportService.java` |
| 7 | Add `question_version` to the respondent import INSERT; reject V1 respondent exports (missing `question_version`) with a clear error rather than defaulting | `src/main/java/com/elicitsoftware/service/RespondentImportService.java` |
| 8 | Regression test: export a known survey from a Kimball-migrated DB; re-import into a fresh DB; verify row counts and FK integrity | QA / test environment |
| 9 | Regression test: export respondents (including answers) and re-import; verify `question_version` round-trips correctly | QA / test environment |
| 10 | Regression test: attempt to import a pre-Kimball (`V1`) export file and confirm it is rejected with a clear error, not partially imported | QA / test environment |

**Rollback strategy**: no Flyway down-migration will be authored for `V0.0.12` or any
other Kimball-related Admin migration. Recovery from a bad rollout is an operational
pre-upgrade database backup/restore.

---

## 8. Summary of Affected Files

| File | Nature of change |
|---|---|
| `src/main/resources/db/migration/V0.0.12__Add_Kimball_Durable_Seq_Grants.sql` | **New file** — grants eight new durable sequences to `${surveyadmin_user}` |
| `src/main/java/com/elicitsoftware/service/SurveyDefinitionExportService.java` | Updated export queries (durable keys, Type 2 columns, renamed metadata columns), new format version |
| `src/main/java/com/elicitsoftware/service/SurveyDefinitionImportService.java` | Updated INSERT statements (durable key allocation, Type 2 defaults); rejects pre-Kimball (`V1`) export files instead of translating them |
| `src/main/java/com/elicitsoftware/service/RespondentExportService.java` | Add `question_version` to answers export |
| `src/main/java/com/elicitsoftware/service/RespondentImportService.java` | Add `question_version` to answers import INSERT; rejects V1 (pre-Kimball) respondent exports |
