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

-- The Participation page (UC-031 BR-123 to BR-125) summarizes each marked question by its type,
-- so it reads survey.question_types. The console never needed the table before: the definition
-- file carries a question's type by id, and the export and apply paths copy that id without
-- looking it up. Without this grant every survey with an ADMINISTRATOR mark fails on the page
-- with "permission denied for table question_types"; the tests run as the container superuser
-- and only ParticipationServiceTest's surveyadmin_user case can see it.
GRANT SELECT ON survey.question_types TO ${surveyadmin_user};
