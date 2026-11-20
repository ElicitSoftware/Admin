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

GRANT ALL ON SEQUENCE survey.message_types_seq TO ${surveyadmin_user};
GRANT DELETE, INSERT, SELECT, UPDATE ON survey.message_types TO ${surveyadmin_user};
GRANT ALL ON SEQUENCE survey.departments_seq TO ${surveyadmin_user};
GRANT DELETE, INSERT, SELECT, UPDATE ON survey.departments TO ${surveyadmin_user};
GRANT ALL ON SEQUENCE survey.subjects_seq TO ${surveyadmin_user};
GRANT DELETE, INSERT, SELECT, UPDATE ON survey.subjects TO ${surveyadmin_user};
GRANT ALL ON SEQUENCE survey.users_seq TO ${surveyadmin_user};
GRANT DELETE, INSERT, SELECT, UPDATE ON survey.users TO ${surveyadmin_user};
GRANT DELETE, INSERT, SELECT, UPDATE ON survey.user_surveys TO ${surveyadmin_user};
GRANT DELETE, INSERT, SELECT, UPDATE ON survey.user_departments TO ${surveyadmin_user};
GRANT ALL ON SEQUENCE survey.message_templates_seq TO ${surveyadmin_user};
GRANT DELETE, INSERT, SELECT, UPDATE ON survey.message_templates TO ${surveyadmin_user};
GRANT ALL ON SEQUENCE survey.messages_seq TO ${surveyadmin_user};
GRANT DELETE, INSERT, SELECT, UPDATE ON survey.messages TO ${surveyadmin_user};
GRANT DELETE, INSERT, SELECT, UPDATE ON survey.post_survey_actions TO ${surveyadmin_user};
GRANT DELETE, INSERT, SELECT, UPDATE ON survey.reports TO ${surveyadmin_user};
GRANT ALL ON SEQUENCE survey.respondents_seq TO ${surveyadmin_user};
GRANT DELETE, INSERT, SELECT, UPDATE ON survey.respondents TO ${surveyadmin_user};
-- surveyreport.fact_respondents was the site-wide fact table Survey's V002 used to create. Since
-- Survey V021 every survey's star lives in a schema of its own and fact_respondents is a view
-- there, so a fresh database never has this table; the grant (never used by this console) is
-- kept only for a database that still has it. Edited in place for that reason -- see README.md.
DO $$
BEGIN
    IF to_regclass('surveyreport.fact_respondents') IS NOT NULL THEN
        GRANT INSERT, SELECT, UPDATE ON surveyreport.fact_respondents TO ${surveyadmin_user};
    END IF;
END $$;
GRANT SELECT ON survey.status TO ${surveyadmin_user};
GRANT SELECT ON survey.status TO ${survey_user};
