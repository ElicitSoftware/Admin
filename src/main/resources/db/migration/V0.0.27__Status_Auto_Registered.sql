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

-- UC-002 BR-181, UC-031 BR-120, issue #96: a respondent Survey auto-registered has no subject
-- row, so the inner joins hid it from the console. The view now starts from the respondent and
-- left-joins its subject and the subject's department. Such a row has no department, name or
-- contact details; its id is the respondent's id negated, which can never equal a subject id, and
-- respondent_id, survey_id and created_dt come from the respondent. Every column keeps its name,
-- position and type, so CREATE OR REPLACE VIEW applies, and Survey's one read (xid and
-- department_code by respondent_id) gets nulls for such a row, which it already treats as no
-- identity.
CREATE OR REPLACE VIEW survey.status AS
SELECT COALESCE(s.id, -r.id::bigint) AS id,
       r.id::bigint AS respondent_id,
       r.survey_id::bigint AS survey_id,
       s.firstname,
       s.lastname,
       s.dob,
       s.email,
       s.middlename,
       s.phone,
       s.xid,
       COALESCE(s.created_dt, r.created_dt) AS created_dt,
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
LEFT JOIN survey.subjects s ON s.respondent_id = r.id
LEFT JOIN survey.departments d ON s.department_id = d.id;

GRANT SELECT ON survey.status TO ${surveyadmin_user};
GRANT SELECT ON survey.status TO ${survey_user};
