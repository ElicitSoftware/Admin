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
ALTER SEQUENCE survey.message_types_seq RESTART WITH 1;
ALTER SEQUENCE survey.departments_seq RESTART WITH 1;
ALTER SEQUENCE survey.message_templates_seq RESTART WITH 1;
ALTER SEQUENCE survey.users_seq RESTART WITH 1;
-- message_types
INSERT INTO survey.message_types(id, name) VALUES(NEXTVAL('survey.message_types_seq'),'email');
-- No department is seeded (Admin UC-028, C-016). A fresh deployment starts with none: the
-- first administrator creates one through Admin > Departments, and creating it assigns it to
-- them. The message template that belonged to the seeded department went with it --
-- survey.message_templates.department_id is NOT NULL with a foreign key to survey.departments,
-- so it cannot outlive it. The sequences above still restart at 1, so the first department and
-- template created through the console get id 1, which departments.default_message_id defaults to.
-- Alice Admin
INSERT INTO survey.users(id, username, first_name, last_name) VALUES (NEXTVAL('survey.users_seq'), 'admin','Alice', 'Admin');
-- Conditional on survey 1 existing: this schema is created before any survey definition is
-- imported, so on a fresh deployment survey.surveys is empty and an unconditional insert
-- fails on user_surveys_surveys_fk, taking the whole application down at startup. The dev
-- user still gets created either way; only the survey assignment waits for a survey.
INSERT INTO survey.user_surveys(user_id, survey_id)
    SELECT CURRVAL('survey.users_seq'), 1
    WHERE EXISTS (SELECT 1 FROM survey.surveys WHERE id = 1);
-- Umar User
INSERT INTO survey.users(id, username, first_name, last_name) VALUES (NEXTVAL('survey.users_seq'), 'user','Umar', 'User');
INSERT INTO survey.user_surveys(user_id, survey_id)
    SELECT CURRVAL('survey.users_seq'), 1
    WHERE EXISTS (SELECT 1 FROM survey.surveys WHERE id = 1);
