# Data Model: Test Coverage for Export/Import Services

**Feature**: `000-testing` | **Date**: 2026-05-13

This document captures the database entities relevant to the test fixtures and services under test. It covers both the survey structure tables (owned by the Elicit Survey service, pre-created via `db/test` stubs) and the Admin-owned tables (created by the Admin Flyway migrations).

---

## 1. Survey Structure Tables *(owned by Elicit Survey service — pre-created in `db/test` stubs)*

These tables are queried via native SQL by `SurveyDefinitionExportService` and `SurveyDefinitionImportService`. They must exist in the test database before the Admin migrations run.

### `survey.surveys`
| Column | Type | Notes |
|--------|------|-------|
| `id` | `bigint PK` | Source ID in exports |
| `name` | `varchar` | |
| `display_order` | `int` | |
| `title` | `varchar` | |
| `description` | `varchar` | |
| `initial_display_key` | `varchar` | |
| `post_survey_url` | `varchar` | nullable |

### `survey.select_groups`
| Column | Type | Notes |
|--------|------|-------|
| `id` | `bigint PK` | |
| `survey_id` | `bigint FK → surveys` | |
| `name` | `varchar` | |
| `description` | `varchar` | nullable |
| `data_type` | `varchar` | |

### `survey.select_items`
| Column | Type | Notes |
|--------|------|-------|
| `id` | `bigint PK` | |
| `survey_id` | `bigint FK → surveys` | |
| `group_id` | `bigint FK → select_groups` | |
| `display_text` | `varchar` | |
| `display_order` | `int` | |
| `coded_value` | `varchar` | nullable |

### `survey.steps`
| Column | Type | Notes |
|--------|------|-------|
| `id` | `bigint PK` | |
| `survey_id` | `bigint FK → surveys` | |
| `display_order` | `int` | |
| `name` | `varchar` | |
| `dimension_name` | `varchar` | nullable |
| `description` | `varchar` | nullable |

### `survey.sections`
| Column | Type | Notes |
|--------|------|-------|
| `id` | `bigint PK` | |
| `survey_id` | `bigint FK → surveys` | |
| `display_order` | `int` | |
| `name` | `varchar` | |
| `dimension_name` | `varchar` | nullable |
| `description` | `varchar` | nullable |

### `survey.steps_sections`
| Column | Type | Notes |
|--------|------|-------|
| `id` | `bigint PK` | |
| `survey_id` | `bigint FK → surveys` | |
| `step_id` | `bigint FK → steps` | |
| `step_display_order` | `int` | |
| `section_id` | `bigint FK → sections` | |
| `section_display_order` | `int` | |
| `display_key` | `varchar` | unique within survey |

### `survey.question_types` *(static lookup — seeded in stub)*
| Column | Type | Notes |
|--------|------|-------|
| `id` | `bigint PK` | |
| `name` | `varchar` | |

### `survey.questions`
| Column | Type | Notes |
|--------|------|-------|
| `id` | `bigint PK` | |
| `survey_id` | `bigint FK → surveys` | |
| `type_id` | `bigint FK → question_types` | static; same values assumed in target |
| `text` | `text` | |
| `short_text` | `varchar` | nullable |
| `tool_tip` | `varchar` | nullable |
| `required` | `boolean` | |
| `min_value` | `varchar` | nullable |
| `max_value` | `varchar` | nullable |
| `validation_text` | `varchar` | nullable |
| `select_group_id` | `bigint FK → select_groups` | nullable |
| `mask` | `varchar` | nullable |
| `placeholder` | `varchar` | nullable |
| `default_value` | `varchar` | nullable |
| `variant` | `varchar` | nullable |

### `survey.sections_questions`
| Column | Type | Notes |
|--------|------|-------|
| `id` | `bigint PK` | |
| `survey_id` | `bigint FK → surveys` | |
| `question_id` | `bigint FK → questions` | |
| `section_id` | `bigint FK → sections` | |
| `display_order` | `int` | |

### `survey.operators` *(static lookup — seeded in stub)*
| Column | Type | Notes |
|--------|------|-------|
| `id` | `bigint PK` | |
| `name` | `varchar` | |

### `survey.actions` *(static lookup — seeded in stub)*
| Column | Type | Notes |
|--------|------|-------|
| `id` | `bigint PK` | |
| `name` | `varchar` | |

### `survey.relationships`
| Column | Type | Notes |
|--------|------|-------|
| `id` | `bigint PK` | |
| `survey_id` | `bigint FK → surveys` | |
| `upstream_step_id` | `bigint FK → steps` | |
| `upstream_sq_id` | `bigint FK → sections_questions` | nullable |
| `downstream_step_id` | `bigint FK → steps` | nullable |
| `downstream_s_id` | `bigint FK → sections` | nullable |
| `downstream_sq_id` | `bigint FK → sections_questions` | nullable |
| `operator_id` | `bigint FK → operators` | static |
| `action_id` | `bigint FK → actions` | static |
| `description` | `varchar` | nullable |
| `token` | `varchar` | nullable |
| `reference_value` | `varchar` | nullable |
| `default_upstream_value` | `varchar` | nullable |
| `override_upstream_value` | `varchar` | nullable |

### `survey.dimensions`
| Column | Type | Notes |
|--------|------|-------|
| `id` | `bigint PK` | |
| `name` | `varchar` | unique |

### `survey.ontology`
| Column | Type | Notes |
|--------|------|-------|
| `id` | `bigint PK` | |
| `survey_id` | `bigint FK → surveys` | |
| `name` | `varchar` | |
| `tag` | `varchar` | |
| `dimension` | `bigint FK → dimensions` | nullable |

### `survey.metadata`
| Column | Type | Notes |
|--------|------|-------|
| `id` | `bigint PK` | |
| `survey_id` | `bigint FK → surveys` | |
| `step_section_id` | `bigint` | nullable (old FK, nullable) |
| `question_id` | `bigint` | nullable |
| `section_question_id` | `bigint` | nullable |
| `ontology_id` | `bigint FK → ontology` | |
| `value` | `varchar` | |

### `survey.reports`
| Column | Type | Notes |
|--------|------|-------|
| `id` | `bigint PK` | |
| `survey_id` | `bigint FK → surveys` | |
| `name` | `varchar` | |
| `description` | `varchar` | nullable |
| `url` | `varchar` | |
| `display_order` | `int` | |

### `survey.post_survey_actions`
| Column | Type | Notes |
|--------|------|-------|
| `id` | `bigint PK` | |
| `survey_id` | `bigint FK → surveys` | |
| `name` | `varchar` | |
| `description` | `varchar` | nullable |
| `url` | `varchar` | |
| `execution_order` | `int` | |

---

## 2. Respondent Tables *(used by RespondentExportService / RespondentImportService)*

### `survey.respondents`
| Column | Type | Notes |
|--------|------|-------|
| `id` | `bigint PK` | |
| `survey_id` | `bigint FK → surveys` | |
| `token` | `varchar` | |
| `active` | `boolean` | |
| `logins` | `int` | |
| `created_dt` | `timestamptz` | |
| `first_access_dt` | `timestamptz` | nullable |
| `finalized_dt` | `timestamptz` | nullable |

### `survey.answers`
| Column | Type | Notes |
|--------|------|-------|
| `id` | `bigint PK` | |
| `respondent_id` | `bigint FK → respondents` | |
| `survey_id` | `bigint FK → surveys` | |
| `step` | `int` | |
| `step_instance` | `int` | |
| `section` | `int` | |
| `section_instance` | `int` | |
| `question_display_order` | `int` | |
| `question_instance` | `int` | |
| `section_question_id` | `bigint FK → sections_questions` | |
| `question_id` | `bigint FK → questions` | |
| `display_key` | `varchar` | |
| `display_text` | `varchar` | nullable |
| `text_value` | `text` | nullable |
| `deleted` | `boolean` | |
| `created_dt` | `timestamptz` | |
| `saved_dt` | `timestamptz` | nullable |

### `survey.dependents`
| Column | Type | Notes |
|--------|------|-------|
| `id` | `bigint PK` | |
| `respondent_id` | `bigint FK → respondents` | |
| `upstream_id` | `bigint FK → answers` | |
| `downstream_id` | `bigint FK → answers` | |
| `relationship_id` | `bigint FK → relationships` | |
| `deleted` | `boolean` | |

---

## 3. Admin-Owned Tables *(created by Admin Flyway migrations)*

These are created by `V0.0.1__CREATE_ADMIN_SCHEMA.sql` and are relevant to Admin UI tests.

### `survey.departments`
| Column | Type | Notes |
|--------|------|-------|
| `id` | `bigint PK` | |
| `name` | `varchar` | unique |
| `code` | `varchar(100)` | unique |
| `default_message_id` | `varchar` | |
| `notification_emails` | `varchar(2000)` | nullable |
| `from_email` | `varchar(50)` | |

### `survey.users`
| Column | Type | Notes |
|--------|------|-------|
| `id` | `bigint PK` | |
| `username` | `varchar(100)` | unique |
| `first_name` | `varchar(255)` | |
| `last_name` | `varchar(255)` | |
| `active` | `boolean` | default true |

---

## 4. Fixture Summary

### Survey Fixture (User Story 1)
- 1 survey row
- 1 step
- 1 section
- 2 questions (with 1 shared `question_type` static row)
- 1 `steps_sections` join
- 2 `sections_questions` joins
- 1 relationship (requires 1 operator and 1 action static row)
- No select_groups, ontology, metadata, reports, or post_survey_actions (keep fixture minimal)

### Respondent Fixture (User Story 2)
- 1 respondent row (referencing the survey fixture)
- 3 answer rows (one per question * multiple instances)
- 0 dependents (keep fixture minimal)
- 0 subjects / messages (not exercised by round-trip assertions)

### State Transitions
No state machines. Both services are stateless read/write pipelines.

### Validation Rules
- `source_id` (first field of every export data line) must be non-null
- Field counts per section are fixed (see `contracts/export-format-v1.md`)
- All FK references must resolve after import (no orphaned rows)
