# Tasks: Kimball Type 2 SCD Support — Admin v3.0.0

**Branch**: `001-kimball-type2-v3.0.0` | **Date**: 2026-05-12 | **Spec**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md)

---

## Phases

| Phase | Scope | Dependencies |
|---|---|---|
| Phase 1 | Flyway migration | Survey V011–V019 applied to shared DB |
| Phase 2 | Export service updates | Phase 1 complete |
| Phase 3 | Import service updates | Phase 2 complete (import must accept what export produces) |
| Phase 4 | Respondent export/import | Phases 1–3 complete |
| Phase 5 | Testing & release | All prior phases complete |

---

## Task List

### Phase 1 — Flyway Migration

- [ ] **T01** — Create `src/main/resources/db/migration/V0.0.9__Add_Kimball_Durable_Seq_Grants.sql`
  - Grant `USAGE, SELECT` on all eight `survey.*_durable_seq` sequences to `${surveyadmin_user}`.
  - *Prerequisite*: Survey Kimball migrations (V011–V019) must be applied to the target database before this migration runs.
  - *Verify*: Run `mvn flyway:migrate` against a Kimball-migrated test database. Confirm V0.0.9 listed as success in `flyway_schema_history`.

---

### Phase 2 — Survey Definition Export

- [ ] **T02** — Update the `FORMAT_VERSION` constant in `SurveyDefinitionExportService` to `ELICIT_SURVEY_EXPORT_V2`; add `# schema_version: V2_KIMBALL_TYPE2` header line output.
  - *Verify*: Run a test export; check the file header.

- [ ] **T03** — Update the `steps` export query to select `step_id` as `source_id` and add six Type 2 columns; add time-range and draft filter.

- [ ] **T04** — Update the `sections` export query (same pattern as T03, using `section_id`).

- [ ] **T05** — Update the `questions` export query (same pattern, using `question_id`).

- [ ] **T06** — Update the `select_groups` export query (same pattern, using `select_group_id`).

- [ ] **T07** — Update the `select_items` export query (same pattern, using `select_item_id`).

- [ ] **T08** — Update the `steps_sections` export query (same pattern, using `steps_sections_id`); FK columns `step_id` and `section_id` now reference durable keys.

- [ ] **T09** — Update the `sections_questions` export query (same pattern, using `sections_question_id`); FK columns `section_id` and `question_id` now reference durable keys.

- [ ] **T10** — Update the `relationships` export query (same pattern, using `relationship_id`); FK columns `upstream_ss_id`, `downstream_ss_id`, `section_id` now reference durable keys.

- [ ] **T11** — Update the `metadata` export query: use column names `steps_sections_id` and `sections_question_id`; resolve `question_id` FK to durable `questions.question_id`.

- [ ] **T12** — Update the `surveys` export query: add `published_by` and `published_comment` fields.

---

### Phase 3 — Survey Definition Import

- [ ] **T13** — Add format-version detection at the top of the import method: read the `# schema_version:` header line; route to V2 path or V1 backward-compat path.

- [ ] **T14** — Declare dual id maps per structural table (`surrogateMap`, `durableMap`) for the V2 import path.

- [ ] **T15** — Update the `steps` import INSERT (V2 path): call `nextval('survey.steps_durable_seq')`, insert six Type 2 columns with defaults, use `RETURNING id, step_id`, populate both maps.

- [ ] **T16** — Update the `sections` import INSERT (V2 path, same pattern, `sections_durable_seq`, `RETURNING id, section_id`).

- [ ] **T17** — Update the `questions` import INSERT (V2 path, same pattern, `questions_durable_seq`, `RETURNING id, question_id`).

- [ ] **T18** — Update the `select_groups` import INSERT (V2 path, same pattern, `select_groups_durable_seq`, `RETURNING id, select_group_id`).

- [ ] **T19** — Update the `select_items` import INSERT (V2 path, same pattern, `select_items_durable_seq`, `RETURNING id, select_item_id`).

- [ ] **T20** — Update the `steps_sections` import INSERT (V2 path): resolve `step_id` and `section_id` from durable maps; call `nextval('survey.steps_sections_durable_seq')`.

- [ ] **T21** — Update the `sections_questions` import INSERT (V2 path): resolve `section_id` and `question_id` from durable maps; call `nextval('survey.sections_questions_durable_seq')`.

- [ ] **T22** — Update the `relationships` import INSERT (V2 path): resolve `upstream_ss_id`, `downstream_ss_id`, `section_id` from durable maps; call `nextval('survey.relationships_durable_seq')`.

- [ ] **T23** — Update the `metadata` import INSERT: parse `steps_sections_id` and `sections_question_id` column names; resolve `question_id` FK from durable map.

- [ ] **T24** — Implement V1 backward-compat import path: allocate new durable ids via sequences; apply Type 2 defaults; resolve join-table FKs via surrogate-then-durable map lookup.

---

### Phase 4 — Respondent Export/Import

- [ ] **T25** — Update `RespondentExportService` answers query and row serialisation to append `question_version` as field index 6.

- [ ] **T26** — Update `RespondentImportService` answer row parsing to read `question_version` from index 6 if present; default to `0` otherwise; include in INSERT.

---

### Phase 5 — Testing & Release

- [ ] **T27** — Write or update `SurveyDefinitionExportServiceTest`: assert V2 header; assert no draft rows; assert `step_id` (durable) is the `source_id` field.

- [ ] **T28** — Write or update `SurveyDefinitionImportServiceTest`: V2 round-trip (export then import to fresh DB); assert all FK relationships intact; assert `effective_to = '9999-12-31 23:59:59+00'` on all imported rows.

- [ ] **T29** — Write or update `SurveyDefinitionImportServiceTest`: V1 backward-compat path — import a pre-Kimball export file; assert zero FK violations; assert `question_version = 0` on all answers.

- [ ] **T30** — Write or update `RespondentExportImportServiceTest`: export/import round-trip for respondent answers; assert `question_version` values are preserved.

- [ ] **T31** — Bump `pom.xml` version to `3.0.0`.

---

## Dependency Graph

```
T01 (migration)
  └── T02-T12 (export queries)
       └── T13-T24 (import logic)
            ├── T25-T26 (respondent export/import)
            │    └── T27-T30 (tests)
            └── T27-T30 (tests)
                 └── T31 (pom version bump)
```

T03–T12 may be implemented in parallel once T02 is done.
T15–T23 may be implemented in parallel once T14 is done.
T25–T26 may be implemented in parallel once T13 is done.
