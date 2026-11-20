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

-- The respondent's login credential is now called the access code. Survey's V014 renamed
-- survey.respondents.token to access_code; PostgreSQL carries that rename into this view's
-- definition but keeps the view's output column named "token". Rename the output column so
-- the Status entity can map access_code.
--
-- On a fresh database V0.0.1 and V0.0.7 already select r.access_code (they were edited in
-- place; repair-at-start rewrites their recorded checksums on existing databases), so the
-- view already exposes access_code and there is nothing to rename. Hence the guard.
DO $$
BEGIN
    IF EXISTS (SELECT 1
               FROM information_schema.columns
               WHERE table_schema = 'survey'
                 AND table_name = 'status'
                 AND column_name = 'token') THEN
        ALTER VIEW survey.status RENAME COLUMN token TO access_code;
    END IF;
END
$$;
