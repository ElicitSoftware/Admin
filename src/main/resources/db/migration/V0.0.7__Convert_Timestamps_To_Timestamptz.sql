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

-- Convert timestamp columns to timestamptz for consistent timezone handling
-- Assumes existing data is in the database's local timezone (America/Detroit)

-- Drop the status view first (it depends on subjects.created_dt)
DROP VIEW IF EXISTS survey.status;

-- messages table
ALTER TABLE survey.messages 
    ALTER COLUMN created_dt TYPE TIMESTAMPTZ USING created_dt AT TIME ZONE 'America/Detroit',
    ALTER COLUMN sent_dt TYPE TIMESTAMPTZ USING sent_dt AT TIME ZONE 'America/Detroit';

-- subjects table
ALTER TABLE survey.subjects 
    ALTER COLUMN created_dt TYPE TIMESTAMPTZ USING created_dt AT TIME ZONE 'America/Detroit';

-- Recreate the status view
CREATE VIEW survey.status AS
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
       r.token,
       CASE
           WHEN r.first_access_dt IS NULL AND r.finalized_dt IS NULL THEN 'Not Started'::text
           WHEN r.first_access_dt IS NOT NULL AND r.finalized_dt IS NULL THEN 'In Progress'::text
           ELSE 'Finished'::text
       END AS status
FROM survey.respondents r
JOIN survey.subjects s ON s.respondent_id = r.id
JOIN survey.departments d ON s.department_id = d.id;

-- Grant permissions on recreated view
GRANT SELECT ON survey.status TO ${surveyadmin_user};
GRANT SELECT ON survey.status TO ${survey_user};
