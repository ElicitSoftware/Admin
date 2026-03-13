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
GRANT SELECT ON survey.answers TO ${surveyadmin_user};
GRANT SELECT ON survey.dependents TO ${surveyadmin_user};
GRANT SELECT ON survey.respondent_psa TO ${surveyadmin_user};
GRANT ALL ON SEQUENCE survey.respondent_psa_seq TO ${surveyadmin_user};