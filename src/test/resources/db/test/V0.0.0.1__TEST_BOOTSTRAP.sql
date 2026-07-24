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

-- =============================================================================
-- TEST-ONLY bootstrap migration (version 0.0.0.1 -> runs before V0.0.1).
-- =============================================================================
-- The Admin db/migration scripts are NOT self-contained: they FK to and GRANT on
-- objects owned by OTHER Elicit modules (surveys, respondents, answers, ...), and
-- reference DB roles that no migration creates. On the shared production database
-- those objects/roles already exist. On a throwaway test container they do not,
-- so this script creates the minimal set needed for db/migration to apply cleanly.
--
-- It only creates objects that db/migration ASSUMES pre-exist. Tables that
-- db/migration creates itself (departments, subjects, users, messages,
-- message_templates, message_types, user_surveys, user_departments, user_roles,
-- and the survey.status view) are intentionally NOT created here.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. Roles. The Dev Services `postgres` user is a superuser, so CREATE ROLE
--    works. PostgreSQL has no CREATE ROLE IF NOT EXISTS, so guard each one.
-- -----------------------------------------------------------------------------
DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'surveyadmin_user') THEN
        CREATE ROLE surveyadmin_user;
    END IF;
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'survey_user') THEN
        CREATE ROLE survey_user;
    END IF;
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'elicit_owner') THEN
        CREATE ROLE elicit_owner;
    END IF;
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'surveyreport_user') THEN
        CREATE ROLE surveyreport_user;
    END IF;
END
$$;

-- -----------------------------------------------------------------------------
-- 2. Schemas. `survey` is also auto-created by quarkus.flyway.owner.schemas, so
--    guard it; `surveyreport` is not managed by Flyway and must be created here.
-- -----------------------------------------------------------------------------
CREATE SCHEMA IF NOT EXISTS survey;
CREATE SCHEMA IF NOT EXISTS surveyreport;

-- -----------------------------------------------------------------------------
-- 3. survey.surveys — FK target for subjects/reports/post_survey_actions/
--    user_surveys. Columns match the Survey entity so Panache can read/write it.
-- -----------------------------------------------------------------------------
CREATE SEQUENCE IF NOT EXISTS survey.surveys_seq START WITH 1 INCREMENT BY 1;
CREATE TABLE IF NOT EXISTS survey.surveys
(
    id                  bigint NOT NULL,
    display_order       integer,
    name                character varying(255),
    title               character varying(255),
    description         character varying(2000),
    initial_display_key character varying(255),
    post_survey_url     character varying(2000),
    CONSTRAINT surveys_pk PRIMARY KEY (id)
);

-- -----------------------------------------------------------------------------
-- 4. survey.respondents — FK target for subjects; drives the survey.status view
--    (r.first_access_dt, r.finalized_dt, r.token). Columns match the Respondent
--    entity. Timestamps use timestamptz to match the post-V0.0.7 world.
-- -----------------------------------------------------------------------------
CREATE SEQUENCE IF NOT EXISTS survey.respondents_seq START WITH 1 INCREMENT BY 1;
CREATE TABLE IF NOT EXISTS survey.respondents
(
    id              bigint NOT NULL,
    created_dt      timestamptz DEFAULT CURRENT_TIMESTAMP,
    first_access_dt timestamptz,
    finalized_dt    timestamptz,
    active          boolean DEFAULT true,
    logins          integer DEFAULT 0,
    survey_id       bigint NOT NULL,
    token           character varying(255),
    CONSTRAINT respondents_pk PRIMARY KEY (id),
    CONSTRAINT respondents_surveys_fk FOREIGN KEY (survey_id) REFERENCES survey.surveys (id)
);

-- -----------------------------------------------------------------------------
-- 5. survey.reports and survey.post_survey_actions — GRANT + FK targets AND
--    eager @OneToMany children of Survey, so loading a Survey queries them.
--    Columns match ReportDefinition / PostSurveyAction.
-- -----------------------------------------------------------------------------
CREATE SEQUENCE IF NOT EXISTS survey.reports_seq START WITH 1 INCREMENT BY 1;
CREATE TABLE IF NOT EXISTS survey.reports
(
    id            bigint NOT NULL,
    survey_id     bigint NOT NULL,
    name          character varying(255),
    description   character varying(2000),
    url           character varying(2000),
    display_order integer,
    CONSTRAINT reports_pk PRIMARY KEY (id),
    CONSTRAINT reports_surveys_fk FOREIGN KEY (survey_id) REFERENCES survey.surveys (id)
);

CREATE SEQUENCE IF NOT EXISTS survey.post_survey_actions_seq START WITH 1 INCREMENT BY 1;
CREATE TABLE IF NOT EXISTS survey.post_survey_actions
(
    id              bigint NOT NULL,
    survey_id       bigint NOT NULL,
    name            character varying(255),
    description     character varying(2000),
    url             character varying(2000),
    execution_order integer,
    CONSTRAINT post_survey_actions_pk PRIMARY KEY (id),
    CONSTRAINT psa_surveys_fk FOREIGN KEY (survey_id) REFERENCES survey.surveys (id)
);

-- -----------------------------------------------------------------------------
-- 6. Remaining cross-module GRANT targets. These are never queried by the Admin
--    app; they exist only so the GRANT statements in V0.0.2/V0.0.6/V0.0.8 apply.
--    Each needs a stub table AND a stub sequence (GRANT ... ON SEQUENCE).
-- -----------------------------------------------------------------------------
CREATE SEQUENCE IF NOT EXISTS survey.answers_seq;
CREATE TABLE IF NOT EXISTS survey.answers (id bigint);
CREATE SEQUENCE IF NOT EXISTS survey.dependents_seq;
CREATE TABLE IF NOT EXISTS survey.dependents (id bigint);
CREATE SEQUENCE IF NOT EXISTS survey.dimensions_seq;
CREATE TABLE IF NOT EXISTS survey.dimensions (id bigint);
CREATE SEQUENCE IF NOT EXISTS survey.metadata_seq;
CREATE TABLE IF NOT EXISTS survey.metadata (id bigint);
CREATE SEQUENCE IF NOT EXISTS survey.ontology_seq;
CREATE TABLE IF NOT EXISTS survey.ontology (id bigint);
CREATE SEQUENCE IF NOT EXISTS survey.questions_seq;
CREATE TABLE IF NOT EXISTS survey.questions (id bigint);
CREATE SEQUENCE IF NOT EXISTS survey.relationships_seq;
CREATE TABLE IF NOT EXISTS survey.relationships (id bigint);
CREATE SEQUENCE IF NOT EXISTS survey.respondent_psa_seq;
CREATE TABLE IF NOT EXISTS survey.respondent_psa (id bigint);
CREATE SEQUENCE IF NOT EXISTS survey.sections_seq;
CREATE TABLE IF NOT EXISTS survey.sections (id bigint);
CREATE SEQUENCE IF NOT EXISTS survey.sections_questions_seq;
CREATE TABLE IF NOT EXISTS survey.sections_questions (id bigint);
CREATE SEQUENCE IF NOT EXISTS survey.select_groups_seq;
CREATE TABLE IF NOT EXISTS survey.select_groups (id bigint);
CREATE SEQUENCE IF NOT EXISTS survey.select_items_seq;
CREATE TABLE IF NOT EXISTS survey.select_items (id bigint);
CREATE SEQUENCE IF NOT EXISTS survey.steps_seq;
CREATE TABLE IF NOT EXISTS survey.steps (id bigint);
CREATE SEQUENCE IF NOT EXISTS survey.steps_sections_seq;
CREATE TABLE IF NOT EXISTS survey.steps_sections (id bigint);

-- -----------------------------------------------------------------------------
-- 7. surveyreport.fact_respondents — GRANT target only (V0.0.6/V0.0.8).
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS surveyreport.fact_respondents (respondent_id bigint);

-- -----------------------------------------------------------------------------
-- 8. Let the application roles use the schemas (postgres owns every object here,
--    so the later per-object GRANTs succeed regardless).
-- -----------------------------------------------------------------------------
GRANT USAGE ON SCHEMA survey TO surveyadmin_user, survey_user;
GRANT USAGE ON SCHEMA surveyreport TO surveyadmin_user, surveyreport_user;

-- -----------------------------------------------------------------------------
-- 9. Seed survey.surveys(id=1). V0.0.3 dev-data (which runs in test because it
--    lives under db/migration) inserts user_surveys(survey_id=1); without this
--    row that FK fails and every @QuarkusTest fails at boot.
-- -----------------------------------------------------------------------------
INSERT INTO survey.surveys (id, display_order, name, title, description)
VALUES (1, 1, 'Test Survey', 'Test Survey', 'Seeded by test bootstrap')
ON CONFLICT (id) DO NOTHING;
ALTER SEQUENCE survey.surveys_seq RESTART WITH 2;
