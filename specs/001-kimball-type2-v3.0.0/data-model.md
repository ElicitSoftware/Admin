# Data Model: Kimball Type 2 SCD Support — Admin v3.0.0

**Branch**: `001-kimball-type2-v3.0.0` | **Date**: 2026-05-12

Admin does not own any database tables. All structural tables are in the `survey` schema, owned and migrated by the Survey application. Admin reads and writes via the `surveyadmin_user` role.

This document captures:
1. New sequences Admin must have access to (granted by V0.0.9).
2. Export format changes — how each section of the export file evolves from V1 to V2.
3. Import INSERT statement changes.

---

## 1. New Sequences (V0.0.9 Grants)

| Sequence | Grantee |
|---|---|
| `survey.questions_durable_seq` | `${surveyadmin_user}` |
| `survey.select_groups_durable_seq` | `${surveyadmin_user}` |
| `survey.select_items_durable_seq` | `${surveyadmin_user}` |
| `survey.sections_durable_seq` | `${surveyadmin_user}` |
| `survey.steps_durable_seq` | `${surveyadmin_user}` |
| `survey.sections_questions_durable_seq` | `${surveyadmin_user}` |
| `survey.steps_sections_durable_seq` | `${surveyadmin_user}` |
| `survey.relationships_durable_seq` | `${surveyadmin_user}` |

---

## 2. Export Format Changes (V1 → V2)

### File Header

| Field | V1 | V2 |
|---|---|---|
| Format version line | `# version: ELICIT_SURVEY_EXPORT_V1` | `# version: ELICIT_SURVEY_EXPORT_V2` |
| Schema version line | *(absent)* | `# schema_version: V2_KIMBALL_TYPE2` |

---

### `surveys` Section

| Field | V1 | V2 |
|---|---|---|
| `id` | ✅ | ✅ |
| `name` | ✅ | ✅ |
| `status` | ✅ | ✅ |
| `published_by` | *(absent)* | ✅ added |
| `published_comment` | *(absent)* | ✅ added |

---

### `steps` Section

| Field | V1 | V2 |
|---|---|---|
| `id` (surrogate, used as source_id) | ✅ | Replaced by `step_id` (durable) |
| `survey_id` | ✅ | ✅ |
| `display_order` | ✅ | ✅ |
| `name` | ✅ | ✅ |
| `dimension_name` | ✅ | ✅ |
| `description` | ✅ | ✅ |
| `version` | *(absent)* | ✅ added |
| `effective_from` | *(absent)* | ✅ added |
| `effective_to` | *(absent)* | ✅ added |
| `published_by` | *(absent)* | ✅ added |
| `published_comment` | *(absent)* | ✅ added |
| `is_draft` | *(absent)* | ✅ added |
| Export filter | none | `effective_from <= NOW() AND effective_to > NOW() AND is_draft = false` |

Same column additions apply to: `sections`, `questions`, `select_groups`, `select_items`, `steps_sections`, `sections_questions`, `relationships`.

---

### `steps_sections` Section — FK columns

| Field | V1 | V2 |
|---|---|---|
| `step_id` FK | Surrogate `steps.id` | Durable `steps.step_id` |
| `section_id` FK | Surrogate `sections.id` | Durable `sections.section_id` |

Same durable FK resolution applies to `sections_questions` (`section_id`, `question_id`) and `relationships` (`upstream_ss_id`, `downstream_ss_id`, `section_id`).

---

### `metadata` Section — column name changes

| Field | V1 | V2 |
|---|---|---|
| `step_section_id` | ✅ | Renamed → `steps_sections_id` |
| `section_question_id` | ✅ | Renamed → `sections_question_id` |
| `question_id` FK | Surrogate `questions.id` | Durable `questions.question_id` |

---

### `answers` Section (Respondent Export)

| Field | V1 | V2 |
|---|---|---|
| `respondent_id` | ✅ | ✅ |
| `question_id` | ✅ | ✅ |
| `section_question_id` | ✅ | ✅ |
| `display_key` | ✅ | ✅ |
| `text_value` | ✅ | ✅ |
| `deleted` | ✅ | ✅ |
| `question_version` | *(absent)* | ✅ added (index 6) |

---

## 3. Import INSERT Changes

### V2 INSERT pattern for structural tables

Each structural INSERT adds:
- A call to `nextval('survey.{table}_durable_seq')` for the durable key column.
- Six Type 2 columns: `version = 0`, `effective_from = '1970-01-01 00:00:00+00'`, `effective_to = '9999-12-31 23:59:59+00'`, `is_draft = false`, `published_by = NULL`, `published_comment = NULL`.
- `RETURNING id, {entity}_id` to capture both the surrogate and durable ids.

### V1 backward-compat INSERT pattern

Same as V2 INSERT except:
- Source file does not contain durable id or Type 2 columns.
- Durable ids are freshly allocated via `nextval(...)` rather than read from the file.
- Join-table FK resolution falls back to the surrogate map then looks up the corresponding durable id from the in-session surrogate-to-durable map.

### `answers` INSERT (Respondent Import)

Adds `question_version` to the INSERT (value from file for V2; `0` for V1 rows with fewer than 7 pipe-delimited fields).
