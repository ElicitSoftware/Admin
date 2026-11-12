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

-- UC-017 BR-113 (issue #157, Author UC-052): applying a definition deletes the post-survey
-- actions the file no longer lists. Each action's delivery records in survey.respondent_psa
-- reference it by foreign key, so they are deleted first, and V0.0.6 granted the runtime user
-- only SELECT and INSERT there. DELETE on survey.post_survey_actions itself was granted by
-- V0.0.2.
GRANT DELETE ON survey.respondent_psa TO ${surveyadmin_user};
