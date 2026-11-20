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

-- Survey V023 adds survey.report_items, the marks of what a survey's default reports show
-- (Survey UC-013), and grants it to survey_user only. The console needs it too:
--   * INSERT for the install path (UC-014 BR-112), which allocates each id from report_items_seq,
--   * UPDATE and DELETE for the update path (UC-017 BR-113), which rewrites a mark in place and
--     removes one whose key has left the file,
--   * SELECT for the export path (UC-013) and for the Participation page (UC-031), which reads
--     the ADMINISTRATOR marks.
-- Without them, applying a file that carries report_items fails with "permission denied for
-- table report_items" on a real database; the tests run as the container superuser and never saw
-- it.
GRANT DELETE, INSERT, SELECT, UPDATE ON survey.report_items TO ${surveyadmin_user};
GRANT USAGE ON SEQUENCE survey.report_items_seq TO ${surveyadmin_user};
