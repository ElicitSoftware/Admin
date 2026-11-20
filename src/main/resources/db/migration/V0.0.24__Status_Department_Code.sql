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

-- UC-018 (Reference), Survey UC-015 BR-001, issue #159, Elicit#95: a post-survey action that
-- sends the subject's External ID also sends the department's code, because an External ID is
-- unique only within its department (subjects_xid_department_un). Survey reads both through this
-- view, which its role may already read, so the code is appended as the view's last column.
-- CREATE OR REPLACE VIEW keeps every existing column, in order, under the name V0.0.17 gave it
-- (access_code), so nothing that reads the view today changes.
CREATE OR REPLACE VIEW survey.status AS
SELECT s.id,
       s.respondent_id,
       s.survey_id,
       s.firstname,
       s.lastname,
       s.dob,
       s.email,
       s.middlename,
       s.phone,
       s.xid,
       s.created_dt,
       r.finalized_dt,
       d.name AS department_name,
       d.id AS department_id,
       r.access_code,
       CASE
           WHEN r.first_access_dt IS NULL AND r.finalized_dt IS NULL THEN 'Not Started'::text
           WHEN r.first_access_dt IS NOT NULL AND r.finalized_dt IS NULL THEN 'In Progress'::text
           ELSE 'Finished'::text
       END AS status,
       d.code AS department_code
FROM survey.respondents r
JOIN survey.subjects s ON s.respondent_id = r.id
JOIN survey.departments d ON s.department_id = d.id;

GRANT SELECT ON survey.status TO ${surveyadmin_user};
GRANT SELECT ON survey.status TO ${survey_user};
