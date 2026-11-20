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

-- V0.0.13 created survey.survey_log and survey.survey_log_seq as the owner but never
-- granted the runtime user anything on them. SurveyLogService writes an audit row on
-- every import/update attempt, so without these grants a survey definition apply fails
-- with "permission denied for sequence survey_log_seq" after the definition itself has
-- been written -- see V0.0.8, which grants the same way for every other table the
-- import path touches.

-- SurveyLogService inserts audit rows and reads them back in findLatestAppliedRevision.
-- The log is append-only: no UPDATE or DELETE is granted.
GRANT SELECT, INSERT ON survey.survey_log TO ${surveyadmin_user};
GRANT ALL ON SEQUENCE survey.survey_log_seq TO ${surveyadmin_user};
