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

-- Records which authored revision of a survey definition each import/update applied.
--
-- The revision is the exporting system's timestamp at the moment the .elicit file was
-- written, carried in the file's "# survey_revision:" header. It is the only identifier
-- in the format that is comparable ACROSS deployments: version numbers on the Type 2
-- structural tables are derived locally at each site (see SurveyDefinitionUpdateService),
-- so a site that joined a multi-site study late sits at a lower `version` than its peers
-- for byte-identical content. "Which revision of the instrument is this site running?" is
-- answered by the most recent successful survey_log row for the survey_key, not by
-- `questions.version` or any of its siblings.
--
-- Nullable: files written before this header existed carry no revision, and a rejected
-- attempt may fail before the header is ever read.
ALTER TABLE survey.survey_log
    ADD COLUMN IF NOT EXISTS revision timestamp with time zone;

COMMENT ON COLUMN survey.survey_log.revision IS
    'Export timestamp of the .elicit file this row applied, from its "# survey_revision:" header; NULL if the file predates the header or failed before it was read.';

-- Supports "latest revision applied for this survey_key", the query that answers which
-- revision a site is running. Partial: rows with no revision can never be the answer.
CREATE INDEX IF NOT EXISTS survey_log_revision_index
    ON survey.survey_log USING btree (survey_key, revision DESC)
    WHERE revision IS NOT NULL;
