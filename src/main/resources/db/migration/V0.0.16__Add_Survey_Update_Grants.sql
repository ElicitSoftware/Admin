---
-- ***LICENSE_START***
-- Elicit Survey
-- %%
-- Copyright (C) 2025 - 2026 The Regents of the University of Michigan - Rogel Cancer Center
-- %%
-- PolyForm Noncommercial License 1.0.0
-- <https://polyformproject.org/licenses/noncommercial/1.0.0>
-- ***LICENSE_END***
---

-- V0.0.8 granted the runtime user SELECT and INSERT for the import path (UC-014), which only
-- ever appends. The update path (UC-017, SurveyDefinitionUpdateService) also UPDATEs:
--   * Type 2 SCD tables -- closeCurrentVersion sets effective_to = NOW() on the row being
--     superseded before inserting the new version;
--   * the single-row tables it edits in place.
-- Without these grants, re-applying a definition file fails with "permission denied for
-- table steps" after the survey has already been matched to an existing survey_key.

-- Type 2 SCD tables: closeCurrentVersion stamps effective_to on the superseded row.
GRANT UPDATE ON survey.steps TO ${surveyadmin_user};
GRANT UPDATE ON survey.sections TO ${surveyadmin_user};
GRANT UPDATE ON survey.steps_sections TO ${surveyadmin_user};
GRANT UPDATE ON survey.questions TO ${surveyadmin_user};
GRANT UPDATE ON survey.sections_questions TO ${surveyadmin_user};
GRANT UPDATE ON survey.select_groups TO ${surveyadmin_user};
GRANT UPDATE ON survey.select_items TO ${surveyadmin_user};
GRANT UPDATE ON survey.relationships TO ${surveyadmin_user};

-- Updated in place rather than versioned.
GRANT UPDATE ON survey.surveys TO ${surveyadmin_user};
GRANT UPDATE ON survey.reports TO ${surveyadmin_user};
GRANT UPDATE ON survey.post_survey_actions TO ${surveyadmin_user};
GRANT UPDATE ON survey.ontology TO ${surveyadmin_user};
GRANT UPDATE ON survey.metadata TO ${surveyadmin_user};
