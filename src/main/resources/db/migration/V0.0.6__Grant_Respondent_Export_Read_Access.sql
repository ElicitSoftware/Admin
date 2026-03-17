---
-- ***LICENSE_START***
-- Elicit Survey
-- %%
-- Copyright (C) 2025 The Regents of the University of Michigan - Rogel Cancer Center
-- %%
-- PolyForm Noncommercial License 1.0.0
-- <https://polyformproject.org/licenses/noncommercial/1.0.0>
-- ***LICENSE_END***
---

-- Respondent export requires read access to these survey response tables.
-- Import requires INSERT access and sequence usage for all response tables.

-- Tables: SELECT for export, INSERT for import
GRANT SELECT, INSERT ON survey.respondents TO ${surveyadmin_user};
GRANT SELECT, INSERT ON survey.answers TO ${surveyadmin_user};
GRANT SELECT, INSERT ON survey.dependents TO ${surveyadmin_user};
GRANT SELECT, INSERT ON survey.subjects TO ${surveyadmin_user};
GRANT SELECT, INSERT ON survey.messages TO ${surveyadmin_user};
GRANT SELECT, INSERT ON survey.respondent_psa TO ${surveyadmin_user};

-- Sequences: needed for INSERT operations
GRANT ALL ON SEQUENCE survey.respondents_seq TO ${surveyadmin_user};
GRANT ALL ON SEQUENCE survey.answers_seq TO ${surveyadmin_user};
GRANT ALL ON SEQUENCE survey.dependents_seq TO ${surveyadmin_user};
GRANT ALL ON SEQUENCE survey.subjects_seq TO ${surveyadmin_user};
GRANT ALL ON SEQUENCE survey.messages_seq TO ${surveyadmin_user};
GRANT ALL ON SEQUENCE survey.respondent_psa_seq TO ${surveyadmin_user};