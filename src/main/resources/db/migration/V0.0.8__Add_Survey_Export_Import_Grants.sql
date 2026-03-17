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

-- Survey export requires read access to these survey response tables.
-- Import requires INSERT access and sequence usage for all response tables.

-- Tables: SELECT for survey export, INSERT for import
GRANT SELECT, INSERT ON survey.dimensions TO ${surveyadmin_user};
GRANT SELECT, INSERT ON survey.surveys TO ${surveyadmin_user};
GRANT SELECT, INSERT ON survey.select_groups TO ${surveyadmin_user};
GRANT SELECT, INSERT ON survey.select_items TO ${surveyadmin_user};
GRANT SELECT, INSERT ON survey.steps TO ${surveyadmin_user};
GRANT SELECT, INSERT ON survey.sections TO ${surveyadmin_user};
GRANT SELECT, INSERT ON survey.steps_sections TO ${surveyadmin_user};
GRANT SELECT, INSERT ON survey.questions TO ${surveyadmin_user};
GRANT SELECT, INSERT ON survey.sections_questions TO ${surveyadmin_user};
GRANT SELECT, INSERT ON survey.relationships TO ${surveyadmin_user};
GRANT SELECT, INSERT ON survey.reports TO ${surveyadmin_user};
GRANT SELECT, INSERT ON survey.post_survey_actions TO ${surveyadmin_user};
GRANT SELECT, INSERT ON survey.ontology TO ${surveyadmin_user};
GRANT SELECT, INSERT ON survey.metadata TO ${surveyadmin_user};

-- Sequences: needed for survey INSERT operations that auto-generate IDs
GRANT ALL ON SEQUENCE survey.dimensions_seq TO ${surveyadmin_user};
GRANT ALL ON SEQUENCE survey.surveys_seq TO ${surveyadmin_user};
GRANT ALL ON SEQUENCE survey.select_groups_seq TO ${surveyadmin_user};
GRANT ALL ON SEQUENCE survey.select_items_seq TO ${surveyadmin_user};
GRANT ALL ON SEQUENCE survey.steps_seq TO ${surveyadmin_user};
GRANT ALL ON SEQUENCE survey.sections_seq TO ${surveyadmin_user};
GRANT ALL ON SEQUENCE survey.steps_sections_seq TO ${surveyadmin_user};
GRANT ALL ON SEQUENCE survey.questions_seq TO ${surveyadmin_user};
GRANT ALL ON SEQUENCE survey.sections_questions_seq TO ${surveyadmin_user};
GRANT ALL ON SEQUENCE survey.relationships_seq TO ${surveyadmin_user};
GRANT ALL ON SEQUENCE survey.reports_seq TO ${surveyadmin_user};
GRANT ALL ON SEQUENCE survey.post_survey_actions_seq TO ${surveyadmin_user};
GRANT ALL ON SEQUENCE survey.ontology_seq TO ${surveyadmin_user};
GRANT ALL ON SEQUENCE survey.metadata_seq TO ${surveyadmin_user};
