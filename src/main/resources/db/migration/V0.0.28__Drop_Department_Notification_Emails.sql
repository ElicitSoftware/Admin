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

-- Issue #221: departments.notification_emails (V0.0.1) was never read, sent to or shown on the
-- department form, and no requirement asks for a notification when a respondent finishes. No
-- view or function reads it (survey.status reads departments.name, id and code), so it is
-- dropped without CASCADE. Any addresses a site entered by hand are discarded.
ALTER TABLE survey.departments DROP COLUMN IF EXISTS notification_emails;
