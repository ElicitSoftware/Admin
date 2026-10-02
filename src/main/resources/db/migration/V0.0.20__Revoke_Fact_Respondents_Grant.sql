--
-- ***LICENSE_START***
-- Elicit Admin
-- %%
-- Copyright (C) 2025 - 2026 The Regents of the University of Michigan - Rogel Cancer Center
-- %%
-- PolyForm Noncommercial License 1.0.0
-- <https://polyformproject.org/licenses/noncommercial/1.0.0>
-- ***LICENSE_END***
--
-- V0.0.20: let go of surveyreport.fact_respondents.
--
-- V0.0.2 granted surveyadmin_user INSERT, SELECT and UPDATE on the site-wide
-- surveyreport.fact_respondents, which no code of this console ever used. Survey V021 drops that
-- table: every survey now has a reporting schema of its own, in which fact_respondents is a view
-- (Survey UC-008 BR-006/BR-010), and the console never touches a reporting schema (UC-030 BR-117).
-- Survey and Admin each run their own migrations against the shared database at startup, in no
-- fixed order, so the revoke is conditional on the table still being there: on a database Survey
-- has already upgraded there is nothing to revoke, and on one it has not yet, the grant goes
-- before the table does.
--
DO $$
BEGIN
    IF to_regclass('surveyreport.fact_respondents') IS NOT NULL THEN
        REVOKE INSERT, SELECT, UPDATE ON surveyreport.fact_respondents FROM ${surveyadmin_user};
    END IF;
END $$;
