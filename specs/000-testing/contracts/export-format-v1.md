# Export Format Contract: ELICIT V1

**Feature**: `000-testing` | **Date**: 2026-05-13

This document formally specifies the two V1 pipe-delimited export formats produced by `SurveyDefinitionExportService` and `RespondentExportService`. It is the ground truth for format contract tests (User Story 3, FR-006).

---

## Survey Definition Export — `ELICIT_SURVEY_EXPORT_V1`

### Header Block

```
# ELICIT_SURVEY_EXPORT_V1
# survey_id: <integer>
# survey_name: <string>
# surveys: <count>
# select_groups: <count>
# select_items: <count>
# steps: <count>
# sections: <count>
# steps_sections: <count>
# questions: <count>
# sections_questions: <count>
# relationships: <count>
# reports: <count>
# post_survey_actions: <count>
# dimensions: <count>
# ontology: <count>
# metadata: <count>
# generated: <ISO-8601 timestamp with offset>
<blank line>
```

- Lines starting with `#` are comments and must be ignored during import except for the first line (version check) and count lines.

### Data Sections (in order)

Each data line begins with `<section-name>: ` followed by pipe-delimited fields. There is **one data line per row** in the source table.

| Section label | Field count | Field order |
|---------------|-------------|-------------|
| `surveys` | 7 | `source_id\|name\|display_order\|title\|description\|initial_display_key\|post_survey_url` |
| `select_groups` | 4 | `source_id\|name\|description\|data_type` |
| `select_items` | 5 | `source_id\|group_id\|display_text\|display_order\|coded_value` |
| `steps` | 5 | `source_id\|display_order\|name\|dimension_name\|description` |
| `sections` | 5 | `source_id\|display_order\|name\|dimension_name\|description` |
| `steps_sections` | 6 | `source_id\|step_id\|step_display_order\|section_id\|section_display_order\|display_key` |
| `questions` | 14 | `source_id\|type_id\|text\|short_text\|tool_tip\|required\|min_value\|max_value\|validation_text\|select_group_id\|mask\|placeholder\|default_value\|variant` |
| `sections_questions` | 4 | `source_id\|question_id\|section_id\|display_order` |
| `relationships` | 13 | `source_id\|upstream_step_id\|upstream_sq_id\|downstream_step_id\|downstream_s_id\|downstream_sq_id\|operator_id\|action_id\|description\|token\|reference_value\|default_upstream_value\|override_upstream_value` |
| `reports` | 5 | `source_id\|name\|description\|url\|display_order` |
| `post_survey_actions` | 5 | `source_id\|name\|description\|url\|execution_order` |
| `dimensions` | 2 | `source_id\|name` |
| `ontology` | 4 | `source_id\|name\|tag\|dimension` |
| `metadata` | 6 | `source_id\|step_section_id\|question_id\|section_question_id\|ontology_id\|value` |

**Total section count**: 14

> `source_id` is always the first field and carries the original database `id` from the exporting system. The importer uses it to resolve cross-table FK references to new IDs.

---

## Respondent Export — `ELICIT_EXPORT_V1`

### Header Block

```
# ELICIT_EXPORT_V1
# respondent_id: <integer>
# timezone: <IANA timezone string>
# generated: <ISO-8601 timestamp with offset>
# answers: <count>
# dependents: <count>
# subjects: <count>
# messages: <count>
# respondent_psa: <count>
<blank line>
```

### Data Sections (in order)

| Section label | Field count | Field order |
|---------------|-------------|-------------|
| `respondents` | 5 | `survey_id\|token\|logins\|created_dt\|first_access_dt` |
| `answers` | 16 | `survey_id\|step\|step_instance\|section\|section_instance\|question_display_order\|question_instance\|section_question_id\|question_id\|display_key\|display_text\|text_value\|deleted\|created_dt\|saved_dt` *(V1 — no `question_version` field)* |
| `dependents` | 4 | `upstream_display_key\|downstream_display_key\|relationship_id\|deleted` |
| `subjects` | variable | `subject_index\|xid\|firstname\|lastname\|...` |
| `messages` | variable | `subject_index\|message_type\|mime_type\|...\|created_dt\|sent_dt` |
| `respondent_psa` | 6 | `post_survey_action_id\|tries\|status\|error_msg\|created_dt\|uploaded_dt` |

> **V1 note**: The `answers` section has **no `question_version` field**. Spec 001 (Kimball Type 2) will add it, bumping the format version. Format contract tests MUST assert the V1 field count to catch any accidental V2 field insertion.

---

## Escape Sequences (both formats)

| Raw character | Escaped representation |
|---------------|----------------------|
| `\` (backslash) | `\\` |
| `\|` (pipe) | `\|` |
| `\n` (newline LF) | `\n` |
| `\r` (carriage return CR) | `\r` |
| `null` value | empty string (empty field between delimiters) |

**Round-trip rule**: `unescape(escape(x)) == x` for all non-null strings. `escape(null) == ""` and `unescape("") == null` (or treated as empty, consistent with import).

### Escape order (MUST be applied in this order to avoid double-escaping)
1. `\` → `\\`
2. `|` → `\|`
3. newline (`\n`) → `\n`
4. carriage return (`\r`) → `\r`

### Unescape order
Reverse of escape: process escape sequences left-to-right in a single pass.
