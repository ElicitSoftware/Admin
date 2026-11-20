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

-- Survey V019 adds survey.translations, the content translations that travel in the definition
-- file (UC-014 BR-110, UC-017 BR-110). The console transports them like any other versioned
-- record and needs the same three grants it holds on the structural tables:
--   * SELECT for the export path (UC-013),
--   * INSERT for the install path (UC-014) and for each new version the update path opens,
--   * UPDATE because closeCurrentVersion stamps effective_to on the row being superseded, and
--     because retiring a structural element also closes every current translation of it.
-- Without them, applying a file carrying translations fails with "permission denied for table
-- translations" after the survey has already been matched by key.
GRANT SELECT, INSERT, UPDATE ON survey.translations TO ${surveyadmin_user};

-- The durable key of each translation is allocated from this sequence when the console inserts a
-- row the file created at another instance; the surrogate id comes from translations_seq.
GRANT USAGE ON SEQUENCE survey.translations_durable_seq TO ${surveyadmin_user};
GRANT USAGE ON SEQUENCE survey.translations_seq TO ${surveyadmin_user};

-- The two survey attributes the file carries beside them (base_language, content_languages) are
-- covered by the existing GRANT UPDATE on survey.surveys (V0.0.16).
