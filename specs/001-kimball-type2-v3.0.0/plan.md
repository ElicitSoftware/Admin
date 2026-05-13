# Implementation Plan: Kimball Type 2 SCD Support — Admin v3.0.0

**Branch**: `001-kimball-type2-v3.0.0` | **Date**: 2026-05-12 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `specs/001-kimball-type2-v3.0.0/spec.md` and research from `research/Kimball_type2.md`

---

## Summary

Admin needs one new Flyway migration (sequence grants) and updates to four Java service classes to handle the new Kimball Type 2 columns in the shared `survey` schema. The version target is `3.0.0`.

---

## Technical Context

**Language/Version**: Java 17
**Primary Dependencies**: Quarkus 3.x, Panache ORM, Flyway
**Storage**: PostgreSQL 14+ — shared `survey` schema (owned by Survey); Admin reads/writes via `surveyadmin_user` role
**Testing**: JUnit 5, `@QuarkusTest`
**Target Platform**: Linux container
**Project Type**: Quarkus web-service + Vaadin UI
**Constraints**: Must deploy after Survey's Kimball migrations (V011–V019) are applied; must not break V1 import for existing partner sites

---

## Project Structure

### Documentation (this feature)

```text
specs/001-kimball-type2-v3.0.0/
├── spec.md
├── plan.md          ← This file
├── research.md
├── data-model.md
├── quickstart.md
└── tasks.md
```

### Source Code (affected paths)

```text
src/main/resources/db/migration/
└── V0.0.9__Add_Kimball_Durable_Seq_Grants.sql        ← New file

src/main/java/com/elicitsoftware/
├── service/
│   ├── SurveyDefinitionExportService.java            ← Major update
│   ├── SurveyDefinitionImportService.java            ← Major update
│   ├── RespondentExportService.java                  ← Minor update
│   └── RespondentImportService.java                  ← Minor update
```

---

## Flyway Migration — V0.0.9

```sql
-- V0.0.9__Add_Kimball_Durable_Seq_Grants.sql
-- Grants the surveyadmin role access to the eight new durable sequences
-- created by Survey's Kimball Type 2 migration (V011__Kimball_Sequences.sql).
-- This migration must run after Survey's Kimball migrations have been applied.

GRANT USAGE, SELECT ON SEQUENCE survey.questions_durable_seq      TO ${surveyadmin_user};
GRANT USAGE, SELECT ON SEQUENCE survey.select_groups_durable_seq  TO ${surveyadmin_user};
GRANT USAGE, SELECT ON SEQUENCE survey.select_items_durable_seq   TO ${surveyadmin_user};
GRANT USAGE, SELECT ON SEQUENCE survey.sections_durable_seq       TO ${surveyadmin_user};
GRANT USAGE, SELECT ON SEQUENCE survey.steps_durable_seq          TO ${surveyadmin_user};
GRANT USAGE, SELECT ON SEQUENCE survey.sections_questions_durable_seq TO ${surveyadmin_user};
GRANT USAGE, SELECT ON SEQUENCE survey.steps_sections_durable_seq TO ${surveyadmin_user};
GRANT USAGE, SELECT ON SEQUENCE survey.relationships_durable_seq  TO ${surveyadmin_user};
```

---

## `SurveyDefinitionExportService` — Changes

### Format version constant

```java
// OLD
private static final String FORMAT_VERSION = "ELICIT_SURVEY_EXPORT_V1";
// NEW
private static final String FORMAT_VERSION = "ELICIT_SURVEY_EXPORT_V2";
```

Add header line after `# generated:`:

```java
writer.println("# schema_version: V2_KIMBALL_TYPE2");
```

### Export query changes per structural table

All structural table queries change in two ways:
1. The durable key column (`step_id`, `section_id`, etc.) replaces the surrogate `id` as `source_id`.
2. Six Type 2 columns are appended: `version`, `effective_from`, `effective_to`, `published_by`, `published_comment`, `is_draft`.
3. Filter: `AND s.effective_from <= NOW() AND s.effective_to > NOW() AND s.is_draft = false`.

Example for `steps`:

```java
// NEW
"SELECT s.step_id, s.display_order, s.name, s.dimension_name, s.description, " +
"       s.version, s.effective_from, s.effective_to, " +
"       s.published_by, s.published_comment, s.is_draft " +
"FROM survey.steps s " +
"WHERE s.survey_id = ?1 " +
"  AND s.effective_from <= NOW() AND s.effective_to > NOW() " +
"  AND s.is_draft = false " +
"ORDER BY s.display_order"
```

Apply the same pattern to `getSelectGroups()`, `getSelectItems()`, `getSections()`, `getQuestions()`, `getStepsSections()`, `getSectionsQuestions()`, `getRelationships()`.

**For `metadata`**: Replace old column names:

```java
// NEW
"SELECT m.id, m.steps_sections_id, m.question_id, m.sections_question_id, m.ontology_id, m.value " +
"FROM survey.metadata m " +
"JOIN survey.questions q ON q.question_id = m.question_id " +   // durable join
"WHERE q.survey_id = ?1"
```

**For `surveys`**: Add `published_by`, `published_comment` fields.

---

## `SurveyDefinitionImportService` — Changes

### Format detection

```java
// At the top of the import method, detect format version:
String schemaVersion = null;
// read header lines — if "# schema_version: V2_KIMBALL_TYPE2" found → V2 path
// otherwise → V1 backward-compat path
```

### Dual id maps (V2 path)

```java
// One pair of maps per structural table (example for steps):
Map<Long, Long> stepSurrogateMap = new HashMap<>();  // sourceId → new surrogate id
Map<Long, Long> stepDurableMap   = new HashMap<>();  // sourceId → new durable id
```

### INSERT statements (V2 path)

All structural INSERT statements must:
- Allocate surrogate id from the existing sequence (e.g. `nextval('survey.steps_seq')`).
- Allocate durable id from the new durable sequence (e.g. `nextval('survey.steps_durable_seq')`).
- Set `version = 0`, `effective_from = '1970-01-01 00:00:00+00'`, `effective_to = '9999-12-31 23:59:59+00'`, `is_draft = false`.
- Use `RETURNING id, step_id` to capture both ids.

Example for steps:

```java
private static final String INSERT_STEP_V2 =
    "INSERT INTO survey.steps " +
    "  (id, survey_id, display_order, name, dimension_name, description, " +
    "   step_id, version, effective_from, effective_to, is_draft) " +
    "VALUES " +
    "  (nextval('survey.steps_seq'), :survey_id, :display_order, :name, " +
    "   :dimension_name, :description, " +
    "   nextval('survey.steps_durable_seq'), 0, " +
    "   '1970-01-01 00:00:00+00', '9999-12-31 23:59:59+00', false) " +
    "RETURNING id, step_id";
```

After INSERT, store in both maps:

```java
stepSurrogateMap.put(sourceId, result.getLong("id"));
stepDurableMap.put(sourceId, result.getLong("step_id"));
```

### Join-table FK resolution (V2 path)

When inserting `steps_sections`, resolve `step_id` and `section_id` from the **durable** map:

```java
long newStepDurableId    = stepDurableMap.get(sourceStepId);
long newSectionDurableId = sectionDurableMap.get(sourceSectionId);
```

Apply the same to `sections_questions`, `relationships` (all five FK columns use durable maps).

### V1 backward-compat path

When the format version header is absent:
- Same INSERT structure as V2 but source file does not contain durable key columns.
- Allocate new durable id via `nextval(...)` regardless.
- Resolve join-table FKs by first looking up the new surrogate id, then finding its corresponding durable id from the surrogate-to-durable map built during the current import session.

---

## `RespondentExportService` — Changes

Add `question_version` as the last field in the answers row:

```java
// Answer row format — OLD:
// respondent_id|question_id|section_question_id|display_key|text_value|deleted

// Answer row format — NEW:
// respondent_id|question_id|section_question_id|display_key|text_value|deleted|question_version
```

Update the export query to include `a.question_version`.

---

## `RespondentImportService` — Changes

Parse `question_version` from each answer row (index 6 if V2; default `0` if V1 row has only 6 fields):

```java
int questionVersion = parts.length > 6 ? Integer.parseInt(parts[6]) : 0;
```

Include `question_version` in the INSERT:

```java
"INSERT INTO survey.answers " +
"  (respondent_id, survey_id, question_id, section_question_id, " +
"   display_key, text_value, deleted, question_version) " +
"VALUES (:respondent_id, :survey_id, :question_id, :section_question_id, " +
"        :display_key, :text_value, :deleted, :question_version)"
```
