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

--------------------------------
-- Department surveys (UC-006 BR-172): the surveys a department's subjects may be registered
-- onto. Every registration path reads it (UC-003, UC-010); the department screen edits it.
-- A row goes with its department or its survey.
--------------------------------
CREATE TABLE survey.department_surveys
(
    department_id bigint  NOT NULL,
    survey_id     integer NOT NULL,
    CONSTRAINT department_surveys_pk PRIMARY KEY (department_id, survey_id),
    CONSTRAINT department_surveys_dep_fk FOREIGN KEY (department_id)
        REFERENCES survey.departments (id) ON DELETE CASCADE,
    CONSTRAINT department_surveys_survey_fk FOREIGN KEY (survey_id)
        REFERENCES survey.surveys (id) ON DELETE CASCADE
);

CREATE INDEX department_surveys_survey_idx ON survey.department_surveys (survey_id);

GRANT DELETE, INSERT, SELECT, UPDATE ON survey.department_surveys TO ${surveyadmin_user};

--------------------------------
-- UC-006 BR-173: the departments that exist now keep registering as they did, onto every
-- survey installed now. Departments and surveys created later start as BR-173 says.
--------------------------------
INSERT INTO survey.department_surveys (department_id, survey_id)
SELECT d.id, s.id
FROM survey.departments d
         CROSS JOIN survey.surveys s;
