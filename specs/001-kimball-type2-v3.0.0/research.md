# Research: Kimball Type 2 SCD Support — Admin v3.0.0

**Branch**: `001-kimball-type2-v3.0.0` | **Date**: 2026-05-12

---

## Master Reference

All design decisions for the Kimball Type 2 SCD transition are documented in the master research file:

**`Admin/research/Kimball_type2.md`** — Read this file for full context on:
- Export format V2 design decisions and field ordering
- Dual surrogate+durable id map rationale
- V1 backward-compat import strategy
- `question_version` in answers format
- Metadata column rename tracking
- V0.0.9 migration content (sequence grants)

---

## Key Decisions Summary

| Decision | Choice | Rationale |
|---|---|---|
| New export format version constant | `ELICIT_SURVEY_EXPORT_V2` | Breaking change — V2 files contain durable key columns |
| Header line for format detection | `# schema_version: V2_KIMBALL_TYPE2` | Allows V1/V2 path split on import without field count heuristics |
| Durable id column name in export | `source_id` | Consistent with Survey's naming convention for cross-DB transfer |
| Import: durable sequence calls | `nextval('survey.{table}_durable_seq')` | Admin must not reuse source durable ids — target DB allocates its own |
| V1 compat: durable id handling | Allocate new durable id; apply `effective_to = '9999-12-31 23:59:59+00'` | V1 files have no durable ids; still need Type 2 defaults |
| `question_version` in answers | Added as field index 6 in answer rows | `answers` table gains `question_version INTEGER NOT NULL DEFAULT 0` in Survey V019 |
| Metadata column rename | `step_section_id` → `steps_sections_id`, `section_question_id` → `sections_question_id` | Survey V019 renames these columns |
| Admin UI version surfacing | Out of scope for 3.0.0 | Listed in research doc as optional enhancement |

---

## Open Questions

All questions from the original research session have been resolved (see `Admin/research/Kimball_type2.md`).

No open questions remain for 3.0.0.
