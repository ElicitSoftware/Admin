---
-- ***LICENSE_START***
-- Elicit Survey
-- %%
-- Copyright (C) 2025 The Regents of the University of Michigan - Rogel Cancer Center
-- %%
-- PolyForm Noncommercial License 1.0.0
-- <https://polyformproject.org/licenses/noncommercial/1.0.0>
-- ***LICENSE_END***
---

-- Audit trail for Admin's survey-definition import/update write path (UC-014/UC-017).
-- One row per attempt, success or failure, so a rejected duplicate-key import or a
-- mismatched-key update is on the record, not just successes.
--------------------------------
-- Survey Log
--------------------------------
CREATE SEQUENCE survey.survey_log_seq START WITH 1 INCREMENT BY 1;
CREATE TABLE survey.survey_log
(
    id              bigint NOT NULL,
    -- Nullable: a rejected attempt (bad header, key mismatch, duplicate key) may never
    -- resolve to a destination row, but the attempt is still logged.
    survey_id       integer,
    survey_key      uuid,
    action          character varying(20)  NOT NULL,
    outcome         character varying(20)  NOT NULL,
    -- Username at time of action, not a FK to users.id — an audit row should survive
    -- that user account later being deactivated or deleted.
    performed_by    character varying(255) NOT NULL,
    performed_at    timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    file_name       character varying(255),
    summary         text,
    error_message   text,
    CONSTRAINT survey_log_pk PRIMARY KEY (id),
    CONSTRAINT survey_log_action_ck CHECK (action IN ('IMPORT', 'UPDATE')),
    CONSTRAINT survey_log_outcome_ck CHECK (outcome IN ('SUCCESS', 'FAILURE')),
    CONSTRAINT survey_log_surveys_fk FOREIGN KEY (survey_id)
        REFERENCES survey.surveys (id)
        ON UPDATE NO ACTION
        ON DELETE NO ACTION
);
CREATE INDEX IF NOT EXISTS survey_log_survey_index ON survey.survey_log USING btree (survey_id ASC NULLS LAST);
CREATE INDEX IF NOT EXISTS survey_log_survey_key_index ON survey.survey_log USING btree (survey_key);
CREATE INDEX IF NOT EXISTS survey_log_performed_at_index ON survey.survey_log USING btree (performed_at DESC);
